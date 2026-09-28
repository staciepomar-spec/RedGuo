package com.wodi.redguotools;

import android.content.Context;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * 播放器微调：默认最高画质 + 自定义倍速（1.0~4.0 无级）。
 *
 * 两个挂载点（7.3.9.32 实测，均为诊断日志确认过的真实调用路径）：
 *   1. 倍速  com.ss.ttm.player.PlaybackParams.setSpeed(F)
 *      弹层选倍速、起播时宿主都通过它把速度写进播放器（1.0 在起播时写）。
 *      启用自定义倍速时替换参数 —— 弹层按钮本身有限位，这里没有。
 *   2. 画质  com.ss.ttvideoengine.TTVideoEngineImpl.configResolution(Resolution)
 *      起播时宿主以所选画质（如 540p）调用；替换为 Resolution.ExtremelyHigh。
 *      引擎对不可用档位自带回退。
 * 设置改动对「下一个起播的视频」生效（短剧一集很短，自动连播即见效）。
 *
 * <h3>倍速 hook 为什么要「有条件」替换（v2.45 修正）</h3>
 * 宿主除「用户选速」外还有一条**临时倍速**通路：在画面边缘长按会临时提速，
 * 松手回落。该通路与选速走的是同一个 {@code PlaybackParams.setSpeed}。
 * 早期实现无条件把每次调用都改写为自定义值，等于把长按提速也一并抹平 ——
 * 表现为「设了自定义倍速后，长按边缘倍速不生效」。
 *
 * v2.42~2.44 曾按「调用值是否等于自定义值」区分，**该判据是错的**，已废弃：
 * 反编译 e55/l.smali:894 证实宿主长按目标值**硬编码为 2.0f**
 * （{@code const/high16 v0, 0x40000000}），与用户自定义值无关。于是：
 *   - 自定义 = 2.0x → 长按 2.0 与自定义同值，被判「临时倍速」放行 → 侥幸能用；
 *   - 自定义 ≠ 2.0x → 长按 2.0 被判「用户选速」，被改写成自定义值 → **长按失效**。
 * 实测现象「设置了自定义速度以后长按倍速就不生效」完全对应后者。
 *
 * 现在改用**调用来源**判据（数值同值不可区分，只能看来源）：
 *   钩子挂在 {@code PlaybackParams.setSpeed(F)}，命中时回溯调用栈，
 *   若栈中出现长按通路的专属帧（e55/l、e55/b、GESTURE 相关）则原样放行，
 *   否则视为用户选速 / 起播写速，改写为自定义值。
 * 判据不依赖具体数值，因此自定义值取任意档都不影响长按。
 *
 * 长按通路的调用链（7.3.9.32 实测反编译）：
 *   e55/l.onLongPress → l.c(...) → bs4/c.o1(...) 或 e55/b.a(...)
 *   → v2/view/adapter/d.a(...) → V4(...) → e65/f.setPlaySpeed(I)
 *   → e65/w.setPlaySpeed(I) → PlaybackParams.setSpeed(I/100f)
 */
final class PlayerTweaks {

    private static volatile boolean sInit;
    private static volatile Object sMaxRes;   // Resolution.ExtremelyHigh（兜底 SuperHigh/High）
    private static volatile String sStatus;   // 安装结果（Activity 出现后延迟落盘）

    /** 供 onActivityCreated 延迟落盘：hook 安装在 Activity 出现前，logFile 当时不可用。 */
    static String status() {
        return sStatus;
    }

    static void hook(XposedModule mod, ClassLoader cl) throws Throwable {
        if (sInit) {
            return;
        }
        try {
            Class<?> resCls = Class.forName("com.ss.ttvideoengine.Resolution", false, cl);
            sMaxRes = staticRes(resCls, "ExtremelyHigh");
            if (sMaxRes == null) {
                sMaxRes = staticRes(resCls, "SuperHigh");
            }
            if (sMaxRes == null) {
                sMaxRes = staticRes(resCls, "High");
            }

            // 1) 倍速：PlaybackParams.setSpeed(F)
            Class<?> pp = Class.forName("com.ss.ttm.player.PlaybackParams", false, cl);
            Method setSpeed = pp.getDeclaredMethod("setSpeed", float.class);
            setSpeed.setAccessible(true);
            mod.hook(setSpeed).setId("rgPlayerSpeed")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        float orig = (Float) chain.getArg(0);
                        boolean customOn = speedOn();
                        if (isTemporarySpeed()) {
                            return onPressPath(chain, orig, customOn);
                        }
                        // 常规通路（用户选速 / 起播写速 / 宿主的其他设定）。
                        // 到达这里说明上一次长按已结束，清掉长按状态，
                        // 免得「回落值恰好等于提速值」时状态卡住。
                        sInPress = false;
                        if (!customOn) {
                            return chain.proceed();
                        }
                        float v = speedValue();
                        if (Math.abs(orig - v) <= 0.001f) {
                            logSpeedPassthrough(orig);
                            return chain.proceed();
                        }
                        UiController.logFile("player: speed " + orig + " -> " + v);
                        return chain.proceed(new Object[]{v});
                    });

            // 2) 画质：TTVideoEngineImpl.configResolution(Resolution)
            Class<?> impl = Class.forName("com.ss.ttvideoengine.TTVideoEngineImpl", false, cl);
            Method cfg = impl.getDeclaredMethod("configResolution", resCls);
            cfg.setAccessible(true);
            mod.hook(cfg).setId("rgPlayerRes")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        if (qualityOn() && sMaxRes != null && chain.getArg(0) != sMaxRes) {
                            Object orig = chain.getArg(0);
                            UiController.logFile("player: resolution " + orig + " -> " + sMaxRes);
                            return chain.proceed(new Object[]{sMaxRes});
                        }
                        return chain.proceed();
                    });

            // 3) 播放 / 暂停状态（供「暂停时恢复原本透明度」使用）
            hookPlayPause(mod, cl);

            sInit = true;
            sStatus = "ok maxRes=" + sMaxRes + " pauseHook=" + sPauseHook;
        } catch (Throwable t) {
            sStatus = "FAILED: " + t;
            throw t;
        }
    }

    /* ---------------- 播放 / 暂停状态 ---------------- */

    /**
     * 当前是否处于「暂停」。
     *
     * <p>数据来源是**宿主自己的播放控制调用**，不是轮询状态：
     * 宿主暂停走 {@code com.ss.ttvideoengine.TTVideoEngine.pause()}，
     * 恢复走 {@code TTVideoEngine.play()}（7.3.9.32 实测：
     * {@code e65/w} 调 pause、{@code j65/d$e}、{@code ix4/e} 调 play）。
     * 两个都是 {@code TTVideoEngine} 上的 public 方法，用 {@code getMethod} 取
     * （可能有继承，getDeclaredMethod 取不到）。
     *
     * <p>用推模式而不是周期查询引擎状态，是因为后者要额外轮询、还要解码
     * {@code getPlaybackState()} 的整数含义；而 play/pause 是明确的语义事件。
     */
    private static volatile boolean sPaused;
    private static volatile String sPauseHook = "-";

    static boolean isPaused() {
        return sPaused;
    }

    private static void hookPlayPause(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> eng = Class.forName("com.ss.ttvideoengine.TTVideoEngine", false, cl);
            int n = 0;
            Method play = findNoArg(eng, "play");
            if (play != null) {
                mod.hook(play).setId("rgPlay")
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            rememberEngine(chain.getThisObject());
                            markPaused(false, "play");
                            return chain.proceed();
                        });
                n++;
            }
            Method pause = findNoArg(eng, "pause");
            if (pause != null) {
                mod.hook(pause).setId("rgPause")
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            rememberEngine(chain.getThisObject());
                            markPaused(true, "pause");
                            return chain.proceed();
                        });
                n++;
            }
            // 状态查询钩子：顺带记录引擎实例与状态码，并作为**主要**的播放/暂停判据。
            // 实测发现只靠 play/pause 事件不可靠：进二级页时会收到一次 pause，
            // 但随后的自动起播**没有走 TTVideoEngine.play()**，状态会错卡在「暂停」——
            // 那会让模块整体停止应用透明度。状态码（1=播放/2=暂停）可以纠正这种情况。
            try {
                Method st = findNoArg(eng, "getPlaybackState");
                if (st != null) {
                    mod.hook(st).setId("rgPlayState")
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                rememberEngine(chain.getThisObject());
                                Object r = chain.proceed();
                                if (r instanceof Integer) {
                                    noteState(((Integer) r).intValue());
                                }
                                return r;
                            });
                }
            } catch (Throwable ignored) {
            }
            sPauseHook = (n == 2) ? "ok" : ("partial(" + n + ")");
        } catch (Throwable t) {
            sPauseHook = "FAILED: " + t;
        }
    }

    /** 引擎实例（弱引用），仅用于诊断时确认拿到的是哪个播放器。 */
    private static volatile java.lang.ref.WeakReference<Object> sEngineRef;
    private static volatile int sLastState = Integer.MIN_VALUE;

    private static void rememberEngine(Object o) {
        if (o != null) {
            sEngineRef = new java.lang.ref.WeakReference<>(o);
        }
    }

    /**
     * 状态码变化 → 直接作为播放/暂停判据。
     *
     * <p>实测（7.3.9.32）：
     * <ul>
     *   <li>{@code state == 1} = 播放中（与宿主 {@code e65/w.isPlaying()} 的判据一致）</li>
     *   <li>{@code state == 2} = 暂停（与宿主 {@code e65/w.B()} 的判据一致）</li>
     * </ul>
     * 其余取值（0 = idle、3 = error 等）**不改状态** —— 不确定时保持原状，
     * 避免在缓冲/出错时误判成暂停而让透明度设置整体失效。
     *
     * <h3>为什么不再用「播放位置在前进」兜底</h3>
     * 曾用位置兜底纠正漏掉的 play 事件，但实测**暂停后位置仍会短暂前进**
     * （实测 894ms / 1208ms，另一次甚至 190ms 内跳 33 秒），
     * 会把刚置上的暂停状态立刻清掉，导致暂停恢复永远不触发。
     * 状态码本身可靠，就不需要这个不可靠的兜底了。
     */
    private static void noteState(int st) {
        if (st == sLastState) {
            return;
        }
        sLastState = st;
        UiController.logFile("player: state=" + st + " paused=" + sPaused);
        if (st == 1) {
            markPaused(false, "state=1");
        } else if (st == 2) {
            markPaused(true, "state=2");
        }
    }

    /** 取无参方法（含继承来的 public 方法）。 */
    private static Method findNoArg(Class<?> cls, String name) {
        try {
            Method m = cls.getMethod(name);
            return m.getParameterCount() == 0 ? m : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 记录播放/暂停状态变化。只在**状态真的翻转**时落盘，
     * 避免宿主频繁调用 play/pause 时刷日志；同时也方便真机核对状态是否准确。
     */
    private static void markPaused(boolean paused, String how) {
        if (sPaused == paused) {
            return;
        }
        sPaused = paused;
        UiController.logFile("player: " + (paused ? "PAUSED" : "PLAYING") + " (" + how + ")");
    }

    /**
     * 判断本次 {@code setSpeed} 是否来自「长按边缘临时倍速」通路。
     *
     * <p>为什么必须看调用栈：宿主长按目标值恒为 2.0f（e55/l.smali 里的
     * {@code const/high16 0x40000000}），而用户自定义值也可能是 2.0f，
     * 二者在 {@code setSpeed(float)} 的参数上**完全同值**，仅凭参数无法区分
     * （v2.42~2.44 用参数判据导致「自定义非 2.0x 时长按失效」）。
     *
     * <p>长按通路会经过以下专属帧（类名来自 7.3.9.32 反编译）：
     * <ul>
     *   <li>{@code e55/l} —— 长按手势控制器（onLongPress / 回落都经它）</li>
     *   <li>{@code e55/b} —— 其速率事件接口实现</li>
     *   <li>{@code bs4/c} —— 速率事件回调接口</li>
     * </ul>
     * 命中任一即为长按通路。用户选速走 {@code c65/c1}（顶部快捷）或弹层，
     * 栈里不会出现这些帧，故可区分。
     *
     * <p>栈帧数量上限 24：长按链路约 8~10 层，24 足够覆盖且控制开销。
     * 该判据只影响「是否改写倍速」，即便类名在某版本被混淆变化导致漏判，
     * 退化为「长按失效但选速正常」，不会引发崩溃。
     */
    private static boolean isTemporarySpeed() {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        int limit = Math.min(stack.length, 24);
        for (int i = 0; i < limit; i++) {
            String cn = stack[i].getClassName();
            if (cn.equals("e55.l") || cn.equals("e55.b") || cn.equals("bs4.c")
                    || cn.endsWith("fullscreen.g") || cn.endsWith("fullscreen.i$d")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 长按边缘临时倍速通路（值必须来自 {@link #isTemporarySpeed()} 判定的调用）。
     *
     * <p>这条通路一次长按会产生两类写入：
     * <ol>
     *   <li><b>提速</b>：宿主把速率写成它**硬编码的峰值 2.0x**（`e55/l.smali` 里的
     *       `const/high16 0x40000000`），并持续以约 30Hz 重复写同一个值；</li>
     *   <li><b>回落</b>：松手时写回它自己记录的基准（通常是 1.0x）。</li>
     * </ol>
     *
     * <p>处理策略：
     * <ul>
     *   <li>提速 → 把 2.0x **改写成用户配置的「长按倍速」**（默认也是 2.0x，即不改动）；</li>
     *   <li>回落 → 改写成用户的常规倍速（若常规倍速开关关着则原样放行，
     *       否则宿主会把它冲成 1.0x，用户设定的倍速就丢了）。</li>
     * </ul>
     *
     * <p><b>为什么用状态机而不是比较数值大小</b>：回落值取决于宿主自己的基准，
     * 当用户常规倍速小于该基准时（例如常规 0.8x、回落写 1.0x），
     * 「谁大谁小」的判断会把回落误认成提速，进而把用户倍速顶成 2.0x。
     * 改为「一次长按内，首个写入即提速，值发生变化即回落」，与数值绝对大小无关。
     */
    private static Object onPressPath(XposedInterface.Chain chain, float orig, boolean customOn)
            throws Throwable {
        long now = android.os.SystemClock.uptimeMillis();
        boolean newPress = !sInPress || (now - sPressAt > 3000L);
        sPressAt = now;

        if (newPress) {
            // 新的一次长按：这一笔就是提速
            sInPress = true;
            sPressVal = orig;
            return applyPress(chain, orig);
        }
        if (Math.abs(orig - sPressVal) <= 0.001f) {
            // 提速持续重复写同一个值
            return applyPress(chain, orig);
        }

        // 值变了 → 本次长按结束，这是回落
        sInPress = false;
        if (!customOn) {
            logSpeedPassthrough(orig);
            return chain.proceed();
        }
        float base = speedValue();
        if (Math.abs(orig - base) <= 0.001f) {
            logSpeedPassthrough(orig);
            return chain.proceed();
        }
        UiController.logFile("player: speed " + orig + " -> " + base + " (长按回落)");
        return chain.proceed(new Object[]{base});
    }

    /** 把宿主的硬编码提速峰值改写成用户配置的长按倍速。 */
    private static Object applyPress(XposedInterface.Chain chain, float orig) throws Throwable {
        float boost = pressValue();
        if (Math.abs(orig - boost) <= 0.001f) {
            logSpeedPassthrough(orig);
            return chain.proceed();
        }
        UiController.logFile("player: press speed " + orig + " -> " + boost);
        return chain.proceed(new Object[]{boost});
    }

    /**
     * 放行日志做限流：边缘长按提速会**持续**以 30Hz 左右重复写同一个值，
     * 不去重会把 rgtools_log.txt 瞬间刷爆，反而看不到真正的选速记录。
     * 相同值 2 秒内只记一条。
     */
    private static volatile float sLastPassVal = Float.NaN;
    private static volatile long sLastPassAt;

    /** 长按状态机：是否处于一次长按中 / 本次长按的提速值 / 最近一次长按通路写入时间。 */
    private static volatile boolean sInPress;
    private static volatile float sPressVal = Float.NaN;
    private static volatile long sPressAt;

    private static void logSpeedPassthrough(float v) {
        long now = android.os.SystemClock.uptimeMillis();
        if (v == sLastPassVal && now - sLastPassAt < 2000L) {
            return;
        }
        sLastPassVal = v;
        sLastPassAt = now;
        UiController.logFile("player: speed " + v + " pass(临时倍速)");
    }

    /* ---------------- 配置读取（hook 回调里没有 Activity，用 appCtx） ---------------- */

    private static boolean qualityOn() {
        Context c = UiController.appCtx();
        return c == null || Config.maxQuality(c);   // 配置未就绪时按默认（开）
    }

    private static boolean speedOn() {
        Context c = UiController.appCtx();
        return c != null && Config.speedCustomOn(c);
    }

    private static float speedValue() {
        Context c = UiController.appCtx();
        return (c == null ? 20 : Config.speedCustom(c)) / 10f;
    }

    private static float pressValue() {
        Context c = UiController.appCtx();
        return (c == null ? 20 : Config.pressSpeedCustom(c)) / 10f;
    }

    /** 读引擎 Resolution 的静态常量（不一定是 public，用 declared + accessible）。 */
    private static Object staticRes(Class<?> cls, String name) {
        try {
            Field f = cls.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(null);
        } catch (Throwable t) {
            return null;
        }
    }
}
