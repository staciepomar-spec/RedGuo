package com.wodi.redguotools;

import android.content.Context;
import android.content.SharedPreferences;

import java.lang.reflect.Method;

/**
 * 「强制显示弹幕」。
 *
 * <h3>宿主的判据（7.3.9.32 反编译确证）</h3>
 * <pre>
 *   com.dragon.read.video.danmaku.i        （源文件 VideoDanmakuConfigHelper.kt）
 *     public static boolean h()            日志串 "isDanmakuEnableByUser: ..."  ← 用户自己的开关
 *     public static boolean i()            日志串 "isVideoDanmakuEnable abValue = ..." ← AB 总闸
 *
 *   两者的存储都落在同一个 SP：video_danmaku_switch_sp
 *     h(): key_enable_danmaku_by_user  (boolean)
 *          该键不存在 → 回落到 libra 配置 VideoDanmakuLibraConfig.defaultSwitchOpen
 *                       再回落服务端下发的 default_switch
 *     i(): key_enable_danmaku          (String，AB 值，再经 b(String) 解析成布尔)
 * </pre>
 *
 * <h3>为什么写 SP 而不是 hook 那个方法</h3>
 * 判据方法所在的类名是<b>混淆短名</b>（本版叫 {@code i}），每换一个版本就换成别的字母，
 * hook 它等于每次发版都要重找一遍 —— 本模块其它地方已经吃过这个亏。
 * <p>
 * 而 {@code KvCacheMgr} 与 SP 文件名 {@code "video_danmaku_switch_sp"} 都是
 * <b>稳定标识</b>：前者是未被混淆的类名，后者是落盘的键名（改名就等于清空用户设置，
 * 宿主自己不会改）。本模块跑在宿主进程里，能直接拿到宿主的 Context，
 * 于是用宿主自己的 {@code KvCacheMgr.getPrivate()} 取到<b>同一个</b> SharedPreferences
 * 实例写进去 —— 格式、加密、缓存全部由宿主自己保证，不会有对不上的风险。
 *
 * <h3>为什么这样能覆盖「有些设备不生效」</h3>
 * 用户反馈的「有的设备弹幕出不来」多半不是用户开关被关了，而是 {@code h()} 第一步
 * {@code contains()} 为 false 时落到了<b>服务端下发的默认值</b>上，而那个默认值按
 * 设备/账号下发、并不统一。把两个键都写成开启，两条回落路径就都用不上了。
 *
 * <h3>关闭时还原（有一处实测限制）</h3>
 * 强制打开前先把两个键的存在性与原值记到 {@link Config#danmakuBak}，关掉开关时按记录写回。
 *
 * <p><b>但「原本不存在」的键删不掉</b>（2026-10-18 真机实测）：
 * {@code KvCacheMgr.getPrivate()} 返回的是宿主 {@code NsBaseLocalCacheDepend} 的
 * MMKV 封装（落盘在 {@code files/mmkv/prefix_private_*}，不是 shared_prefs 的 xml），
 * 它的 {@code Editor.remove()} 是<b>空操作</b> —— 强开后关掉，重启红果再查，
 * {@code key_enable_danmaku_by_user} 仍在。
 * <p>
 * 所以准确的说法是：
 * <ul>
 *   <li>键<b>原本就存在</b>（用户自己拨过弹幕开关）→ 按记录写回原值，✅ 完全还原；</li>
 *   <li>键<b>原本不存在</b>（正是反馈里"没弹幕"的那些设备）→ 删不掉，会保持开启。
 *       此时红果自己的弹幕开关会显示为「开」，用户在那里关掉即可（会写入 false）。</li>
 * </ul>
 * 要做到真正可逆只能改用 hook {@code i#h()} 直接返回 true，但那个类名/方法名都是混淆短名、
 * 每版会变 —— 与本类「用稳定标识而非混淆名」的取舍相反，故暂未采用。
 */
final class Danmaku {

    /** 取 SP 的入口（类名未混淆，跨版本稳定）。 */
    private static final String CLS_KV = "com.dragon.read.local.KvCacheMgr";
    /** 宿主弹幕开关的 SP 文件名（落盘键名，跨版本稳定）。 */
    private static final String SP_NAME = "video_danmaku_switch_sp";
    /** h()：用户是否开启了弹幕。 */
    private static final String K_USER = "key_enable_danmaku_by_user";
    /** i()：AB 总闸。 */
    private static final String K_AB = "key_enable_danmaku";

    private static volatile Method sGetPrivate;
    private static volatile boolean sResolved;
    private static volatile String sStatus = "未初始化";
    /** 上次写入的值，避免每次 resume 都无谓地 commit。 */
    private static volatile boolean sApplied;

    private Danmaku() {
    }

    static String status() {
        return sStatus;
    }

    /**
     * 解析取 SP 的方法。放在宿主 ClassLoader 就绪之后调用。
     *
     * <p>失败只标记状态、不抛 —— 弹幕是附加功能，不该拖累其他 hook。
     */
    static void init(ClassLoader cl) {
        if (sResolved) {
            return;
        }
        sResolved = true;
        try {
            Class<?> kv = RGModule.tryLoad(cl, CLS_KV);
            if (kv == null) {
                sStatus = "FAILED: KvCacheMgr not found";
                return;
            }
            sGetPrivate = kv.getMethod("getPrivate", Context.class, String.class);
            sStatus = "ok";
        } catch (Throwable t) {
            sStatus = "FAILED: " + t;
        }
    }

    /**
     * 按配置把宿主弹幕开关拨到开启；关闭时还原。
     *
     * <p>幂等、可在任意线程调用（内部已加锁）。
     *
     * @return 是否真的动过宿主的 SP
     */
    static boolean apply(final Context c) {
        if (c == null) {
            return false;
        }
        if (sGetPrivate == null) {
            // 早退也要留痕：否则「开关打开了但没反应」和「没打开」长得一模一样，
            // 只能靠猜。这里只在每进程第一次早退时记一条，避免刷日志。
            if (!sMissLogged) {
                sMissLogged = true;
                sStatus = "FAILED: getPrivate 未解析 (" + sStatus + ")";
                UiController.logFile("danmaku: skip - " + sStatus);
            }
            return false;
        }
        try {
            Context app = c.getApplicationContext();
            SharedPreferences sp = (SharedPreferences) sGetPrivate.invoke(
                    null, app != null ? app : c, SP_NAME);
            if (sp == null) {
                if (!sMissLogged) {
                    sMissLogged = true;
                    UiController.logFile("danmaku: skip - getPrivate 返回 null");
                }
                return false;
            }
            synchronized (Danmaku.class) {
                if (!Config.danmakuForce(c)) {
                    boolean r = restore(sp, c);
                    if (!sApplied && !r) {
                        logOnce("danmaku: off (未开启强制)");
                    }
                    return r;
                }
                if (sApplied && sp.getBoolean(K_USER, false)
                        && "1".equals(sp.getString(K_AB, ""))) {
                    return false;   // 已经是目标状态，别反复 commit
                }
                SharedPreferences.Editor ed = sp.edit();
                if (Config.danmakuBak(c).isEmpty()) {
                    Config.setDanmakuBak(c, snapshot(sp));
                }
                ed.putBoolean(K_USER, true);
                ed.putString(K_AB, "1");
                if (!ed.commit()) {
                    sStatus = "写入失败";
                    UiController.logFile("danmaku: commit 返回 false");
                    return false;
                }
                sApplied = true;
                sAppliedLogged = false;
                sStatus = "已强制开启";
                UiController.logFile("danmaku: force on -> " + K_USER + "=true, "
                        + K_AB + "=1 (" + SP_NAME + ")");
                return true;
            }
        } catch (Throwable t) {
            sStatus = "FAILED: " + t;
            UiController.logFile("danmaku: force failed " + t);
            return false;
        }
    }

    private static volatile boolean sMissLogged;
    private static volatile boolean sAppliedLogged;

    private static void logOnce(String msg) {
        if (!sAppliedLogged) {
            sAppliedLogged = true;
            UiController.logFile(msg);
        }
    }

    /**
     * 关闭开关：按当初记下的原值还原。
     *
     * <p>注意 {@code remove()} 在宿主的 MMKV 封装上是空操作（见类注释），
     * 所以「原本不存在」的键实际删不掉，这里如实记进日志，不假装成功。
     */
    private static boolean restore(SharedPreferences sp, Context c) {
        String bak = Config.danmakuBak(c);
        if (bak.isEmpty()) {
            sApplied = false;
            return false;
        }
        String[] p = bak.split("\\|", -1);
        if (p.length < 4) {
            Config.clearDanmakuBak(c);
            sApplied = false;
            return false;
        }
        try {
            SharedPreferences.Editor ed = sp.edit();
            if ("1".equals(p[0])) {
                ed.putBoolean(K_USER, "1".equals(p[1]));
            } else {
                ed.remove(K_USER);
            }
            if ("1".equals(p[2])) {
                // 原值理论上只有 "1" / "2" / ""，但它是宿主从云端原样存下来的，
                // 万一含分隔符，这里按「第 4 段起全部拼回」处理，别截断用户数据。
                StringBuilder sb = new StringBuilder(p[3]);
                for (int i = 4; i < p.length; i++) {
                    sb.append('|').append(p[i]);
                }
                ed.putString(K_AB, sb.toString());
            } else {
                ed.remove(K_AB);
            }
            ed.commit();
            Config.clearDanmakuBak(c);
            sApplied = false;
            sStatus = "已还原";
            UiController.logFile("danmaku: restored " + bak);
            return true;
        } catch (Throwable t) {
            UiController.logFile("danmaku: restore failed " + t);
            return false;
        }
    }

    /** 记下两个键的存在性与原值，供关闭时还原。 */
    private static String snapshot(SharedPreferences sp) {
        boolean hasUser = sp.contains(K_USER);
        boolean user = hasUser && sp.getBoolean(K_USER, false);
        boolean hasAb = sp.contains(K_AB);
        String ab = hasAb ? String.valueOf(sp.getString(K_AB, "")) : "";
        return (hasUser ? "1" : "0") + "|" + (user ? "1" : "0")
                + "|" + (hasAb ? "1" : "0") + "|" + ab;
    }
}