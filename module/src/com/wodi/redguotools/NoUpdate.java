package com.wodi.redguotools;

import android.content.Context;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * 屏蔽「发现新版本」升级弹窗。
 *
 * <h3>挂载点（红果 7.3.9.32 反编译确证）</h3>
 * {@code com.dragon.read.component.biz.impl.update.UpdateManager.h()}
 * —— 该方法是升级弹窗的**唯一创建/弹出入口**：
 * <pre>
 *   h()  →  PopProxy.popup(activity, popType, runnable, listener)
 * </pre>
 * 三条 UI 通路最终都汇聚到这里：
 * <ul>
 *   <li>{@code UpdateManager$c}（Pop 处理器，注册在 official_upgrade_dialog / gray_upgrade_dialog）</li>
 *   <li>{@code UpdateManager$a}（升级状态回调）</li>
 *   <li>{@code NsUpdateServiceImpl}（升级服务）</li>
 * </ul>
 *
 * <p>四处调用点（含 {@code UpdateManager} 自身）都**不读取返回值**：
 * 三处直接 {@code return-void}，一处只把它包进 {@code WeakReference}
 * （`WeakReference(null)` 合法）。所以直接返回 {@code null} 并跳过原方法调用是安全的。
 *
 * <h3>为什么不用宿主自带的开关</h3>
 * {@code app_update_config_v733} 里的 {@code enableOfficialUpgrade==false} 确实会
 * 让 {@code h()} 提前返回（日志：「成本优化配置-关闭正式版的主动升级能力」），
 * 但那个判据**只在「当前是非正式版本」分支里才被读到**（`versionCode % 100` 落在
 * 32~63 之间才进那个分支）。改装版 / 非官方包会绕过它，拦截不彻底。
 * 直接拦 {@code h()} 则无此缺口。
 */
final class NoUpdate {

    private static volatile boolean sInit;
    private static volatile String sStatus;

    /** 供 onActivityCreated 延迟落盘（hook 安装期 logFile 还不可用）。 */
    static String status() {
        return sStatus;
    }

    static void hook(XposedModule mod, ClassLoader cl) throws Throwable {
        if (sInit) {
            return;
        }
        try {
            Class<?> um = Class.forName(
                    "com.dragon.read.component.biz.impl.update.UpdateManager", false, cl);
            Method h = um.getDeclaredMethod("h");
            h.setAccessible(true);
            mod.hook(h).setId("rgNoUpdate")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Context c = UiController.appCtx();
                        if (c != null && Config.blockUpdate(c)) {
                            UiController.logFile("update: popup blocked");
                            return null;   // 不调原方法 = 不弹升级框
                        }
                        return chain.proceed();
                    });
            sInit = true;
            sStatus = "ok UpdateManager.h";
        } catch (Throwable t) {
            sStatus = "FAILED: " + t;
            throw t;
        }
    }
}
