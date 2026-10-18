package com.wodi.redguotools;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * 暗色模式（宿主自带皮肤体系的开关）。
 *
 * <h3>宿主的实现（7.3.9.32 反编译确证，改版与 Play 版同名）</h3>
 * <pre>
 *   com.dragon.read.base.skin.base.Skin           枚举：LIGHT / DARK
 *   com.dragon.read.base.skin.SkinManager
 *       public static void changeSkinType(Skin)     立即换肤 + 持久化 + 广播
 *       public static boolean isNightMode()         curSkin == Skin.DARK
 *       public static void initSKinMode()           启动时按「跟随系统」决定初值
 *   偏好文件 preference_dark_mode_config，键 key_skin_mode（JSON 化的 Skin）
 * </pre>
 *
 * <p>宿主在 {@code initSKinMode()} 里会看「跟随系统」开关 {@code lf3.h.c}：
 * 打开时**无视偏好**、直接按系统深色与否给初值，所以单纯写 SharedPreferences
 * 是无效的（下一轮启动就被覆盖）。本模块因此走<b>运行时纠正</b>：
 * 启动 hook 一次 {@code initSKinMode}，之后每次 Activity onResume 校验一次，
 * 与目标不符才调用 {@code changeSkinType}，一致时零开销。
 *
 * <p>换肤本身会重建界面（广播 {@code action_skin_type_change} + 刷新
 * skinFactory2），所以只在状态确实不一致时触发，且必须在主线程执行。
 */
final class DarkMode {

    private static final String CLS_SKIN_MANAGER = "com.dragon.read.base.skin.SkinManager";
    private static final String CLS_SKIN = "com.dragon.read.base.skin.base.Skin";

    private static Method sChange;      // changeSkinType(Skin)
    private static Method sIsNight;     // isNightMode()
    private static Object sDark;
    private static Object sLight;

    private static volatile boolean sInit;
    private static volatile String sStatus;
    private static volatile boolean sBusy;

    private static final Handler UI = new Handler(Looper.getMainLooper());

    private DarkMode() {
    }

    static String status() {
        return sStatus;
    }

    /**
     * 宿主当前是否处于深色皮肤。供面板选配色 —— 宿主的「深色」是换肤
     * （资源后缀切换），{@code Configuration.uiMode} 不一定跟着变，
     * 只有问 {@code SkinManager.isNightMode()} 才准。
     */
    static boolean hostIsNight() {
        if (!sInit) {
            return false;
        }
        try {
            return (Boolean) sIsNight.invoke(null);
        } catch (Throwable t) {
            return false;
        }
    }

    static void hook(XposedModule mod, ClassLoader cl) {
        if (sInit) {
            return;
        }
        Class<?> mgr = RGModule.tryLoad(cl, CLS_SKIN_MANAGER);
        Class<?> skin = RGModule.tryLoad(cl, CLS_SKIN);
        if (mgr == null || skin == null) {
            sStatus = "FAILED: SkinManager/Skin not found";
            return;
        }
        try {
            sDark = enumConst(skin, "DARK");
            sLight = enumConst(skin, "LIGHT");
            sChange = mgr.getMethod("changeSkinType", skin);
            sIsNight = mgr.getMethod("isNightMode");
            sChange.setAccessible(true);
            sIsNight.setAccessible(true);
        } catch (Throwable t) {
            sStatus = "FAILED: reflect " + t;
            return;
        }

        // 启动时机：宿主 initSKinMode() 读完初值后立刻纠正一次
        try {
            Method init = mgr.getDeclaredMethod("initSKinMode");
            init.setAccessible(true);
            mod.hook(init)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object r = chain.proceed();
                        try {
                            Context c = UiController.appCtx();
                            if (c != null) {
                                apply(c);
                            }
                        } catch (Throwable ignored) {
                        }
                        return r;
                    });
        } catch (Throwable t) {
            sStatus = "PARTIAL: initSKinMode hook " + t;
        }

        sInit = true;
        if (sStatus == null) {
            sStatus = "ok";
        }
    }

    /**
     * 把当前皮肤纠正到配置要求的状态。可安全重入、可在任意线程调用。
     *
     * @return 是否真的触发了一次换肤
     */
    static boolean apply(final Context c) {
        if (!sInit || c == null || sBusy) {
            return false;
        }
        final int mode = Config.darkMode(c);
        if (mode == Config.DARK_FOLLOW) {
            return false;
        }
        final boolean wantDark = (mode == Config.DARK_DARK);
        if (Looper.myLooper() != Looper.getMainLooper()) {
            UI.post(new Runnable() {
                @Override
                public void run() {
                    apply(c);
                }
            });
            return false;
        }
        sBusy = true;
        try {
            boolean cur = (Boolean) sIsNight.invoke(null);
            if (cur == wantDark) {
                return false;
            }
            sChange.invoke(null, wantDark ? sDark : sLight);
            UiController.logFile("dark: switch -> " + (wantDark ? "DARK" : "LIGHT"));
            return true;
        } catch (Throwable t) {
            UiController.logFile("dark: switch failed " + t);
            return false;
        } finally {
            sBusy = false;
        }
    }

    private static Object enumConst(Class<?> cls, String name) throws Throwable {
        Field f = cls.getField(name);
        f.setAccessible(true);
        return f.get(null);
    }
}
