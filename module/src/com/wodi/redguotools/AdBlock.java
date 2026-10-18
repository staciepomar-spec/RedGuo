package com.wodi.redguotools;

import android.content.Context;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * 去广告：广告位总闸 + 四道闸门 + 开屏 + 视频流队列。
 *
 * <h3>核心思路（借鉴开源模块 FanqieHook 的静态复核结论）</h3>
 * 红果主进程里所有广告位的「现在能不能出广告」判定，<b>全部汇聚到一个方法</b>
 * {@code checkAdAvailable(String position, String source)}，它自己只是薄委托：
 * <pre>
 *   NsAdImpl.checkAdAvailable(pos, src)   → tc3.a.checkAdAvailable(pos, src)
 *   tc3.a.c(pos)   不带 source 的包装     → checkAdAvailable(pos, null)
 *   tc3.a.b(pos)   AB 实验表（335 条指令） → 只被 checkAdAvailable 调用
 * </pre>
 * hook 这一处返回 false，等于告诉整个宿主「任何位置都没有广告」，
 * <b>宿主以后新增的广告位也自动被拦</b>，不需要维护名单。
 *
 * <p>代价要说清楚：用户主动点的「看视频得金币 / 免广告 / 催更」等入口也会失效
 * （那里已经没有广告可播）。发奖由服务端校验，本地拦广告不会让金币白得。
 *
 * <h3>为什么还要另外几道闸门</h3>
 * 总闸管的是「查可用性」，但宿主有些路径在查之前就自己过了一道总开关，
 * 或者根本不查（直接把已缓存的广告对象塞进视图）。所以另加四道闸门做纵深防御：
 * <pre>
 *   NsUtilsDependImpl.canShowScreenAd(Object)      全屏广告（含开屏）根闸
 *   SeriesPauseAdImpl.canShowPauseAd(...)          短剧暂停页插播
 *   NsAdImpl.enableSeriesFeedTopViewAd()           单列短剧 TopView 广告总开关
 *   NsAdImpl.checkCanShowTopViewInMainPage(...)    首页 TopView 广告
 * </pre>
 * 这几个都是**长类名**（{@code NsAdImpl} / {@code NsUtilsDependImpl} …），
 * 不像混淆短名那样每次发版换字母，是这套里最稳的一层。
 *
 * <p>{@code FeedDrawAdManager} 是「预插入视频流广告」的队列：它在查可用性之前
 * 就自己把广告对象 offer 进来，所以单独拦它的出队与请求。
 *
 * <p>实现依据：<a href="https://github.com/afwfv/FanqieHook">afwfv/FanqieHook</a>
 * （番茄/红果去广告模块）的调用点分析；本模块用 dex 静态检索在红果 7.3.9.32 上
 * 逐条核对了这些方法确实存在。
 */
final class AdBlock {

    private static final String CLS_FEED_AD = "com.dragon.read.ad.feeddraw.FeedDrawAdManager";
    private static final String CLS_NS_AD_IMPL = "com.dragon.read.component.biz.impl.NsAdImpl";
    private static final String CLS_NS_UTILS = "com.dragon.read.component.NsUtilsDependImpl";
    private static final String CLS_PAUSE_AD =
            "com.dragon.read.ad.onestop.seriespause.impl.SeriesPauseAdImpl";

    /**
     * {@code checkAdAvailable} 的真正实现类候选 —— 它实现
     * {@code com.dragon.read.ad.manager.NsAdConfigManagerApi}，是混淆短名，每次发版换字母：
     * <pre>
     *   tc3.a  红果改版 7.3.9.32（本模块实测命中）
     *   yb3.a  红果 7.3.7.32      eg3.a  番茄 7.3.9.32
     *   lf3.a  番茄 7.3.7.32      fe3.a  番茄 7.3.5.32      h83.a  番茄 7.3.5.32
     * </pre>
     * 全都找不到时退化成只 hook {@code NsAdImpl}（它已覆盖绝大多数调用点），不会整体失效。
     */
    private static final String[] POSITION_FILTER_IMPLS = {
            "tc3.a", "yb3.a", "eg3.a", "lf3.a", "fe3.a", "h83.a",
    };

    /** 被动广告位：只用于日志分类，不参与判定（判定是一律 false）。 */
    private static final String[] PASSIVE_POSITIONS = {
            "splash_ad", "page_front_ad", "page_middle_ad", "page_end_ad",
            "reader_banner", "reader_text_link_ad", "reader_disconnected_ad",
            "reader_ad_for_sati", "video_reader_ad", "series_pause_ad",
            "comment_list_ad", "series_comment_ad", "story_ad", "creator_ad",
            "processed_ad", "landscape_short_series_ad",
            "landscape_short_series_pause_ad", "short_series_ad",
            "short_series_banner", "audio_info_flow_ad", "audio_patch_ad",
            "recommend_video",
    };

    /** 用户主动的金币 / 激励位：同样拦，但日志标 reward，便于事后回看。 */
    private static final String[] REWARD_POSITIONS = {
            "reader_gold_coin_popup", "video_tts_ad", "video_voice_ad",
            "video_reward_gift_ad", "video_reader_end_urge_update",
            "gold_coin_reward_box_other", "gold_coin_reward_box_welfare",
            "gold_coin_reward_dialog_ad_audio_page", "gold_coin_reward_dialog_ad_general",
            "gold_coin_reward_dialog_ad_open_treasure",
            "video_gold_coin_reward_dialog_audio_page",
            "video_gold_coin_reward_dialog_general",
            "video_gold_coin_reward_dialog_open_treasure",
            "listen_coin", "video_coin_ad", "video_chapter_front",
            "video_chapter_middle", "video_book_download", "video_comic_book_download",
            "video_reader_ad_free_dialog", "video_reader_auto_page_turn",
            "video_reader_offline_reading", "video_reading_latest_chapter",
            "video_short_story",
    };

    private static final Set<String> sPassive = new HashSet<>(Arrays.asList(PASSIVE_POSITIONS));
    private static final Set<String> sReward = new HashSet<>(Arrays.asList(REWARD_POSITIONS));
    /** 每个键只记一次日志，否则高频调用会把日志刷爆。 */
    private static final Set<String> sLogged = Collections.synchronizedSet(new HashSet<String>());

    private static volatile boolean sInit;
    private static volatile String sStatus;

    private AdBlock() {
    }

    static String status() {
        return sStatus;
    }

    static void hook(XposedModule mod, ClassLoader cl) {
        if (sInit) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("gate=").append(hookGates(mod, cl));
        sb.append(" pos=").append(hookPositionFilter(mod, cl));
        sb.append(" splash=").append(hookSplash(mod, cl));
        sb.append(" feed=").append(hookFeed(mod, cl));
        sStatus = sb.toString();
        sInit = true;
    }

    /* ------------------------------------------------------------------ */

    /** 广告位总闸：{@code checkAdAvailable} 一律回答「没有广告」。 */
    private static String hookPositionFilter(XposedModule mod, ClassLoader cl) {
        int n = 0;
        if (blockBoolean(mod, cl, CLS_NS_AD_IMPL, "checkAdAvailable",
                new Class<?>[]{String.class, String.class}, "gate-nsAdImpl", true)) {
            n++;
        }
        for (String cn : POSITION_FILTER_IMPLS) {
            if (blockBoolean(mod, cl, cn, "checkAdAvailable",
                    new Class<?>[]{String.class, String.class}, "gate-adCfg", true)) {
                n++;
                break;   // 命中一个即可，避免重复记账
            }
        }
        return n + "/2";
    }

    /** 其余闸门：长类名，逐条 try，单条失败不影响其它。 */
    private static String hookGates(XposedModule mod, ClassLoader cl) {
        int n = 0;
        if (blockBoolean(mod, cl, CLS_PAUSE_AD, "canShowPauseAd",
                null, "gate-canShowPauseAd", false)) {
            n++;
        }
        if (blockBoolean(mod, cl, CLS_NS_AD_IMPL, "enableSeriesFeedTopViewAd",
                null, "gate-enableSeriesFeedTopView", false)) {
            n++;
        }
        if (blockBoolean(mod, cl, CLS_NS_AD_IMPL, "checkCanShowTopViewInMainPage",
                null, "gate-canShowTopViewMain", false)) {
            n++;
        }
        return n + "/3";
    }

    /**
     * 把某个 boolean 判定方法恒定改成 false。
     *
     * @param paramTypes    传 null 表示按名字取唯一重载（无参优先，其次单参）
     * @param logPosition   true 时按第一个参数（广告位名）分类记日志
     */
    private static boolean blockBoolean(XposedModule mod, ClassLoader cl, String clsName,
                                        String methodName, Class<?>[] paramTypes,
                                        String id, boolean logPosition) {
        try {
            Class<?> cls = RGModule.tryLoad(cl, clsName);
            if (cls == null) {
                return false;
            }
            Method m = (paramTypes != null)
                    ? cls.getDeclaredMethod(methodName, paramTypes)
                    : findBooleanOverload(cls, methodName);
            if (m == null) {
                return false;
            }
            m.setAccessible(true);
            mod.hook(m)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        if (gateOn()) {
                            if (logPosition) {
                                logPosition(id, chain.getArg(0));
                            } else {
                                logOnce(id);
                            }
                            return Boolean.FALSE;
                        }
                        return chain.proceed();
                    });
            return true;
        } catch (Throwable t) {
            UiController.logFile("ad: " + id + " failed " + t);
            return false;
        }
    }

    /** 无参重载优先，否则取任意一个（闸门常见一个 context/配置参数）。 */
    private static Method findBooleanOverload(Class<?> cls, String name) {
        Method pick = null;
        for (Method m : cls.getDeclaredMethods()) {
            if (!m.getName().equals(name) || m.getReturnType() != boolean.class) {
                continue;
            }
            if (m.getParameterTypes().length == 0) {
                return m;
            }
            if (pick == null) {
                pick = m;
            }
        }
        return pick;
    }

    /** 总闸是否开启。上下文取不到时按「开」处理 —— 这是用户的显式诉求。 */
    private static boolean gateOn() {
        Context c = UiController.appCtx();
        return c == null || Config.adGate(c);
    }

    private static void logOnce(String id) {
        if (sLogged.add(id)) {
            UiController.logFile("ad: blocked " + id);
        }
    }

    private static void logPosition(String id, Object arg) {
        String pos = String.valueOf(arg);
        if (!sLogged.add(pos)) {
            return;
        }
        String kind = sPassive.contains(pos) ? "passive"
                : (sReward.contains(pos) ? "reward" : "unnamed");
        UiController.logFile("ad: position=" + pos + " (" + kind + ") via " + id);
    }

    /* ------------------------------------------------------------------ */

    /**
     * 开屏 / 全屏广告的根闸。
     *
     * <p><b>这里踩过一个坑，不要改回 {@code AttributionManager.hasHitAttribution()}</b>：
     * 那个方法看起来更「彻底」（返回 false 就连请求都不发，SplashActivity 里它确实是
     * {@code 是否需要请求开屏广告} 的开关），但实测把它恒成 false 之后，
     * <b>冷启动会落在「剧场」页而不是「首页」</b> —— 它同时参与落地页决策，
     * 不只是个广告开关。属于「改了有副作用」的接口，动它等于替宿主改导航。
     *
     * <p>{@code NsUtilsDependImpl.canShowScreenAd(Object)} 是纯展示判定，
     * 拦它只影响广告、不影响跳哪儿（FanqieHook 实测这也是开屏的真正根闸）。
     */
    private static String hookSplash(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> cls = RGModule.tryLoad(cl, CLS_NS_UTILS);
            if (cls == null) {
                return "absent";
            }
            Method m = cls.getDeclaredMethod("canShowScreenAd", Object.class);
            m.setAccessible(true);
            mod.hook(m)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Context c = UiController.appCtx();
                        if (c != null && Config.blockSplashAd(c)) {
                            logOnce("splash-root-gate");
                            return Boolean.FALSE;
                        }
                        return chain.proceed();
                    });
            return "ok";
        } catch (Throwable t) {
            UiController.logFile("ad: splash hook failed " + t);
            return "fail";
        }
    }

    private static String hookFeed(XposedModule mod, ClassLoader cl) {
        Class<?> cls = RGModule.tryLoad(cl, CLS_FEED_AD);
        if (cls == null) {
            return "absent";
        }
        int n = 0;
        try {
            Method getAd = cls.getDeclaredMethod("getAd");
            getAd.setAccessible(true);
            mod.hook(getAd)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Context c = UiController.appCtx();
                        if (c != null && Config.blockFeedAd(c)) {
                            logOnce("feed-ad-queue");
                            return null;
                        }
                        return chain.proceed();
                    });
            n++;
        } catch (Throwable t) {
            UiController.logFile("ad: getAd hook failed " + t);
        }
        // 队列空 → 宿主会去请求新广告；挡掉这两个入口就不再发请求
        for (String mn : new String[]{"onPageCreate", "onVideoChange"}) {
            try {
                Method m = cls.getDeclaredMethod(mn);
                m.setAccessible(true);
                mod.hook(m)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Context c = UiController.appCtx();
                            if (c != null && Config.blockFeedAd(c)) {
                                return null;
                            }
                            return chain.proceed();
                        });
                n++;
            } catch (Throwable t) {
                UiController.logFile("ad: " + mn + " hook failed " + t);
            }
        }
        return n + "/3";
    }
}
