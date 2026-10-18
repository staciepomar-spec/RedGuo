package com.wodi.redguotools;

import android.app.Application;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * 禁用热更新（字节 Reparo 框架）。
 *
 * <h3>宿主实现（7.3.9.32 反编译确证）</h3>
 * <pre>
 *   com.dragon.read.base.hotfix.ReparoHotFixInitServiceImpl   (classes25.dex)
 *     public void initialize(Application)     ← 服务初始化入口
 *          └─ private static void doInit(Application)
 *                 com.bytedance.reparo.g.a().b(config)   ← SDK 初始化，会加载本地已下发的补丁
 *          └─ 主进程：订阅 device id 后立刻 loadRemotePatch()
 *     public void loadRemotePatch()
 *          └─ com.bytedance.reparo.g.a().e()             ← 请求/下载远端补丁
 * </pre>
 *
 * <p>两者的作用面不同，必须一起拦：
 * <ul>
 *   <li>只拦 {@code loadRemotePatch} → 不再下载新补丁，但<b>此前已落盘的补丁
 *       仍会在每次启动时被 Reparo 加载</b>，「禁用」是不彻底的；</li>
 *   <li>只拦 {@code doInit} → 不加载旧补丁，但后续订阅回调仍会调
 *       {@code loadRemotePatch}，此时 Reparo 未初始化，反而可能 NPE。</li>
 * </ul>
 * 因此 {@code doInit} 直接短路（返回、不执行原方法），
 * {@code loadRemotePatch} 也短路为 no-op。
 *
 * <p>兼容性：Google Play 版（{@code com.phoenix.read.oversea.gp}）**没有这个类**
 * （Play 政策禁止热更新，dex 里整包缺失），此时本功能自动跳过，
 * status 记为 {@code n/a}，不影响其余 hook。
 */
final class HotfixBlock {

    private static final String CLS = "com.dragon.read.base.hotfix.ReparoHotFixInitServiceImpl";

    private static volatile boolean sInit;
    private static volatile String sStatus;
    private static volatile boolean sBlockedOnce;

    private HotfixBlock() {
    }

    static String status() {
        return sStatus;
    }

    static void hook(XposedModule mod, ClassLoader cl) {
        if (sInit) {
            return;
        }
        Class<?> cls = RGModule.tryLoad(cl, CLS);
        if (cls == null) {
            sStatus = "n/a (no Reparo in this build)";
            sInit = true;
            return;
        }
        int n = 0;
        try {
            Method doInit = cls.getDeclaredMethod("doInit", Application.class);
            doInit.setAccessible(true);
            mod.hook(doInit)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        if (!sBlockedOnce) {
                            sBlockedOnce = true;
                            UiController.logFile("hotfix: blocked doInit (Reparo not initialized)");
                        }
                        return null;   // 不调用原方法
                    });
            n++;
        } catch (Throwable t) {
            UiController.logFile("hotfix: doInit hook failed " + t);
        }
        try {
            Method load = cls.getDeclaredMethod("loadRemotePatch");
            load.setAccessible(true);
            mod.hook(load)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        UiController.logFile("hotfix: blocked loadRemotePatch");
                        return null;
                    });
            n++;
        } catch (Throwable t) {
            UiController.logFile("hotfix: loadRemotePatch hook failed " + t);
        }
        sInit = true;
        sStatus = n == 2 ? "ok" : ("partial (" + n + "/2)");
    }
}
