package com.wodi.redguotools;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;

/**
 * 应用内 DPI（屏幕密度）调节。
 *
 * <h3>原理</h3>
 * Android 所有 dp→px 换算都走 {@code Resources.getDisplayMetrics()} 返回的
 * {@link DisplayMetrics#density}（以及 {@link DisplayMetrics#densityDpi}）。
 * 这个对象是**可变且被共享**的：改它的字段，之后所有
 * {@code TypedValue.applyDimension()} / 布局 inflate 都按新密度计算。
 *
 * <p>因此不需要重签 APK、也不需要动系统设置，只改宿主进程里每个 Activity
 * 的 Resources 即可。关键约束是**必须赶在该 Activity inflate 布局之前**：
 * 已经测量摆放过的 View 不会因为 density 变化重新排版，所以要挂在
 * {@code Activity.onCreate} 的 before 阶段（见 {@code RGModule.hookActivity}）。
 *
 * <p>红果主体没有缓存 density 的工具类（全 dex 检索无自建 DisplayUtils /
 * DensityUtil，只有电商与小游戏 SDK 内部各有一份，且不参与短剧 UI），
 * 故直接改 DisplayMetrics 即可全局生效，无需额外 hook 缓存点。
 *
 * <p>注意：本功能只影响**界面尺寸**，视频画面是 SurfaceView 输出，
 * 不受 density 影响。改完需要重建 Activity（切页或重启 App）才能看到效果。
 */
final class DpiTweak {

    /** 合法范围：低于 240 界面过大易崩版，高于 720 基本不可读。 */
    public static final int MIN_DPI = 240;
    public static final int MAX_DPI = 720;

    private DpiTweak() {
    }

    /** @return 配置的目标 dpi；0 表示跟随系统（不干预）。 */
    static int targetDpi(Context c) {
        return Config.dpi(c);
    }

    /**
     * 把目标密度写进该 Activity 的 Resources。幂等，可对同一 Activity 多次调用。
     *
     * @return true 表示本次确实改写了密度
     */
    static boolean applyTo(Activity a) {
        if (a == null) {
            return false;
        }
        int dpi = Config.dpi(a);
        if (dpi <= 0) {
            return false;
        }
        dpi = Math.max(MIN_DPI, Math.min(MAX_DPI, dpi));
        try {
            Resources res = a.getResources();
            if (res == null) {
                return false;
            }
            DisplayMetrics dm = res.getDisplayMetrics();
            Configuration cfg = res.getConfiguration();
            if (dm == null || cfg == null || dm.densityDpi == dpi) {
                return false;
            }
            float fontScale = cfg.fontScale > 0 ? cfg.fontScale : 1f;
            cfg.densityDpi = dpi;
            dm.densityDpi = dpi;
            dm.density = dpi / 160f;
            dm.scaledDensity = dm.density * fontScale;
            return true;
        } catch (Throwable t) {
            UiController.logFile("dpi: apply failed " + t);
            return false;
        }
    }

    /** 当前 Activity 实际生效的 densityDpi（未安装前为 0）。 */
    static int currentDpi(Activity a) {
        try {
            return a.getResources().getDisplayMetrics().densityDpi;
        } catch (Throwable t) {
            return 0;
        }
    }
}
