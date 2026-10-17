package com.wodi.redguotools;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/**
 * 面板视觉层：配色表 + 圆角卡片 + 控件工厂。
 *
 * <p>两条硬约束（面板跑在红果进程里，用的是宿主 Activity 的 Context）：
 * <ol>
 *   <li>颜色一律自带，<b>绝不</b>用 {@code ?attr}。宿主主题里没有该属性时，
 *       属性解析会抛 {@code Resources$NotFoundException}，直接崩宿主进程。</li>
 *   <li>只用 framework 控件，不引入 Material 组件。本模块无 Gradle、无 {@code res/}，
 *       且 Material 控件首次构建时会去读宿主不存在的 {@code Theme.MaterialComponents}
 *       属性，同样会崩。</li>
 * </ol>
 *
 * <p>浅色为默认外观；宿主处于深色模式时自动切深色，避免在红果深色皮肤下露出一块白板。
 */
final class Theme {

    private Theme() {
    }

    /* ==================== 调色板 ==================== */

    /** 对比度均按 WCAG 相对亮度公式实算过，注释里标的是「白底/深底上的对比度」。 */
    static final class Palette {
        int bg;          // 面板底色
        int card;        // 卡片填充
        int stroke;      // 卡片 / 输入框描边
        int primary;     // 主色：填充块、开关激活轨道、滑块。**不可作文字色**（白底仅 2.56:1）
        int primaryInk;  // 主色的「文字版」：压深后用于描边按钮文字
        int title;       // 一级标题
        int body;        // 正文 / 控件文字
        int sub;         // 备注小字
        int ghostInk;    // 次级按钮文字
        int danger;      // 危险操作
        int trackOff;    // 开关关闭态轨道
        int thumbOff;    // 开关关闭态滑块（中灰，白底/白球才分得清开关状态）
        int seekTrack;   // 滑块轨道
        int press;       // 列表行按下时的叠加色
        int divider;     // 行间分割线 #E5E5EA
        int switchOn;    // 开关打开态轨道（iOS 绿）
        int segSlot;     // 分段控件底槽
        int segSel;      // 分段控件选中块
    }

    private static final Palette LIGHT = light();
    private static final Palette DARK = dark();

    private static Palette light() {
        Palette p = new Palette();
        // 参考稿是「干净浅色」而不是重透明：页面浅灰白、卡片纯白、蓝色强调。
        p.bg = 0xFFF4F4F5;         // 页面底：iOS 分组列表的标准灰白
        p.card = 0xFFFFFFFF;       // 卡片纯白（无边框、无阴影）
        p.stroke = 0xFFE5E5EA;     // 仅输入框焦点框还会用
        p.primary = 0xFF007AFF;    // iOS 系统蓝，唯一强调色
        p.primaryInk = 0xFF007AFF;
        p.title = 0xFF111111;      // 正文黑
        p.body = 0xFF111111;
        p.sub = 0xFF8E8E93;        // 分组标题 / 右侧状态
        p.ghostInk = 0xFF007AFF;
        p.danger = 0xFFFF3B30;
        p.trackOff = 0xFFE3E3E8;   // 关态轨道
        p.thumbOff = 0xFFFFFFFF;
        p.seekTrack = 0xFFE5E5EA;
        p.press = 0x0F000000;      // 按下叠加：中性灰，不用蓝
        p.divider = 0xFFE5E5EA;
        p.switchOn = 0xFF34C759;   // iOS 开关绿
        p.segSlot = 0xFFEFEFF0;
        p.segSel = 0xFFFFFFFF;
        return p;
    }

    private static Palette dark() {
        Palette p = new Palette();
        p.bg = 0xFF000000;         // iOS 深色分组列表底
        p.card = 0xFF1C1C1E;       // iOS 深色卡片
        p.stroke = 0xFF38383A;
        p.primary = 0xFF0A84FF;    // iOS 深色蓝
        p.primaryInk = 0xFF0A84FF;
        p.title = 0xFFF2F2F7;
        p.body = 0xFFEBEBF5;
        p.sub = 0xFF8E8E93;
        p.ghostInk = 0xFF0A84FF;
        p.danger = 0xFFFF453A;
        p.trackOff = 0xFF3A3A3C;
        p.thumbOff = 0xFFFFFFFF;
        p.seekTrack = 0xFF38383A;
        p.press = 0x24FFFFFF;
        p.divider = 0xFF38383A;
        p.switchOn = 0xFF30D158;
        p.segSlot = 0xFF2C2C2E;
        p.segSel = 0xFF5B5B60;
        return p;
    }

    /**
     * 面板配色跟随**宿主当前皮肤**的深浅。
     *
     * <p>历史上这里固定返回 LIGHT（v2.97 的决定：宿主深色也保持浅色）。
     * 但 v3.06 有了「暗色模式」开关之后这个决定不再成立 —— 用户把红果
     * 切到深色后，重开的面板还是一片白，看起来就像「切换没生效」。
     * 宿主的深色是换肤（资源后缀），不是 uiMode，所以只能问
     * {@code SkinManager.isNightMode()}（见 {@link DarkMode#hostIsNight()}）。
     */
    static Palette of(Context c) {
        return DarkMode.hostIsNight() ? DARK : LIGHT;
    }

    /* ==================== 尺寸 ==================== */

    static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    /* ==================== 背景 ==================== */

    private static GradientDrawable round(int fill, int strokeColor, int radiusPx, int strokePx) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(fill);
        // setCornerRadius / setStroke 的单位都是 px
        g.setCornerRadius(radiusPx);
        if (strokePx > 0) {
            g.setStroke(Math.max(1, strokePx), strokeColor);
        }
        return g;
    }

    /** 面板外轮廓（圆角白底）。 */
    static GradientDrawable sheet(Context c, Palette p) {
        return round(p.bg, 0, dp(c, 20), 0);
    }

    /** 分组卡片：纯白 + 16dp 圆角，**无描边、无阴影**（iOS 分组列表）。 */
    static GradientDrawable card(Context c, Palette p) {
        return round(p.card, 0, dp(c, 16), 0);
    }

    /** 一级顶部的标题卡：极淡的蓝→白渐变，做出「液态」的层次感。 */
    static GradientDrawable heroCard(Context c, Palette p) {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFF1F5FF, 0xFFFFFFFF});
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(dp(c, 18));
        return g;
    }

    /**
     * 可点击卡片：圆角卡片 + 按下水波纹。
     * mask 用同形状圆角矩形，否则波纹会溢出到方角外面。
     */
    static android.graphics.drawable.RippleDrawable rippleCard(Context c, Palette p) {
        GradientDrawable mask = round(0xFFFFFFFF, 0, dp(c, 12), 0);
        return new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(p.press), card(c, p), mask);
    }

    /** 卡片标题栏：按下时叠一层淡色作为触感反馈（不能用 selector 资源，模块没有 res/）。 */
    static StateListDrawable pressable(Context c, Palette p) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed},
                round(p.press, 0, dp(c, 8), 0));
        s.addState(new int[]{}, round(0x00000000, 0, 0, 0));
        return s;
    }

    /** 输入框：常态浅描边，聚焦时描边转主色。 */
    static StateListDrawable inputBg(Context c, Palette p) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused},
                round(p.bg, p.primary, dp(c, 8), dp(c, 1)));
        s.addState(new int[]{},
                round(p.bg, p.stroke, dp(c, 8), dp(c, 1)));
        return s;
    }

    /* ==================== 控件工厂 ==================== */

    private static final int[][] TRACK_STATES = {
            {android.R.attr.state_checked},
            {}
    };

    static TextView title(Context c, Palette p, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(p.title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        return t;
    }

    /** 备注 / 说明小字。 */
    static TextView hint(Context c, Palette p, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(p.sub);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        return t;
    }

    /** 卡片标题文字（字距拉开的分类名）。 */
    static TextView cardTitle(Context c, Palette p, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(p.sub);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        t.setLetterSpacing(0.08f);
        return t;
    }

    /** 分组标题：13sp 粗体、#8E8E93，压在卡片上方。 */
    static TextView groupTitle(Context c, Palette p, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(p.sub);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        t.setIncludeFontPadding(false);
        t.setPadding(0, 0, 0, 0);
        return t;
    }

    /** 折叠行 / 组内功能行的标题：正常字号、一级色。 */
    static TextView rowTitle(Context c, Palette p, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(p.title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** 折叠头右侧的状态摘要（如「11 项 · 已隐藏 2」）。 */
    static TextView rowSummary(Context c, Palette p, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(p.sub);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** 滑杆行右侧的高亮数值（主色压深版，白底可读）。 */
    static TextView blockValue(Context c, Palette p, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(p.primaryInk);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        return t;
    }

    /** 正文级文字（用于清单项名称等）。 */
    static TextView plain(Context c, Palette p, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(p.body);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        return t;
    }

    /** 滑块上方那行「XX 透明度：N%」。 */
    static TextView value(Context c, Palette p, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(p.body);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setPadding(0, dp(c, 10), 0, 0);
        return t;
    }

    /** Switch：轨道与滑块都自带不透明形状。 */
    static Switch makeSwitch(Context c, Palette p, String text, boolean checked) {
        Switch s = new Switch(c);
        s.setText(text);
        s.setTextColor(p.body);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        s.setChecked(checked);
        // 参考稿的行高偏松：靠内边距把每一行撑到 ~52dp，而不是靠分隔线
        s.setPadding(0, dp(c, 11), 0, dp(c, 11));
        s.setMinHeight(dp(c, 52));
        styleSwitch(c, p, s);
        return s;
    }

    /**
     * 换掉宿主主题给的 thumb / track drawable。
     *
     * <p>只有 setTrackTintList 是不够的：宿主 Switch 的轨道是半透明 9-patch，
     * 实测会把 #FF7D29 稀释成 #FAD9C5（约 26% alpha），这个 alpha 写在贴图像素里，
     * tint 只换 RGB 换不掉它。所以自带不透明形状。
     *
     * <p>尺寸沿用原 drawable，避免打乱 Switch 自己的布局度量；StateList 用子类
     * 固定 intrinsic 尺寸，否则新 drawable 还没 attach 时 intrinsic 返回 -1，
     * Switch 会把滑块算成 0 宽。
     */
    private static void styleSwitch(Context c, Palette p, Switch s) {
        try {
            int tw = dp(c, 51);
            int th = dp(c, 31);
            int hw = dp(c, 27);
            int hh = dp(c, 27);

            SizedStateList thumb = new SizedStateList(hw, hh);
            thumb.addState(new int[]{android.R.attr.state_checked},
                    ovalRing(0xFFFFFFFF, hw, hh, 0x1A000000));
            thumb.addState(new int[]{}, ovalRing(0xFFFFFFFF, hw, hh, 0x1A000000));
            s.setThumbDrawable(thumb);

            SizedStateList track = new SizedStateList(tw, th);
            track.addState(new int[]{android.R.attr.state_checked},
                    pill(p.switchOn, tw, th));
            track.addState(new int[]{}, pill(p.trackOff, tw, th));
            s.setTrackDrawable(track);

            s.requestLayout();
            s.invalidate();
        } catch (Throwable t) {
            // 兜底：退回只改 tint（轨道会偏淡，但至少不是原样）
            s.setTrackTintList(new ColorStateList(TRACK_STATES, new int[]{p.primary, p.trackOff}));
            s.setThumbTintList(new ColorStateList(TRACK_STATES, new int[]{0xFFFFFFFF, p.thumbOff}));
        }
    }

    private static int intrinsic(Drawable d, int fallback, boolean horizontal) {
        int v = -1;
        try {
            v = horizontal ? d.getIntrinsicWidth() : d.getIntrinsicHeight();
        } catch (Throwable ignored) {
        }
        return v > 0 ? v : fallback;
    }

    private static GradientDrawable oval(int fill, int w, int h) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        g.setSize(w, h);
        return g;
    }

    /** 带 1px 淡描边的白色圆——iOS 开关滑块靠它跟纯白卡面区分开。 */
    private static GradientDrawable ovalRing(int fill, int w, int h, int ring) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        g.setStroke(1, ring);
        g.setSize(w, h);
        return g;
    }

    /* ==================== 分段控件 ==================== */

    interface OnSegment {
        void onSelect(int index);
    }

    /**
     * iOS 分段控件（Segmented Control）：浅灰底槽 + 选中项白色圆角块 + 细分隔线。
     *
     * <p>替代单选按钮 —— 安卓的 RadioButton 是圆圈 + 文字，和 iOS 的形态差得最远，
     * 而面板里的「状态栏底色」「暗色模式」都只有 2~3 个互斥选项，正适合分段控件。
     */
    static LinearLayout segmented(Context c, Palette p, String[] items, int selected,
                                  final OnSegment cb) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setBackground(round(p.segSlot, 0, dp(c, 9), 0));
        box.setPadding(dp(c, 2), dp(c, 2), dp(c, 2), dp(c, 2));

        final TextView[] tabs = new TextView[items.length];
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            TextView t = new TextView(c);
            t.setText(items[i]);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            t.setGravity(android.view.Gravity.CENTER);
            t.setMinHeight(dp(c, 30));
            t.setIncludeFontPadding(false);
            t.setPadding(dp(c, 8), dp(c, 6), dp(c, 8), dp(c, 6));
            t.setClickable(true);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    for (int k = 0; k < tabs.length; k++) {
                        styleSegment(tabs[k], p, k == idx);
                    }
                    if (cb != null) {
                        cb.onSelect(idx);
                    }
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            box.addView(t, lp);
            tabs[i] = t;
        }
        for (int i = 0; i < tabs.length; i++) {
            styleSegment(tabs[i], p, i == selected);
        }
        return box;
    }

    private static void styleSegment(TextView t, Palette p, boolean on) {
        t.setTextColor(on ? p.title : p.sub);
        t.setTypeface(android.graphics.Typeface.create("sans-serif",
                on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
        if (on) {
            t.setBackground(round(p.segSel, 0, dp(t.getContext(), 7), 0));
            t.setElevation(dp(t.getContext(), 1));
        } else {
            t.setBackground(null);
            t.setElevation(0);
        }
    }

    private static GradientDrawable pill(int fill, int w, int h) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(fill);
        g.setCornerRadius(Math.max(w, h));
        g.setSize(w, h);
        return g;
    }

    /** StateListDrawable 默认在 select 之前 intrinsic 返回 -1，Switch 会因此算出 0 宽滑块。 */
    private static final class SizedStateList extends StateListDrawable {
        private final int w;
        private final int h;

        SizedStateList(int w, int h) {
            this.w = w;
            this.h = h;
        }

        @Override
        public int getIntrinsicWidth() {
            return w;
        }

        @Override
        public int getIntrinsicHeight() {
            return h;
        }
    }

    /** SeekBar：轨道浅灰、进度与滑块用主色。 */
    static void styleSeekBar(Palette p, SeekBar b) {
        b.setProgressTintList(ColorStateList.valueOf(p.primary));
        b.setProgressBackgroundTintList(ColorStateList.valueOf(p.seekTrack));
        // 参考稿的滑块是「白心 + 蓝环」，默认的蓝实心圆显得笨重
        try {
            int d = dp(b.getContext(), 18);
            GradientDrawable thumb = new GradientDrawable();
            thumb.setShape(GradientDrawable.OVAL);
            thumb.setColor(0xFFFFFFFF);
            thumb.setStroke(Math.max(2, dp(b.getContext(), 2)), p.primary);
            thumb.setSize(d, d);
            b.setThumb(thumb);
            b.setThumbOffset(0);
            b.setSplitTrack(false);
        } catch (Throwable ignored) {
        }
    }

    /* ---- 按钮：填充 / 描边 / 次级，全部自绘背景 ---- */

    private static Button baseButton(Context c, Palette p, String text, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(text);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(c, 6), dp(c, 10), dp(c, 6), dp(c, 10));
        // 去掉主题带来的默认阴影动画，浅色下会显脏
        try {
            b.setStateListAnimator(null);
        } catch (Throwable ignored) {
        }
        b.setOnClickListener(l);
        return b;
    }

    static Button filledButton(Context c, Palette p, String text, View.OnClickListener l) {
        Button b = baseButton(c, p, text, l);
        b.setBackground(round(p.primary, 0, dp(c, 8), 0));
        b.setTextColor(0xFFFFFFFF);
        return b;
    }

    static Button outlineButton(Context c, Palette p, String text, View.OnClickListener l) {
        Button b = baseButton(c, p, text, l);
        b.setBackground(round(0x00000000, p.primary, dp(c, 8), dp(c, 1)));
        b.setTextColor(p.primaryInk);
        return b;
    }

    static Button ghostButton(Context c, Palette p, String text, View.OnClickListener l) {
        Button b = baseButton(c, p, text, l);
        b.setBackground(round(p.card, 0, dp(c, 12), 0));
        b.setTextColor(p.primary);
        return b;
    }

    static Button dangerButton(Context c, Palette p, String text, View.OnClickListener l) {
        Button b = baseButton(c, p, text, l);
        // iOS 的破坏性操作用「白底 + 红字」，不画描边
        b.setBackground(round(p.card, 0, dp(c, 12), 0));
        b.setTextColor(p.danger);
        return b;
    }
}
