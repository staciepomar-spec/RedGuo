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

    /**
     * 宿主包名（同一套代码的三个分发渠道）。数据本体在 {@link Hosts} ——
     * 那个类不依赖 libxposed，模块 App 进程也能安全读。
     */
    public static final String[] HOST_PKGS = Hosts.PKGS;

    /** 兼容旧引用：默认（主测试）宿主包名。需要「当前进程是哪个渠道」时用 {@link #curPkg()}。 */
    public static final String HOST_PKG = Hosts.DEFAULT;

    private static volatile String sCurPkg;

    /** 当前进程对应的宿主包名；未在宿主进程时为 null。 */
    public static String curPkg() {
        return sCurPkg;
    }

    public static boolean isHostPkg(String pkg) {
        return Hosts.isHost(pkg);
    }

    /** 短剧全屏播放页里承载 onDoubleTap 的手势监听类（多版本候选，存在即挂）。 */
    private static final String[] DOUBLE_TAP_CLASSES = {
            // 红果 7.3.7.32
            "com.dragon.read.component.shortvideo.impl.fullscreen.i$d",
            // 旧版本（7.3.2.x / 7.3.3.x）
            "com.dragon.read.component.shortvideo.impl.fullscreen.d$d",
            "com.dragon.read.component.shortvideo.impl.fullscreen.f$d",
            // 7.3.9.32：feed 双击点赞走社交层手势（实测屏蔽 i$d 后仍会点赞）
            "com.dragon.read.social.ui.t",
            // 7.3.9.32 短剧 v2 竖滑适配器的双击监听（feed 与二级页共用，
            // dt2 诊断日志确认真实触发，屏蔽后双击点赞消失）
            // 改版 com.phoenix.read → r65.o0；Play 版同结构类 → o15.o0
            "r65.o0",
            "o15.o0",
    };

    private ClassLoader appLoader;

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);
        String pkg = param.getPackageName();
        appLoader = param.getClassLoader();
        if (!isHostPkg(pkg)) {
            return;
        }
        sCurPkg = pkg;
        Hosts.setCurPkg(pkg);
        cacheFrameworkInfo();
        log(Log.INFO, TAG, "onPackageReady pkg=" + pkg);
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
            SleepTimer.hook(this, appLoader);
            log(Log.INFO, TAG, "hooked sleep timer -> screen off");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hookSleepTimer failed", t);
        }
        try {
            hookViewAttach();
            log(Log.INFO, TAG, "hooked view-attach");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hookViewAttach failed", t);
        }
        try {
            hookAppContext(appLoader);
            log(Log.INFO, TAG, "hooked app context");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hookAppContext failed", t);
        }
        try {
            AdBlock.hook(this, appLoader);
            log(Log.INFO, TAG, "ad block: " + AdBlock.status());
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hookAdBlock failed", t);
        }
        try {
            DarkMode.hook(this, appLoader);
            log(Log.INFO, TAG, "dark mode: " + DarkMode.status());
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hookDarkMode failed", t);
        }
        try {
            HotfixBlock.hook(this, appLoader);
            log(Log.INFO, TAG, "hotfix block: " + HotfixBlock.status());
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hookHotfix failed", t);
        }
        try {
            Danmaku.init(appLoader);
            log(Log.INFO, TAG, "danmaku: " + Danmaku.status());
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "initDanmaku failed", t);
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
    /**
     * 尽早拿到宿主 Application：开屏广告的判定发生在 SplashActivity.onCreate 里，
     * 比 {@link UiController#onActivityCreated}（onCreate 返回后）更早。
     * {@code Instrumentation.callApplicationOnCreate} 是 Application 创建的唯一入口，
     * 无论子类是否覆写 onCreate 都会走到。
     */
    private void hookAppContext(ClassLoader cl) throws Throwable {
        Method m = android.app.Instrumentation.class.getDeclaredMethod(
                "callApplicationOnCreate", Application.class);
        m.setAccessible(true);
        hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    try {
                        Application app = (Application) chain.getArg(0);
                        UiController.setAppCtx(app);
                        // 取证要尽早：开屏阶段的崩溃也指望它留记录
                        CrashGuard.install(app);
                    } catch (Throwable ignored) {
                    }
                    return chain.proceed();
                });
    }

    /* ---------------- 框架信息（给崩溃日志用的环境快照） ---------------- */

    private void cacheFrameworkInfo() {
        try {
            Hosts.setFramework(getFrameworkName(), getFrameworkVersion(), getApiVersion());
        } catch (Throwable ignored) {
        }
    }

    private void hookViewAttach() throws Throwable {
        Method m = View.class.getDeclaredMethod("onAttachedToWindow");
        m.setAccessible(true);
        hook(m)
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
        hook(onCreate).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Activity act = (Activity) chain.getThisObject();
                    // DPI 必须赶在 inflate 之前改写：已测量的 View 不会重新排版。
                    // 放在 proceed() 前，此时 Resources 已 attach、setContentView 尚未调用。
                    try {
                        if (isHostPkg(act.getPackageName())) {
                            DpiTweak.applyTo(act);
                        }
                    } catch (Throwable ignored) {
                    }
                    Object r = chain.proceed();
                    try {
                        UiController.onActivityCreated((Activity) chain.getThisObject());
                    } catch (Throwable ignored) {
                    }
                    return r;
                });

        Method onResume = Activity.class.getDeclaredMethod("onResume");
        hook(onResume).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object r = chain.proceed();
                    try {
                        UiController.onActivityResumed((Activity) chain.getThisObject());
                    } catch (Throwable ignored) {
                    }
                    return r;
                });

        Method dispatch = Activity.class.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class);
        hook(dispatch).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
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
        hook(onStop).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
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
                    hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
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
                hook(m)
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
                    hook(m)
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
        hook(show).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
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
                hook(m)
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
