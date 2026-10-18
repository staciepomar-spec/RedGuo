package com.wodi.redguotools;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 模块 App 自身的视觉层（iOS 原生设置页 / ChatGPT 设置页的极简风格）。
 *
 * <p>与 {@link Theme} 的分工：{@code Theme} 服务的是跑在红果进程里的设置面板，
 * 那里必须自带颜色与形状（宿主主题不可控）；这里是模块自己的 Activity，
 * 有完整 framework 环境，可以放心用尺寸换算与动画。
 *
 * <h3>版式规范（全部单位 dp）</h3>
 * <pre>
 *   页面底色      #F4F4F5
 *   卡片          纯白、圆角 16、无边框、无阴影
 *   分组标题      #8E8E93、13sp、粗体、左缩进 16
 *   列表行        高 52（带副标题 62），左内边距 16
 *   行内图标      24×24 线框、描边 1.5
 *   行内文字      17sp、#111111、无衬线
 *   右侧状态      15sp、#8E8E93
 *   分割线        #E5E5EA、厚 1px、**左缩进 48**（正好避开图标）、右侧到卡片边缘
 *   强调色        仅「特殊项」用 #007AFF，其余纯黑白灰
 * </pre>
 *
 * <p>分割线为什么必须缩进：iOS 的 inset divider 从文字起点开始画，
 * 视觉上把「图标列」和「内容列」分开。本实现里文字起点 = 16(行内边距)
 * + 24(图标) + 8(图标右间距) = 48，与分割线缩进严格一致。
 */
final class MiUi {

    private MiUi() {
    }

    /* ==================== 调色板 ==================== */

    static final class Palette {
        int bg;         // 页面底色 #F4F4F5
        int card;       // 卡片
        int title;      // 正文
        int sub;        // 分组标题 / 右侧状态
        int hint;       // 更弱的提示
        int divider;    // 分割线 #E5E5EA
        int blue;       // 唯一点缀色
        int green;      // 开关打开态
        int greenSoft;  // 保留：旧版激活卡底色（新 UI 不再使用）
        int navSelBg;   // 底栏选中态
        int trackOff;   // 开关关态轨道
        int press;      // 按下叠加
        int segSlot;    // 分段控件底槽
        int segSel;     // 分段控件选中块
    }

    private static final Palette LIGHT = new Palette();
    private static final Palette DARK = new Palette();

    static {
        LIGHT.bg = 0xFFF4F4F5;
        LIGHT.card = 0xFFFFFFFF;
        LIGHT.title = 0xFF111111;
        LIGHT.sub = 0xFF8E8E93;
        LIGHT.hint = 0xFFC7C7CC;
        LIGHT.divider = 0xFFE5E5EA;
        LIGHT.blue = 0xFF007AFF;
        LIGHT.green = 0xFF34C759;
        LIGHT.greenSoft = 0xFFEAF7ED;
        LIGHT.navSelBg = 0xFF007AFF;
        LIGHT.trackOff = 0xFFE3E3E8;
        LIGHT.press = 0x14000000;
        LIGHT.segSlot = 0xFFEFEFF0;
        LIGHT.segSel = 0xFFFFFFFF;

        DARK.bg = 0xFF000000;
        DARK.card = 0xFF1C1C1E;
        DARK.title = 0xFFF2F2F7;
        DARK.sub = 0xFF8E8E93;
        DARK.hint = 0xFF48484A;
        DARK.divider = 0xFF38383A;
        DARK.blue = 0xFF0A84FF;
        DARK.green = 0xFF30D158;
        DARK.greenSoft = 0xFF14281A;
        DARK.navSelBg = 0xFF0A84FF;
        DARK.trackOff = 0xFF3A3A3C;
        DARK.press = 0x24FFFFFF;
        DARK.segSlot = 0xFF2C2C2E;
        DARK.segSel = 0xFF5B5B60;
    }

    static Palette pal(Context c) {
        int mode = c.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES ? DARK : LIGHT;
    }

    static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 发丝线：0.5dp 在 2.75 密度下取 1px，再粗就是不折不扣的「描边」了。 */
    static int hairline(Context c) {
        return Math.max(1, dp(c, 0.5f));
    }

    /* ==================== 基础图形 ==================== */

    static GradientDrawable round(int fill, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    /** 按下时叠一层淡色，代替 selector 资源。 */
    static StateListDrawable pressable(Context c, Palette p, float radiusDp) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed},
                round(p.press, radiusDp, c));
        s.addState(new int[]{}, round(0x00000000, radiusDp, c));
        return s;
    }

    /* ==================== 文本 ==================== */

    /** iOS Large Title：34sp 粗体、左对齐。 */
    static TextView bigTitle(Context c, Palette p, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(p.title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        t.setLetterSpacing(-0.02f);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** 列表行主标题。 */
    static TextView rowTitle(Context c, Palette p, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(p.title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    /** 列表行副标题。 */
    static TextView rowSub(Context c, Palette p, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(p.sub);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setLineSpacing(dp(c, 2), 1f);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** 行尾状态文字（如「个人」「已隐藏」）。 */
    static TextView value(Context c, Palette p, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(p.sub);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setIncludeFontPadding(false);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    /** 分组标题：卡片外、左缩进 16、#8E8E93、13sp、粗体。 */
    static TextView groupLabel(Context c, Palette p, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(p.sub);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        t.setIncludeFontPadding(false);
        t.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        return t;
    }

    /** 段落正文（免责声明一类）。 */
    static TextView body(Context c, Palette p, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(p.sub);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setLineSpacing(dp(c, 4), 1f);
        t.setIncludeFontPadding(false);
        return t;
    }

    /* ==================== 容器 ==================== */

    /**
     * 分组标题 + 白卡。返回卡片容器。
     *
     * <p>标题在卡片**外面**（iOS 原生版式），卡内不加任何内边距 ——
     * 左右留白由每一行自己给，这样分割线才能「左缩进 48、右侧顶到卡片边缘」。
     *
     * @param label 分组标题，可为 null（无标题的独立卡片）
     */
    static LinearLayout group(Context c, Palette p, LinearLayout parent, String label) {
        if (label != null && !label.isEmpty()) {
            TextView h = groupLabel(c, p, label);
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            hlp.topMargin = dp(c, 22);
            hlp.bottomMargin = dp(c, 8);
            parent.addView(h, hlp);
        }
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(p.card, 16, c));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        parent.addView(card, lp);
        return card;
    }

    /** 兼容旧调用：无标题白卡（不再自带内边距）。 */
    static LinearLayout card(Context c, Palette p, LinearLayout parent) {
        return group(c, p, parent, null);
    }

    /**
     * 卡片内的一行：可选图标 + 主标题（+ 副标题）+ 右侧插槽。
     *
     * @param iconType {@link #I_NONE} 表示不放图标
     */
    static LinearLayout row(Context c, Palette p, LinearLayout card,
                            String title, String sub, int iconType) {
        LinearLayout line = new LinearLayout(c);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setMinimumHeight(dp(c, sub == null || sub.isEmpty() ? 52 : 62));
        line.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        line.setBackground(pressable(c, p, 0));

        if (iconType != I_NONE) {
            Icon ic = new Icon(c);
            ic.setType(iconType);
            ic.setColor(p.title);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(c, 24), dp(c, 24));
            ilp.rightMargin = dp(c, 8);
            line.addView(ic, ilp);
        } else {
            // 无图标行也要让文字与有图标行对齐（16 + 24 + 8 = 48）
            View gap = new View(c);
            line.addView(gap, new LinearLayout.LayoutParams(dp(c, 32), 1));
        }

        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = rowTitle(c, p, title);
        texts.addView(t);
        if (sub != null && !sub.isEmpty()) {
            TextView s = rowSub(c, p, sub);
            s.setPadding(0, dp(c, 2), 0, 0);
            texts.addView(s);
        }
        line.addView(texts, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        card.addView(line, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return line;
    }

    /**
     * 行间分割线：厚 1px、色 #E5E5EA、左缩进 48（文字起点）、右侧到卡片边缘。
     *
     * <p>用 {@code addView} 直接挂在卡片里；调用方只需在「两条行之间」调一次。
     */
    static View divider(Context c, Palette p, LinearLayout card) {
        View v = new View(c);
        v.setBackgroundColor(p.divider);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, hairline(c));
        lp.leftMargin = dp(c, 48);
        card.addView(v, lp);
        return v;
    }

    /** 行尾的「›」：iOS 的窄箭头，不是尖括号字符。 */
    static TextView chevron(Context c, Palette p) {
        TextView t = new TextView(c);
        t.setText("\u203A");
        t.setTextColor(p.hint);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        t.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        t.setPadding(dp(c, 8), 0, 0, 0);
        t.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return t;
    }

    /** 卡片底部说明小字（iOS 的 footer 文案）。 */
    static TextView footer(Context c, Palette p, String text) {
        TextView t = body(c, p, text);
        t.setPadding(dp(c, 16), dp(c, 8), dp(c, 16), 0);
        return t;
    }

    /* ==================== 图标 ==================== */

    static final int I_NONE = -1;
    static final int I_HOME = 0;
    static final int I_SLIDERS = 1;
    static final int I_GEAR = 2;
    static final int I_INFO = 3;
    static final int I_REFRESH = 4;
    static final int I_LOG = 5;
    static final int I_APPS = 6;
    static final int I_BOOK = 7;
    static final int I_RING_CHECK = 8;
    static final int I_LINK = 9;
    static final int I_HEART = 10;

    /** 24dp 网格上的线性图标，全部用 Path 现画（模块没有 res/，也没有图标字体）。 */
    static final class Icon extends View {

        private int type = I_HOME;
        private int color = 0xFF111111;
        private float stroke;

        Icon(Context c) {
            super(c);
            stroke = dp(c, 1.5f);
        }

        void setType(int t) {
            type = t;
            invalidate();
        }

        void setColor(int c) {
            color = c;
            invalidate();
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int s = dp(getContext(), 24);
            setMeasuredDimension(resolveSize(s, wSpec), resolveSize(s, hSpec));
        }

        @Override
        protected void onDraw(Canvas cv) {
            int size = Math.min(getWidth(), getHeight());
            if (size <= 0) {
                return;
            }
            float u = size / 24f;
            cv.save();
            cv.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
            cv.scale(u, u);

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(color);
            paint.setStrokeWidth(stroke / u);

            Path path = new Path();
            switch (type) {
                case I_HOME:
                    path.moveTo(3.6f, 11.4f);
                    path.lineTo(12f, 4.6f);
                    path.lineTo(20.4f, 11.4f);
                    cv.drawPath(path, paint);
                    path.reset();
                    path.moveTo(6.2f, 10.2f);
                    path.lineTo(6.2f, 19.4f);
                    path.lineTo(17.8f, 19.4f);
                    path.lineTo(17.8f, 10.2f);
                    cv.drawPath(path, paint);
                    break;

                case I_SLIDERS:
                    for (int i = 0; i < 3; i++) {
                        float y = 7f + i * 5f;
                        cv.drawLine(3.6f, y, 20.4f, y, paint);
                    }
                    paint.setStyle(Paint.Style.FILL);
                    cv.drawCircle(9f, 7f, 1.9f, paint);
                    cv.drawCircle(15f, 12f, 1.9f, paint);
                    cv.drawCircle(7.8f, 17f, 1.9f, paint);
                    break;

                case I_GEAR: {
                    cv.drawCircle(12f, 12f, 6.2f, paint);
                    cv.drawCircle(12f, 12f, 2.4f, paint);
                    for (int i = 0; i < 8; i++) {
                        double a = Math.PI * 2 * i / 8;
                        float cos = (float) Math.cos(a);
                        float sin = (float) Math.sin(a);
                        cv.drawLine(12f + cos * 6.5f, 12f + sin * 6.5f,
                                12f + cos * 8.8f, 12f + sin * 8.8f, paint);
                    }
                    break;
                }

                case I_INFO:
                    cv.drawCircle(12f, 12f, 8f, paint);
                    paint.setStyle(Paint.Style.FILL);
                    cv.drawCircle(12f, 7.8f, 1.1f, paint);
                    cv.drawRoundRect(new RectF(11.05f, 10.4f, 12.95f, 16f), 0.95f, 0.95f, paint);
                    break;

                case I_REFRESH:
                    RectF arc = new RectF(4.6f, 4.6f, 19.4f, 19.4f);
                    cv.drawArc(arc, 55, 275, false, paint);
                    path.moveTo(19.6f, 3.8f);
                    path.lineTo(20.2f, 8.6f);
                    path.lineTo(15.6f, 7.9f);
                    cv.drawPath(path, paint);
                    break;

                case I_LOG:
                    path.moveTo(6.2f, 3.6f);
                    path.lineTo(13.2f, 3.6f);
                    path.lineTo(18f, 8.4f);
                    path.lineTo(18f, 20.4f);
                    path.lineTo(6.2f, 20.4f);
                    path.close();
                    cv.drawPath(path, paint);
                    path.reset();
                    path.moveTo(13f, 3.8f);
                    path.lineTo(13f, 8.6f);
                    path.lineTo(17.8f, 8.6f);
                    cv.drawPath(path, paint);
                    cv.drawLine(8.8f, 12.6f, 15.2f, 12.6f, paint);
                    cv.drawLine(8.8f, 16f, 15.2f, 16f, paint);
                    break;

                case I_APPS:
                    for (int gy = 0; gy < 2; gy++) {
                        for (int gx = 0; gx < 2; gx++) {
                            float l = 4.4f + gx * 9.2f;
                            float tp = 4.4f + gy * 9.2f;
                            cv.drawRoundRect(new RectF(l, tp, l + 7f, tp + 7f), 1.5f, 1.5f, paint);
                        }
                    }
                    break;

                case I_BOOK:
                    cv.drawRoundRect(new RectF(4.4f, 3.6f, 19.6f, 20.4f), 1.8f, 1.8f, paint);
                    cv.drawLine(12f, 3.8f, 12f, 20.2f, paint);
                    cv.drawLine(6.6f, 9f, 9.8f, 9f, paint);
                    cv.drawLine(6.6f, 13f, 9.8f, 13f, paint);
                    cv.drawLine(14.2f, 9f, 17.4f, 9f, paint);
                    cv.drawLine(14.2f, 13f, 17.4f, 13f, paint);
                    break;

                case I_RING_CHECK:
                    cv.drawCircle(12f, 12f, 8.6f, paint);
                    path.moveTo(7.8f, 12.2f);
                    path.lineTo(10.8f, 15.2f);
                    path.lineTo(16.4f, 8.8f);
                    cv.drawPath(path, paint);
                    break;

                case I_LINK:
                    cv.drawRoundRect(new RectF(3.6f, 9.6f, 13f, 14.4f), 2.4f, 2.4f, paint);
                    cv.drawRoundRect(new RectF(11f, 9.6f, 20.4f, 14.4f), 2.4f, 2.4f, paint);
                    break;

                case I_HEART: {
                    path.moveTo(12f, 19.8f);
                    path.cubicTo(6.8f, 15.4f, 4f, 12.6f, 4f, 9.4f);
                    path.cubicTo(4f, 6.9f, 5.9f, 5f, 8.2f, 5f);
                    path.cubicTo(9.9f, 5f, 11.3f, 5.9f, 12f, 7.3f);
                    path.cubicTo(12.7f, 5.9f, 14.1f, 5f, 15.8f, 5f);
                    path.cubicTo(18.1f, 5f, 20f, 6.9f, 20f, 9.4f);
                    path.cubicTo(20f, 12.6f, 17.2f, 15.4f, 12f, 19.8f);
                    path.close();
                    cv.drawPath(path, paint);
                    break;
                }

                default:
                    cv.drawCircle(12f, 12f, 8f, paint);
                    break;
            }
            cv.restore();
        }
    }


    /* ==================== 分段控件 ==================== */

    interface OnSegment {
        void onSelect(int index);
    }

    /** iOS 分段控件：浅灰底槽 + 白色选中块 + 轻微投影。 */
    static LinearLayout segment(Context c, Palette p, String[] items, int selected,
                                final OnSegment cb) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setBackground(round(p.segSlot, 9, c));
        box.setPadding(dp(c, 2), dp(c, 2), dp(c, 2), dp(c, 2));

        final TextView[] tabs = new TextView[items.length];
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            TextView t = new TextView(c);
            t.setText(items[i]);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            t.setGravity(Gravity.CENTER);
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
            box.addView(t, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            tabs[i] = t;
        }
        for (int i = 0; i < tabs.length; i++) {
            styleSegment(tabs[i], p, i == selected);
        }
        return box;
    }

    private static void styleSegment(TextView t, Palette p, boolean on) {
        t.setTextColor(on ? p.title : p.sub);
        t.setTypeface(Typeface.create("sans-serif",
                on ? Typeface.BOLD : Typeface.NORMAL));
        if (on) {
            t.setBackground(round(p.segSel, 7, t.getContext()));
            t.setElevation(dp(t.getContext(), 1));
        } else {
            t.setBackground(null);
            t.setElevation(0);
        }
    }

    /* ==================== 开关 ==================== */

    /**
     * iOS 风格开关：51×31dp 轨道 + 27dp 白色滑块，带 180ms 位移与配色过渡。
     *
     * <p>不用 framework {@code Switch}：它的轨道/滑块 drawable 来自系统主题，
     * 尺寸与配色都不可控，这里自己画更贴近 iOS 的观感。
     */
    static final class MiSwitch extends View {

        private final Palette p;
        private boolean on;
        private float pos;
        private ValueAnimator anim;

        MiSwitch(Context c, Palette p, boolean checked) {
            super(c);
            this.p = p;
            this.on = checked;
            this.pos = checked ? 1f : 0f;
            setClickable(true);
        }

        boolean isOn() {
            return on;
        }

        void setOnSilently(boolean v) {
            on = v;
            pos = v ? 1f : 0f;
            invalidate();
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            setMeasuredDimension(resolveSize(dp(getContext(), 51), wSpec),
                    resolveSize(dp(getContext(), 31), hSpec));
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_UP && isEnabled()) {
                performClick();
                animateTo(!on);
                on = !on;
                // 用 instanceof 而不是直接强转：调用方一旦往 tag 里塞了别的东西，
                // 直接 (OnToggle) getTag() 就是 ClassCastException，
                // 而这里正处在点击事件里 —— 一次强转就把整个设置页点崩（v3.07 实测）。
                Object tag = getTag();
                if (tag instanceof OnToggle) {
                    ((OnToggle) tag).onChanged(on);
                } else if (tag != null) {
                    android.util.Log.e("RGTools",
                            "MiSwitch tag must be MiUi.OnToggle, got " + tag.getClass().getName());
                }
            }
            return true;
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }

        private void animateTo(final boolean target) {
            if (anim != null) {
                anim.cancel();
            }
            anim = ValueAnimator.ofFloat(pos, target ? 1f : 0f);
            anim.setDuration(180);
            anim.setInterpolator(new DecelerateInterpolator());
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    pos = (Float) a.getAnimatedValue();
                    invalidate();
                }
            });
            anim.start();
        }

        @Override
        protected void onDraw(Canvas cv) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            float trackR = h / 2f;
            cv.drawRoundRect(new RectF(0, 0, w, h), trackR, trackR,
                    fillPaint(blend(p.trackOff, p.green, pos)));

            float inset = dp(getContext(), 2);
            float r = h / 2f - inset;
            float cx = trackR + pos * (w - 2 * trackR);
            cv.drawCircle(cx, trackR, r, fillPaint(0xFFFFFFFF));
        }

        private Paint fillPaint(final int color) {
            Paint p2 = new Paint(Paint.ANTI_ALIAS_FLAG);
            p2.setStyle(Paint.Style.FILL);
            p2.setColor(color);
            return p2;
        }
    }

    private static int blend(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t);
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.argb(a, r, g, b);
    }

    /** 开关回调（由调用方实现，避免匿名内部类泛滥）。 */
    interface OnToggle {
        void onChanged(boolean on);
    }

    /** 次要操作按钮：只用蓝色文字，不加底色（iOS 的 plain button）。 */
    static TextView ghostButton(Context c, Palette p, String text, View.OnClickListener l) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(p.blue);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        t.setBackground(pressable(c, p, 12));
        if (l != null) {
            t.setOnClickListener(l);
        }
        return t;
    }

    /** 强调色文字行（如「升级套餐」这种唯一带主题色的项）。 */
    static TextView accentRow(Context c, Palette p, String text, View.OnClickListener l) {
        TextView t = rowTitle(c, p, text);
        t.setTextColor(p.blue);
        t.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        t.setMinHeight(dp(c, 52));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setBackground(pressable(c, p, 0));
        if (l != null) {
            t.setOnClickListener(l);
        }
        return t;
    }
}
