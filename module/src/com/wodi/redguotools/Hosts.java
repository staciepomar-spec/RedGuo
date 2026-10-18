package com.wodi.redguotools;

/**
 * 宿主包名清单。
 *
 * <p><b>为什么单独一个类</b>：这些常量既要给宿主进程里的 hook 用，也要给
 * **模块自己的 App** 用（设置页显示版本、重启按钮、日志目录）。而
 * {@link RGModule} 继承自 {@code io.github.libxposed.api.XposedModule}，
 * 这个类只在 LSPosed 注入过的进程里存在 —— 在模块 App 进程里
 * 一引用 {@code RGModule.HOST_PKGS} 就会触发 RGModule 类加载，
 * 抛 {@code NoClassDefFoundError: Lio/github/libxposed/api/XposedModule;}
 * 直接把 App 崩掉。所以纯数据放这里，两边都能安全读。
 */
final class Hosts {

    /**
     * 同一套代码的三个分发渠道，业务类名完全一致，只有混淆短名不同：
     * <ul>
     *   <li>{@code com.phoenix.read} —— 第三方改版（当前主力测试机型）</li>
     *   <li>{@code com.phoenix.read.oversea.gp} —— Google Play 版（同 7.3.9.32，
     *       少 2 个 dex，无 Reparo 热更新）</li>
     *   <li>{@code com.dragon.read} —— 官方原版</li>
     * </ul>
     */
    static final String[] PKGS = {
            "com.phoenix.read",
            "com.phoenix.read.oversea.gp",
            "com.dragon.read",
    };

    /** 默认（主测试）宿主包名。 */
    static final String DEFAULT = PKGS[0];

    static boolean isHost(String pkg) {
        if (pkg == null) {
            return false;
        }
        for (String p : PKGS) {
            if (p.equals(pkg)) {
                return true;
            }
        }
        return false;
    }

    /* ---------------- 运行环境快照（崩溃取证用） ---------------- */
    /*
     * 同样不能放 RGModule：CrashGuard 要在**模块 App 进程**里也能跑（App 自己也会闪退），
     * 而那里拿不到 libxposed。所以框架信息由 RGModule 在宿主进程里写入这里，
     * 两个进程都只读这个纯数据类。
     */

    private static volatile String fwName = "?";
    private static volatile String fwVer = "?";
    private static volatile int apiVer = -1;
    private static volatile String curPkg;

    static void setFramework(String name, String ver, int api) {
        fwName = name == null ? "?" : name;
        fwVer = ver == null ? "?" : ver;
        apiVer = api;
    }

    static void setCurPkg(String pkg) {
        curPkg = pkg;
    }

    static String frameworkName() {
        return fwName;
    }

    static String frameworkVersion() {
        return fwVer;
    }

    static int apiVersion() {
        return apiVer;
    }

    /** 当前进程对应的宿主包名；不在宿主进程里时为 null（App 进程内即 null）。 */
    static String modifiedBy() {
        return curPkg == null ? "?" : curPkg;
    }

    private Hosts() {
    }
}
