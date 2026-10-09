package com.wodi.redguotools;

import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * 宿主自带的「定时关闭」到点后，<b>直接把屏幕熄灭</b>。
 *
 * <h3>宿主原本的行为（7.3.9.32 反编译确证）</h3>
 * 短剧「更多」菜单与 feed 长按菜单里的「定时关闭」，走的是宿主同一套
 * {@code ScheduledStopPlayController}（混淆名 {@code yr4.i}）：
 * <pre>
 *   菜单点击 → d15.h（ScheduledStopPlaySelectedDialog，选时长）
 *            → 投递 (类型, 秒数) 到 yr4.i.g（PublishSubject）
 *            → yr4.e.invoke 启动 1 秒一次的 interval
 *            → yr4.g.invoke 每秒递减静态计数 yr4.i.d
 *            → 归零时：yr4.i.h.onNext(new yr4.l())   ← 「count down ! stop play」
 *            → 订阅者（如 r65.k）停播，并 decor.setKeepScreenOn(false)
 * </pre>
 * 也就是官方只做了「停播 + 放开屏幕常亮」，屏幕要等系统的息屏超时（实测
 * 小米 15 上是 60 秒；若开了「充电时保持唤醒」甚至永远不灭）才会黑下去。
 *
 * <h3>本模块的做法</h3>
 * {@code yr4.l} 是「倒计时归零、且没有被拦截器拦下」时**唯一**构造出来的标记对象
 * （全 APK 只有 {@code yr4.g} 里那一处 {@code new Lyr4/l;}），
 * 因此在它的构造函数上挂一个钩子就等于拿到「定时关闭生效」这个事件，
 * 且不会在每秒 tick 上产生任何开销。
 *
 * <p>命中后注入一次电源键（KEYCODE_POWER = 26）把屏幕灭掉。发键之前先查
 * {@link PowerManager#isInteractive()} —— 电源键是**切换**键，屏幕已经黑着时
 * 再发一次会把它点亮，必须先确认当前是亮屏。
 *
 * <h3>谁来执行熄屏</h3>
 * 灭屏只有三条路 —— {@code PowerManager.goToSleep()}（需 DEVICE_POWER 系统签名权限）、
 * {@code DevicePolicyManager.lockNow()}（需设备管理员）、注入电源键（需 INJECT_EVENTS）。
 * 三者普通应用都拿不到，只能用 {@code su}；而 root 授权是**按调用方 uid** 判的。
 *
 * <p>于是设计成两级：
 * <ol>
 *   <li>宿主自己先 su 一次（宿主恰好被授权就省掉下一步）；
 *   <li>失败则把请求写进 {@code files/rg_guard/} 下的触发文件，
 *       由**ReadGuo 自己的 root 守护**（{@code /data/adb/service.d/rg_screenoff.sh}）
 *       用 inotify 收到后注入电源键 —— 这样 root 只需要给 ReadGuo，
 *       红果保持无 root。</li>
 * </ol>
 *
 * <p><b>为什么不直接让宿主叫 ReadGuo</b>（2026-10-02 实测，四条通道全废）：
 * Android 11+ 的**包可见性**会让宿主看不见 ReadGuo ——
 * {@code ContentResolver.call} 报 {@code Unknown authority}、{@code bindService} 返回 false、
 * 显式广播投递不到、显式 {@code startActivity} 静默不起来。宿主清单里既没有指向
 * ReadGuo 的 {@code <queries>} 也没有 {@code QUERY_ALL_PACKAGES}，而宿主 APK 改不了。
 * 让 ReadGuo 去匹配宿主 {@code <queries>} 里已有的 {@code VIEW+BROWSABLE+https}
 * 签名做「对等暴露」也无效。文件触发因此成了唯一不需要改动系统的通道。
 */
final class SleepTimer {

    /** 倒计时归零标记类（构造一次 = 定时关闭生效一次）。 */
    private static final String[] EVENT_CLASSES = {
            "yr4.l",
    };

    /**
     * 触发目录（宿主私有目录下的子目录）。红果写、ReadGuo 的 root 守护读。
     *
     * <p>故意建在**应用私有目录**而不是外置存储：外置要过 FUSE、开机早期还可能没挂上，
     * 而且 root 本来就什么都能读。
     */
    private static final String GUARD_DIR = "rg_guard";

    private static volatile boolean sInit;
    private static volatile String sStatus;
    /** 去重：同一次归零只处理一遍（构造函数理论上只跑一次，保险起见）。 */
    private static volatile long sLastFireAt;

    private SleepTimer() {
    }

    /** 供 onActivityCreated 延迟落盘（hook 安装期 logFile 还不可用）。 */
    static String status() {
        return sStatus;
    }

    /**
     * 确保触发目录存在。
     *
     * <p>必须在**宿主一启动**就建好，不能等熄屏那一下才建：守护是用 inotify 盯着这个
     * 目录的，目录不存在时它无从监听（守护脚本里有个等待循环，最多 3 秒轮询一次）。
     * 目录必须由宿主自己创建 —— root 建出来会属于 root，红果反而写不进去。
     */
    static void ensureGuardDir(Context c) {
        if (c == null) {
            return;
        }
        try {
            File dir = guardDir(c);
            if (!dir.isDirectory()) {
                dir.mkdirs();
            }
        } catch (Throwable ignored) {
        }
    }

    private static File guardDir(Context c) {
        return new File(c.getFilesDir(), GUARD_DIR);
    }

    static void hook(XposedModule mod, ClassLoader cl) throws Throwable {
        if (sInit) {
            return;
        }
        Class<?> evt = null;
        String used = null;
        for (String cn : EVENT_CLASSES) {
            evt = RGModule.tryLoad(cl, cn);
            if (evt != null) {
                used = cn;
                break;
            }
        }
        if (evt == null) {
            sStatus = "FAILED: " + EVENT_CLASSES[0] + " not found";
            throw new ClassNotFoundException(EVENT_CLASSES[0]);
        }
        int n = 0;
        for (Constructor<?> c : evt.getDeclaredConstructors()) {
            if (c.getParameterCount() != 0) {
                continue;
            }
            c.setAccessible(true);
            mod.hook(c).setId("rgSleepTimer")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object r = chain.proceed();   // 构造函数照常执行
                        onFired();
                        return r;
                    });
            n++;
        }
        sInit = n > 0;
        sStatus = n > 0 ? ("ok " + used) : ("FAILED: no <init> in " + used);
    }

    /* ------------------------------------------------------------------ */

    /** 定时关闭到点：熄屏。跑在宿主 RxJava 的 mainThread 上，自身必须极轻。 */
    private static void onFired() {
        long now = SystemClock.uptimeMillis();
        if (now - sLastFireAt < 3000L) {
            return;   // 同一次归零只处理一遍
        }
        sLastFireAt = now;
        UiController.logFile("sleep-timer: 到点 -> 熄灭屏幕");
        if (!isScreenOn()) {
            UiController.logFile("sleep-timer: 屏幕已熄灭，跳过（电源键是切换键）");
            return;
        }
        // 起进程会阻塞，必须离开主线程
        new Thread(new Runnable() {
            @Override
            public void run() {
                pressPower();
            }
        }, "rgScreenOff").start();
    }

    /** 屏幕当前是否亮着（含 AOD 之外的所有「可交互」状态）。 */
    private static boolean isScreenOn() {
        try {
            Context c = UiController.appCtx();
            if (c == null) {
                return true;   // 取不到就按亮屏处理，宁可多发一次
            }
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            return pm == null || pm.isInteractive();
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * 熄屏。两级：
     * <ol>
     *   <li>宿主自己 su 一次 —— 宿主恰好被授予 root 时直接生效，省掉跨进程那一步；</li>
     *   <li>失败（多数情况：宿主没被授权，KernelSU 下 su 直接不可见）则把请求写进
     *       触发目录，交给 ReadGuo 的 root 守护执行。</li>
     * </ol>
     * 已经在后台线程（{@code rgScreenOff}）里跑，可以放心阻塞。
     */
    private static void pressPower() {
        RootShell.Result r = RootShell.run(RootShell.CMD_SCREEN_OFF);
        UiController.logFile("sleep-timer: 宿主直接执行 -> " + r.describe());
        if (r.ok()) {
            return;
        }
        requestGuard();
    }

    /**
     * 把熄屏请求交给 ReadGuo 的 root 守护（见 assets/rg_guard/*.sh）。
     *
     * <p>为什么要绕这一下：Android 11+ 的**包可见性**让宿主看不见 ReadGuo，
     * Provider / bindService / 显式广播 / 显式 Activity 四条显式通道**全部被静默拦掉**，
     * 而宿主的清单里没有、也无法加上指向 ReadGuo 的 {@code <queries>}。
     * 唯一不需要改动系统、也不需要给宿主 root 的通道，就是「宿主写自己目录里的文件、
     * 由那边的 root 守护盯着」——两边都不需要知道对方的存在。
     *
     * <p>文件名带序号：每次都新建一个，保证 inotify 一定收到「创建」事件；
     * 守护处理完会把它删掉。
     */
    private static void requestGuard() {
        Context c = UiController.appCtx();
        if (c == null) {
            UiController.logFile("sleep-timer: 拿不到 Context，无法投递熄屏请求");
            return;
        }
        try {
            File dir = guardDir(c);
            if (!dir.isDirectory() && !dir.mkdirs()) {
                UiController.logFile("sleep-timer: 触发目录创建失败 " + dir);
                return;
            }
            File f = new File(dir, "off." + SystemClock.uptimeMillis() + ".req");
            FileOutputStream fos = new FileOutputStream(f);
            fos.write("off".getBytes("UTF-8"));
            fos.close();
            UiController.logFile("sleep-timer: 已投递熄屏请求 -> rg_guard/" + f.getName()
                    + "（由 ReadGuo 的 root 守护执行）");
        } catch (Throwable t) {
            UiController.logFile("sleep-timer: 投递熄屏请求失败 " + t
                    + "（ReadGuo 守护装了没？见 README「定时关闭 → 直接关屏」）");
        }
    }
}
