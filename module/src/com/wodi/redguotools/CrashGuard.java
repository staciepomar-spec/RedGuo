package com.wodi.redguotools;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 崩溃取证。
 *
 * <h3>为什么需要它</h3>
 * 已经收到过「点了开关就闪退」的反馈，但拿不到任何日志：HyperOS 上 logcat 被屏蔽，
 * 而宿主自己的崩溃记录用户也不会去导。少了堆栈就只能猜，猜错一次就要用户再崩一次。
 * <p>
 * 这里在宿主进程里挂一个 {@link Thread.UncaughtExceptionHandler}：
 * <b>先把完整堆栈写进模块自己的日志文件，再原样交给宿主原本的 handler</b>。
 * 也就是说 —— 宿主该崩还是崩（我们绝不吞异常，那只会把崩溃变成更诡异的行为），
 * 但下一次用户只要把日志发过来，就能直接看到崩在哪一行。
 *
 * <h3>顺带记录环境</h3>
 * 每次冷启动写一条「会话头」：框架名 / 框架版本 / Xposed API 版本 / 宿主包名。
 * 闪退问题十有八九和「哪个框架、哪个版本、哪个渠道」有关，
 * 没有这行就得反过来问用户，效率极低。
 */
final class CrashGuard {

    private static final String CRASH_FILE = "rgtools_crash.txt";
    private static volatile boolean sInstalled;

    private CrashGuard() {
    }

    /** 宿主 Application 创建后立刻装一次（越早越好，开屏阶段的崩溃也要能兜住）。 */
    static void install(final Context app) {
        if (sInstalled || app == null) {
            return;
        }
        synchronized (CrashGuard.class) {
            if (sInstalled) {
                return;
            }
            try {
                final Thread.UncaughtExceptionHandler prev =
                        Thread.getDefaultUncaughtExceptionHandler();
                Thread.setDefaultUncaughtExceptionHandler(
                        new Thread.UncaughtExceptionHandler() {
                            @Override
                            public void uncaughtException(Thread t, Throwable e) {
                                try {
                                    dump(app, t, e);
                                } catch (Throwable ignored) {
                                    // 取证失败绝不能影响宿主原有的崩溃流程
                                }
                                if (prev != null) {
                                    prev.uncaughtException(t, e);
                                }
                            }
                        });
                sInstalled = true;
                UiController.logFile("crash-guard: 已启用"
                        + "  framework=" + Hosts.frameworkName()
                        + " " + Hosts.frameworkVersion()
                        + " api=" + Hosts.apiVersion()
                        + " pkg=" + app.getPackageName());
            } catch (Throwable ignored) {
            }
        }
    }

    private static void dump(Context app, Thread t, Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        append(app, header(app, "thread=" + t.getName()) + sw.toString() + "\n");
        UiController.logFile("crash-guard: 捕获崩溃 -> " + CRASH_FILE
                + " (" + e.getClass().getName() + ": " + e.getMessage() + ")");
    }

    /**
     * 记录一个**已被捕获、未致命**的异常。
     *
     * <p>护栏接住的异常不会让进程死掉，但对用户来说同样是「功能没反应」，
     * 而且不留痕就等于没有反馈入口 —— 所以照样落一份，带上出错位置。
     */
    static void record(String where, Throwable e) {
        try {
            Context app = UiController.appCtx();
            if (app == null || e == null) {
                return;
            }
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            append(app, header(app, where) + sw.toString() + "\n");
        } catch (Throwable ignored) {
        }
    }

    /** 环境快照：闪退十有八九与「哪个框架 / 哪个版本 / 哪个渠道」有关。 */
    private static String header(Context app, String where) {
        String ver = "?";
        try {
            ver = app.getPackageManager()
                    .getPackageInfo(app.getPackageName(), 0).versionName;
        } catch (Throwable ignored) {
        }
        return System.currentTimeMillis()
                + " ==== " + where + " ==== pkg=" + app.getPackageName()
                + " ver=" + ver
                + " framework=" + Hosts.frameworkName()
                + " " + Hosts.frameworkVersion()
                + " api=" + Hosts.apiVersion()
                + "\n";
    }

    /** 崩溃单独写一份文件：日志主文件会被轮换/截断，崩溃这份必须留得住。 */
    private static void append(Context app, String body) {
        try {
            File dir = app.getExternalFilesDir(null);
            if (dir == null) {
                return;
            }
            File f = new File(dir, CRASH_FILE);
            if (f.exists() && f.length() > 256 * 1024) {
                f.delete();
            }
            FileOutputStream fos = new FileOutputStream(f, true);
            fos.write(body.getBytes("UTF-8"));
            fos.close();
        } catch (Throwable ignored) {
        }
    }
}