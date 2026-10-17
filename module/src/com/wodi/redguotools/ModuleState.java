package com.wodi.redguotools;

import android.content.Context;

import java.util.Locale;

/**
 * 模块是否真的在宿主里跑着 —— **靠证据说话，不靠猜**。
 *
 * <h3>为什么需要它</h3>
 * 模块 App 进程和红果是两个 uid、两个私有目录，App 这边<b>看不到红果加载了哪些类</b>。
 * 以前主页直接写死一行「模块已激活」，在一台既没 root 也没 LSPosed 的机器上
 * 同样显示「已激活」—— 那是在说假话，用户根本没法判断功能到底有没有生效。
 *
 * <h3>判定依据</h3>
 * 唯一可靠的可观测信号是<b>红果自己的运行日志</b>：
 * 模块被真正加载时，会往红果的外置私有目录写 {@code rgtools_log.txt}，
 * 里面有 {@code dark install: ok} / {@code player install: ok} 这类安装记录。
 *
 * <ul>
 *   <li><b>有 root</b> → 直接读那个文件，用日志时间戳算新鲜度。</li>
 *   <li><b>没 root</b> → 读不到，如实说「无法检测」，并告诉用户去哪确认。
 *       绝不因为读不到就报「已激活」。</li>
 * </ul>
 *
 * <p>阈值取 30 分钟：宿主进程一般不会长期挂着，超过这个时间没写日志，
 * 就认为「当前没有在运行」，比一直显示已激活诚实得多。
 */
final class ModuleState {

    static final int RUNNING = 0;   // 日志新鲜，模块确实在宿主里
    static final int IDLE = 1;      // 读得到日志但已过期
    static final int NONE = 2;      // 从来没读到过（日志不存在）
    static final int UNKNOWN = 3;   // 没有 root，读不到，无法判断

    /** 日志新鲜阈值（毫秒）。 */
    private static final long FRESH_MS = 30 * 60 * 1000L;

    private ModuleState() {
    }

    /** 检测结果：状态 + 一句可读的依据。 */
    static final class Result {
        final int state;
        final String detail;

        Result(int state, String detail) {
            this.state = state;
            this.detail = detail;
        }
    }

    static Result detect(Context c) {
        long age = logAgeMs(c);
        if (age < 0) {
            // 读不到：区分「真没有日志」和「没权限读」需要 root 探测
            return hasRoot(c)
                    ? new Result(NONE, "红果里没有本模块的运行记录")
                    : new Result(UNKNOWN, "当前设备没有 root，读不到红果的运行日志");
        }
        if (age <= FRESH_MS) {
            return new Result(RUNNING, String.format(Locale.getDefault(),
                    "%d 分钟内有活动", Math.max(1, age / 60000L)));
        }
        return new Result(IDLE, String.format(Locale.getDefault(),
                "上次活动在 %d 小时前，红果当前没在运行", Math.max(1, age / 3600000L)));
    }

    /** 红果日志文件距今多少毫秒；-1 表示读不到。 */
    private static long logAgeMs(Context c) {
        String dir = MainActivity.hostLogDir(c);
        RootShell.Result r = RootShell.run("stat -c %Y '" + dir + "/rgtools_log.txt' 2>/dev/null");
        if (r == null || !r.ok() || r.out == null) {
            return -1;
        }
        String s = r.out.trim();
        if (s.isEmpty()) {
            return -1;
        }
        try {
            long mtime = Long.parseLong(s.split("\\s+")[0]);
            return System.currentTimeMillis() - mtime * 1000L;
        } catch (Throwable t) {
            return -1;
        }
    }

    private static boolean hasRoot(Context c) {
        RootShell.Result r = RootShell.run("id -u");
        return r != null && r.ok() && r.out != null && r.out.trim().startsWith("0");
    }
}