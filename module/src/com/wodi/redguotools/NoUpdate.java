package com.wodi.redguotools;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.ArrayList;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * 屏蔽「发现新版本」升级弹窗。
 *
 * <h3>挂载点（红果 7.3.9.32 反编译确证）</h3>
 * {@code com.dragon.read.component.biz.impl.update.UpdateManager.h()}
 * —— 该方法是升级弹窗的**唯一创建/弹出入口**：
 * <pre>
 *   h()  →  PopProxy.popup(activity, popType, runnable, listener)
 * </pre>
 * 三条 UI 通路最终都汇聚到这里：
 * <ul>
 *   <li>{@code UpdateManager$c}（Pop 处理器，注册在 official_upgrade_dialog / gray_upgrade_dialog）</li>
 *   <li>{@code UpdateManager$a}（升级状态回调）</li>
 *   <li>{@code NsUpdateServiceImpl}（升级服务）</li>
 * </ul>
 *
 * <p>四处调用点（含 {@code UpdateManager} 自身）都**不读取返回值**：
 * 三处直接 {@code return-void}，一处只把它包进 {@code WeakReference}
 * （`WeakReference(null)` 合法）。所以直接返回 {@code null} 并跳过原方法调用是安全的。
 *
 * <h3>为什么不用宿主自带的开关</h3>
 * {@code app_update_config_v733} 里的 {@code enableOfficialUpgrade==false} 确实会
 * 让 {@code h()} 提前返回（日志：「成本优化配置-关闭正式版的主动升级能力」），
 * 但那个判据**只在「当前是非正式版本」分支里才被读到**（`versionCode % 100` 落在
 * 32~63 之间才进那个分支）。改装版 / 非官方包会绕过它，拦截不彻底。
 * 直接拦 {@code h()} 则无此缺口。
 *
 * <h3>为什么还要第二层：第三方改版的自建弹窗</h3>
 * 第三方改版（如包名 {@code com.phoenix.read} 的「会员纯净修复版」）**不走官方通路**，
 * 而是自己拼了一套更新提示 Dialog：
 * <pre>
 *   文案全部来自远端接口（本地 dex 搜不到「更新内容」「纯净修复版」等字样）
 *   控件全是 framework 原生件（FrameLayout/TextView/Button），无自定义类名
 *   官方 UpdateManager.h() 从未被调用 → 第一层拦不到（日志只有 install 没有 blocked）
 * </pre>
 * 反编译确认改版注入代码在 {@code classes25/26.dex}，用 Unicode 类名混淆，
 * 更新文案又分片存储，无法静态定位实现类。故第二层改用**内容签名**：
 * hook {@link Dialog#show()}，遍历弹窗视图树找特征文案，命中即 cancel。
 * 该方式不依赖类名/混淆名，第三方再改版也照样命中。
 *
 * <h3>签名判据（三条同时命中才算升级弹窗，避免误伤）</h3>
 * <ol>
 *   <li>含「更新内容」或「立即更新」；</li>
 *   <li>含版本号展示（{@code x.y.z.w (12345)} 或包名 {@code com.phoenix.read}）；</li>
 *   <li>含版本箭头「▶」。</li>
 * </ol>
 * 单用第 1 条会误伤正常的功能更新说明弹窗；三条齐备在本 App 内只对应改版升级框。
 */
final class NoUpdate {

    private static volatile boolean sInit;
    private static volatile String sStatus;
    private static volatile String sOfficial = "?";
    private static volatile String sSignature = "?";

    /** 供 onActivityCreated 延迟落盘（hook 安装期 logFile 还不可用）。 */
    static String status() {
        return "official=" + sOfficial + " signature=" + sSignature;
    }

    static void hook(XposedModule mod, ClassLoader cl) throws Throwable {
        if (sInit) {
            return;
        }
        boolean official = hookOfficial(mod, cl);
        boolean signature = hookSignature(mod);
        if (!official && !signature) {
            sStatus = "FAILED: no hook installed";
            throw new IllegalStateException("no-update: no hook installed");
        }
        sInit = true;
    }

    /** 第一层：拦官方 {@code UpdateManager.h()}（正式包走这条路）。 */
    private static boolean hookOfficial(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> um = Class.forName(
                    "com.dragon.read.component.biz.impl.update.UpdateManager", false, cl);
            Method h = um.getDeclaredMethod("h");
            h.setAccessible(true);
            mod.hook(h)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Context c = UiController.appCtx();
                        if (c != null && Config.blockUpdate(c)) {
                            UiController.logFile("update: popup blocked (official)");
                            return null;   // 不调原方法 = 不弹升级框
                        }
                        return chain.proceed();
                    });
            sOfficial = "ok";
            return true;
        } catch (Throwable t) {
            // 改版包可能没有这个类，不算失败
            sOfficial = "none";
            return false;
        }
    }

    /**
     * 第二层：拦 {@link Dialog#show()}，按内容签名识别改版自建的更新弹窗。
     *
     * <p><b>关键时序坑（v2.92 修）</b>：在 {@code show()} <i>之前</i>遍历视图树是<b>无效</b>的
     * —— Dialog 的内容是 show() 内部才 inflate 并 attach 到窗口的，此刻 decorView 里
     * 一个 TextView 都没有，判据恒不命中（表现为「日志有 blocked，弹窗却还在」，
     * 其实那条日志来自另一个更早、内容已就绪的弹窗）。
     *
     * <p>正确做法：<b>先放行 show()，等窗口落地后再判</b>。命中就把 decorView 置
     * {@code GONE} 并从窗口移除，等效于"从未弹出"，用户看不到也点不到。
     * 不能用 dismiss/cancel —— 弹窗若被重复 show 或由宿主持引用，改掉视图比关窗口更稳。
     */
    private static boolean hookSignature(XposedModule mod) {
        try {
            Method show = Dialog.class.getDeclaredMethod("show");
            show.setAccessible(true);
            mod.hook(show)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object r = chain.proceed();   // 放行：让弹窗正常 inflate
                        Dialog dlg = (Dialog) chain.getThisObject();
                        if (dlg != null) {
                            scheduleCheck(dlg);
                        }
                        return r;
                    });
            sSignature = "ok";
            return true;
        } catch (Throwable t) {
            sSignature = "FAILED: " + t;
            return false;
        }
    }

    /**
     * 弹窗 show 之后再异步检查一次内容。
     *
     * <p>用 {@code post} 延迟到下一帧，确保 inflate 与布局都已完成；
     * 同一弹窗只查一次（查完移除回调），避免反复遍历视图树。
     */
    private static void scheduleCheck(final Dialog dlg) {
        try {
            final View decor = dlg.getWindow() != null
                    ? dlg.getWindow().getDecorView() : null;
            if (decor == null) {
                return;
            }
            decor.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Context c = UiController.appCtx();
                        if (c == null || !Config.blockUpdate(c) || dlg.isShowing() == false) {
                            return;
                        }
                        try {
                            // 同一实例已处理过：宿主 dismiss 后可能再次 show()，不再重复遍历
                            if (BLOCKED.contains(dlg)) {
                                hideDialog(dlg, decor);
                                return;
                            }
                        } catch (Throwable ignored) {
                        }
                        if (!isModUpdateDialog(dlg)) {
                            return;
                        }
                        UiController.logFile("update: popup blocked (mod dialog)");
                        hideDialog(dlg, decor);
                        // 宿主可能在 onDismiss 里再次 show()，标记后不再重复处理
                        try {
                            BLOCKED.add(dlg);
                        } catch (Throwable ignored) {
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    /**
     * 隐藏弹窗：decorView 置 GONE 并从窗口移除。
     *
     * <p>只置 GONE 不够 —— Dialog 窗口仍在栈上会挡触摸，所以两者都做。
     * 顺序上先 GONE 再 removeView：GONE 后 remove 不会触发可见性回调里的异常。
     */
    private static void hideDialog(Dialog dlg, View decor) {
        try {
            decor.setVisibility(View.GONE);
        } catch (Throwable ignored) {
        }
        try {
            // 移除 decorView：Dialog 的 window token 随窗口走，只置 GONE 窗口仍在栈上会挡触摸。
            // Window#setDecorViewVisibility 已废弃，改用从 ViewRootImpl 摘除的方式不可行，
            // 因此这里走 Dialog 自己的 dismiss 通道把它整个撤掉（幂等，重复调用安全）。
            dlg.dismiss();
        } catch (Throwable ignored) {
        }
        try {
            decor.setVisibility(View.GONE);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 判断一个 Dialog 是否为改版自建的更新弹窗。
     *
     * <p>改版作者换过 UI：先是「红果免费短剧 / com.phoenix.read / 7.4.0.32 (74032) \u25b6 7.4.1.32 /
     * 更新内容：\u2026」，后改成「温馨提示 / 2026.10.10 / \u7ea2\u679c\u77ed\u52677.4.1.32\u2026\u5df2\u66f4\u65b0 /
     * >>\u7acb\u5373\u66f4\u65b0<<」。
     * <p>因此判据不能绑死版式（版本号括号格式、\u25b6 箭头、标题字样都变过），改为
     * <b>语义组合</b>：既含「更新」类动作词（更新/升级/新版本），又含改版特有的
     * 「修复版/纯净版/免费获取/断网安装」等话术，二者齐备才拦。
     *
     * <p>只判「更新」会误伤正常功能更新说明；只判改版话术又会漏掉别的变体。
     * 两者必须同时出现，且全文用 contains 而非 equals —— 按钮文本常带
     * {@code >>} {@code <<} 等装饰前缀。
     */
    private static boolean isModUpdateDialog(Dialog dlg) {
        try {
            View decor = dlg.getWindow() != null
                    ? dlg.getWindow().getDecorView() : null;
            if (decor == null) {
                return false;
            }
            ArrayList<String> texts = new ArrayList<>();
            collectTexts(decor, texts, 0);
            if (texts.isEmpty()) {
                return false;
            }
            // 标题型 TextView 权重最高：弹窗正文常整段塞进一个 TextView，
            // 版本号与话术分散在其中；标题则几乎总是独立的一行。
            StringBuilder all = new StringBuilder();
            String head = "";
            for (String t : texts) {
                all.append(t).append('\n');
                if (t.length() <= 24 && head.isEmpty()) {
                    head = t;
                }
            }
            String body = all.toString();

            boolean action = containsAny(body, ACTION_WORDS);        // 更新动作词
            boolean modTone = containsAny(body, MOD_TONE_WORDS);      // 改版话术
            boolean toneInTitle = containsAny(head, MOD_TONE_WORDS);  // 话术出现在标题
            boolean pkg = containsHostPkg(body);                      // 包名（任一渠道）
            boolean verNo = VERSION_RE.matcher(body).find();          // 7.4.0.32 (74032)
            boolean verLoose = LOOSE_VERSION_RE.matcher(body).find();// 7.4.1.32 任意形式
            boolean date = DATE_RE.matcher(body).find();             // 2026.10.10

            // 路径 A：改版话术 + 更新动作（最强信号，正文里出现即可）
            if (action && modTone) {
                return true;
            }
            // 路径 B：包名 / 版本号（含宽松形式）/ 日期 + 更新动作
            if (action && (pkg || verNo || verLoose)) {
                return true;
            }
            // 路径 C：标题即改版话术（如「温馨提示」+ 正文有版本号与日期）
            if (toneInTitle && (verNo || verLoose || date) && containsAny(body, ACTION_WORDS)) {
                return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean containsAny(String text, String[] words) {
        for (int i = 0; i < words.length; i++) {
            if (text.contains(words[i])) {
                return true;
            }
        }
        return false;
    }

    /** 形如 {@code 7.4.0.32 (74032)} 的版本展示。 */
    private static final java.util.regex.Pattern VERSION_RE =
            java.util.regex.Pattern.compile("\\d+\\.\\d+\\.\\d+\\.\\d+\\s*\\(\\d+\\)");
    /** 宽松版本号：只要 {@code 7.4.1.32} 这种四段式就算（改版新版正文里就是它）。 */
    private static final java.util.regex.Pattern LOOSE_VERSION_RE =
            java.util.regex.Pattern.compile("\\b\\d+\\.\\d+\\.\\d+\\.\\d+\\b");
    /** 形如 {@code 2026.10.10} 的发布日期。 */
    private static final java.util.regex.Pattern DATE_RE =
            java.util.regex.Pattern.compile("\\b\\d{4}\\.\\d{1,2}\\.\\d{1,2}\\b");

    /** 宿主包名（改版弹窗有时会把它和版本号一起显示出来）。三渠道都算。 */
    private static boolean containsHostPkg(String body) {
        for (String p : Hosts.PKGS) {
            if (body.contains(p)) {
                return true;
            }
        }
        return false;
    }

    /** 已处理过的弹窗实例。WeakHashMap 随 Dialog 被回收自动清理，不会泄漏。
     *  用它而不是 {@code View.setTag(int,Object)} —— 后者的 key 必须是合法资源 id，
     *  随便编一个会被 {@code IllegalArgumentException} 拒绝。 */
    private static final java.util.Set<Dialog> BLOCKED =
            java.util.Collections.synchronizedSet(
                    java.util.Collections.newSetFromMap(
                            new java.util.WeakHashMap<Dialog, Boolean>()));

    // 特征文案一律用 Unicode 转义书写：构建链存在中文编码问题，
    // 源码里直接写中文字面量会在复制到构建目录后编译失败。
    /** 更新类动作词：命中其一即视为「在谈更新」。 */
    private static final String[] ACTION_WORDS = {
            "\u66f4\u65b0",      // 更新
            "\u5347\u7ea7",      // 升级
            "\u65b0\u7248\u672c",  // 新版本
            "\u65b0\u7248",          // 新版
    };

    /** 改版作者特有的话术：用于把「改版更新框」和正常功能更新弹窗区分开。 */
    private static final String[] MOD_TONE_WORDS = {
            "\u4fee\u590d\u7248",        // 修复版
            "\u7eaf\u51c0\u7248",        // 纯净版
            "\u4fee\u590d\u5361\u6b7b",  // 修复卡死
            "\u5f7b\u5e95\u4fee\u590d",  // 彻底修复
            "\u514d\u8d39\u83b7\u53d6",  // 免费获取
            "\u65ad\u7f51\u5b89\u88c5",  // 断网安装
            "\u7eaf\u51c0",                    // 纯净
            "\u4fee\u590d\u95ea\u9000",  // 修复闪退
    };

    /** 递归收集视图树里的文本，限制深度与总量防止异常布局拖慢。 */
    private static void collectTexts(View v, ArrayList<String> out, int depth) {
        if (v == null || depth > 12 || out.size() > 60) {
            return;
        }
        if (v instanceof TextView) {
            CharSequence cs = ((TextView) v).getText();
            if (cs != null && cs.length() > 0) {
                out.add(cs.toString());
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            int n = vg.getChildCount();
            for (int i = 0; i < n; i++) {
                collectTexts(vg.getChildAt(i), out, depth + 1);
            }
        }
    }
}
