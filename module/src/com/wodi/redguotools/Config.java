package com.wodi.redguotools;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置读写。宿主进程与模块自身进程共用同一份配置：
 * 优先使用「模块自己包」的 SharedPreferences，取不到时回退到当前进程 Context。
 */
public final class Config {

    public static final String MODULE_PKG = "com.wodi.redguotools";
    private static final String PREF = "rg_tools_cfg";

    /* 状态栏底色 */
    public static final int BG_FOLLOW = 0; // 跟随背景（不干预应用自身配色）
    public static final int BG_BLACK = 1;  // 纯黑
    public static final int BG_WHITE = 2;  // 纯白

    /* ---------------- 组件槽位表 ---------------- */

    /**
     * 槽位的顺序即面板里的显示顺序。
     *
     * <p>前 3 项 + 末项是首页（MainFragmentActivity）的浮层，中间 5 项是二级界面
     * （ShortSeriesActivity，即短剧播放页）的浮层。注意首页信息流里的视频卡片
     * 用的是同一套 ShortSeries 组件，所以「播放页」那几项在首页卡片上同样生效。
     */
    public static final String[] SLOT_KEYS = {
            "top", "right", "bottom",
            "plbar", "pldanmaku", "plheader", "plhot", "plbottom",
            "feedfull", "feedrec", "feedtag",
    };

    public static final String[] SLOT_NAMES = {
            "顶部导航栏（首页）",
            "右侧互动栏（通用）",
            "底部选集条（首页）",
            "顶部信息栏（播放页）",
            "弹幕输入条（播放页）",
            "标题行（播放页 / 首页卡片）",
            "热评行（播放页）",
            "底部信息/选集条（播放页）",
            "全屏观看按钮（首页卡片）",
            "备案号水印（首页卡片）",
            "类型标签（剧名下方）",
    };

    /**
     * 每个槽位的候选匹配规则，**全部生效（并集）**。
     * 前缀含义见 {@link #resolveHint()}：
     *   cls:  类名后缀        pcls: 类名后缀节点的父级
     *   text: 文案包含（多个用 | 分隔）   bar: 文案 → 满宽可点击祖先
     *   id:   资源 id 名       @xxx: 等价 text:xxx
     *
     * 播放页（ShortSeriesActivity）会预 inflation 多个 ViewPager 页，
     * 同一个组件的可见实例与不可见实例并存，所以按类名/文案全量匹配才稳。
     * 少数组件没有稳定类名，只能退到资源 id；红果升级后若失效，
     * 用面板里的「扫描当前页面…」重新取一条即可。
     */
    public static final String[][] SLOT_RULES = {
            // 首页顶部导航栏 = SlidingTabLayout 的父级（同时含 ☰ 与搜索）
            {"pcls:SlidingTabLayout"},
            // 右侧互动栏（关注/收藏/评论/点赞/分享），首页与播放页共用同一个类
            {"cls:com.dragon.read.component.shortvideo.impl.rightview.ShortSeriesRightView"},
            // 首页底部「观看完整漫剧 · 全N集」条
            {"bar:观看完整|观看全集|立即观看"},
            // 播放页顶部信息栏：← 第N集 / 1.0x / ⋮
            {"cls:com.dragon.read.component.seriessdk.ui.titlebar.CommonTitleBar"},
            // 播放页「弹」弹幕输入入口
            {"cls:com.dragon.read.component.shortvideo.danmaku.PublishDanmakuEntranceView"},
            // 播放页标题行（剧名 + 标签行）：header 是占位行，真正显示的是同级的 g41；
            // 首页信息流视频卡片上是 ShortSeriesExtendTextView（实测 7.3.9.32），
            // 只写 header 会命中一个 0 尺寸占位行、调了透明度却看不到任何变化。
            {"cls:com.dragon.read.component.shortvideo.impl.infoheader.ShortSeriesInfoHeaderView",
             "id:g41",
             "cls:com.dragon.read.component.seriessdk.ui.infolayer.ShortSeriesExtendTextView"},
            // 播放页「热评」行
            {"cls:com.dragon.read.component.shortvideo.impl.comment.view.InfoPanelHotCommentView",
             "cls:com.dragon.read.component.shortvideo.impl.infopanel.hotcomment.SeriesHotCommentView"},
            // 播放页底部信息区：作者声明行 + 「选集·已完结·全N集」条
            {"cls:com.dragon.read.component.shortvideo.impl.infobottom.ShortSeriesInfoBottomView",
             "cls:com.dragon.read.component.shortvideo.impl.bottombar.view.BottomRelateSeriesView",
             "id:hh"},
            // 首页卡片上那枚「全屏观看」浮起按钮（图标 + 文字，可点击祖先由 text: 规则回捞）
            {"text:全屏观看"},
            // 视频画面右上角的「（番茄）网微剧备字（2026）第N号」备案号水印。
            // 它是盖在视频上面的一个 AppCompatTextView（id=i0a），可以单独隐藏/降透明度。
            // 注意：它上面那行「内容纯属虚构 无不良引导」**不属于本模块能控制的范围** ——
            // 整棵视图树里没有对应控件，是画在视频画面上的（片源自带/播放器自绘）。
            {"text:网微剧备字", "id:i0a"},
            // 剧名下方那排类型标签（「异界 玄幻 系统 穿越」这种圆角 chip）。
            // 它是 com.dragon.read.widget.tag.PriorityTagLayout，实测全树只有 1 个实例。
            // 这排标签之前不在任何槽位里，所以全局透明度拉到底它也不动。
            {"cls:com.dragon.read.widget.tag.PriorityTagLayout"},
    };

    public static String resolveHint() {
        return "规则写法：类名（可只写后半段）/ @文案 / text:文案 / id:名字，多个用 | 分隔";
    }

    private static SharedPreferences sp;

    private Config() {
    }

    /**
     * 注意：不能用 createPackageContext 去拿模块自己的 SharedPreferences——
     * 宿主进程与模块包不是同一个 UID，读到的是无法写入的目录，set 会静默失败。
     * 面板与 Hook 都跑在宿主进程里，因此直接用宿主自己的存储即可。
     */
    public static synchronized void init(Context ctx) {
        if (sp != null || ctx == null) {
            return;
        }
        Context app = ctx.getApplicationContext();
        sp = (app != null ? app : ctx).getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static SharedPreferences sp(Context fallback) {
        if (sp == null) {
            init(fallback);
        }
        return sp;
    }

    public static boolean ready() {
        return sp != null;
    }

    /* ---------------- 长按入口 ---------------- */

    public static boolean entryEnabled(Context c) {
        return get(c, "entry", true);
    }

    /** 播放设置弹层（清晰度/弹幕设置那一块）里是否显示「ReadGuo 设置」入口。 */
    public static boolean sheetEntry(Context c) {
        return get(c, "sheet_entry", true);
    }

    /* ---------------- 状态栏 ---------------- */

    public static boolean hideStatusBar(Context c) {
        return get(c, "sb_hide", true);
    }

    /** 状态栏区域底色。 */
    public static int statusBarBg(Context c) {
        return getInt(c, "sb_bg", BG_FOLLOW);
    }

    /* 状态栏「点击显示数字」配置（sb_tap）已移除 —— 该功能整体下线。 */

    /* ---------------- 统一透明度 ---------------- */

    /** 叠加组件不透明度百分比，100 表示不透明。 */
    public static int alphaPercent(Context c) {
        return getInt(c, "alpha", 100);
    }

    /* ---------------- 双击 ---------------- */

    public static boolean doubleTapComment(Context c) {
        return get(c, "dt_comment", true);
    }

    /**
     * 横屏下是否也启用「三击打开评论区」手势。**默认关闭。**
     *
     * <p><b>为什么默认关闭：</b>横屏的「三击开评论区」与宿主的「双击暂停」在实现上
     * 互斥 —— 三击要求把第 1、2 击**吞掉**（不让宿主看到），否则宿主的双击检测
     * 会吃掉中间态；而双击/单击暂停恰恰要求这些点击**送达宿主**。
     * 两者无法同时成立，所以只能二选一，默认选「保住原生双击暂停」。
     *
     * <p>关闭时（默认）横屏**完全不干预**：不计数、不吞点击、也不屏蔽宿主的
     * {@code onDoubleTap}，单击/双击暂停与边缘长按倍速全部按宿主原样工作。
     *
     * <p>开启后横屏变成「三击」模式：整段连击被吞掉以稳定进评论区，
     * 代价是横屏双击不再暂停。需要时在面板「手势」卡片里手动打开。
     */
    public static boolean landscapeGesture(Context c) {
        return get(c, "dt_land", false);
    }

    /* ---------------- 组件槽位：隐藏 / 透明度 ---------------- */

    /** 某个槽位是否隐藏。默认 false = 显示。 */
    public static boolean overlayHide(Context c, String k) {
        return get(c, k + "_hide", false);
    }

    /** 某个槽位的不透明度百分比。默认 100。 */
    public static int overlayAlpha(Context c, String k) {
        return getInt(c, k + "_alpha", 100);
    }

    /* ---------------- 自定义隐藏清单 ---------------- */
    /*
     * 存成一段文本，一行一条：  match|hide|alpha
     * 默认空串 —— 即「不填就全部显示」。
     */

    private static final String KEY_OVL = "ovl_list";

    /** 返回 [match, hide(0/1), alpha]，顺序即用户添加顺序。 */
    public static List<String[]> overlayList(Context c) {
        List<String[]> out = new ArrayList<>();
        String raw = "";
        try {
            raw = sp(c).getString(KEY_OVL, "");
        } catch (Throwable ignored) {
        }
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String line : raw.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] p = line.split("\\|");
            if (p.length < 3 || p[0].trim().isEmpty()) {
                continue;
            }
            String hide = "1".equals(p[1]) ? "1" : "0";
            String alpha = "100";
            try {
                int v = Integer.parseInt(p[2]);
                alpha = String.valueOf(Math.max(0, Math.min(100, v)));
            } catch (Throwable ignored) {
            }
            out.add(new String[]{p[0].trim(), hide, alpha});
        }
        return out;
    }

    public static void setOverlayList(Context c, List<String[]> list) {
        StringBuilder sb = new StringBuilder();
        for (String[] it : list) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(it[0]).append('|').append(it[1]).append('|').append(it[2]);
        }
        try {
            sp(c).edit().putString(KEY_OVL, sb.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    /** 往清单里追加一条（去重）。 */
    public static void addOverlay(Context c, String match) {
        if (match == null || match.trim().isEmpty()) {
            return;
        }
        match = match.trim();
        List<String[]> list = overlayList(c);
        for (String[] it : list) {
            if (it[0].equals(match)) {
                return;
            }
        }
        list.add(new String[]{match, "1", "100"});
        setOverlayList(c, list);
    }

    /* ---------------- 面板卡片折叠 ---------------- */
    /*
     * 只影响面板自身的浏览形态，与 Hook 行为无关。
     * 不写 init 标记：key 不存在时返回调用方给的默认值，
     * 用户一旦点过标题栏，记录就落到 key 上，之后完全按用户操作记忆。
     */

    /** 面板里某张卡片是否处于折叠状态。 */
    public static boolean folded(Context c, String cardKey, boolean def) {
        return get(c, "fold_" + cardKey, def);
    }

    public static void setFolded(Context c, String cardKey, boolean v) {
        set(c, "fold_" + cardKey, v);
    }

    /* ---------------- 底部标签栏：按文字隐藏单个 tab ---------------- */
    /*
     * 存成一段文本，标签文字之间用 | 分隔（如 "商城|赚钱"）。
     * 默认空串 —— 即底部标签全部显示。
     */

    private static final String KEY_BTAB = "btab_hidden";

    /** 当前配置要隐藏的底部标签（原文，含分隔符；空 = 全部显示）。 */
    public static String hiddenBottomTabs(Context c) {
        try {
            return sp(c).getString(KEY_BTAB, "");
        } catch (Throwable t) {
            return "";
        }
    }

    public static void setHiddenBottomTabs(Context c, List<String> labels) {
        StringBuilder sb = new StringBuilder();
        for (String s : labels) {
            s = s == null ? "" : s.trim();
            if (s.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append(s);
        }
        try {
            sp(c).edit().putString(KEY_BTAB, sb.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    /* ---------------- 播放器：最高画质 / 自定义倍速 ---------------- */

    /** 起播时强制最高画质（autoplay.o.setResolution 首参替换为 ExtremelyHigh）。 */
    public static boolean maxQuality(Context c) {
        return get(c, "max_quality", true);
    }

    public static void setMaxQuality(Context c, boolean v) {
        set(c, "max_quality", v);
    }

    /** 自定义倍速开关（关 = 跟随宿主设置）。 */
    public static boolean speedCustomOn(Context c) {
        return get(c, "speed_on", false);
    }

    public static void setSpeedCustomOn(Context c, boolean v) {
        set(c, "speed_on", v);
    }

    /**
     * 自定义倍速值 ×10（10~40 = 1.0~4.0 倍）。
     *
     * <p>这是「常规播放速率」。PlayerTweaks 通过**调用栈**区分「用户选速」与
     * 「边缘长按临时倍速」（见 {@code PlayerTweaks.isTemporarySpeed()}），
     * 不再依赖数值比较，所以本值的取值域可以自由调整。
     */
    public static int speedCustom(Context c) {
        return getInt(c, "speed_x10", 20);
    }

    public static void setSpeedCustom(Context c, int v) {
        setInt(c, "speed_x10", Math.max(10, Math.min(40, v)));
    }

    /**
     * 长按边缘临时倍速值 ×10（10~40 = 1.0~4.0 倍），默认 20 = 2.0x
     * （与宿主自身的长按提速峰值一致，即默认不改变宿主行为）。
     *
     * <p>宿主的长按提速峰值是**硬编码 2.0x**，PlayerTweaks 会把该值改写成这里的配置值。
     */
    public static int pressSpeedCustom(Context c) {
        return getInt(c, "press_x10", 20);
    }

    public static void setPressSpeedCustom(Context c, int v) {
        setInt(c, "press_x10", Math.max(10, Math.min(40, v)));
    }

    /** 清屏态下「无操作多少秒」自动收起其余控件（3~60 秒，默认 10）。 */
    public static int clearIdleSec(Context c) {
        return getInt(c, "clear_idle_s", 10);
    }

    public static void setClearIdleSec(Context c, int v) {
        setInt(c, "clear_idle_s", Math.max(3, Math.min(60, v)));
    }

    /** 屏蔽「发现新版本」升级弹窗。默认开启（用户明确提出要屏蔽）。 */
    public static boolean blockUpdate(Context c) {
        return get(c, "no_update", true);
    }

    public static void setBlockUpdate(Context c, boolean v) {
        set(c, "no_update", v);
    }

    /**
     * 「暂停时还原组件显示」。**默认开启。**
     *
     * <p>效果：视频暂停时把模块改过的 alpha 全部还原成宿主原值，被模块隐藏的组件
     * 也临时显示出来，方便看清界面（读剧名、挑选集等）；恢复播放时重新套用透明度
     * 并重新隐藏。清屏（清屏省心）模式下不强制显示隐藏的组件。
     * 首页信息流与二级播放页都生效。
     */
    public static boolean pauseRestore(Context c) {
        return get(c, "pause_restore", true);
    }

    public static void setPauseRestore(Context c, boolean v) {
        set(c, "pause_restore", v);
    }

    /* ---------------- 底部导航栏 / 小白条：自动隐藏 ---------------- */

    /**
     * 底部导航栏（小白条）隐藏开关。
     *
     * <p>开启后：进入红果即隐藏导航栏，**软件内全程不显示**，只有退出红果时才恢复。
     * 默认关闭 —— 不填就保持宿主原样。
     */
    public static boolean autoHideNav(Context c) {
        return get(c, "nav_auto", false);
    }

    public static void setAutoHideNav(Context c, boolean v) {
        set(c, "nav_auto", v);
    }

    /* ---------------- 写 ---------------- */

    public static void set(Context c, String key, boolean v) {
        try {
            sp(c).edit().putBoolean(key, v).apply();
        } catch (Throwable ignored) {
        }
    }

    public static void setInt(Context c, String key, int v) {
        try {
            sp(c).edit().putInt(key, v).apply();
        } catch (Throwable ignored) {
        }
    }

    private static boolean get(Context c, String key, boolean def) {
        try {
            return sp(c).getBoolean(key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    private static int getInt(Context c, String key, int def) {
        try {
            return sp(c).getInt(key, def);
        } catch (Throwable t) {
            return def;
        }
    }
}
