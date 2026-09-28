package com.wodi.redguotools;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 长按唤出的模块设置面板。纯代码构建 UI，不依赖任何资源与第三方库。
 *
 * <p>信息架构（v2.68 重排）：五个分组 —— 界面显示 / 底部区域 / 播放控制 /
 * 面板与交互 / 高级·自定义。分组标题是弱化小字；<b>纯开关项一律不折叠</b>，
 * 直接以「标题 + 右侧开关」露出；只有内容多于一条的（组件显隐、底部标签栏、
 * 倍速细节、自定义清单）才用折叠行，折叠头自带状态摘要；低频/调试项
 * （自定义清单、输出视图树日志）收进「高级·自定义」且默认折叠。
 * 视觉全部走 {@link Theme}，功能逻辑与旧版一致。
 */
public final class SettingsPanel {

    private static final String TAG = RGModule.TAG;
    private static android.app.Dialog sDialog;

    private SettingsPanel() {
    }

    public static boolean isShowing() {
        return sDialog != null && sDialog.isShowing();
    }

    public static void show(final Activity a) {
        if (isShowing()) {
            return;
        }
        final Theme.Palette p = Theme.of(a);

        final LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Theme.sheet(a, p));
        int pad = Theme.dp(a, 12);
        root.setPadding(pad, pad, pad, pad);

        TextView title = Theme.title(a, p, "ReadGuo");
        root.addView(title);

        TextView hint = Theme.hint(a, p, "长按底部「首页」或顶部正中唤出 · 改动即时生效");
        hint.setPadding(0, Theme.dp(a, 3), 0, 0);
        root.addView(hint);

        buildDisplayGroup(a, p, root);
        buildBottomGroup(a, p, root);
        buildPlaybackGroup(a, p, root);
        buildPanelGroup(a, p, root);
        buildAdvancedGroup(a, p, root);

        /* ---------- 底部操作 ---------- */
        LinearLayout btns = new LinearLayout(a);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams btnsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnsLp.topMargin = Theme.dp(a, 8);
        root.addView(btns, btnsLp);
        Button close = Theme.filledButton(a, p, "关闭", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sDialog != null) {
                    sDialog.dismiss();
                }
            }
        });
        close.setPadding(Theme.dp(a, 6), Theme.dp(a, 6), Theme.dp(a, 6), Theme.dp(a, 6));
        btns.addView(close, weight(1f, 0));

        ScrollView sv = new ScrollView(a);
        sv.addView(root);

        // 上下留 6dp：面板内容超高时 Dialog 会被撑到满屏，不垫这一层圆角就被裁没了
        android.widget.FrameLayout wrapper = new android.widget.FrameLayout(a);
        int vGap = Theme.dp(a, 6);
        wrapper.setPadding(0, vGap, 0, vGap);
        wrapper.addView(sv, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        android.app.Dialog d = new android.app.Dialog(a);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        d.setContentView(wrapper);
        android.view.Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setLayout((int) (a.getResources().getDisplayMetrics().widthPixels * 0.94),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
        }
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(android.content.DialogInterface di) {
                sDialog = null;
            }
        });
        sDialog = d;
        try {
            d.show();
        } catch (Throwable t) {
            Log.e(TAG, "dialog show failed", t);
            sDialog = null;
        }
    }

    /* ==================== 分组骨架 ==================== */

    /** 分组标题（弱化小字）+ 该组的圆角卡片，返回卡片内容容器。 */
    private static LinearLayout groupCard(Activity a, Theme.Palette p,
                                          LinearLayout root, String label, boolean first) {
        TextView tv = Theme.groupTitle(a, p, label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Theme.dp(a, first ? 5 : 8);
        lp.bottomMargin = Theme.dp(a, 3);
        root.addView(tv, lp);

        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Theme.card(a, p));
        int ip = Theme.dp(a, 14);
        card.setPadding(ip, 0, ip, Theme.dp(a, 5));
        root.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    /** 组内行分隔线（1px 描边色）。 */
    private static void divider(Activity a, Theme.Palette p, LinearLayout parent) {
        View v = new View(a);
        v.setBackground(new ColorDrawable(p.stroke));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Theme.dp(a, 1) / 2));
        parent.addView(v, lp);
    }

    /** 一行「标题 + 右侧开关」。纯开关项的主体形态。 */
    private static Switch switchRow(Activity a, Theme.Palette p, LinearLayout parent,
                                    String text, boolean checked,
                                    CompoundButton.OnCheckedChangeListener l) {
        Switch sw = Theme.makeSwitch(a, p, text, checked);
        sw.setOnCheckedChangeListener(l);
        parent.addView(sw);
        return sw;
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

    /* ==================== 分组一：界面显示 ==================== */

    private static void buildDisplayGroup(final Activity a, final Theme.Palette p,
                                          LinearLayout root) {
        LinearLayout card = groupCard(a, p, root, "界面显示", true);

        switchRow(a, p, card, "隐藏系统状态栏", Config.hideStatusBar(a),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        Config.set(a, "sb_hide", v);
                        UiController.refresh(a);
                    }
                });
        divider(a, p, card);

        // 状态栏底色：单选一行（开关的伴随选项，不单独立卡）
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Theme.dp(a, 2), 0, Theme.dp(a, 4));
        TextView lbl = Theme.plain(a, p, "状态栏底色");
        row.addView(lbl, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        RadioGroup rg = new RadioGroup(a);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        String[] names = {"跟随背景", "纯黑", "纯白"};
        int cur = Config.statusBarBg(a);
        for (int i = 0; i < names.length; i++) {
            RadioButton rb = new RadioButton(a);
            rb.setId(1000 + i);
            rb.setText(names[i]);
            rb.setTextColor(p.body);
            rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            rb.setButtonTintList(ColorStateList.valueOf(p.primary));
            rb.setTag(i);
            if (i > 0) {
                RadioGroup.LayoutParams rlp = new RadioGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.leftMargin = Theme.dp(a, 10);
                rg.addView(rb, rlp);
            } else {
                rg.addView(rb);
            }
            if (i == cur) {
                rb.setChecked(true);
            }
        }
        rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup g, int id) {
                View v = g.findViewById(id);
                if (v != null) {
                    Config.setInt(a, "sb_bg", (Integer) v.getTag());
                    UiController.refresh(a);
                }
            }
        });
        row.addView(rg);
        card.addView(row);
        divider(a, p, card);

        switchRow(a, p, card, "暂停时还原组件显示", Config.pauseRestore(a),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        Config.set(a, "pause_restore", v);
                        UiController.refresh(a);
                    }
                });
        divider(a, p, card);

        // 全局透明度：与「组件显隐」逐项透明度同属一个体系，集中在本组
        alphaBlock(a, p, card, "全局透明度", Config.alphaPercent(a), 100,
                new SimpleSeek() {
                    @Override
                    public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                        if (fromUser) {
                            Config.setInt(a, "alpha", value);
                            UiController.refresh(a);
                        }
                    }
                });
        divider(a, p, card);

        // 组件显隐：11 个子项，折叠 + 摘要
        Expander ex = expander(a, p, card, "slots", "组件显隐",
                slotsSummary(a), true);
        TextView slotsHint = Theme.hint(a, p,
                "开关 = 隐藏该组件；滑杆 = 单项透明度（再乘全局透明度）");
        slotsHint.setPadding(0, 0, 0, Theme.dp(a, 4));
        ex.body.addView(slotsHint);
        for (int i = 0; i < Config.SLOT_KEYS.length; i++) {
            addSlotRow(a, p, ex.body, Config.SLOT_KEYS[i], Config.SLOT_NAMES[i], ex);
        }
    }

    /* ==================== 分组二：底部区域 ==================== */

    private static void buildBottomGroup(final Activity a, final Theme.Palette p,
                                         LinearLayout root) {
        LinearLayout card = groupCard(a, p, root, "底部区域", false);

        switchRow(a, p, card, "隐藏底部导航栏（小白条）", Config.autoHideNav(a),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        Config.setAutoHideNav(a, v);
                        UiController.refresh(a);
                        rebuild(a);
                    }
                });
        // 状态行：一眼看出开关是否已生效（面板能打开说明 App 在前台）
        TextView navState = Theme.hint(a, p, UiController.navBarEnableState());
        navState.setPadding(0, 0, 0, Theme.dp(a, 4));
        card.addView(navState);
        divider(a, p, card);

        // 底部标签栏：多个子项（按标签勾选），折叠 + 摘要
        Expander ex = expander(a, p, card, "btab", "底部标签栏",
                btabSummary(a), true);
        TextView btabHint = Theme.hint(a, p,
                "勾选要隐藏的标签（如 我的、商城、赚钱），剩余标签自动摊满整行");
        btabHint.setPadding(0, 0, 0, Theme.dp(a, 6));
        ex.body.addView(btabHint);
        ex.body.addView(Theme.filledButton(a, p, "选择要隐藏的标签", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bottomTabsDialog(a);
            }
        }));
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
        sbSpeed.setMax(30);   // 10~40 => 1.0x~4.0x
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
                "在画面边缘长按临时提速到该倍率，松手恢复常规倍速。上限 2.0x：宿主播放器会丢弃超过 2.0 的提速值（设更高时长按不会加速）。");
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

        // 清屏省心：单一数值项，直接露出不折叠
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

    /* ==================== 分组五：高级·自定义 ==================== */

    private static void buildAdvancedGroup(final Activity a, final Theme.Palette p,
                                           LinearLayout root) {
        LinearLayout card = groupCard(a, p, root, "高级 · 自定义", false);

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

        divider(a, p, ex.body);
        LinearLayout logRow = new LinearLayout(a);
        logRow.setOrientation(LinearLayout.HORIZONTAL);
        logRow.setPadding(0, Theme.dp(a, 8), 0, 0);
        logRow.addView(Theme.ghostButton(a, p, "输出视图树日志（调试）", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UiController.logTree(a);
            }
        }), weight(1f, 0));
        ex.body.addView(logRow);
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
        if (sDialog != null) {
            sDialog.dismiss();
        }
        a.getWindow().getDecorView().post(new Runnable() {
            @Override
            public void run() {
                show(a);
            }
        });
    }

    /* ---------------- 行构建 ---------------- */

    private static void addSlotRow(final Activity a, final Theme.Palette p, LinearLayout card,
                                   final String key, String name, final Expander ex) {
        Switch sw = Theme.makeSwitch(a, p, "隐藏 " + name, Config.overlayHide(a, key));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean v) {
                Config.set(a, key + "_hide", v);
                UiController.refresh(a);
                ex.summary.setText(slotsSummary(a));
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

    /** 底部标签栏：枚举当前标签，勾选后按标签文字隐藏。 */
    private static void bottomTabsDialog(final Activity a) {
        final List<String> labels = UiController.bottomTabLabels(a);
        if (labels.isEmpty()) {
            new AlertDialog.Builder(a).setTitle("底部标签栏")
                    .setMessage("没有识别到底部标签。请先回到首页（底部有标签栏的页面）再打开本面板。")
                    .setPositiveButton("好", null).show();
            return;
        }
        final boolean[] checked = new boolean[labels.size()];
        List<String> hidden = new ArrayList<>();
        for (String s : Config.hiddenBottomTabs(a).split("\\|")) {
            s = s.trim();
            if (!s.isEmpty()) {
                hidden.add(s);
            }
        }
        for (int i = 0; i < labels.size(); i++) {
            checked[i] = hidden.contains(labels.get(i));
        }
        new AlertDialog.Builder(a)
                .setTitle("勾选要隐藏的底部标签")
                .setMultiChoiceItems(labels.toArray(new String[0]), checked,
                        new DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which, boolean isChecked) {
                                checked[which] = isChecked;
                            }
                        })
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        List<String> keep = new ArrayList<>();
                        for (int i = 0; i < labels.size(); i++) {
                            if (checked[i]) {
                                keep.add(labels.get(i));
                            }
                        }
                        Config.setHiddenBottomTabs(a, keep);
                        UiController.refresh(a);
                        rebuild(a);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
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
