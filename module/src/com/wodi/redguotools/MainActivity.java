package com.wodi.redguotools;

import android.app.Activity;
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

    /** 模块日志落在宿主的外置私有目录下，模块进程要读得靠 root。 */
    private static final String HOST_LOG_DIR =
            "/sdcard/Android/data/com.phoenix.read/files";

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

    /** 页面外壳：大标题 + 可选右上角动作按钮 + 滚动内容列。 */
    private LinearLayout page(String title, int rightIcon, Runnable rightAction) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        FrameLayout bar = new FrameLayout(this);
        int h = dp(56);
        bar.setPadding(dp(20), dp(6), dp(16), 0);
        col.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, h));

        TextView tv = MiUi.bigTitle(this, p, title);
        bar.addView(tv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.START | Gravity.CENTER_VERTICAL));

        if (rightIcon >= 0) {
            FrameLayout btn = new FrameLayout(this);
            btn.setBackground(MiUi.pressable(this, p, 14));
            MiUi.Icon ic = new MiUi.Icon(this);
            ic.setType(rightIcon);
            ic.setColor(p.title);
            FrameLayout.LayoutParams icLp = new FrameLayout.LayoutParams(dp(22), dp(22));
            icLp.gravity = Gravity.CENTER;
            btn.addView(ic, icLp);
            FrameLayout.LayoutParams bLp = new FrameLayout.LayoutParams(dp(44), dp(44));
            bLp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
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
        col.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(4), dp(16), dp(24));
        sv.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        holder.put(col, body);
        return col;
    }

    /** 页面外壳 → 该页的滚动内容列（构建页时记下，方便调用方往里塞卡片）。 */
    private final java.util.Map<View, View> holder = new java.util.HashMap<>();

    /** 普通底栏：整条贴底 + 顶部分割线，四个图标 + 文字，选中项只做颜色高亮（无衬底不悬浮）。 */
    private View bottomBar() {
        FrameLayout wrap = new FrameLayout(this);

        View line = new View(this);
        line.setBackgroundColor(p.navSelBg);
        wrap.addView(line, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(p.card);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(1);
        blp.bottomMargin = dp(0);
        bar.setPadding(0, dp(6), 0, dp(6));
        wrap.addView(bar, blp);

        String[] names = {"主页", "功能", "设置", "关于"};
        int[] icons = {MiUi.I_HOME, MiUi.I_SLIDERS, MiUi.I_GEAR, MiUi.I_INFO};
        mTabs = new View[names.length];
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            // 外层均分整格做点击区，内层只按内容宽度撑开—— 衬底要贴着图标+文字，
            // 直接把衬底铺在均分格上会显得又宽又挤（v2.80 初版就是这样）。
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
            item.setPadding(0, dp(8), 0, dp(8));

            MiUi.Icon ic = new MiUi.Icon(this);
            ic.setType(icons[i]);
            ic.setColor(p.sub);
            item.addView(ic, new LinearLayout.LayoutParams(dp(22), dp(22)));

            TextView label = new TextView(this);
            label.setText(names[i]);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            label.setTextColor(p.sub);
            label.setGravity(Gravity.CENTER);
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

    /** 右上角按钮：强制停止红果并重新拉起（需 root；无 root 时退化为仅强停）。 */
    private void restartHost() {
        RootShell.run("am force-stop com.phoenix.read");
        RootShell.run("monkey -p com.phoenix.read -c android.intent.category.LAUNCHER 1");
        toast("已重启红果");
    }

    private void buildHome(LinearLayout body) {
        /* 激活卡：绿底 + 右侧大圆环对勾 */
        FrameLayout card = new FrameLayout(this);
        card.setBackground(MiUi.round(p.greenSoft, 18, this));
        int pad = dp(18);
        card.setPadding(pad, pad, pad, pad);

        LinearLayout vcol = new LinearLayout(this);
        vcol.setOrientation(LinearLayout.VERTICAL);
        TextView st = new TextView(this);
        st.setText("模块已激活");
        st.setTextColor(p.title);
        st.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        st.setTypeface(null, Typeface.BOLD);
        vcol.addView(st);

        TextView ver = new TextView(this);
        ver.setText("v" + versionName());
        ver.setTextColor(p.sub);
        ver.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        ver.setPadding(0, dp(3), 0, 0);
        vcol.addView(ver);

        TextView name = new TextView(this);
        name.setText("ReadGuo");
        name.setTextColor(p.title);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        name.setTypeface(null, Typeface.BOLD);
        name.setPadding(0, dp(10), 0, 0);
        vcol.addView(name);
        card.addView(vcol);

        MiUi.Icon ring = new MiUi.Icon(this);
        ring.setType(MiUi.I_RING_CHECK);
        ring.setColor(p.green);
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(dp(58), dp(58));
        rlp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        rlp.rightMargin = dp(-10);
        card.addView(ring, rlp);

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(12);
        body.addView(card, clp);

        /* 设备信息卡 */
        LinearLayout dev = MiUi.card(this, p, body);
        infoRow(dev, "设备名称", Build.MODEL);
        infoRow(dev, "设备型号", Build.MANUFACTURER + " " + Build.DEVICE);
        infoRow(dev, "HyperOS 版本", hyperOsVersion());
        infoRow(dev, "Android 版本", Build.VERSION.RELEASE + "（SDK " + Build.VERSION.SDK_INT + "）");
        infoRow(dev, "红果版本", hostVersion());
        infoRow(dev, "模块版本", versionName() + "（" + versionCode() + "）");

        /* 捐赠支持 */
        LinearLayout open = MiUi.card(this, p, body);
        LinearLayout line = MiUi.row(this, p, open, "捐赠支持", "模块免费，请作者喝杯咖啡", MiUi.I_HEART);
        // 爱心固定红色，不随主题灰
        for (int i = 0; i < line.getChildCount(); i++) {
            View ch = line.getChildAt(i);
            if (ch instanceof MiUi.Icon) {
                ((MiUi.Icon) ch).setColor(0xFFFF4D67);
                break;
            }
        }
        line.addView(MiUi.chevron(this, p));
        line.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDonate();
            }
        });
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
        col.setBackground(MiUi.round(p.card, 18, this));

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

    /** 卡片内「标题 / 取值」两行，取值用灰色。 */
    private void infoRow(LinearLayout card, String label, String value) {
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(p.title);
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        l.setTypeface(null, Typeface.BOLD);
        l.setPadding(0, dp(10), 0, dp(2));
        card.addView(l);

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(p.sub);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        v.setPadding(0, 0, 0, dp(8));
        card.addView(v);
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
                "叠加透明度：全局系数 × 各项独立，双层叠加");

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

    /** 一张卡：标题 + 若干条要点。 */
    private void featureCard(LinearLayout body, String title, String... points) {
        LinearLayout card = MiUi.card(this, p, body);
        TextView t = MiUi.groupLabel(this, p, title);
        t.setTextColor(p.title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(0, dp(8), 0, dp(2));
        card.addView(t);
        for (String s : points) {
            TextView v = MiUi.rowSub(this, p, "· " + s);
            v.setPadding(0, dp(3), 0, dp(3));
            card.addView(v);
        }
    }

    /* ==================== 设置页 ==================== */

    private View settingsPage() {
        LinearLayout col = page("设置", -1, null);
        LinearLayout body = (LinearLayout) holder.get(col);

        /* --- 外观与启动 --- */
        LinearLayout card1 = MiUi.card(this, p, body);
        switchRow(card1, "隐藏桌面图标",
                "关闭后从 LSPosed 模块列表的「打开」进入",
                iconHidden(), new MiUi.OnToggle() {
            @Override
            public void onChanged(boolean on) {
                applyIconHidden(on);
                // 「恢复桌面图标」那行的状态文案要跟着变，重建本页同步
                refreshSettings();
            }
        });

        /* --- 日志 --- */
        LinearLayout card2 = MiUi.card(this, p, body);
        TextView logTitle = MiUi.groupLabel(this, p, "日志");
        logTitle.setTextColor(p.title);
        logTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        logTitle.setTypeface(null, Typeface.BOLD);
        logTitle.setPadding(0, dp(8), 0, 0);
        card2.addView(logTitle);

        LinearLayout l1 = MiUi.row(this, p, card2, "运行日志", logDesc("rgtools_log.txt"), MiUi.I_LOG);
        l1.addView(MiUi.chevron(this, p));
        l1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLog("rgtools_log.txt");
            }
        });

        LinearLayout l2 = MiUi.row(this, p, card2, "视图树快照", logDesc("rgtools_tree.txt"), MiUi.I_BOOK);
        l2.addView(MiUi.chevron(this, p));
        l2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLog("rgtools_tree.txt");
            }
        });

        LinearLayout restore = MiUi.row(this, p, card2, "恢复桌面图标",
                iconHidden() ? "当前已隐藏，点此恢复" : "当前未隐藏", MiUi.I_APPS);
        restore.addView(MiUi.chevron(this, p));
        restore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!iconHidden()) {
                    toast("桌面图标本来就是显示的");
                    return;
                }
                applyIconHidden(false);
                toast("已恢复桌面图标");
                refreshSettings();
            }
        });

        TextView pathHint = MiUi.rowSub(this, p,
                "日志与快照写在红果的外置私有目录（" + HOST_LOG_DIR + "/）。"
                        + "Android 11 起应用读不到别的 App 的私有目录，所以本模块用 root 读取；"
                        + "没给 ReadGuo 授权 root 时可用 adb 拉取。");
        pathHint.setPadding(dp(4), dp(4), dp(4), dp(8));
        card2.addView(pathHint);

        return col;
    }

    /** 卡片内一行开关：标题 + 副标题 + 右侧 MIUI 开关。 */
    private void switchRow(LinearLayout card, String title, String sub, boolean on,
                           final MiUi.OnToggle l) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setMinimumHeight(dp(56));
        line.setPadding(0, dp(10), 0, dp(10));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(MiUi.rowTitle(this, p, title));
        if (sub != null && !sub.isEmpty()) {
            texts.addView(MiUi.rowSub(this, p, sub));
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
        col.setBackground(MiUi.round(p.card, 18, this));

        TextView title = new TextView(this);
        title.setText(name);
        title.setTextColor(p.title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(dp(18), dp(16), dp(18), dp(2));
        col.addView(title);

        TextView sub = new TextView(this);
        sub.setText(HOST_LOG_DIR + "/" + name);
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
        String path = HOST_LOG_DIR + "/" + name;
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

        LinearLayout card = MiUi.card(this, p, body);
        infoRow(card, "模块名称", "ReadGuo（红果工具）");
        infoRow(card, "版本", versionName() + "（" + versionCode() + "）");
        infoRow(card, "作者", AUTHOR);
        infoRow(card, "作用域", "红果免费短剧（com.phoenix.read）");
        infoRow(card, "运行框架", "LSPosed / libxposed API 102");
        infoRow(card, "构建时间", buildTime());

        LinearLayout open = MiUi.card(this, p, body);
        LinearLayout line = MiUi.row(this, p, open, "源码仓库", GITHUB, MiUi.I_BOOK);
        line.addView(MiUi.chevron(this, p));
        line.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openUrl("https://" + GITHUB);
            }
        });

        TextView t = MiUi.groupLabel(this, p, "免责声明");
        t.setTextColor(p.title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(dp(4), dp(2), 0, dp(6));
        body.addView(t);

        LinearLayout dis = MiUi.card(this, p, body);
        dis.addView(MiUi.body(this, p,
                "· 本模块仅供学习交流与个人技术研究使用，请勿用于任何商业或非法用途。\n\n"
                        + "· 本模块不修改、不破解、不绕过任何付费、会员或版权保护机制；"
                        + "所有功能仅调整本机的界面显示与个人偏好设置。\n\n"
                        + "· 请在遵守法律法规及平台用户协议的前提下使用；请支持正版，"
                        + "相关内容、商标与软件版权归原作者及平台所有。\n\n"
                        + "· 本模块按「现状」提供，不附带任何明示或默示的担保；"
                        + "因下载、安装或使用本模块而产生的一切后果，由使用者自行承担。\n\n"
                        + "· 若本模块侵犯了相关方的合法权益，请联系作者删除。"));
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
            return getPackageManager().getPackageInfo("com.phoenix.read", 0).versionName;
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