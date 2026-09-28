package com.wodi.redguotools;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.PopupWindow;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * 红果免费短剧（com.phoenix.read）界面工具模块。
 *
 * 全部使用「框架级 hook + 按名字反射查找」的方式，不直接引用宿主类，
 * 以降低宿主升级后类名变化导致的失效风险。
 */
public class RGModule extends XposedModule {

    public static final String TAG = "RGTools";
    public static final String HOST_PKG = "com.phoenix.read";

    /** 短剧全屏播放页里承载 onDoubleTap 的手势监听类（多版本候选，存在即挂）。 */
    private static final String[] DOUBLE_TAP_CLASSES = {
            // 红果 7.3.7.32
            "com.dragon.read.component.shortvideo.impl.fullscreen.i$d",
            // 旧版本（7.3.2.x / 7.3.3.x）
            "com.dragon.read.component.shortvideo.impl.fullscreen.d$d",
            "com.dragon.read.component.shortvideo.impl.fullscreen.f$d",
            // 7.3.9.32：feed 双击点赞走社交层手势（实测屏蔽 i$d 后仍会点赞）
            "com.dragon.read.social.ui.t",
            // 7.3.9.32：短剧 v2 竖滑适配器的双击监听（feed 与二级页共用，
            // dt2 诊断日志确认真实触发，屏蔽后双击点赞消失）
            "r65.o0",
    };

    private ClassLoader appLoader;

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);
        String pkg = param.getPackageName();
        appLoader = param.getClassLoader();
        log(Log.INFO, TAG, "onPackageReady pkg=" + pkg);

        if (!HOST_PKG.equals(pkg)) {
            return;
        }
        try {
            hookActivity(appLoader);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hookActivity failed", t);
        }
        try {
            hookStatusBarUtil(appLoader);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hookStatusBarUtil failed", t);
        }
        try {
            hookDoubleTap(appLoader);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hookDoubleTap failed", t);
        }
        try {
            hookDoubleTapDiag(appLoader);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hookDoubleTapDiag failed", t);
        }
        try {
            hookSheetWindows(appLoader);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hookSheetWindows failed", t);
        }
        try {
            PlayerTweaks.hook(this, appLoader);
            log(Log.INFO, TAG, "hooked player tweaks");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hookPlayerTweaks failed", t);
        }
        try {
            NoUpdate.hook(this, appLoader);
            log(Log.INFO, TAG, "hooked no-update");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hookNoUpdate failed", t);
        }
        try {
            hookViewAttach();
            log(Log.INFO, TAG, "hooked view-attach");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hookViewAttach failed", t);
        }
    }

    /**
     * 新视图挂到窗口上时，立刻补一次界面设置（而不是等下一轮 1.2s 循环）。
     *
     * <p>播放页/浮层都是新 inflate 的（ViewPager 每页一套实例），等待周期循环的
     * 那段时间里组件会以「宿主原样」显示 —— 表现就是切视频时透明度闪一下。
     * 见 {@link UiController#onViewAttached()}（内部做了合并与限流）。
     *
     * <p>这是全进程每个 View 挂载都会走的点，所以回调体必须极轻：
     * 只做两次 volatile 读 + 必要时 post 一个消息。
     */
    private void hookViewAttach() throws Throwable {
        Method m = View.class.getDeclaredMethod("onAttachedToWindow");
        m.setAccessible(true);
        hook(m).setId("rgViewAttach")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object r = chain.proceed();     // 先让视图真正挂上去
                    UiController.onViewAttached();
                    return r;
                });
    }

    /* ------------------------------------------------------------------ */
    /* Activity 生命周期：拿 Context、挂手势、应用界面设置                    */
    /* ------------------------------------------------------------------ */

    private void hookActivity(ClassLoader cl) throws Throwable {
        Method onCreate = Activity.class.getDeclaredMethod("onCreate", Bundle.class);
        hook(onCreate).setId("rgActCreate").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object r = chain.proceed();
                    try {
                        UiController.onActivityCreated((Activity) chain.getThisObject());
                    } catch (Throwable ignored) {
                    }
                    return r;
                });

        Method onResume = Activity.class.getDeclaredMethod("onResume");
        hook(onResume).setId("rgActResume").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object r = chain.proceed();
                    try {
                        UiController.onActivityResumed((Activity) chain.getThisObject());
                    } catch (Throwable ignored) {
                    }
                    return r;
                });

        Method dispatch = Activity.class.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class);
        hook(dispatch).setId("rgActTouch").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object self = chain.getThisObject();
                    if (self instanceof Activity) {
                        MotionEvent ev = (MotionEvent) chain.getArg(0);
                        try {
                            if (UiController.onTouch((Activity) self, ev)) {
                                return Boolean.TRUE;
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                    return chain.proceed();
                });

        // 前台状态跟踪：用来区分「App 内部切页面」与「真的离开 App」。
        // 导航栏全程隐藏，只有整个 App 退到后台才恢复 —— 见 UiController.onAppBackground()。
        Method onStop = Activity.class.getDeclaredMethod("onStop");
        hook(onStop).setId("rgActStop").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object self = chain.getThisObject();
                    Object r = chain.proceed();
                    try {
                        if (self instanceof Activity && UiController.isHostActivity((Activity) self)) {
                            UiController.onAppBackground();
                        }
                    } catch (Throwable ignored) {
                    }
                    return r;
                });
        log(Log.INFO, TAG, "hooked Activity lifecycle");
    }

    /* ------------------------------------------------------------------ */
    /* 状态栏：拦宿主自己的状态栏工具，避免它把我们的设置改回去              */
    /* ------------------------------------------------------------------ */

    private void hookStatusBarUtil(ClassLoader cl) throws Throwable {
        // com.dragon.read.base.ui.util.StatusBarUtil#clearFullScreenFlag
        Class<?> cls = tryLoad(cl, "com.dragon.read.base.ui.util.StatusBarUtil");
        if (cls != null) {
            for (Method m : cls.getDeclaredMethods()) {
                String n = m.getName();
                if ("clearFullScreenFlag".equals(n) || "hideStatusBar".equals(n)) {
                    hook(m).setId("rgSb_" + n).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                Activity act = currentTopActivity();
                                if (act != null && Config.hideStatusBar(act)) {
                                    // 保持沉浸：不执行宿主恢复状态栏的逻辑
                                    return null;
                                }
                                return chain.proceed();
                            });
                    log(Log.INFO, TAG, "hooked StatusBarUtil." + n);
                }
            }
        } else {
            log(Log.WARN, TAG, "StatusBarUtil not found");
        }
    }

    private static Activity currentTopActivity() {
        return UiController.topActivity();
    }

    /* ------------------------------------------------------------------ */
    /* 双击打开评论区：屏蔽播放页的 onDoubleTap（宿主默认用它做点赞/暂停）      */
    /* ------------------------------------------------------------------ */

    private void hookDoubleTap(ClassLoader cl) throws Throwable {
        int n = 0;
        for (String cn : DOUBLE_TAP_CLASSES) {
            Class<?> cls = tryLoad(cl, cn);
            if (cls == null) {
                log(Log.WARN, TAG, "class not found: " + cn);
                continue;
            }
            for (Method m : cls.getDeclaredMethods()) {
                if (!"onDoubleTap".equals(m.getName()) || m.getParameterCount() != 1
                        || !MotionEvent.class.equals(m.getParameterTypes()[0])) {
                    continue;
                }
                hook(m).setId("rgDoubleTap_" + cn)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Activity act = UiController.activityOf(chain.getThisObject());
                            // 只在「本页面确实由模块接管双击评论」时屏蔽：
                            // 正常路径下触发前的点击已被 UiController 吞掉，
                            // 宿主收不到完整连击；这里再挡一道兜底，确保不暂停、不点赞。
                            //
                            // 判据必须与 UiController.needSwallow 完全一致 —— 曾经按
                            // Config.doubleTapComment() 无条件屏蔽，结果横屏若走进竖屏分支
                            // （方向判定不一致 / 用户关了横屏手势），宿主的「双击暂停」
                            // 被模块屏蔽掉、模块自己又不处理，双击就彻底没反应了。
                            if (act != null && UiController.gestureTakeover(act)) {
                                UiController.logFile("dt: blocked onDoubleTap on "
                                        + chain.getThisObject().getClass().getName()
                                        + " act=" + act.getClass().getSimpleName());
                                return Boolean.FALSE;
                            }
                            return chain.proceed();
                        });
                n++;
                log(Log.INFO, TAG, "hooked " + cn + ".onDoubleTap");
            }
        }
        if (n == 0) {
            log(Log.WARN, TAG, "no onDoubleTap hooked");
        }
    }

    /**
     * 诊断：把宿主里所有声明 onDoubleTap 的混淆类都挂上「只记日志」的钩子，
     * 用于定位 feed 双击点赞的真实处理类（用户真机双击一次即可从日志看出是谁）。
     */
    private void hookDoubleTapDiag(ClassLoader cl) {
        String[] candidates = {
                "i57.a0", "lv6.k0$a", "r47.p2$b", "w37.x3", "wd.b$c", "gc8.b$c",
                "ah2.s$d", "ch2.d$c$a", "ch2.q$b", "pc2.h$b", "pc2.j", "pc2.o$c",
                "dw2.g", "dw2.o$c", "nn2.o", "es4.a", "e55.h", "kx4.m", "wp4.e",
                "r65.o0", "kc6.s", "bt6.y",
        };
        int n = 0;
        for (String cn : candidates) {
            try {
                Class<?> cls = tryLoad(cl, cn);
                if (cls == null) {
                    continue;
                }
                for (final Method m : cls.getDeclaredMethods()) {
                    if (!"onDoubleTap".equals(m.getName()) || m.getParameterCount() != 1
                            || !MotionEvent.class.equals(m.getParameterTypes()[0])) {
                        continue;
                    }
                    m.setAccessible(true);
                    hook(m).setId("rgDtDiag_" + cn)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                try {
                                    UiController.logFile("dt2: " + cn
                                            + " act=" + UiController.topActivitySimple());
                                } catch (Throwable ignored) {
                                }
                                return chain.proceed();
                            });
                    n++;
                }
            } catch (Throwable ignored) {
            }
        }
        UiController.logFile("dt2 diag hooked=" + n);
    }

    /* ------------------------------------------------------------------ */

    /**
     * 播放设置弹层的模块入口：弹层可能是 Dialog / PopupWindow，
     * 也可能直接挂在 DecorView 上（那条路由 UiController 的重应用循环负责）。
     * 这里只拦前两种 —— 弹层 show 出来后按特征文案识别、注入入口行。
     */
    private void hookSheetWindows(ClassLoader cl) throws Throwable {
        Method show = Dialog.class.getDeclaredMethod("show");
        hook(show).setId("rgSheetDlg").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object r = chain.proceed();
                    try {
                        Object self = chain.getThisObject();
                        if (self instanceof Dialog) {
                            android.view.Window w = ((Dialog) self).getWindow();
                            View decor = w != null ? w.getDecorView() : null;
                            if (decor != null) {
                                SheetEntry.tryInjectLater(UiController.topActivity(), decor);
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                    return r;
                });
        log(Log.INFO, TAG, "hooked Dialog.show for sheet entry");

        for (String mn : new String[]{"showAtLocation", "showAsDropDown"}) {
            for (Method m : PopupWindow.class.getDeclaredMethods()) {
                if (!mn.equals(m.getName()) || m.getParameterCount() == 0) {
                    continue;
                }
                hook(m).setId("rgSheetPw_" + mn + m.getParameterCount())
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object r = chain.proceed();
                            try {
                                Object self = chain.getThisObject();
                                if (self instanceof PopupWindow) {
                                    View cv = ((PopupWindow) self).getContentView();
                                    View root = cv != null ? cv.getRootView() : null;
                                    if (root != null) {
                                        SheetEntry.tryInjectLater(UiController.topActivity(), root);
                                    }
                                }
                            } catch (Throwable ignored) {
                            }
                            return r;
                        });
            }
        }
        log(Log.INFO, TAG, "hooked PopupWindow show for sheet entry");
    }

    static Class<?> tryLoad(ClassLoader cl, String name) {
        try {
            return Class.forName(name, false, cl);
        } catch (Throwable t) {
            return null;
        }
    }

    static Executable none() {
        return null;
    }
}
