package com.wodi.redguotools;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * 模块设置界面（**全屏**，不是浮窗）。
 *
 * <p>信息架构（v2.95）：一级只列分组标题，点进去才是二级的具体设置，
 * 避免一屏塞下全部配置。五个分组 —— 界面显示 / 底部区域 / 播放控制 /
 * 面板与交互 / 高级·自定义；二级页里纯开关项一律不折叠，只有内容多于一条的
 * （组件显隐、底部标签栏、倍速细节、自定义清单）才用折叠行，折叠头自带状态摘要。
 *
 * <p>进入面板时会**先暂停视频**（全屏界面盖住画面，继续播没有意义）；
 * 返回键在二级页回一级，在一级页关闭面板。
 */
public final class SettingsPanel {

    private static final String TAG = RGModule.TAG;
    private static android.app.Dialog sDialog;

    /* 全屏面板的导航状态：null = 一级（分组列表），否则是当前分组 key。 */
    private static String sGroup;
    private static String sTitle;
    private static LinearLayout sContent;
    private static TextView sHeaderTitle;
    private static TextView sBack;
    private static Activity sActivity;

    private SettingsPanel() {
    }

    public static boolean isShowing() {
        return sDialog != null && sDialog.isShowing();
    }

    /**
     * 宿主换页（首页 ↔ 二级播放页）时，把面板迁移到新的前台 Activity 上。
     *
     * <p>对话框是挂在某个 Activity 上的，宿主一旦切页面，旧面板就跟着旧页面
     * 一起不可见了 —— 用户体验上就是「点进剧集，设置自己退了」。这里把面板
     * 重新建到新页面上，并保留用户当前所在的分组。
     */
    public static void ensureOn(Activity fg) {
        if (sDialog == null || !sDialog.isShowing() || fg == null || sActivity == fg) {
            return;
        }
        if (dead(fg)) {
            return;
        }
        final String keep = sGroup;
        try {
            sDialog.dismiss();
        } catch (Throwable ignored) {
        }
        try {
            show(fg);
            if (keep != null && sDialog != null) {
                renderGroup(fg, Theme.of(fg), keep, groupTitleOf(keep));
            }
        } catch (Throwable t) {
            Log.e(TAG, "ensureOn failed", t);
            UiController.crashDump("ensureOn", t);
        }
    }

    private static String groupTitleOf(String key) {
        if ("display".equals(key)) {
            return "界面显示";
        }
        if ("slots".equals(key)) {
            return "组件显隐";
        }
        if ("play".equals(key)) {
            return "播放控制";
        }
        return "交互";
    }

    public static void show(final Activity a) {
        if (isShowing() || dead(a)) {
            return;
        }
        final Theme.Palette p;
        try {
            p = Theme.of(a);
        } catch (Throwable t) {
            UiController.crashDump("show:palette", t);
            return;
        }

        // 全屏界面一盖，视频继续播既看不见也没意义 —— 进来先暂停。
        // 只暂停不自动恢复：用户关掉面板后自己点一下继续更符合预期。
        PlayerTweaks.pauseCurrent();

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(p.bg);

        /* ---------- 顶部栏：← 返回 / 居中标题 / 关闭 ---------- */
        FrameLayout bar = new FrameLayout(a);
        int barH = Theme.dp(a, 52);
        bar.setPadding(Theme.dp(a, 8), Theme.dp(a, 4), Theme.dp(a, 12), Theme.dp(a, 4));

        sBack = new TextView(a);
        sBack.setText("\u2190");
        sBack.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        sBack.setTextColor(p.title);
        sBack.setGravity(Gravity.CENTER);
        sBack.setClickable(true);
        sBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                renderHome(a, p);
            }
        });
        sBack.setVisibility(View.INVISIBLE);   // 一级页也占位，标题不会左右跳
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                Theme.dp(a, 44), Theme.dp(a, 44));
        blp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        bar.addView(sBack, blp);

        sHeaderTitle = new TextView(a);
        sHeaderTitle.setText("ReadGuo 设置");
        sHeaderTitle.setTextColor(p.title);
        sHeaderTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        sHeaderTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        sHeaderTitle.setGravity(Gravity.CENTER);
        sHeaderTitle.setSingleLine(true);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.CENTER;
        bar.addView(sHeaderTitle, tlp);

        TextView close = new TextView(a);
        close.setText("关闭");
        close.setTextColor(p.primary);
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        close.setGravity(Gravity.CENTER);
        close.setPadding(Theme.dp(a, 10), Theme.dp(a, 8), Theme.dp(a, 10), Theme.dp(a, 8));
        close.setClickable(true);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sDialog != null) {
                    sDialog.dismiss();
                }
            }
        });
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        bar.addView(close, clp);

        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, barH));

        /* ---------- 内容区（一级 / 二级共用） ---------- */
        ScrollView sv = new ScrollView(a);
        sv.setClipToPadding(false);
        sContent = new LinearLayout(a);
        sContent.setOrientation(LinearLayout.VERTICAL);
        int cp = Theme.dp(a, 16);
        sContent.setPadding(cp, Theme.dp(a, 8), cp, Theme.dp(a, 32));
        sv.addView(sContent, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        android.app.Dialog d = new android.app.Dialog(a) {
            /**
             * 返回键的两级语义：二级页先回一级，一级页才关面板。
             *
             * <p>不能挂 {@code setOnKeyListener}：Dialog 自己在 dispatchKeyEvent 里
             * 就把 BACK 处理掉了（onBackPressed → cancel），监听器收不到完整的
             * DOWN/UP 对，实测按一次完全没反应、再按一次直接关掉。
             */
            @Override
            public void onBackPressed() {
                // sContent == null 说明面板状态被清过（迁移/关闭的竞态），
                // 这时没有二级页可回，直接按关闭处理，避免「按了没反应」。
                if (sGroup != null && sContent != null) {
                    renderHome(a, p);
                } else {
                    super.onBackPressed();
                }
            }
        };
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        d.setContentView(root);
        android.view.Window w = d.getWindow();
        if (w != null) {
            /*
             * 参考稿要的是「干净的浅色页面」，不是重透明玻璃：
             * 底色用不透明的 #F7F8FA，卡片纯白 + 柔和阴影。
             * （重透明会让视频画面透上来，观感发灰、字也被拖脏。）
             */
            w.setBackgroundDrawable(new ColorDrawable(p.bg));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setGravity(Gravity.TOP | Gravity.START);
            // 全屏界面不该压暗自己身后的东西；系统栏内边距由 insets 监听补
            w.setDimAmount(0f);
            w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            try {
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    w.setDecorFitsSystemWindows(false);
                }
                // 宿主设置了「隐藏系统状态栏」时，设置页也一起隐藏 ——
                // 否则全屏面板顶上会突然冒出一条系统状态栏，观感割裂。
                if (Config.hideStatusBar(a)) {
                    android.view.WindowInsetsController ic = w.getInsetsController();
                    if (ic != null) {
                        ic.hide(WindowInsets.Type.statusBars());
                    }
                }
            } catch (Throwable ignored) {
            }
            applyWindowInsets(w.getDecorView());
        }

        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(android.content.DialogInterface di) {
                // 迁移到新页面时旧对话框的 dismiss 可能晚于新对话框的 show，
                // 不加这个判断会把新面板的静态状态清空（表现为「打开后点什么都没反应」）。
                if (sDialog != di) {
                    return;
                }
                sDialog = null;
                sGroup = null;
                sContent = null;
                sHeaderTitle = null;
                sBack = null;
                sActivity = null;
            }
        });
        sDialog = d;
        sActivity = a;
        try {
            d.show();
            renderHome(a, p);
        } catch (Throwable t) {
            Log.e(TAG, "panel show failed", t);
            sDialog = null;
        }
    }

    /** 全屏窗口自己补系统栏内边距（宿主 targetSdk 35，边到边是强制行为）。 */
    private static void applyWindowInsets(View decor) {
        try {
            decor.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override
                public WindowInsets onApplyWindowInsets(View v, WindowInsets ins) {
                    android.graphics.Insets b = ins.getInsets(
                            WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    v.setPadding(0, b.top, 0, b.bottom);
                    return ins;
                }
            });
            decor.requestApplyInsets();
        } catch (Throwable ignored) {
        }
    }

    /* ==================== 一级：分组列表 ==================== */

    private static void renderHome(final Activity a, final Theme.Palette p) {
        if (sContent == null) {
            return;
        }
        sGroup = null;
        sTitle = null;
        if (sBack != null) {
            sBack.setVisibility(View.INVISIBLE);
        }
        if (sHeaderTitle != null) {
            sHeaderTitle.setText("ReadGuo 设置");
        }
        sContent.removeAllViews();

        // 顶栏已经写了「ReadGuo 设置」，这里不再重复一个同名大标题
        View topGap = new View(a);
        sContent.addView(topGap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(a, 6)));

        // 四个分组装进一个「线框组」，组内用细线分隔 —— 参考稿的列表形态
        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackground(Theme.card(a, p));
        int lp16 = Theme.dp(a, 16);
        list.setPadding(lp16, 0, lp16, 0);
        sContent.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        entryRow(a, p, list, "界面显示", "状态栏 · 导航栏 · 底部标签栏 · 界面透明度 "
                + Config.alphaPercent(a) + "%", "display", true);
        entryRow(a, p, list, "组件显隐", slotsSummary(a) + " · 自定义清单 "
                + customSummary(a), "slots", true);
        entryRow(a, p, list, "播放控制", speedSummary(a) + " · 默认最高画质 · 定时关闭",
                "play", true);
        entryRow(a, p, list, "净化", cleanSummary(a), "clean", true);
        entryRow(a, p, list, "交互", "模块入口 · 双击评论 · 清屏 · 升级弹窗", "more", false);

        // 输出视图树：调试用，放一级，省得为了拿一份树先进二级
        LinearLayout.LayoutParams logLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        logLp.topMargin = Theme.dp(a, 4);
        Button logBtn = Theme.ghostButton(a, p, "输出日志（视图树 + 运行日志）",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String dir = UiController.exportLogs(a);
                        if (dir == null) {
                            android.widget.Toast.makeText(a,
                                    "导出失败：没有可写的目录",
                                    android.widget.Toast.LENGTH_LONG).show();
                            return;
                        }
                        android.widget.Toast.makeText(a,
                                "已导出到\n" + dir + "\nrgtools_tree.txt · rgtools_log.txt",
                                android.widget.Toast.LENGTH_LONG).show();
                    }
                });
        sContent.addView(logBtn, logLp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Theme.dp(a, 14);
        Button reset = Theme.dangerButton(a, p, "重置所有设置", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmReset(a, p);
            }
        });
        sContent.addView(reset, lp);

        TextView warn = Theme.hint(a, p, "重置会清空本模块的全部配置（含透明度、显隐、倍速、手势、清单），"
                + "并让红果重建当前界面。只想恢复透明度的话，把它拉回 100% 即可。");
        warn.setPadding(0, Theme.dp(a, 8), 0, 0);
        sContent.addView(warn);

        // 应用信息小字统一放最底下，别和顶栏标题抢位置
        TextView footer = Theme.hint(a, p, "红果免费短剧 · 界面增强 · 改动即时生效");
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, Theme.dp(a, 24), 0, 0);
        sContent.addView(footer);
    }

    /** 一级列表里的一行：标题 + 摘要 + 右箭头，整行可点（按下只有淡色叠加，不画卡片）。 */
    private static void entryRow(final Activity a, final Theme.Palette p, LinearLayout parent,
                                 final String title, String summary, final String key,
                                 boolean dividerAfter) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Theme.pressable(a, p));
        row.setClickable(true);
        row.setFocusable(true);
        row.setPadding(0, Theme.dp(a, 13), 0, Theme.dp(a, 13));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                renderGroup(a, p, key, title);
            }
        });

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = Theme.rowTitle(a, p, title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        col.addView(t);
        TextView s = Theme.rowSummary(a, p, summary);
        s.setPadding(0, Theme.dp(a, 4), 0, 0);
        col.addView(s);
        row.addView(col, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView arrow = Theme.value(a, p, "\u203A");
        arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        arrow.setTextColor(p.sub);
        arrow.setPadding(Theme.dp(a, 8), 0, 0, 0);
        row.addView(arrow);

        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (dividerAfter) {
            divider(a, p, parent);
        }
    }

    /* ==================== 二级：具体分组 ==================== */

    private static void renderGroup(Activity a, Theme.Palette p, String key, String title) {
        if (sContent == null) {
            return;
        }
        sGroup = key;
        sTitle = title;
        if (sBack != null) {
            sBack.setVisibility(View.VISIBLE);
        }
        if (sHeaderTitle != null) {
            sHeaderTitle.setText(title);
        }
        sContent.removeAllViews();

        if ("display".equals(key)) {
            buildDisplayGroup(a, p, sContent);
        } else if ("slots".equals(key)) {
            // 组件显隐 + 自定义隐藏清单：两者都是「按规则挑元素」，放同一页最顺
            buildSlotsGroup(a, p, sContent);
            buildAdvancedGroup(a, p, sContent);
        } else if ("play".equals(key)) {
            buildPlaybackGroup(a, p, sContent);
        } else if ("clean".equals(key)) {
            buildCleanGroup(a, p, sContent);
        } else {
            buildPanelGroup(a, p, sContent);
        }
    }

    /** 重置整个模块：确认后清空配置并让宿主重建界面。 */
    private static void confirmReset(final Activity a, final Theme.Palette p) {
        new AlertDialog.Builder(a)
                .setTitle("重置所有设置")
                .setMessage("将清空本模块的全部配置（状态栏、界面透明度、组件显隐、"
                        + "底部标签栏、倍速、手势、自定义清单等），并让红果重建当前界面。")
                .setNegativeButton("取消", null)
                .setPositiveButton("重置", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dlg, int which) {
                        Config.resetAll(a);
                        if (sDialog != null) {
                            sDialog.dismiss();
                        }
                        UiController.resetRuntime(a);
                    }
                })
                .show();
    }

    /* ==================== 分组骨架 ==================== */

    /** 分组标题（弱化小字）+ 该组的圆角卡片，返回卡片内容容器。 */
    private static LinearLayout groupCard(Activity a, Theme.Palette p,
                                          LinearLayout root, String label, boolean first) {
        // 分组小标题：与当前页面标题同名时省略，避免同一行字出现两次
        if (label != null && !label.equals(sTitle)) {
            TextView tv = Theme.groupTitle(a, p, label);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = Theme.dp(a, 4);               // 与 iOS 一样比卡片多缩进一点点
            lp.topMargin = Theme.dp(a, first ? 4 : 24);
            lp.bottomMargin = Theme.dp(a, 7);
            root.addView(tv, lp);
        }

        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Theme.card(a, p));
        card.setElevation(0);                   // 无边框无阴影
        int ip = Theme.dp(a, 16);
        card.setPadding(ip, 0, ip, 0);
        root.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    /**
     * 行间分割线：#E5E5EA、厚 1px、**左缩进 16、右侧贴到卡片边缘**。
     *
     * <p>缩进量与行的文字起点一致（面板行没有图标列，所以不是 48）。
     * 这条线只负责分隔触摸目标，绝不贯穿到卡片外。
     */
    private static void divider(Activity a, Theme.Palette p, LinearLayout parent) {
        View v = new View(a);
        v.setBackgroundColor(p.divider);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Theme.dp(a, 0.5f)));
        // 卡片左右各有 16 内边距；右端用 -16 的负 margin 顶到卡片边缘，
        // 左端正好落在文字起点。ViewGroup 会按自身边界裁剪，不会溢出卡片。
        lp.rightMargin = -Theme.dp(a, 16);
        parent.addView(v, lp);
    }

    /** 一行「标题 + 右侧开关」。纯开关项的主体形态。 */
    private static Switch switchRow(Activity a, Theme.Palette p, LinearLayout parent,
                                    String text, boolean checked,
                                    CompoundButton.OnCheckedChangeListener l) {
        Switch sw = Theme.makeSwitch(a, p, text, checked);
        sw.setOnCheckedChangeListener(guard(text, l));
        parent.addView(sw);
        return sw;
    }

    /**
     * 给开关回调包一层护栏。
     *
     * <p><b>为什么必须包</b>：这些回调跑在**宿主进程的主线程**上，且直接
     * {@code Config.set()} → {@code refresh()} → 可能 {@code recreate()}。
     * 里面任何一处抛异常，都是整个红果闪退，而用户那边只会得到一句
     * 「点了开关就崩」，连是哪一行都不知道 —— 已经收到过这种反馈。
     * <p>
     * 包住之后：异常变成一条日志 + 一次提示，界面还在，其它开关照样能用。
     * 这不是掩盖问题，是把「一次闪退」降级成「一个功能不生效」，
     * 同时把现场（堆栈）留下来。
     */
    private static CompoundButton.OnCheckedChangeListener guard(
            final String what, final CompoundButton.OnCheckedChangeListener l) {
        if (l == null) {
            return null;
        }
        return new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean v) {
                try {
                    l.onCheckedChanged(b, v);
                } catch (Throwable t) {
                    Log.e(TAG, "switch failed: " + what, t);
                    UiController.logFile("panel: switch '" + what + "' failed " + t);
                    UiController.crashDump("switch:" + what, t);
                }
            }
        };
    }

    /** 折叠行：标题 + 状态摘要 + 箭头，点击展开/收起 body；状态按 key 持久化。 */
    private static final class Expander {
        final LinearLayout body;
        final TextView summary;
        boolean folded;

        Expander(LinearLayout body, TextView summary) {
            this.body = body;
            this.summary = summary;
        }
    }

    private static Expander expander(final Activity a, final Theme.Palette p,
                                     LinearLayout parent, final String key, String title,
                                     String summary, boolean defaultFolded) {
        final LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, Theme.dp(a, 2), 0, Theme.dp(a, 2));

        final TextView sum = Theme.rowSummary(a, p, summary);
        sum.setGravity(Gravity.RIGHT);
        final TextView arrow = new TextView(a);
        arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        arrow.setTextColor(p.sub);
        arrow.setPadding(Theme.dp(a, 6), 0, 0, 0);

        LinearLayout header = new LinearLayout(a);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, Theme.dp(a, 8), 0, Theme.dp(a, 8));
        header.setBackground(Theme.pressable(a, p));
        header.addView(Theme.rowTitle(a, p, title),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(sum, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        header.addView(arrow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        parent.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        parent.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final Expander ex = new Expander(body, sum);
        ex.folded = Config.folded(a, key, defaultFolded);
        applyFold(ex, arrow);
        header.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ex.folded = !ex.folded;
                Config.setFolded(a, key, ex.folded);
                applyFold(ex, arrow);
            }
        });
        return ex;
    }

    private static void applyFold(Expander ex, TextView arrow) {
        ex.body.setVisibility(ex.folded ? View.GONE : View.VISIBLE);
        arrow.setText(ex.folded ? "\u25B6" : "\u25BC");
    }

    /* ==================== 摘要文案 ==================== */

    private static String slotsSummary(Activity a) {
        int hidden = 0;
        for (String k : Config.SLOT_KEYS) {
            if (Config.overlayHide(a, k)) {
                hidden++;
            }
        }
        return hidden > 0
                ? Config.SLOT_KEYS.length + " 项 · 已隐藏 " + hidden
                : Config.SLOT_KEYS.length + " 项 · 全部显示";
    }

    private static String btabSummary(Activity a) {
        String cur = Config.hiddenBottomTabs(a);
        return cur.isEmpty() ? "全部显示" : "已隐藏 " + cur.replace("|", "、");
    }

    private static String speedSummary(Activity a) {
        return "常规 " + (Config.speedCustom(a) / 10f) + "x · 长按 "
                + (Config.pressSpeedCustom(a) / 10f) + "x";
    }

    private static String customSummary(Activity a) {
        List<String[]> list = Config.overlayList(a);
        if (list.isEmpty()) {
            return "空";
        }
        int on = 0;
        for (String[] it : list) {
            if ("1".equals(it[1])) {
                on++;
            }
        }
        return list.size() + " 项 · 隐藏 " + on;
    }

    private static String cleanSummary(Activity a) {
        int on = 0;
        if (Config.adGate(a)) {
            on++;
        }
        if (Config.blockSplashAd(a)) {
            on++;
        }
        if (Config.blockFeedAd(a)) {
            on++;
        }
        if (Config.hideLuckyBag(a)) {
            on++;
        }
        if (Config.blockHotfix(a)) {
            on++;
        }
        return on + "/5 已启用";
    }

    /* ==================== 分组一：界面显示 ==================== */

    private static void buildDisplayGroup(final Activity a, final Theme.Palette p,
                                          LinearLayout root) {
        /* ---------- 1. 状态栏 ---------- */
        LinearLayout card = groupCard(a, p, root, "状态栏", true);

        switchRow(a, p, card, "隐藏系统状态栏", Config.hideStatusBar(a),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        Config.set(a, "sb_hide", v);
                        UiController.refresh(a);
                    }
                });
        divider(a, p, card);

        // 状态栏底色：iOS 分段控件（比一排单选按钮更贴近原生观感）
        TextView lbl = Theme.plain(a, p, "状态栏底色");
        lbl.setPadding(0, Theme.dp(a, 14), 0, Theme.dp(a, 8));
        card.addView(lbl);
        LinearLayout seg = Theme.segmented(a, p,
                new String[]{"跟随背景", "纯黑", "纯白"}, Config.statusBarBg(a),
                new Theme.OnSegment() {
                    @Override
                    public void onSelect(int i) {
                        Config.setInt(a, "sb_bg", i);
                        UiController.refresh(a);
                    }
                });
        LinearLayout.LayoutParams segLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        segLp.bottomMargin = Theme.dp(a, 14);
        card.addView(seg, segLp);

        /* ---------- 2. 导航栏 ---------- */
        LinearLayout navCard = groupCard(a, p, root, "导航栏", false);
        switchRow(a, p, navCard, "隐藏底部导航栏（小白条）", Config.autoHideNav(a),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        Config.setAutoHideNav(a, v);
                        UiController.refresh(a);
                        rebuild(a);
                    }
                });
        TextView navState = Theme.hint(a, p, UiController.navBarEnableState());
        navState.setPadding(0, Theme.dp(a, 2), 0, Theme.dp(a, 4));
        navCard.addView(navState);

        /* ---------- 3. 底部标签栏 ---------- */
        LinearLayout btabCard = groupCard(a, p, root, "底部标签栏", false);
        TextView btabHint = Theme.hint(a, p, "勾选要隐藏的标签，剩余标签自动摊满整行");
        btabHint.setPadding(0, Theme.dp(a, 2), 0, Theme.dp(a, 2));
        btabCard.addView(btabHint);
        inlineBottomTabs(a, p, btabCard);

        /* ---------- 4. 界面透明度 ---------- */
        LinearLayout alphaCard = groupCard(a, p, root, "界面透明度", false);
        alphaBlock(a, p, alphaCard, "视频以外的浮层", Config.alphaPercent(a), 100,
                new SimpleSeek() {
                    @Override
                    public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                        if (fromUser) {
                            Config.setInt(a, "alpha", value);
                            UiController.refresh(a);
                        }
                    }
                });
        TextView alphaHint = Theme.hint(a, p,
                "首页 / 二级页上视频以外的所有浮层统一乘这个系数；单项滑杆再叠加。"
                        + "设置会保存，拉回 100% 即恢复原样。");
        alphaHint.setPadding(0, 0, 0, Theme.dp(a, 6));
        alphaCard.addView(alphaHint);

        divider(a, p, alphaCard);
        switchRow(a, p, alphaCard, "横屏也调节浮层透明度", Config.alphaLandscape(a),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        Config.setAlphaLandscape(a, v);
                        UiController.refresh(a);
                    }
                });
        TextView alphaLandHint = Theme.hint(a, p,
                "默认关闭：横屏是沉浸式观看，控件本来就少，整体压暗会让画面像蒙了层灰。"
                        + "打开后与竖屏保持一致。");
        alphaLandHint.setPadding(0, Theme.dp(a, 2), 0, Theme.dp(a, 6));
        alphaCard.addView(alphaLandHint);

        switchRow(a, p, alphaCard, "暂停时还原组件显示", Config.pauseRestore(a),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        Config.set(a, "pause_restore", v);
                        UiController.refresh(a);
                    }
                });

        /* ---------- 5. 暗色模式 ---------- */
        LinearLayout darkCard = groupCard(a, p, root, "暗色模式", false);
        TextView darkHint = Theme.hint(a, p,
                "调用宿主自带的深色皮肤（换的是整套夜间资源，不是给界面蒙一层灰）。"
                        + "宿主若开着「跟随系统」，模块会在每次进入页面时纠正回所选模式。");
        darkHint.setPadding(0, Theme.dp(a, 2), 0, Theme.dp(a, 6));
        darkCard.addView(darkHint);

        LinearLayout dseg = Theme.segmented(a, p,
                new String[]{"跟随系统", "浅色", "深色"}, Config.darkMode(a),
                new Theme.OnSegment() {
                    @Override
                    public void onSelect(int i) {
                        Config.setDarkMode(a, i);
                        DarkMode.apply(a);
                        rebuild(a);
                    }
                });
        LinearLayout.LayoutParams dsegLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dsegLp.topMargin = Theme.dp(a, 10);
        dsegLp.bottomMargin = Theme.dp(a, 6);
        darkCard.addView(dseg, dsegLp);

        /* ---------- 6. 屏幕密度 ---------- */
        LinearLayout dpiCard = groupCard(a, p, root, "屏幕密度（DPI）", false);
        final int dpiCur = Config.dpi(a);
        final TextView dpiVal = Theme.value(a, p, dpiCur <= 0 ? "跟随系统" : (dpiCur + " dpi"));
        dpiVal.setPadding(0, Theme.dp(a, 4), 0, Theme.dp(a, 2));
        dpiCard.addView(dpiVal);
        SeekBar dpiBar = new SeekBar(a);
        dpiBar.setMax(48);   // 0 = 跟随系统，1..48 → 250..720 dpi（步进 10）
        dpiBar.setProgress(dpiCur <= 0 ? 0 : Math.max(1, Math.min(48, (dpiCur - 240) / 10)));
        Theme.styleSeekBar(p, dpiBar);
        dpiBar.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar b, int v, boolean fromUser) {
                if (v == 0) {
                    dpiVal.setText("跟随系统");
                    if (fromUser) {
                        Config.setDpi(a, 0);
                    }
                    return;
                }
                int d = 240 + v * 10;
                dpiVal.setText(d + " dpi");
                if (fromUser) {
                    Config.setDpi(a, d);
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar b) {
                // 密度是布局测量时就定死的，不重建就看不到任何变化。
                // 先同步落盘 → 收起面板（面板挂在要重建的 Activity 上）→ 重建界面。
                Config.setDpi(a, Config.dpi(a));
                if (sDialog != null) {
                    sDialog.dismiss();
                }
                UiController.recreateActivity(a);
            }
        });
        dpiCard.addView(dpiBar);
        TextView dpiHint = Theme.hint(a, p,
                "只改界面尺寸，视频画面不受影响。松手后界面会按新密度立即重建一次，"
                        + "所以面板会自己收起。");
        dpiHint.setPadding(0, 0, 0, Theme.dp(a, 6));
        dpiCard.addView(dpiHint);
    }

    /* ==================== 分组五：净化（去广告 / 福袋 / 热更新） ==================== */

    private static void buildCleanGroup(final Activity a, final Theme.Palette p,
                                        LinearLayout root) {
        LinearLayout adCard = groupCard(a, p, root, "广告", true);
        switchRow(a, p, adCard, "拦截全部广告位", Config.adGate(a),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        Config.setAdGate(a, v);
                    }
                });
        divider(a, p, adCard);
        switchRow(a, p, adCard, "拦截开屏广告", Config.blockSplashAd(a),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        Config.setBlockSplashAd(a, v);
                    }
                });
        divider(a, p, adCard);
        switchRow(a, p, adCard, "隐藏信息流广告", Config.blockFeedAd(a),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        Config.setBlockFeedAd(a, v);
                        UiController.refresh(a);
                    }
                });
        TextView adHint = Theme.hint(a, p,
                "「拦截全部广告位」把宿主的广告可用性判定总闸关掉："
                        + "开屏 / 推荐流 / 短剧 banner / 暂停插播 / 横屏插入等一并失效，"
                        + "宿主以后新增的广告位也自动被拦。\n"
                        + "代价：「看视频得金币」这类需要主动播广告换奖励的入口会没反应"
                        + "（发奖由服务端校验，拦广告不会让金币白得）。\n"
                        + "「隐藏信息流广告」针对的是已经预插入到视频流里的广告队列。"
                        + "所有改动需重启红果生效。");
        adHint.setPadding(0, Theme.dp(a, 2), 0, Theme.dp(a, 6));
        adCard.addView(adHint);

        LinearLayout miscCard = groupCard(a, p, root, "其他", false);
        switchRow(a, p, miscCard, "隐藏播放页福袋", Config.hideLuckyBag(a),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        Config.setHideLuckyBag(a, v);
                        UiController.refresh(a);
                    }
                });
        divider(a, p, miscCard);
        switchRow(a, p, miscCard, "禁用热更新", Config.blockHotfix(a),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        Config.setBlockHotfix(a, v);
                    }
                });
        TextView miscHint = Theme.hint(a, p,
                "「禁用热更新」在 Play 版上无效——该渠道本身不含热更新框架。"
                        + "改动在红果下次启动时生效。");
        miscHint.setPadding(0, Theme.dp(a, 2), 0, Theme.dp(a, 6));
        miscCard.addView(miscHint);
    }

    /* ==================== 分组二：组件显隐（独立一页） ==================== */

    private static void buildSlotsGroup(final Activity a, final Theme.Palette p,
                                        LinearLayout root) {
        LinearLayout card = groupCard(a, p, root, "组件显隐", true);
        TextView hint = Theme.hint(a, p,
                "开 = 隐藏该组件；滑杆 = 单项透明度（再乘「界面透明度」）。"
                        + "首页信息流与二级播放页共用同一套规则。");
        hint.setPadding(0, Theme.dp(a, 6), 0, Theme.dp(a, 2));
        card.addView(hint);
        for (int i = 0; i < Config.SLOT_KEYS.length; i++) {
            if (i > 0) {
                divider(a, p, card);
            }
            addSlotRow(a, p, card, Config.SLOT_KEYS[i], Config.SLOT_NAMES[i], null);
        }
    }

    /** 底部标签栏：把原来「选择要隐藏的标签」对话框改成页内开关，少一层点击。 */
    private static void inlineBottomTabs(final Activity a, final Theme.Palette p,
                                         LinearLayout card) {
        List<String> labels = UiController.bottomTabLabels(a);
        if (labels.isEmpty()) {
            // 播放页（二级页）本来就没有底部标签栏，这不是功能缺失 ——
            // 把「去哪能看」说清楚，免得以为开关被删了。
            TextView none = Theme.hint(a, p,
                    "当前页面没有底部标签栏（短剧播放页本来就没有）。"
                            + "回到首页或剧场页再打开本面板，就会列出「首页 / 剧场 / 我的」等标签。");
            none.setPadding(0, Theme.dp(a, 4), 0, Theme.dp(a, 4));
            none.setLineSpacing(Theme.dp(a, 3), 1f);
            card.addView(none);
            return;
        }
        HashSet<String> hidden = new HashSet<>();
        for (String s : Config.hiddenBottomTabs(a).split("\\|")) {
            s = s.trim();
            if (!s.isEmpty()) {
                hidden.add(s);
            }
        }
        for (String label : labels) {
            final String lb = label;
            Switch sw = Theme.makeSwitch(a, p, "隐藏「" + label + "」", hidden.contains(label));
            sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton b, boolean v) {
                    List<String> cur = new ArrayList<>();
                    for (String s : Config.hiddenBottomTabs(a).split("\\|")) {
                        s = s.trim();
                        if (!s.isEmpty() && !s.equals(lb)) {
                            cur.add(s);
                        }
                    }
                    if (v) {
                        cur.add(lb);
                    }
                    Config.setHiddenBottomTabs(a, cur);
                    UiController.refresh(a);
                }
            });
            card.addView(sw);
        }
    }

    /* ==================== 分组三：播放控制 ==================== */

    private static void buildPlaybackGroup(final Activity a, final Theme.Palette p,
                                           LinearLayout root) {
        LinearLayout card = groupCard(a, p, root, "播放控制", false);

        switchRow(a, p, card, "默认最高画质", Config.maxQuality(a),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        Config.setMaxQuality(a, v);
                    }
                });
        divider(a, p, card);

        switchRow(a, p, card, "强制显示弹幕", Config.danmakuForce(a),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        Config.setDanmakuForce(a, v);
                        Danmaku.apply(a);
                    }
                });
        TextView dmHint = Theme.hint(a, p,
                "有些设备/账号上宿主下发的弹幕默认值是关的，弹幕就怎么都不出来。"
                        + "打开后本模块会直接把宿主自己的弹幕开关写成开启（下次进入播放页生效）。"
                        + "关闭时：以前自己拨过弹幕开关的会按原值还原；"
                        + "从没拨过的（就是没弹幕的那类设备）宿主存储层删不掉这个键，"
                        + "需要在红果自己的弹幕开关里关掉。");
        dmHint.setPadding(0, 0, 0, Theme.dp(a, 6));
        card.addView(dmHint);
        divider(a, p, card);

        switchRow(a, p, card, "自定义倍速（关闭 = 跟随宿主）", Config.speedCustomOn(a),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        Config.setSpeedCustomOn(a, v);
                    }
                });
        divider(a, p, card);

        switchRow(a, p, card, "双击打开评论区", Config.doubleTapComment(a),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        Config.set(a, "dt_comment", v);
                    }
                });
        divider(a, p, card);

        // 倍速滑杆与横屏手势：每个都有说明文字，收进折叠行
        final Expander ex = expander(a, p, card, "playdetail", "倍速调节与横屏手势",
                speedSummary(a), true);
        final TextView speedLabel = blockLabelRow(a, p, ex.body,
                "常规倍速", (Config.speedCustom(a) / 10f) + "x");
        SeekBar sbSpeed = new SeekBar(a);
        sbSpeed.setMax(10);   // 10~20 => 1.0x~2.0x（引擎对速率的硬上限，同长按）
        sbSpeed.setProgress(Config.speedCustom(a) - 10);
        Theme.styleSeekBar(p, sbSpeed);
        sbSpeed.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                int x10 = value + 10;
                speedLabel.setText((x10 / 10f) + "x");
                Config.setSpeedCustom(a, x10);
                ex.summary.setText(speedSummary(a));
            }
        });
        ex.body.addView(sbSpeed);
        TextView speedHint = Theme.hint(a, p,
                "上限 2.0x：常规与长按是同一条播放器通路，native 引擎把超过 2.0 的请求降档——"
                        + "实测官方面板的 3.0x 实际只有约 1.5x，2.0x 已是真实上限。");
        speedHint.setPadding(0, 0, 0, Theme.dp(a, 6));
        ex.body.addView(speedHint);

        final TextView pressLabel = blockLabelRow(a, p, ex.body,
                "长按倍速", (Config.pressSpeedCustom(a) / 10f) + "x");
        SeekBar sbPress = new SeekBar(a);
        sbPress.setMax(10);   // 10~20 => 1.0x~2.0x（宿主引擎对速率有 2.0x 硬上限）
        sbPress.setProgress(Config.pressSpeedCustom(a) - 10);
        Theme.styleSeekBar(p, sbPress);
        sbPress.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                int x10 = value + 10;
                pressLabel.setText((x10 / 10f) + "x");
                Config.setPressSpeedCustom(a, x10);
                ex.summary.setText(speedSummary(a));
            }
        });
        ex.body.addView(sbPress);
        TextView pressHint = Theme.hint(a, p,
                "在画面边缘长按临时提速到该倍率，松手恢复常规倍速。上限 2.0x：播放器引擎（native）把超过 2.0 的请求降档——实测官方面板的 3.0x 也只有约 1.5x，2.0x 已是真实上限。");
        pressHint.setPadding(0, 0, 0, Theme.dp(a, 6));
        ex.body.addView(pressHint);

        // 横屏是另一套 Activity，与宿主的原生手势互斥：三击必须吞掉前两击，
        // 宿主就收不到双击 → 横屏暂停失效。默认关闭，说明写在面板上避免当 bug。
        Switch swLand = Theme.makeSwitch(a, p,
                "横屏三击打开评论区（开启后横屏双击不再暂停）",
                Config.landscapeGesture(a));
        swLand.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean v) {
                Config.set(a, "dt_land", v);
            }
        });
        ex.body.addView(swLand);
        TextView landHint = Theme.hint(a, p,
                "横屏单击=切换控制栏，双击=暂停。三击 =「双击 + 再补一下」，"
                        + "两者是包含关系无法同时生效：开启则三击开评论区、横屏双击不再暂停；"
                        + "关闭则横屏保持原生双击暂停。");
        landHint.setPadding(0, 0, 0, Theme.dp(a, 4));
        ex.body.addView(landHint);
    }

    /* ==================== 分组四：面板与交互 ==================== */

    private static void buildPanelGroup(final Activity a, final Theme.Palette p,
                                        LinearLayout root) {
        LinearLayout card = groupCard(a, p, root, "面板与交互", false);

        switchRow(a, p, card, "长按底部「首页」唤出面板", Config.entryEnabled(a),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        Config.set(a, "entry", v);
                    }
                });
        divider(a, p, card);

        switchRow(a, p, card, "播放设置弹层里显示模块入口（竖屏）", Config.sheetEntry(a),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        Config.set(a, "sheet_entry", v);
                    }
                });
        divider(a, p, card);

        switchRow(a, p, card, "屏蔽「发现新版本」升级弹窗", Config.blockUpdate(a),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        Config.set(a, "no_update", v);
                    }
                });
        divider(a, p, card);

        // 清屏省心：总开关 + 单一数值项，直接露出不折叠
        switchRow(a, p, card, "清屏后自动收起控件", Config.idleHideOn(a),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        Config.setIdleHideOn(a, v);
                    }
                });
        divider(a, p, card);
        TextView idleLabel = blockLabelRow(a, p, card,
                "清屏自动收起（仅二级播放页）", Config.clearIdleSec(a) + " 秒");
        SeekBar sbIdle = new SeekBar(a);
        sbIdle.setMax(57);   // 3~60 秒
        sbIdle.setProgress(Config.clearIdleSec(a) - 3);
        Theme.styleSeekBar(p, sbIdle);
        sbIdle.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                int sec = value + 3;
                idleLabel.setText(sec + " 秒");
                Config.setClearIdleSec(a, sec);
            }
        });
        card.addView(sbIdle);
    }

    /* ==================== 分组五：自定义隐藏清单（并入「组件显隐」页） ==================== */

    private static void buildAdvancedGroup(final Activity a, final Theme.Palette p,
                                           LinearLayout root) {
        LinearLayout card = groupCard(a, p, root, "自定义隐藏清单", false);

        // 低频/调试：默认折叠。清单内容 + 调试按钮都收在这里
        Expander ex = expander(a, p, card, "adv", "自定义隐藏清单",
                customSummary(a), true);

        // 小白条诊断：只在高级组露出，主视图不给它留行
        TextView navDiag = Theme.hint(a, p, "小白条诊断：" + UiController.navBarStatus(a));
        navDiag.setPadding(0, 0, 0, Theme.dp(a, 6));
        ex.body.addView(navDiag);

        TextView rule = Theme.hint(a, p, Config.resolveHint());
        rule.setPadding(0, 0, 0, Theme.dp(a, 8));
        ex.body.addView(rule);

        List<String[]> list = Config.overlayList(a);
        if (list.isEmpty()) {
            TextView empty = Theme.hint(a, p, "（清单为空，当前全部显示）");
            empty.setPadding(0, 0, 0, Theme.dp(a, 6));
            ex.body.addView(empty);
        }
        for (int i = 0; i < list.size(); i++) {
            addCustomRow(a, p, ex.body, list, i);
        }

        LinearLayout tools = new LinearLayout(a);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setPadding(0, Theme.dp(a, 8), 0, 0);
        tools.addView(Theme.filledButton(a, p, "扫描当前页面", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanDialog(a);
            }
        }), weight(1.4f, 0));
        tools.addView(Theme.outlineButton(a, p, "手动添加", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addDialog(a);
            }
        }), weight(1f, 8));
        tools.addView(Theme.dangerButton(a, p, "清空", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Config.setOverlayList(a, new ArrayList<String[]>());
                UiController.refresh(a);
                rebuild(a);
            }
        }), weight(0.8f, 8));
        ex.body.addView(tools);

    }

    /* ==================== 滑杆块 ==================== */

    /**
     * 滑杆块：上行「标签 + 右侧数值」，下行 SeekBar。
     * 返回数值 TextView 供回调里更新文本。
     */
    private static TextView blockLabelRow(Activity a, Theme.Palette p, LinearLayout parent,
                                          String label, String value) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Theme.dp(a, 8), 0, 0);
        row.addView(Theme.plain(a, p, label), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView val = Theme.blockValue(a, p, value);
        row.addView(val, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        parent.addView(row);
        return val;
    }

    /** 全局透明度滑杆块（数值文本由内部维护，配置写入走 extra 回调）。 */
    private static void alphaBlock(final Activity a, final Theme.Palette p, LinearLayout parent,
                                   String label, int value, int max,
                                   final SeekBar.OnSeekBarChangeListener extra) {
        final TextView val = blockLabelRow(a, p, parent, label, value + "%");
        SeekBar bar = new SeekBar(a);
        bar.setMax(max);
        bar.setProgress(value);
        Theme.styleSeekBar(p, bar);
        bar.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar b, int v, boolean fromUser) {
                val.setText(v + "%");
                extra.onProgressChanged(b, v, fromUser);
            }
        });
        parent.addView(bar);
    }

    /* ---------------- 清单增删后重开面板 ---------------- */

    /** 清单/开关结构变了直接重建比增量刷新简单可靠。 */
    private static void rebuild(final Activity a) {
        if (dead(a)) {
            return;
        }
        // 重建前记住当前分组：全屏两级结构下，重建后回到一级会让用户
        // 刚操作的二级页「跳走」，体验很差。
        final String keep = sGroup;
        try {
            if (sDialog != null) {
                sDialog.dismiss();
            }
        } catch (Throwable t) {
            UiController.crashDump("rebuild:dismiss", t);
        }
        try {
            a.getWindow().getDecorView().post(new Runnable() {
                @Override
                public void run() {
                    // post 回来时页面可能已经被宿主换掉（DPI 重建、暗色换肤、切剧），
                    // 对着死掉的 Activity new Dialog + show 会抛 BadTokenException，
                    // 而这正好是「点了某个开关就闪退」的典型现场。
                    if (dead(a)) {
                        return;
                    }
                    try {
                        show(a);
                        if (keep != null && sDialog != null) {
                            renderGroup(a, Theme.of(a), keep, groupTitleOf(keep));
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "rebuild failed", t);
                        UiController.crashDump("rebuild:show", t);
                    }
                }
            });
        } catch (Throwable t) {
            UiController.crashDump("rebuild:post", t);
        }
    }

    /** Activity 是否已经不可用（正在结束 / 已销毁 / 窗口没了）。 */
    private static boolean dead(Activity a) {
        if (a == null) {
            return true;
        }
        try {
            if (a.isFinishing() || a.isDestroyed()) {
                return true;
            }
            return a.getWindow() == null || a.getWindow().getDecorView() == null;
        } catch (Throwable t) {
            return true;
        }
    }

    /* ---------------- 行构建 ---------------- */

    private static void addSlotRow(final Activity a, final Theme.Palette p, LinearLayout card,
                                   final String key, String name, final Expander ex) {
        Switch sw = Theme.makeSwitch(a, p, "隐藏 " + name, Config.overlayHide(a, key));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean v) {
                Config.setOverlayHide(a, key, v);
                UiController.refresh(a);
                if (ex != null) {
                    ex.summary.setText(slotsSummary(a));
                }
            }
        });
        card.addView(sw);

        // 所属组件已由上面那行开关点明，这里只留数值
        final TextView label = Theme.value(a, p, "透明度：" + Config.overlayAlpha(a, key) + "%");
        card.addView(label);

        SeekBar bar = new SeekBar(a);
        bar.setMax(100);
        bar.setProgress(Config.overlayAlpha(a, key));
        Theme.styleSeekBar(p, bar);
        bar.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar sb, int value, boolean fromUser) {
                label.setText("透明度：" + value + "%");
                if (fromUser) {
                    Config.setInt(a, key + "_alpha", value);
                    UiController.refresh(a);
                }
            }
        });
        card.addView(bar);
    }

    private static void addCustomRow(final Activity a, final Theme.Palette p, LinearLayout card,
                                     final List<String[]> list, final int index) {
        final String[] item = list.get(index);
        String[] n = Names.describe(item[0]);

        LinearLayout line = new LinearLayout(a);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(0, Theme.dp(a, 8), 0, 0);

        // 图标 + 中文功能名，原始规则作为小字副标题（排查用）
        LinearLayout nameCol = new LinearLayout(a);
        nameCol.setOrientation(LinearLayout.VERTICAL);
        TextView name = Theme.plain(a, p, n[0] + " " + n[1]);
        name.setTextColor(p.title);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        nameCol.addView(name);
        TextView rule = Theme.hint(a, p, item[0]);
        rule.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        nameCol.addView(rule);
        line.addView(nameCol, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button del = Theme.dangerButton(a, p, "删除", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<String[]> now = Config.overlayList(a);
                // 按内容重新定位，避免索引错位
                for (int i = 0; i < now.size(); i++) {
                    if (now.get(i)[0].equals(item[0])) {
                        now.remove(i);
                        break;
                    }
                }
                Config.setOverlayList(a, now);
                UiController.refresh(a);
                rebuild(a);
            }
        });
        del.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        del.setPadding(Theme.dp(a, 12), Theme.dp(a, 4), Theme.dp(a, 12), Theme.dp(a, 4));
        line.addView(del, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(line);

        Switch sw = Theme.makeSwitch(a, p, "隐藏", "1".equals(item[1]));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean v) {
                patch(a, item[0], "hide", v ? "1" : "0");
            }
        });
        card.addView(sw);

        final TextView label = Theme.value(a, p, "透明度：" + item[2] + "%");
        card.addView(label);

        SeekBar bar = new SeekBar(a);
        bar.setMax(100);
        try {
            bar.setProgress(Integer.parseInt(item[2]));
        } catch (Throwable ignored) {
        }
        Theme.styleSeekBar(p, bar);
        bar.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar sb, int value, boolean fromUser) {
                label.setText("透明度：" + value + "%");
                if (fromUser) {
                    patch(a, item[0], "alpha", String.valueOf(value));
                }
            }
        });
        card.addView(bar);
    }

    private static void patch(Activity a, String match, String field, String value) {
        List<String[]> now = Config.overlayList(a);
        boolean found = false;
        for (String[] it : now) {
            if (it[0].equals(match)) {
                if ("hide".equals(field)) {
                    it[1] = value;
                } else {
                    it[2] = value;
                }
                found = true;
                break;
            }
        }
        if (!found) {
            String[] it = {match, "1", "100"};
            if ("hide".equals(field)) {
                it[1] = value;
            } else {
                it[2] = value;
            }
            now.add(it);
        }
        Config.setOverlayList(a, now);
        UiController.refresh(a);
    }

    /* ---------------- 扫描 / 手动添加 ---------------- */

    /* 注：下面两个弹窗走 AlertDialog.Builder，用的是**宿主主题**，
       模块无法指定其明暗；只有内容视图（EditText）能自带配色。 */

    private static void scanDialog(final Activity a) {
        final List<String[]> blocks = UiController.scanBlocks(a);
        if (blocks.isEmpty()) {
            new AlertDialog.Builder(a).setTitle("扫描当前页面")
                    .setMessage("没有扫描到可单独控制的组件。")
                    .setPositiveButton("好", null).show();
            return;
        }
        final Theme.Palette p = Theme.of(a);
        final boolean[] checked = new boolean[blocks.size()];
        final List<String[]> existing = Config.overlayList(a);

        // 每行：图标 + 中文功能名（原始类名·id·尺寸 作为小字副标题）+ 勾选框
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(a, 8), 0, Theme.dp(a, 8), 0);
        for (int i = 0; i < blocks.size(); i++) {
            final int idx = i;
            String[] b = blocks.get(i);
            // 规则（cls:/id:）与扫描标签（类名 #id · 文案）一起拿去翻译：
            // 规则常是 id:hg6 这种无意义短 id，特征往往在标签的类名/文案里
            String[] n = Names.describe(b[1] + " " + b[0]);
            final boolean on = containsRule(existing, b[1]);
            checked[i] = on;

            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Theme.dp(a, 8), 0, Theme.dp(a, 8));
            row.setBackground(Theme.pressable(a, p));

            TextView icon = Theme.plain(a, p, n[0]);
            icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            icon.setGravity(Gravity.CENTER);
            icon.setMinWidth(Theme.dp(a, 30));
            row.addView(icon, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout texts = new LinearLayout(a);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView title = Theme.plain(a, p, n[1]);
            title.setTextColor(p.title);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            texts.addView(title);
            TextView sub = Theme.hint(a, p, b[0]);
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            texts.addView(sub);
            row.addView(texts, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            final CheckBox cb = new CheckBox(a);
            cb.setChecked(on);
            cb.setButtonTintList(ColorStateList.valueOf(p.primary));
            cb.setClickable(false);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    checked[idx] = !checked[idx];
                    cb.setChecked(checked[idx]);
                }
            });
            row.addView(cb, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            box.addView(row);
        }
        ScrollView sv = new ScrollView(a);
        sv.addView(box);

        new AlertDialog.Builder(a)
                .setTitle("扫描到 " + blocks.size() + " 个组件（勾选即加入清单并隐藏）")
                .setView(sv)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        List<String[]> now = Config.overlayList(a);
                        for (int i = 0; i < blocks.size(); i++) {
                            String rule = blocks.get(i)[1];
                            boolean has = false;
                            for (String[] e : now) {
                                if (e[0].equals(rule)) {
                                    has = true;
                                    break;
                                }
                            }
                            if (checked[i] && !has) {
                                now.add(new String[]{rule, "1", "100"});
                            } else if (!checked[i] && has) {
                                for (int k = 0; k < now.size(); k++) {
                                    if (now.get(k)[0].equals(rule)) {
                                        now.remove(k);
                                        break;
                                    }
                                }
                            }
                        }
                        Config.setOverlayList(a, now);
                        UiController.refresh(a);
                        rebuild(a);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static boolean containsRule(List<String[]> list, String rule) {
        for (String[] e : list) {
            if (e[0].equals(rule)) {
                return true;
            }
        }
        return false;
    }

    private static void addDialog(final Activity a) {
        final Theme.Palette p = Theme.of(a);
        final EditText input = new EditText(a);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("类名 / @文案 / text:文案 / id:名字");
        input.setTextColor(p.title);
        input.setHintTextColor(p.sub);
        input.setBackground(Theme.inputBg(a, p));
        int pad = Theme.dp(a, 14);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(a)
                .setTitle("手动添加（多个用 | 分隔）")
                .setView(input)
                .setPositiveButton("添加", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String text = input.getText().toString().trim();
                        if (text.isEmpty()) {
                            return;
                        }
                        for (String part : text.split("\\|")) {
                            part = part.trim();
                            if (!part.isEmpty()) {
                                Config.addOverlay(a, part);
                            }
                        }
                        UiController.refresh(a);
                        rebuild(a);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /* ---------------- 基础控件 ---------------- */

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar bar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar bar) {
        }
    }

    private static LinearLayout.LayoutParams weight(float w, int leftMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, w);
        lp.leftMargin = leftMarginDp;
        return lp;
    }
}
