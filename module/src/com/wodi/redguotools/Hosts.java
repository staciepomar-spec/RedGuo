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

    private Hosts() {
    }
}
