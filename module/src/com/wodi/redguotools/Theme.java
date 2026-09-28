package com.wodi.redguotools;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
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
    }

    private static final Palette LIGHT = light();
    private static final Palette DARK = dark();

    private static Palette light() {
        Palette p = new Palette();
        p.bg = 0xFFFFFFFF;         // 纯白，不用半透明（半透明白会显脏）
        p.card = 0xFFF8F9FB;       // 与白底仅 1.05:1，必须靠描边成边界
        p.stroke = 0xFFE3E5EA;     // 描边 / 白底 = 1.26:1，视觉刚好
        p.primary = 0xFFFF7D29;
        p.primaryInk = 0xFFC24E12;
        p.title = 0xFF1A1A1A;      // 17.40:1
        p.body = 0xFF444444;       // 9.74:1
        p.sub = 0xFF767676;        // 4.54:1，恰过 AA（原方案的 #868686 只有 3.64:1）
        p.ghostInk = 0xFF6B6B6B;
        p.danger = 0xFFC62828;     // 5.62:1（原方案的 #ED4337 只有 3.85:1）
        p.trackOff = 0xFFC9CFD8;   // 再浅就与卡片同色，关态只剩一个球在飘
        p.thumbOff = 0xFF8A9099;
        p.seekTrack = 0xFFE3E5EA;
        p.press = 0x14000000;
        return p;
    }

    private static Palette dark() {
        Palette p = new Palette();
        p.bg = 0xFF16181C;
        p.card = 0xFF1F2227;
        p.stroke = 0xFF2E333A;
        p.primary = 0xFFFF8F45;
        p.primaryInk = 0xFFFFB27A;
        p.title = 0xFFF2F3F5;
        p.body = 0xFFC9CDD4;
        p.sub = 0xFF9AA0A8;
        p.ghostInk = 0xFFADB3BB;
        p.danger = 0xFFE5645C;
        p.trackOff = 0xFF4A515A;
        p.thumbOff = 0xFF9AA0A8;
        p.seekTrack = 0xFF4A515A;
        p.press = 0x1AFFFFFF;
        return p;
    }

    static Palette of(Context c) {
        int mode = c.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES ? DARK : LIGHT;
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

    /** 分组卡片：浅灰填充 + 1dp 描边。不用 elevation —— 浅色下阴影几乎不可见且显脏。 */
    static GradientDrawable card(Context c, Palette p) {
        return round(p.card, p.stroke, dp(c, 12), dp(c, 1));
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
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
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

    /** 分组标题：弱化小字，压在每张组卡片上方。 */
    static TextView groupTitle(Context c, Palette p, String text) {
        TextView t = cardTitle(c, p, text);
        t.setPadding(0, 0, 0, 0);
        return t;
    }

    /** 折叠行 / 组内功能行的标题：正常字号、一级色。 */
    static TextView rowTitle(Context c, Palette p, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(p.title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        return t;
    }

    /** 折叠头右侧的状态摘要（如「11 项 · 已隐藏 2」）。 */
    static TextView rowSummary(Context c, Palette p, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(p.sub);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
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
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        s.setChecked(checked);
        s.setPadding(0, dp(c, 1), 0, dp(c, 1));
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
            int tw = intrinsic(s.getTrackDrawable(), dp(c, 44), true);
            int th = intrinsic(s.getTrackDrawable(), dp(c, 24), false);
            int hw = intrinsic(s.getThumbDrawable(), dp(c, 24), true);
            int hh = intrinsic(s.getThumbDrawable(), dp(c, 24), false);

            SizedStateList thumb = new SizedStateList(hw, hh);
            thumb.addState(new int[]{android.R.attr.state_checked},
                    oval(0xFFFFFFFF, hw, hh));
            thumb.addState(new int[]{}, oval(p.thumbOff, hw, hh));
            s.setThumbDrawable(thumb);

            SizedStateList track = new SizedStateList(tw, th);
            track.addState(new int[]{android.R.attr.state_checked},
                    pill(p.primary, tw, th));
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
        b.setThumbTintList(ColorStateList.valueOf(p.primary));
        b.setProgressBackgroundTintList(ColorStateList.valueOf(p.seekTrack));
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
        b.setBackground(round(0x00000000, p.stroke, dp(c, 8), dp(c, 1)));
        b.setTextColor(p.ghostInk);
        return b;
    }

    static Button dangerButton(Context c, Palette p, String text, View.OnClickListener l) {
        Button b = baseButton(c, p, text, l);
        b.setBackground(round(0x00000000, p.danger, dp(c, 8), dp(c, 1)));
        b.setTextColor(p.danger);
        return b;
    }
}
