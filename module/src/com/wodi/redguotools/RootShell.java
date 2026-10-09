package com.wodi.redguotools;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * 用 root 跑一次性命令的公共实现（目前只有 {@link SleepTimer} 的熄屏在用）。
 *
 * <p>之所以单独抽出来：以后要加别的 root 动作（改系统设置、注入按键）可以直接复用，
 * 里面已经处理好了「多个 su 候选 + 每个候选各自的失败原因 + 超时」这些脏活。
 *
 * <p>本类**不引用任何 Xposed / UiController 的符号** —— 它跑在宿主的 hook 回调里，
 * 保持零依赖便于单测与搬用。
 *
 * <h3>KernelSU 的坑（实测 v3.2.5）</h3>
 * KernelSU **只对白名单内的 uid 暴露 `su`**：以未授权的应用 uid 看，
 * {@code /system/bin/su} 连 {@code ls} 都是 No such file or directory，
 * 真去 exec 会直接抛 {@code IOException: ... error=2}（ENOENT）。
 * 所以「所有候选都起不来」几乎总是「这个 App 还没被授予 root」，不是路径写错 ——
 * 候选表里每个失败原因都会记下来，方便一眼分辨。
 */
public final class RootShell {

    /** 熄屏命令。`input` 是 shell 脚本，显式拉 sh 跑，避免依赖 root shell 的 PATH。 */
    static final String CMD_SCREEN_OFF = "sh /system/bin/input keyevent 26";

    /** su 授权弹框若挂着不返回，超时后放弃（不能把线程吊死）。 */
    static final long TIMEOUT_MS = 15000L;

    /** 一次执行的完整结果（含「进程根本没起来」的原因），全部可落盘。 */
    public static final class Result {
        public boolean started;        // 至少有一个候选成功 spawn
        public int rc = -1;
        public String out = "";
        public String err = "";
        public String spawnFail = "";  // 所有候选都起不来时，逐个候选的失败原因
        public String used = "";       // 实际用上的那个候选

        public boolean ok() {
            return started && rc == 0;
        }

        /** 单行摘要，直接进日志。 */
        public String describe() {
            if (!started) {
                return "无法启动 su（" + spawnFail + "）";
            }
            StringBuilder sb = new StringBuilder("via ").append(used).append(" rc=").append(rc);
            if (!out.isEmpty()) {
                sb.append(" out=").append(out);
            }
            if (!err.isEmpty()) {
                sb.append(" err=").append(err);
            }
            return sb.toString();
        }
    }

    private RootShell() {
    }

    /**
     * 依次尝试各候选启动方式；第一个**能起进程**的就用它（不再继续尝试）。
     * 之所以不是「第一个成功执行的」：某个候选可能起得来但命令失败
     * （例如 sh 找不到 su 会以 127 退出），那种情况要由调用方决定下一步，
     * 而不是在这里无限换候选。
     */
    public static Result run(String cmd) {
        return run(cmd, 1024);
    }

    /**
     * @param maxChars 单个流最多取多少字符。日志正文要读几万字，必须放大；
     *                 熄屏那种只看一行结果的用默认 1024 就够。
     */
    public static Result run(String cmd, int maxChars) {
        Result r = new Result();
        Process p = null;
        for (String[] argv : launchArgvs(cmd)) {
            try {
                p = Runtime.getRuntime().exec(argv);
                r.started = true;
                r.used = argv[0];
                break;
            } catch (Throwable t) {
                r.spawnFail += (r.spawnFail.isEmpty() ? "" : " | ") + argv[0] + " -> " + brief(t);
            }
        }
        if (p == null) {
            return r;
        }
        try {
            if (!p.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                r.err = "timeout " + TIMEOUT_MS + "ms（可能在等 su 授权弹框）";
                return r;
            }
            r.rc = p.exitValue();
            r.out = drain(p.getInputStream());
            r.err = drain(p.getErrorStream());
        } catch (Throwable t) {
            r.err = String.valueOf(t);
        } finally {
            try {
                p.destroy();
            } catch (Throwable ignored) {
            }
        }
        return r;
    }

    /**
     * 候选启动方式，按顺序试：先直接 `su -c`（进程 PATH 里通常有 /system/bin），
     * 再试几个绝对路径，最后交给 `sh -c` 自己去找（Android 的 mksh 在 PATH
     * 缺失时会退回一套默认 PATH，比 Java 侧 ProcessBuilder 的查找更靠谱）。
     */
    private static String[][] launchArgvs(String cmd) {
        return new String[][]{
                {"su", "-c", cmd},
                {"/system/bin/su", "-c", cmd},
                {"/system/xbin/su", "-c", cmd},
                {"/sbin/su", "-c", cmd},
                {"/debug_ramdisk/su", "-c", cmd},
                {"/system/bin/sh", "-c", "su -c '" + cmd + "'"},
                {"/system/xbin/sh", "-c", "su -c '" + cmd + "'"},
                {"sh", "-c", "su -c '" + cmd + "'"},
        };
    }

    /** 只留异常里最有信息量的那截（`error=2, No such file...`），否则日志会被包名刷爆。 */
    private static String brief(Throwable t) {
        String m = t.getMessage();
        if (m == null) {
            return t.getClass().getSimpleName();
        }
        int i = m.indexOf("error=");
        if (i >= 0) {
            return m.substring(i);
        }
        return m.length() > 48 ? m.substring(m.length() - 48) : m;
    }

    /** 读干净子进程输出（量很小，读完整串即可）。 */
    private static String drain(InputStream in) {
        if (in == null) {
            return "";
        }
        try {
            byte[] buf = new byte[512];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = in.read(buf)) > 0 && sb.length() < 1024) {
                sb.append(new String(buf, 0, n, "UTF-8"));
            }
            return sb.toString().trim();
        } catch (Throwable t) {
            return "";
        } finally {
            try {
                in.close();
            } catch (Throwable ignored) {
            }
        }
    }
}
