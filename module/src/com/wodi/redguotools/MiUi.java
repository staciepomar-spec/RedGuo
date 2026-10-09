package com.wodi.redguotools;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 模块 App 自身的 MIUI 风格视觉层（对照 HyperMusicCover 的观感重做）。
 *
 * <p>与 {@link Theme} 的分工：{@code Theme} 服务的是跑在红果进程里的设置面板，
 * 那里必须自带颜色与形状（宿主主题不可控）；这里是模块自己的 Activity，
 * 有完整的 framework 环境，可以放心用尺寸换算与动画。
 *
 * <p>只依赖 framework 控件与 Canvas 自绘，不引入任何资源文件与第三方库。
 */
final class MiUi {

    private MiUi() {
    }

    /* ==================== 调色板 ==================== */

    /** 跟随系统明暗，避免系统切深色时 App 内留一块白板。 */
    static final class Palette {
        int bg;         // 页面底
        int card;       // 卡片
        int title;      // 一级文字
        int sub;        // 二级文字（取值行）
        int hint;       // 提示小字
        int blue;       // 主色（选中态、主按钮）
        int green;      // 激活态
        int greenSoft;  // 激活卡底色
        int navSelBg;   // 底栏选中项的衬底
        int trackOff;   // 开关关态轨道
        int press;      // 按下叠加
    }

    private static final Palette LIGHT = new Palette();
    private static final Palette DARK = new Palette();

    static {
        LIGHT.bg = 0xFFF2F3F5;
        LIGHT.card = 0xFFFFFFFF;
        LIGHT.title = 0xFF1C1C1E;
        LIGHT.sub = 0xFF8A8A8E;
        LIGHT.hint = 0xFF9A9A9E;
        LIGHT.blue = 0xFF3478F6;
        LIGHT.green = 0xFF22C55E;
        LIGHT.greenSoft = 0xFFDCF6E4;
        LIGHT.navSelBg = 0xFFEDEDF0;
        LIGHT.trackOff = 0xFFDCDCE0;
        LIGHT.press = 0x0F000000;

        DARK.bg = 0xFF101012;
        DARK.card = 0xFF1C1C1F;
        DARK.title = 0xFFF2F2F4;
        DARK.sub = 0xFF9A9AA0;
        DARK.hint = 0xFF7C7C82;
        DARK.blue = 0xFF4A8CFF;
        DARK.green = 0xFF32D66F;
        DARK.greenSoft = 0xFF16321F;
        DARK.navSelBg = 0xFF2A2A2E;
        DARK.trackOff = 0xFF3A3A3E;
        DARK.press = 0x1AFFFFFF;
    }

    static Palette pal(Context c) {
        int mode = c.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES ? DARK : LIGHT;
    }

    static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
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

    /** 页面大标题（MIUI 风格：34sp 粗体、贴顶）。 */
    static TextView bigTitle(Context c, Palette p, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(p.title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setLetterSpacing(0.01f);
        return t;
    }

    /** 列表行主标题。 */
    static TextView rowTitle(Context c, Palette p, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(p.title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        return t;
    }

    /** 列表行副标题。 */
    static TextView rowSub(Context c, Palette p, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(p.sub);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setLineSpacing(dp(c, 2), 1f);
        return t;
    }

    /** 卡片内的分组小标（比 MIUI 原生稍大，长说明文字下更易读）。 */
    static TextView groupLabel(Context c, Palette p, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(p.sub);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        return t;
    }

    /** 段落正文（免责声明一类）。 */
    static TextView body(Context c, Palette p, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(p.sub);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setLineSpacing(dp(c, 4), 1f);
        return t;
    }

    /* ==================== 容器 ==================== */

    /** 白色圆角卡片，内部纵向排布。 */
    static LinearLayout card(Context c, Palette p, LinearLayout parent) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(p.card, 18, c));
        int pad = dp(c, 16);
        card.setPadding(pad, dp(c, 6), pad, dp(c, 6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 12);
        parent.addView(card, lp);
        return card;
    }

    /** 卡片内：主标题 + 副标题（取值行、开关行、入口行都用它）。 */
    static LinearLayout row(Context c, Palette p, LinearLayout card,
                            String title, String sub, int iconType) {
        LinearLayout line = new LinearLayout(c);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setMinimumHeight(dp(c, 56));
        int v = dp(c, 10);
        line.setPadding(0, v, 0, v);
        line.setBackground(pressable(c, p, 12));

        if (iconType >= 0) {
            Icon ic = new Icon(c);
            ic.setType(iconType);
            ic.setColor(p.title);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(c, 24), dp(c, 24));
            ilp.rightMargin = dp(c, 12);
            line.addView(ic, ilp);
        }

        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(rowTitle(c, p, title));
        if (sub != null && !sub.isEmpty()) {
            texts.addView(rowSub(c, p, sub));
        }
        line.addView(texts, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        card.addView(line, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return line;
    }

    /** 行尾的「›」。 */
    static TextView chevron(Context c, Palette p) {
        TextView t = new TextView(c);
        t.setText("›");
        t.setTextColor(p.hint);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 6), 0, 0, 0);
        t.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return t;
    }

    /* ==================== 图标 ==================== */

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
        private int color = 0xFF333333;
        private float stroke;

        Icon(Context c) {
            super(c);
            stroke = dp(c, 1.7f);
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
            int s = Math.min(getSuggestedMinimumWidth(), getSuggestedMinimumHeight());
            if (s <= 0) {
                s = dp(getContext(), 24);
            }
            setMeasuredDimension(
                    resolveSize(s, wSpec), resolveSize(s, hSpec));
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
                    path.moveTo(3.4f, 11.2f);
                    path.lineTo(12f, 4.4f);
                    path.lineTo(20.6f, 11.2f);
                    cv.drawPath(path, paint);
                    path.reset();
                    path.moveTo(5.9f, 9.9f);
                    path.lineTo(5.9f, 19.6f);
                    path.lineTo(18.1f, 19.6f);
                    path.lineTo(18.1f, 9.9f);
                    cv.drawPath(path, paint);
                    path.reset();
                    path.moveTo(9.9f, 19.6f);
                    path.lineTo(9.9f, 14.6f);
                    path.lineTo(14.1f, 14.6f);
                    path.lineTo(14.1f, 19.6f);
                    cv.drawPath(path, paint);
                    break;

                case I_SLIDERS:
                    for (int i = 0; i < 3; i++) {
                        float y = 6.6f + i * 5.4f;
                        cv.drawLine(3.4f, y, 20.6f, y, paint);
                    }
                    paint.setStyle(Paint.Style.FILL);
                    cv.drawCircle(9.2f, 6.6f, 2.1f, paint);
                    cv.drawCircle(15.2f, 12f, 2.1f, paint);
                    cv.drawCircle(7.6f, 17.4f, 2.1f, paint);
                    break;

                case I_GEAR: {
                    cv.drawCircle(12f, 12f, 6.1f, paint);
                    cv.drawCircle(12f, 12f, 2.5f, paint);
                    for (int i = 0; i < 8; i++) {
                        double a = Math.PI * 2 * i / 8;
                        float cos = (float) Math.cos(a);
                        float sin = (float) Math.sin(a);
                        cv.drawLine(12f + cos * 6.4f, 12f + sin * 6.4f,
                                12f + cos * 8.7f, 12f + sin * 8.7f, paint);
                    }
                    break;
                }

                case I_INFO:
                    cv.drawCircle(12f, 12f, 7.6f, paint);
                    paint.setStyle(Paint.Style.FILL);
                    cv.drawCircle(12f, 7.6f, 1.15f, paint);
                    cv.drawRoundRect(new RectF(10.95f, 10.2f, 13.05f, 16.2f), 1.05f, 1.05f, paint);
                    break;

                case I_REFRESH:
                    RectF arc = new RectF(4.4f, 4.4f, 19.6f, 19.6f);
                    cv.drawArc(arc, 55, 275, false, paint);
                    path.moveTo(19.9f, 3.6f);
                    path.lineTo(20.4f, 8.6f);
                    path.lineTo(15.5f, 7.9f);
                    cv.drawPath(path, paint);
                    break;

                case I_LOG:
                    path.moveTo(6f, 3.4f);
                    path.lineTo(13.4f, 3.4f);
                    path.lineTo(18.4f, 8.4f);
                    path.lineTo(18.4f, 20.6f);
                    path.lineTo(6f, 20.6f);
                    path.close();
                    cv.drawPath(path, paint);
                    path.reset();
                    path.moveTo(13.2f, 3.6f);
                    path.lineTo(13.2f, 8.6f);
                    path.lineTo(18.2f, 8.6f);
                    cv.drawPath(path, paint);
                    paint.setStyle(Paint.Style.FILL);
                    cv.drawRoundRect(new RectF(8.6f, 12f, 15.4f, 13.3f), 0.6f, 0.6f, paint);
                    cv.drawRoundRect(new RectF(8.6f, 15f, 15.4f, 16.3f), 0.6f, 0.6f, paint);
                    cv.drawRoundRect(new RectF(8.6f, 18f, 12.6f, 19.3f), 0.6f, 0.6f, paint);
                    break;

                case I_APPS:
                    for (int gy = 0; gy < 2; gy++) {
                        for (int gx = 0; gx < 2; gx++) {
                            float l = 4.2f + gx * 9.2f;
                            float tp = 4.2f + gy * 9.2f;
                            cv.drawRoundRect(new RectF(l, tp, l + 7.2f, tp + 7.2f), 1.6f, 1.6f, paint);
                        }
                    }
                    break;

                case I_BOOK:
                    cv.drawRoundRect(new RectF(4.2f, 3.4f, 19.8f, 20.6f), 1.8f, 1.8f, paint);
                    cv.drawLine(12f, 3.6f, 12f, 20.4f, paint);
                    cv.drawLine(6.4f, 9f, 9.8f, 9f, paint);
                    cv.drawLine(6.4f, 13f, 9.8f, 13f, paint);
                    cv.drawLine(14.2f, 9f, 17.6f, 9f, paint);
                    cv.drawLine(14.2f, 13f, 17.6f, 13f, paint);
                    break;

                case I_RING_CHECK:
                    paint.setStrokeWidth(stroke / u * 2.1f);
                    cv.drawCircle(12f, 12f, 9f, paint);
                    path.moveTo(7.6f, 12.2f);
                    path.lineTo(10.8f, 15.4f);
                    path.lineTo(16.6f, 8.6f);
                    cv.drawPath(path, paint);
                    break;

                case I_LINK:
                    cv.drawRoundRect(new RectF(3.4f, 9.4f, 13.2f, 14.6f), 2.6f, 2.6f, paint);
                    cv.drawRoundRect(new RectF(10.8f, 9.4f, 20.6f, 14.6f), 2.6f, 2.6f, paint);
                    cv.drawLine(9.2f, 12f, 14.8f, 12f, paint);
                    break;

                case I_HEART: {
                    // 实心爱心（捐赠入口），24 单位坐标
                    Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
                    fill.setStyle(Paint.Style.FILL);
                    fill.setColor(color);
                    Path hp = new Path();
                    hp.moveTo(12f, 20.2f);
                    hp.cubicTo(6.4f, 15.4f, 3.2f, 12.4f, 3.2f, 8.9f);
                    hp.cubicTo(3.2f, 6.2f, 5.3f, 4.2f, 7.8f, 4.2f);
                    hp.cubicTo(9.6f, 4.2f, 11.2f, 5.2f, 12f, 6.8f);
                    hp.cubicTo(12.8f, 5.2f, 14.4f, 4.2f, 16.2f, 4.2f);
                    hp.cubicTo(18.7f, 4.2f, 20.8f, 6.2f, 20.8f, 8.9f);
                    hp.cubicTo(20.8f, 12.4f, 17.6f, 15.4f, 12f, 20.2f);
                    hp.close();
                    cv.drawPath(hp, fill);
                    break;
                }

                default:
                    cv.drawCircle(12f, 12f, 7.6f, paint);
                    break;
            }
            cv.restore();
        }
    }

    /* ==================== 开关 ==================== */

    /**
     * MIUI 风格开关：52x32dp 圆角轨道 + 24dp 白色滑块，带 180ms 位移与配色过渡。
     *
     * <p>不用 framework {@code Switch}：它的轨道/滑块 drawable 来自系统主题，
     * 尺寸与配色都不可控，这里自己画更贴近 MIUI 的观感。
     */
    static final class MiSwitch extends View {

        private final Palette p;
        private boolean on;
        private float pos;          // 0 = 关，1 = 开
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
            setMeasuredDimension(resolveSize(dp(getContext(), 52), wSpec),
                    resolveSize(dp(getContext(), 32), hSpec));
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_UP && isEnabled()) {
                performClick();
                animateTo(!on);
                on = !on;
                OnToggle l = (OnToggle) getTag();
                if (l != null) {
                    l.onChanged(on);
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
            int trackColor = blend(p.trackOff, p.blue, pos);
            cv.drawRoundRect(new RectF(0, 0, w, h), trackR, trackR,
                    fillPaint(trackColor));

            float inset = dp(getContext(), 4);
            float r = h / 2f - inset;
            float cx = trackR + r + pos * (w - 2 * trackR);
            // 滑块下方一点点浅影，MIUI 上滑块是有层次的
            cv.drawCircle(cx + dp(getContext(), 0.6f), trackR + dp(getContext(), 1.2f), r,
                    fillPaint(blend(0x00000000, 0x22000000, pos)));
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

    /** 圆角描边按钮（次级操作）。 */
    static TextView ghostButton(Context c, Palette p, String text, View.OnClickListener l) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(p.blue);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10));
        t.setBackground(round(p.card == 0xFFFFFFFF ? 0x0D3478F6 : 0x1A4A8CFF, 14, c));
        if (l != null) {
            t.setOnClickListener(l);
        }
        return t;
    }
}