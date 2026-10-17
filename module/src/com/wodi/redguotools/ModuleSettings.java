package com.wodi.redguotools;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 模块 App 内置的设置界面（不打开红果也能配）。
 *
 * <h3>它解决什么</h3>
 * 原来所有配置只能在红果里改 —— 得先进播放页、从播放设置弹层最底下点开模块面板。
 * 这里把同一批开关做进模块 App，改完<b>导出到公共目录</b>（见 {@link CfgBridge}），
 * 红果下次启动读回去生效，全程不需要 root。
 *
 * <h3>取舍</h3>
 * 只收录「开关 / 滑杆 / 三选一」这三类能一屏说清的项。
 * 需要多选或自由输入的（自定义隐藏清单、底部标签栏勾选、逐槽位透明度）
 * 仍然只能在红果面板里改 —— 它们要实时扫描宿主视图树，本进程没有那个能力。
 *
 * <h3>渲染方式</h3>
 * 用一份静态清单驱动：{@link It} 描述「键 / 标题 / 说明 / 分组 / 类型 / 默认值」，
 * 下面统一渲染。这样加一项只要往数组里加一行，不会散落成两套 UI 代码。
 * 视觉沿用 {@link MiUi}（iOS 分组列表规范），与红果内面板保持一致。
 */
final class ModuleSettings {

    /* ---------------- 项类型 ---------------- */
    private static final int T_BOOL = 0;
    private static final int T_INT = 1;
    private static final int T_ENUM = 2;

    private static final class It {
        final String key;
        final String title;
        final String sub;
        final int type;
        final int def;        // bool 用 0/1；int 用默认值；enum 用默认下标
        final int min, max;   // int 滑杆范围
        final String[] opts;  // enum 的选项文案

        It(String key, String title, int type, int def) {
            this(key, title, null, type, def, 0, 0, null);
        }

        It(String key, String title, int type, int def, int min, int max) {
            this(key, title, null, type, def, min, max, null);
        }

        /** 枚举项：类型 + 默认下标 + 选项文案。 */
        It(String key, String title, int type, int def, String... opts) {
            this(key, title, null, type, def, 0, 0, opts);
        }

        It(String key, String title, String sub, int type, int def, String... opts) {
            this(key, title, sub, type, def, 0, 0, opts);
        }

        It(String key, String title, String sub, int type, int def,
            int min, int max, String[] opts) {
            this.key = key;
            this.title = title;
            this.sub = sub;
            this.type = type;
            this.def = def;
            this.min = min;
            this.max = max;
            this.opts = opts;
        }
    }

    private static final String G_DISPLAY = "界面显示";
    private static final String G_SLOTS = "组件显隐";
    private static final String G_PLAY = "播放控制";
    private static final String G_CLEAN = "净化";
    private static final String G_UI = "交互与入口";

    /**
     * 全部可配置项。顺序即面板里的显示顺序。
     *
     * <p>默认值必须与 {@link Config} 里各 getter 的默认值一致，否则
     * 「模块 App 里显示的开」和「红果里实际生效的」会不一致。
     */
    private static final String[] GROUP_NAMES = {
            G_DISPLAY, G_SLOTS, G_PLAY, G_CLEAN, G_UI,
    };

    private static final It[][] GROUPS = {
            /* ---- 界面显示 ---- */
            {
            new It("sb_hide", "隐藏系统状态栏", T_BOOL, 1),
            new It("sb_bg", "状态栏底色", T_ENUM, 0, "跟随背景", "纯黑", "纯白"),
            new It("nav_auto", "隐藏底部导航栏（小白条）", T_BOOL, 0),
            new It("alpha", "界面透明度（视频以外浮层）", T_INT, 100, 0, 100),
            new It("dark_mode", "暗色模式", T_ENUM, 0, "跟随系统", "浅色", "深色"),
            new It("dpi", "屏幕密度 DPI（0 = 跟随系统）", T_INT, 0, 0, 720),
            new It("pause_restore", "暂停时还原组件显示", T_BOOL, 0),

            },
            /* ---- 组件显隐（只放隐藏开关，逐项透明度仍留在红果面板） ---- */
            {
            new It("top_hide", "顶部导航栏（首页）", T_BOOL, 0),
            new It("right_hide", "右侧互动栏", T_BOOL, 0),
            new It("bottom_hide", "底部选集条（首页）", T_BOOL, 0),
            new It("plbar_hide", "顶部信息栏（播放页）", T_BOOL, 0),
            new It("pldanmaku_hide", "弹幕输入条", T_BOOL, 0),
            new It("plheader_hide", "标题行", T_BOOL, 0),
            new It("plhot_hide", "热评行", T_BOOL, 0),
            new It("plbottom_hide", "底部信息 / 选集条", T_BOOL, 0),
            new It("plprogress_hide", "底部进度条", T_BOOL, 0),
            new It("feedfull_hide", "全屏观看按钮", T_BOOL, 0),
            new It("feedrec_hide", "备案号水印", T_BOOL, 0),
            new It("feedtag_hide", "类型标签", T_BOOL, 0),
            new It("luckybag_hide", "右侧「领取」挂件（福袋）", T_BOOL, 1),

            },
            /* ---- 播放控制 ---- */
            {
            new It("max_quality", "默认最高画质", T_BOOL, 1),
            new It("speed_on", "自定义倍速（关 = 跟随宿主）", T_BOOL, 0),
            new It("speed_x10", "常规倍速 ×10（10~20 = 1.0~2.0x）", T_INT, 20, 10, 20),
            new It("press_x10", "长按倍速 ×10", T_INT, 20, 10, 20),
            new It("idle_hide_on", "清屏时自动收起控件", T_BOOL, 1),
            new It("clear_idle_s", "自动收起秒数", T_INT, 10, 3, 60),

            },
            /* ---- 净化 ---- */
            {
            new It("ad_gate", "拦截全部广告位", T_BOOL, 1),
            new It("ad_splash", "拦截开屏广告", T_BOOL, 1),
            new It("ad_feed", "隐藏信息流广告", T_BOOL, 1),
            new It("no_hotfix", "禁用热更新", T_BOOL, 1),

            },
            /* ---- 交互与入口 ---- */
            {
            new It("entry", "长按屏幕唤出面板", T_BOOL, 1),
            new It("sheet_entry", "播放设置弹层里显示入口", T_BOOL, 1),
            new It("dt_comment", "双击打开评论区", T_BOOL, 1),
            new It("dt_land", "横屏三击评论区", T_BOOL, 0),
            new It("no_update", "屏蔽升级弹窗", T_BOOL, 1),
            },
    };

    /** 槽位类开关在红果侧的键是 {@code <key>_hide}，这里统一转换。 */
    private static boolean isSlot(String key) {
        for (String s : Config.SLOT_KEYS) {
            if ((s + "_hide").equals(key)) {
                return true;
            }
        }
        return false;
    }

    private ModuleSettings() {
    }

    /* ------------------------------------------------------------------ */

    /** 渲染整页到宿主 content 列。 */
    static void build(final Activity a, final MiUi.Palette p, LinearLayout body) {
        Config.init(a);

        // 顶部说明：把「为什么改了要重启」讲清楚，免得以为没生效
        LinearLayout tip = MiUi.group(a, p, body, null);
        TextView t = MiUi.body(a, p,
                "这里的改动会立即保存，并导出到公共目录；"
                        + "红果下次启动时读回去生效（点下方「重启红果」可立即生效）。\n"
                        + "自定义隐藏清单、底部标签栏勾选、逐项透明度仍需在红果内的模块面板里设置。");
        t.setPadding(MiUi.dp(a, 16), MiUi.dp(a, 14), MiUi.dp(a, 16), MiUi.dp(a, 14));
        tip.addView(t);

        for (int g = 0; g < GROUPS.length; g++) {
            MiUi.group(a, p, body, GROUP_NAMES[g]);
            for (final It it : GROUPS[g]) {
                switch (it.type) {
                    case T_INT:
                        addSlider(a, p, body, it);
                        break;
                    case T_ENUM:
                        addEnum(a, p, body, it);
                        break;
                    default:
                        addSwitch(a, p, body, it);
                        break;
                }
            }
        }

        // 收尾：重启按钮 + 导出状态
        LinearLayout act = MiUi.group(a, p, body, null);
        TextView sync = MiUi.body(a, p, "公共目录已存 " + CfgBridge.countStored(a) + " 项配置");
        sync.setPadding(MiUi.dp(a, 16), MiUi.dp(a, 12), MiUi.dp(a, 16), MiUi.dp(a, 12));
        act.addView(sync);

        TextView export = MiUi.accentRow(a, p, "立即保存并同步到红果", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String err = Config.exportAll(a);
                toast(a, err == null ? "已保存，红果下次启动生效" : err);
            }
        });
        act.addView(export);
        MiUi.divider(a, p, act);
        TextView restart = MiUi.rowTitle(a, p, "重启红果使其生效");
        restart.setTextColor(p.title);
        restart.setPadding(MiUi.dp(a, 16), 0, MiUi.dp(a, 16), 0);
        restart.setMinimumHeight(MiUi.dp(a, 52));
        restart.setGravity(Gravity.CENTER_VERTICAL);
        restart.setBackground(MiUi.pressable(a, p, 0));
        restart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Config.exportAll(a);
                MainActivity.restartHostFrom(a);
            }
        });
        act.addView(restart);
    }

    /* ---------------- 三种行的渲染 ---------------- */

    private static void addSwitch(final Activity a, final MiUi.Palette p,
                                 LinearLayout body, final It it) {
        boolean on = readBool(a, it);
        LinearLayout line = new LinearLayout(a);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setMinimumHeight(MiUi.dp(a, 52));
        line.setPadding(MiUi.dp(a, 16), 0, MiUi.dp(a, 16), 0);
        line.addView(MiUi.rowTitle(a, p, it.title), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final MiUi.MiSwitch sw = new MiUi.MiSwitch(a, p, on);
        sw.setTag(new It[]{it});
        sw.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MiUi.MiSwitch s = (MiUi.MiSwitch) v;
                It[] tag = (It[]) s.getTag();
                writeBool(a, tag[0], s.isOn());
                afterChange(a, tag[0]);
            }
        });
        line.addView(sw);
        body.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static void addEnum(final Activity a, final MiUi.Palette p,
                                LinearLayout body, final It it) {
        final int[] cur = {readInt(a, it)};
        TextView lbl = MiUi.body(a, p, it.title);
        lbl.setTextColor(p.sub);
        lbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        lbl.setPadding(MiUi.dp(a, 16), MiUi.dp(a, 12), MiUi.dp(a, 16), MiUi.dp(a, 8));
        body.addView(lbl);

        LinearLayout seg = MiUi.segment(a, p, it.opts, cur[0], new MiUi.OnSegment() {
            @Override
            public void onSelect(int index) {
                cur[0] = index;
                writeInt(a, it, index);
                afterChange(a, it);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = MiUi.dp(a, 16);
        lp.rightMargin = MiUi.dp(a, 16);
        lp.bottomMargin = MiUi.dp(a, 14);
        body.addView(seg, lp);
    }

    private static void addSlider(final Activity a, final MiUi.Palette p,
                                  LinearLayout body, final It it) {
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView lbl = MiUi.body(a, p, it.title);
        lbl.setTextColor(p.sub);
        head.addView(lbl, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView val = MiUi.value(a, p, format(it, readInt(a, it)));
        head.addView(val);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.leftMargin = MiUi.dp(a, 16);
        hlp.rightMargin = MiUi.dp(a, 16);
        hlp.topMargin = MiUi.dp(a, 12);
        body.addView(head, hlp);

        SeekBar bar = new SeekBar(a);
        bar.setMax(it.max - it.min);
        bar.setProgress(clamp(readInt(a, it) - it.min, 0, it.max - it.min));
        bar.setPadding(MiUi.dp(a, 16), 0, MiUi.dp(a, 16), 0);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(p.blue));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(
                p.card == 0xFFFFFFFF ? 0xFFE3E3E8 : 0xFF3A3A3C));
        final boolean[] touching = {false};
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int v, boolean fromUser) {
                val.setText(format(it, v + it.min));
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                touching[0] = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                touching[0] = false;
                writeInt(a, it, sb.getProgress() + it.min);
                afterChange(a, it);
            }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.bottomMargin = MiUi.dp(a, 6);
        body.addView(bar, blp);
    }

    /* ---------------- 读写 ---------------- */

    private static boolean readBool(Context c, It it) {
        try {
            if (it.key.endsWith("_hide") && isSlot(it.key)) {
                String base = it.key.substring(0, it.key.length() - 5);
                return Config.overlayHide(c, base);
            }
            return Config.spOf().getBoolean(it.key, it.def == 1);
        } catch (Throwable t) {
            return it.def == 1;
        }
    }

    private static void writeBool(Context c, It it, boolean v) {
        try {
            if (it.key.endsWith("_hide") && isSlot(it.key)) {
                String base = it.key.substring(0, it.key.length() - 5);
                Config.setOverlayHide(c, base, v);
            } else {
                Config.spOf().edit().putBoolean(it.key, v).commit();
            }
        } catch (Throwable ignored) {
        }
    }

    private static int readInt(Context c, It it) {
        try {
            if (it.key.endsWith("_hide") && isSlot(it.key)) {
                return it.def;
            }
            return Config.spOf().getInt(it.key, it.def);
        } catch (Throwable t) {
            return it.def;
        }
    }

    private static void writeInt(Context c, It it, int v) {
        try {
            Config.spOf().edit().putInt(it.key, v).commit();
        } catch (Throwable ignored) {
        }
    }

    /** 每次改动都立刻导出，红果下次启动就能读到。 */
    private static void afterChange(Activity a, It it) {
        String err = Config.exportAll(a);
        if (err != null) {
            toast(a, "已改，但同步失败：" + err);
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static String format(It it, int v) {
        if ("dpi".equals(it.key)) {
            return v <= 0 ? "跟随系统" : v + " dpi";
        }
        if ("alpha".equals(it.key)) {
            return v + "%";
        }
        if (it.key.endsWith("_x10")) {
            return (v / 10f) + "x";
        }
        if ("clear_idle_s".equals(it.key)) {
            return v + " 秒";
        }
        return String.valueOf(v);
    }

    private static void toast(Context c, String s) {
        try {
            Toast.makeText(c, s == null ? "" : s, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }
}
