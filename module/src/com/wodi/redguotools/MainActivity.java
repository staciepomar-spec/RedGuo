package com.wodi.redguotools;

import android.app.Activity;
import android.content.Context;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 模块自身的入口：MIUI 风格四页（主页 / 功能 / 设置 / 关于）。
 *
 * <p>v2.80 重做：视觉换成 MIUI 观感（大标题、圆角白卡、绿色激活卡、胶囊底栏），
 * 视觉层全部走 {@link MiUi}；新增「设置」页承载模块自身设置 ——
 * 隐藏桌面图标、运行日志查看。
 *
 * <p>界面仍然<b>纯代码构建</b>：模块没有 {@code res/} 布局，也不用 Gradle，
 * 所有图标由 {@link MiUi.Icon} 用 Canvas 现画。
 *
 * <p>系统栏处理：targetSdk 35 起强制 edge-to-edge，{@code setStatusBarColor} 被忽略，
 * 所以靠 {@code OnApplyWindowInsetsListener} 给根布局补上下内边距，
 * 否则内容会压在状态栏 / 小白条底下。
 */
public class MainActivity extends Activity {

    private static final String TAG = RGModule.TAG;
    private static final String AUTHOR = "酷安@残話";
    private static final String GITHUB = "github.com/staciepomar-spec/RedGuo";

    /**
     * 宿主日志目录。三个渠道包名不同（改版 / Play 版 / 官方），
     * 这里按「哪个包真的产出过 rgtools_log.txt」来判定当前在用的宿主。
     * 目录读取本身要 root（模块进程读不到别的 App 的 Android/data）。
     */
    private static String mHostPkg;

    static String hostPkgResolved(Context c) {
        if (mHostPkg != null) {
            return mHostPkg;
        }
        for (String p : Hosts.PKGS) {
            RootShell.Result r = RootShell.run(
                    "test -f '/sdcard/Android/data/" + p + "/files/rgtools_log.txt' && echo Y");
            if (r.ok() && r.out != null && r.out.contains("Y")) {
                mHostPkg = p;
                return mHostPkg;
            }
        }
        for (String p : Hosts.PKGS) {
            try {
                if (c == null) {
                    continue;
                }
                c.getPackageManager().getPackageInfo(p, 0);
                mHostPkg = p;
                return mHostPkg;
            } catch (Throwable ignored) {
            }
        }
        mHostPkg = Hosts.DEFAULT;
        return mHostPkg;
    }

    static String hostLogDir(Context c) {
        return "/sdcard/Android/data/" + hostPkgResolved(c) + "/files";
    }

    /** 桌面图标的 LAUNCHER 载体（alias）。隐藏图标 = 禁 alias；MainActivity 本体
     *  挂 MODULE_SETTINGS category（LSPosed「打开」入口），永远保持 enabled。 */
    private static final String LAUNCHER_ALIAS =
            "com.wodi.redguotools.LauncherAlias";

    private MiUi.Palette p;
    private LinearLayout root;
    private View[] mPages;
    private View[] mTabs;
    private int mTab = 0;

    public MainActivity() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        p = MiUi.pal(this);
        lightSystemBars();

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(p.bg);
        setContentView(root);
        applyInsets(root);

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        mPages = new View[]{homePage(), featurePage(), settingsPage(), aboutPage()};
        for (int i = 0; i < mPages.length; i++) {
            content.addView(mPages[i], new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            mPages[i].setVisibility(i == 0 ? View.VISIBLE : View.GONE);
        }

        root.addView(bottomBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        select(0);
        Log.i(TAG, "MainActivity created");
    }

    /* ==================== 系统栏 ==================== */

    @SuppressWarnings("deprecation")
    private void lightSystemBars() {
        Window w = getWindow();
        if (w == null) {
            return;
        }
        int flags = w.getDecorView().getSystemUiVisibility()
                | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        w.getDecorView().setSystemUiVisibility(flags);
    }

    /** edge-to-edge 下用内边距把内容让开系统栏（API 29 与 30+ 两套取值）。 */
    private void applyInsets(final View v) {
        v.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                int top;
                int bottom;
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets bars = insets.getInsets(
                            WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    top = bars.top;
                    bottom = bars.bottom;
                } else {
                    top = insets.getSystemWindowInsetTop();
                    bottom = insets.getSystemWindowInsetBottom();
                }
                view.setPadding(view.getPaddingLeft(), top, view.getPaddingRight(), bottom);
                return insets;
            }
        });
        v.requestApplyInsets();
    }

    /* ==================== 骨架 ==================== */

    /** 页面外壳：Large Title + 可选右上角动作按钮 + 滚动内容列。 */
    private LinearLayout page(String title, int rightIcon, Runnable rightAction) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        FrameLayout bar = new FrameLayout(this);
        int h = dp(64);
        bar.setPadding(dp(16), dp(4), dp(8), dp(4));
        col.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, h));

        TextView tv = MiUi.bigTitle(this, p, title);
        bar.addView(tv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.START | Gravity.CENTER_VERTICAL));

        if (rightIcon >= 0) {
            FrameLayout btn = new FrameLayout(this);
            btn.setBackground(MiUi.pressable(this, p, 22));
            MiUi.Icon ic = new MiUi.Icon(this);
            ic.setType(rightIcon);
            ic.setColor(p.title);
            FrameLayout.LayoutParams icLp = new FrameLayout.LayoutParams(dp(22), dp(22));
            icLp.gravity = Gravity.CENTER;
            btn.addView(ic, icLp);
            FrameLayout.LayoutParams bLp = new FrameLayout.LayoutParams(dp(44), dp(44));
            bLp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
            bLp.rightMargin = dp(4);
            bar.addView(btn, bLp);
            final Runnable act = rightAction;
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (act != null) {
                        act.run();
                    }
                }
            });
        }

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setClipToPadding(false);
        sv.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        col.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), 0, dp(16), dp(28));
        sv.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        holder.put(col, body);
        return col;
    }

    /** 页面外壳 → 该页的滚动内容列（构建页时记下，方便调用方往里塞卡片）。 */
    private final java.util.Map<View, View> holder = new java.util.HashMap<>();

    /** iOS 底部标签栏：白底 + 顶部 1px 分割线，选中项只做蓝色高亮。 */
    private View bottomBar() {
        FrameLayout wrap = new FrameLayout(this);

        View line = new View(this);
        line.setBackgroundColor(p.divider);
        wrap.addView(line, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, MiUi.hairline(this)));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(p.card);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = MiUi.hairline(this);
        bar.setPadding(0, dp(6), 0, dp(4));
        wrap.addView(bar, blp);

        String[] names = {"主页", "功能", "设置", "关于"};
        int[] icons = {MiUi.I_HOME, MiUi.I_SLIDERS, MiUi.I_GEAR, MiUi.I_INFO};
        mTabs = new View[names.length];
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            FrameLayout cell = new FrameLayout(this);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    select(idx);
                }
            });

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, dp(2), 0, dp(2));

            MiUi.Icon ic = new MiUi.Icon(this);
            ic.setType(icons[i]);
            ic.setColor(p.sub);
            item.addView(ic, new LinearLayout.LayoutParams(dp(24), dp(24)));

            TextView label = new TextView(this);
            label.setText(names[i]);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            label.setTextColor(p.sub);
            label.setGravity(Gravity.CENTER);
            label.setIncludeFontPadding(false);
            label.setPadding(0, dp(3), 0, 0);
            item.addView(label);

            cell.addView(item, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cell.setLayoutParams(lp);
            bar.addView(cell);

            item.setTag(new Object[]{ic, label});
            cell.setTag(item);
            mTabs[i] = cell;
        }
        return wrap;
    }

    private void select(int idx) {
        mTab = idx;
        for (int i = 0; i < mPages.length; i++) {
            mPages[i].setVisibility(i == idx ? View.VISIBLE : View.GONE);
        }
        for (int i = 0; i < mTabs.length; i++) {
            View inner = (View) mTabs[i].getTag();
            MiUi.Icon ic = (MiUi.Icon) ((Object[]) inner.getTag())[0];
            TextView label = (TextView) ((Object[]) inner.getTag())[1];
            boolean on = i == idx;
            ic.setColor(on ? p.blue : p.sub);
            label.setTextColor(on ? p.blue : p.sub);
        }
    }

    /* ==================== 主页 ==================== */

    private View homePage() {
        LinearLayout col = page("主页", MiUi.I_REFRESH, new Runnable() {
            @Override
            public void run() {
                restartHost();
            }
        });
        LinearLayout body = (LinearLayout) holder.get(col);
        buildHome(body);
        return col;
    }

    /**
     * 右上角按钮：强制停止红果并重新拉起（需 root；无 root 时退化为仅强停）。
     *
     * <p>三个渠道包名可能同时装在同一台机器上（改版 / Play 版 / 官方），
     * 全部停掉以免残留进程占用；只重新拉起**当前在用**的那个
     * （判定见 {@link #hostPkgResolved(a)}）。
     */
    private void restartHost() {
        restartHostFrom(this);
    }

    /**
     * 强制停止红果并重新拉起（需 root；无 root 时退化为仅强停）。
     *
     * <p>做成静态是为了给模块内的设置页复用 —— 配置改完点一下就能重启，
     * 不用切回主页。
     */
    static void restartHostFrom(final Activity a) {
        if (a == null) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                String primary = hostPkgResolved(a);
                int n = 0;
                for (String p : Hosts.PKGS) {
                    try {
                        a.getPackageManager().getPackageInfo(p, 0);
                    } catch (Throwable ignored) {
                        continue;   // 没装就跳过
                    }
                    RootShell.run("am force-stop " + p);
                    n++;
                }
                final String msg;
                if (n == 0) {
                    msg = "未找到已安装的红果";
                } else {
                    RootShell.run("monkey -p " + primary
                            + " -c android.intent.category.LAUNCHER 1");
                    msg = "已重启红果（" + primary + "）";
                }
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        android.widget.Toast.makeText(a, msg,
                                android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }, "rg-restart").start();
    }

    private void buildHome(LinearLayout body) {
        /* ---------- 状态（按证据显示，不做断言） ---------- */
        LinearLayout st = MiUi.group(this, p, body, "状态");
        ModuleState.Result detected = ModuleState.detect(this);
        int state = detected.state;
        String detail = detected.detail;
        String title;
        String note;
        int icon;
        switch (state) {
            case ModuleState.RUNNING:
                title = "模块正在红果中运行";
                note = detail;
                icon = MiUi.I_RING_CHECK;
                break;
            case ModuleState.IDLE:
                title = "上次在红果里运行过";
                note = detail + "。打开一次红果即可恢复。";
                icon = MiUi.I_RING_CHECK;
                break;
            case ModuleState.NONE:
                title = "没有运行记录";
                note = "红果里找不到本模块的日志。通常是没在 LSPosed 中启用本模块、"
                        + "或作用域没勾选红果免费短剧。";
                icon = MiUi.I_INFO;
                break;
            default:
                title = "无法检测运行状态";
                note = "本机没有 root，读不到红果的运行日志，所以这里不做断言。"
                        + "请在 LSPosed 中确认模块已启用、且作用域勾选了红果免费短剧。";
                icon = MiUi.I_INFO;
                break;
        }
        LinearLayout line = MiUi.row(this, p, st, title, null, icon);
        line.addView(MiUi.value(this, p, "v" + versionName()));
        TextView stateNote = MiUi.body(this, p, note);
        stateNote.setPadding(MiUi.dp(this, 16), MiUi.dp(this, 2), MiUi.dp(this, 16), MiUi.dp(this, 14));
        st.addView(stateNote);

        /* ---------- 设备 ---------- */
        LinearLayout dev = MiUi.group(this, p, body, "设备与版本");
        valueRow(dev, "设备", Build.MODEL);
        MiUi.divider(this, p, dev);
        valueRow(dev, "型号", Build.MANUFACTURER + " " + Build.DEVICE);
        MiUi.divider(this, p, dev);
        valueRow(dev, "系统", hyperOsVersion());
        MiUi.divider(this, p, dev);
        valueRow(dev, "Android", Build.VERSION.RELEASE);
        MiUi.divider(this, p, dev);
        valueRow(dev, "红果版本", hostVersion());
        MiUi.divider(this, p, dev);
        valueRow(dev, "模块版本", versionName() + " (" + versionCode() + ")");

        /* ---------- 支持 ---------- */
        LinearLayout open = MiUi.group(this, p, body, null);
        LinearLayout dl = MiUi.row(this, p, open, "捐赠支持", null, MiUi.I_HEART);
        dl.addView(MiUi.chevron(this, p));
        dl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDonate();
            }
        });
    }

    /**
     * iOS 标准 label / value 行：左标签、右取值（灰色、单行、过长截断）。
     * 不加图标 —— 设备信息这类纯文本项在原生设置里本来就没有图标列，
     * 强行加图标反而会让整页密度失衡。
     */
    private void valueRow(LinearLayout card, String label, String value) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setMinimumHeight(dp(52));
        line.setPadding(dp(16), 0, dp(16), 0);

        TextView l = MiUi.rowTitle(this, p, label);
        line.addView(l, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView v = MiUi.value(this, p, value);
        v.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        v.setMaxLines(1);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.leftMargin = dp(12);
        line.addView(v, vlp);

        card.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** 捐赠弹窗：支付宝 / 微信收款码（assets 内置图），纵向可滚。 */
    private void showDonate() {
        Dialog dlg = new Dialog(this);
        ScrollView sc = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(18);
        col.setPadding(pad, pad, pad, pad);
        col.setBackground(MiUi.round(p.card, 16, this));

        TextView tip = new TextView(this);
        tip.setText("扫码请作者喝杯咖啡");
        tip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tip.setTextColor(p.title);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, 0, 0, dp(10));
        col.addView(tip);

        String[][] items = {
                {"donate_alipay.jpg", "支付宝"},
                {"donate_wechat.jpg", "微信"},
        };
        for (String[] it : items) {
            TextView label = new TextView(this);
            label.setText(it[1]);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            label.setTextColor(p.sub);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, dp(8), 0, dp(6));
            col.addView(label);

            ImageView iv = new ImageView(this);
            try {
                java.io.InputStream is = getAssets().open(it[0]);
                android.graphics.Bitmap bmp =
                        android.graphics.BitmapFactory.decodeStream(is);
                is.close();
                iv.setImageBitmap(bmp);
            } catch (Throwable t) {
                Log.w(TAG, "load donate image failed: " + it[0], t);
                continue;
            }
            int w = dp(280);
            iv.setAdjustViewBounds(true);
            col.addView(iv, new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        Button close = new Button(this);
        close.setText("关闭");
        close.setTextColor(p.blue);
        close.setBackgroundColor(0x00000000);
        close.setPadding(0, dp(10), 0, 0);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dlg.dismiss();
            }
        });
        col.addView(close);

        sc.addView(col);
        dlg.setContentView(sc);
        android.view.Window win = dlg.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            win.setLayout(dp(340), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dlg.show();
    }

    /* ==================== 功能页 ==================== */

    private View featurePage() {
        LinearLayout col = page("功能", -1, null);
        LinearLayout body = (LinearLayout) holder.get(col);

        featureCard(body, "界面显示",
                "状态栏：隐藏系统状态栏，底色可选 跟随背景 / 纯黑 / 纯白",
                "组件显隐：12 个常用组件（顶栏 / 右侧互动 / 底部条 / 标题 / 热评 / 进度条…）逐项开关",
                "自定义隐藏清单：「扫描当前页面」自动列出，或手动加规则（cls: / @文案 / text: / id:）",
                "底部标签栏：按标签文字勾选隐藏，剩余标签自动摊满整行",
                "底部导航栏：软件内全程隐藏小白条，退出软件才恢复",
                "界面透明度：首页/二级页上视频以外的所有浮层统一调透明，设置持久保存，只有重置才还原");

        featureCard(body, "播放器",
                "默认最高画质：起播自动选最高可用档",
                "自定义倍速：1.0 ~ 2.0 倍无级调节（0.1 步进），不依赖宿主档位",
                "倍速上限 2.0x：常规与长按是同一条播放器通路，native 引擎把超过 2.0 的请求降档——官方面板的 3.0x 实测只有约 1.5x，2.0x 已是物理上限",
                "定时关闭：到点停播并直接熄屏（需给 ReadGuo 授予 root）");

        featureCard(body, "交互与自动化",
                "双击屏幕直接打开评论区（横屏三击），可吞掉第二次点击避免误触点赞",
                "清屏省心：清屏状态下 10 秒不碰屏幕，自动收起其余控件只留视频，触摸立即恢复",
                "屏蔽「发现新版本」升级弹窗");

        featureCard(body, "使用方式",
                "在 LSPosed 中启用本模块，作用域勾选「红果免费短剧」",
                "强制停止红果后重新打开",
                "长按底部「首页」标签唤出设置面板，或在播放设置弹层里点「ReadGuo 设置」",
                "面板里所有改动即时生效，无需重启红果");
        return col;
    }

    /** 一个分组：分组标题（卡外）+ 白卡，卡内每条要点一行，行间一条缩进分割线。 */
    private void featureCard(LinearLayout body, String title, String... points) {
        LinearLayout card = MiUi.group(this, p, body, title);
        for (int i = 0; i < points.length; i++) {
            if (i > 0) {
                MiUi.divider(this, p, card);
            }
            TextView v = new TextView(this);
            v.setText(points[i]);
            v.setTextColor(p.title);
            v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            v.setLineSpacing(dp(3), 1f);
            v.setIncludeFontPadding(false);
            v.setPadding(dp(16), dp(13), dp(16), dp(13));
            card.addView(v);
        }
    }

    /* ==================== 设置页 ==================== */

    private View settingsPage() {
        LinearLayout col = page("设置", -1, null);
        LinearLayout body = (LinearLayout) holder.get(col);
        ModuleSettings.build(this, p, body);
        return col;
    }

    /** 卡片内一行开关：标题 + 副标题 + 右侧开关（行高与普通列表行一致）。 */
    private void switchRow(LinearLayout card, String title, String sub, boolean on,
                           final MiUi.OnToggle l) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setMinimumHeight(dp(sub == null || sub.isEmpty() ? 52 : 62));
        line.setPadding(dp(16), 0, dp(16), 0);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);
        texts.addView(MiUi.rowTitle(this, p, title));
        if (sub != null && !sub.isEmpty()) {
            TextView s = MiUi.rowSub(this, p, sub);
            s.setPadding(0, dp(2), 0, 0);
            texts.addView(s);
        }
        line.addView(texts, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final MiUi.MiSwitch sw = new MiUi.MiSwitch(this, p, on);
        sw.setTag(l);
        line.addView(sw);
        card.addView(line);
    }

    /**
     * 行副标题。
     *
     * <p><b>不能在这里报文件大小</b>：日志在红果的私有目录下，普通应用连 stat
     * 都被SELinux 挡掉（{@code File.exists()} 恒 false），只有走 root 才读得到。
     * 所以副标题只给文件名，大小/时间放进对话框里用 root 读。
     */
    private String logDesc(String name) {
        return name;
    }

    /* ---- 桌面图标显隐 ---- */

    /**
     * 桌面图标挂在 {@code LauncherAlias} 上（见 AndroidManifest），隐藏时禁alias、
     * {@code MainActivity} 本体保持 enabled —— 否则 LSPosed 管理器按组件名启动模块时
     * 会一起失效（v2.80 初版踩过：禁 MainActivity 后 LSPosed 里点不进模块）。
     */
    /**
     * 桌面图标与 LSPosed「打开」入口分离（v2.89）：
     * 桌面图标挂在 alias（LAUNCHER）上，「隐藏桌面图标」只禁 alias；
     * MainActivity 本体挂 {@code de.robv.android.xposed.category.MODULE_SETTINGS}
     * （LSPosed 管理器 AppHelper.getSettingsIntent 的首选入口），永远 enabled ——
     * 隐藏图标后 LSPosed 模块列表的「打开」照常可用。
     * （v2.84~2.88 曾禁本体：实测 LSPosed FAB 消失 + 系统直接 force stop 进程。）
     */
    private ComponentName launcherAlias() {
        return new ComponentName(this, LAUNCHER_ALIAS);
    }

    private boolean iconHidden() {
        try {
            return getPackageManager().getComponentEnabledSetting(launcherAlias())
                    == PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        } catch (Throwable t) {
            return false;
        }
    }

    private void applyIconHidden(final boolean hide) {
        if (hide) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("隐藏桌面图标？")
                    .setMessage("桌面将不再显示 ReadGuo。\n"
                            + "随时可从 LSPosed 模块列表的「打开」"
                            + "进入本设置页恢复。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("隐藏", new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface d, int w) {
                            doSetIcon(false);
                            toast(iconHidden() ? "已隐藏桌面图标，可在设置页恢复" : "隐藏失败");
                        }
                    })
                    .show();
            return;
        }
        doSetIcon(true);
        toast(iconHidden() ? "恢复失败" : "已恢复桌面图标");
    }

    private void doSetIcon(boolean visible) {
        try {
            getPackageManager().setComponentEnabledSetting(
                    launcherAlias(),
                    visible ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        } catch (Throwable t) {
            Log.w(TAG, "setComponentEnabledSetting failed", t);
            toast("系统拒绝了图标显隐：" + t.getMessage());
        }
    }

    /* ---- 日志查看 ---- */

    private void showLog(final String name) {
        final Dialog dlg = new Dialog(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackground(MiUi.round(p.card, 16, this));

        TextView title = new TextView(this);
        title.setText(name);
        title.setTextColor(p.title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(dp(18), dp(16), dp(18), dp(2));
        col.addView(title);

        TextView sub = new TextView(this);
        sub.setText(hostLogDir(this) + "/" + name);
        sub.setTextColor(p.hint);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        sub.setPadding(dp(18), 0, dp(18), dp(8));
        col.addView(sub);

        final TextView body = new TextView(this);
        body.setTextColor(p.sub);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        body.setTypeface(Typeface.MONOSPACE);
        body.setPadding(dp(18), 0, dp(18), dp(8));
        body.setText("正在读取…");

        ScrollView sv = new ScrollView(this);
        sv.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setGravity(Gravity.END);
        btns.setPadding(dp(12), 0, dp(12), dp(12));
        TextView again = MiUi.ghostButton(this, p, "重新读取", null);
        again.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                load(body, name);
            }
        });
        btns.addView(again);
        TextView copy = MiUi.ghostButton(this, p, "复制", null);
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyText(body.getText().toString());
            }
        });
        btns.addView(copy);
        TextView close = MiUi.ghostButton(this, p, "关闭", null);
        btns.addView(close);
        col.addView(btns);

        FrameLayout wrapper = new FrameLayout(this);
        int gap = dp(10);
        wrapper.setPadding(gap, gap, gap, gap);
        wrapper.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.92f),
                    (int) (getResources().getDisplayMetrics().heightPixels * 0.8f));
        }
        dlg.setContentView(wrapper);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dlg.dismiss();
            }
        });
        try {
            dlg.show();
        } catch (Throwable t) {
            toast("无法打开日志窗口：" + t.getMessage());
            return;
        }
        load(body, name);
    }

    /** root 读文件（后台线程）：只取末尾若干行，避免把几 MB 的树快照塞进内存。 */
    private void load(final TextView view, final String name) {
        view.setText("正在读取…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String text = readTail(name);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        view.setText(text);
                        view.setTextIsSelectable(true);
                    }
                });
            }
        }, "rg-log-read").start();
    }

    private String readTail(String name) {
        String path = hostLogDir(this) + "/" + name;
        int lines = "rgtools_tree.txt".equals(name) ? 400 : 600;
        // 先问一句文件状态：大小/时间/是否存在都拿得到，用户一眼知道是不是还没生成
        RootShell.Result st = RootShell.run("ls -l '" + path + "' 2>&1");
        String stat = st.ok() && st.out != null && !st.out.isEmpty()
                ? st.out.trim() : "（文件不存在，红果跑起来之后才会生成）";

        RootShell.Result r = RootShell.run(
                "tail -n " + lines + " '" + path + "' 2>/dev/null", 200000);
        if (!r.ok()) {
            return stat + "\n\n读取失败：" + r.describe()
                    + "\n\n若提示找不到 su，说明 ReadGuo 还没被授予 root（KernelSU 里放行一下即可）。"
                    + "\n也可以用 adb 手动拉取：\nadb pull " + path;
        }
        String out = r.out;
        return stat + "\n——— 以下为末尾 " + lines + " 行 ———\n"
                + ((out == null || out.isEmpty()) ? "（文件为空）" : out);
    }

    private void copyText(String s) {
        try {
            android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(android.content.ClipData.newPlainText("ReadGuo", s));
                toast("已复制");
                return;
            }
        } catch (Throwable ignored) {
        }
        toast("复制失败");
    }

    /* ==================== 关于页 ==================== */

    private View aboutPage() {
        LinearLayout col = page("关于", -1, null);
        LinearLayout body = (LinearLayout) holder.get(col);

        LinearLayout card = MiUi.group(this, p, body, "应用信息");
        valueRow(card, "模块名称", "ReadGuo");
        MiUi.divider(this, p, card);
        valueRow(card, "版本", versionName() + " (" + versionCode() + ")");
        MiUi.divider(this, p, card);
        valueRow(card, "作者", AUTHOR);
        MiUi.divider(this, p, card);
        valueRow(card, "作用域", "红果免费短剧");
        MiUi.divider(this, p, card);
        valueRow(card, "运行框架", "LSPosed / libxposed 102");
        MiUi.divider(this, p, card);
        valueRow(card, "构建时间", buildTime());

        LinearLayout open = MiUi.group(this, p, body, null);
        LinearLayout line = MiUi.row(this, p, open, "源码仓库", null, MiUi.I_BOOK);
        line.addView(MiUi.chevron(this, p));
        line.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openUrl("https://" + GITHUB);
            }
        });

        LinearLayout dis = MiUi.group(this, p, body, "免责声明");
        TextView disBody = MiUi.body(this, p,
                "本模块仅供学习交流与个人技术研究使用，请勿用于任何商业或非法用途。\n\n"
                        + "本模块不修改、不破解、不绕过任何付费、会员或版权保护机制；"
                        + "所有功能仅调整本机的界面显示与个人偏好设置。\n\n"
                        + "请在遵守法律法规及平台用户协议的前提下使用；请支持正版，"
                        + "相关内容、商标与软件版权归原作者及平台所有。\n\n"
                        + "本模块按「现状」提供，不附带任何明示或默示的担保；"
                        + "因下载、安装或使用本模块而产生的一切后果，由使用者自行承担。\n\n"
                        + "若本模块侵犯了相关方的合法权益，请联系作者删除。");
        disBody.setPadding(dp(16), dp(14), dp(16), dp(14));
        dis.addView(disBody);

        /* --- 模块自身 --- */
        LinearLayout self = MiUi.group(this, p, body, "模块自身");
        switchRow(self, "隐藏桌面图标",
                "关闭后从 LSPosed 模块列表的「打开」进入",
                iconHidden(), new MiUi.OnToggle() {
            @Override
            public void onChanged(boolean on) {
                applyIconHidden(on);
            }
        });
        MiUi.divider(this, p, self);
        LinearLayout l1 = MiUi.row(this, p, self, "运行日志",
                "红果进程里的 hook 记录", MiUi.I_LOG);
        l1.addView(MiUi.chevron(this, p));
        l1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLog("rgtools_log.txt");
            }
        });
        MiUi.divider(this, p, self);
        LinearLayout l2 = MiUi.row(this, p, self, "视图树快照",
                "最近一次导出的视图树", MiUi.I_BOOK);
        l2.addView(MiUi.chevron(this, p));
        l2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLog("rgtools_tree.txt");
            }
        });

        return col;
    }

    /* ==================== 小工具 ==================== */

    private void refreshSettings() {
        // 设置页的开关状态与副标题是快照，重建一次最省事
        View old = mPages[2];
        View fresh = settingsPage();
        ViewGroup parent = (ViewGroup) old.getParent();
        int idx = parent.indexOfChild(old);
        parent.removeViewAt(idx);
        parent.addView(fresh, idx, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mPages[2] = fresh;
        fresh.setVisibility(mTab == 2 ? View.VISIBLE : View.GONE);
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "?";
        }
    }

    private int versionCode() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Throwable t) {
            return 0;
        }
    }

    private String hostVersion() {
        try {
            return getPackageManager().getPackageInfo(hostPkgResolved(this), 0).versionName;
        } catch (Throwable t) {
            return "未安装";
        }
    }

    /**
     * HyperOS 版本：小米把 {@code Build.DISPLAY} 换成了基线版本号（CP2A.…），
     * 真正的 OS 版本在 {@code ro.mi.os.version.incremental} 里，反射读一下。
     * 读不到就退回 DISPLAY / RELEASE。
     */
    private String hyperOsVersion() {
        String s = sysProp("ro.mi.os.version.incremental");
        if (s == null || s.isEmpty()) {
            s = sysProp("ro.miui.ui.version.name");
        }
        if (s != null && !s.isEmpty()) {
            return s;
        }
        String d = Build.DISPLAY;
        return (d != null && !d.isEmpty()) ? d : Build.VERSION.RELEASE;
    }

    /** 读系统属性。走反射，失败返回 null（不能因取不到版本号就崩）。 */
    private String sysProp(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method m = sp.getMethod("get", String.class);
            Object v = m.invoke(null, key);
            return v == null ? null : String.valueOf(v);
        } catch (Throwable t) {
            return null;
        }
    }

    private String buildTime() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return new SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.CHINA)
                    .format(new Date(pi.lastUpdateTime));
        } catch (Throwable t) {
            return "?";
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable t) {
            toast("没有可用的浏览器");
        }
    }

    private void toast(String msg) {
        try {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }

    private int dp(float v) {
        return MiUi.dp(this, v);
    }

    @SuppressWarnings("unused")
    private static void keepWindowFlagRef() {
        // 仅为记录：曾在调试里用过 WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS，
        // 现在改成 insets 内边距方案（edge-to-edge 下 setStatusBarColor 已失效）。
    }
}