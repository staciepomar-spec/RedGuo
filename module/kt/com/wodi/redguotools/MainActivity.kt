package com.wodi.redguotools

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wodi.redguotools.ui.Icons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 模块自身入口（v2.82）：用 Compose + **miuix** 组件重写。
 *
 * 迁移原因：v2.80~v2.81 的底栏与卡片是照着 MIUI 观感手写的 View（Canvas 画图标 +
 * 自己拼圆角），跟原版有肉眼可见的差距；miuix 是现成的 Compose MIUI 组件库，
 * 底栏 / 卡片 / 开关 / 文字的间距与圆角都是官方对齐的。
 *
 * 功能与 v2.81 保持一致：主页 / 功能 / 设置 / 关于四页，设置页只有
 *「隐藏桌面图标」与「日志」。已修的坑不许复发：
 * - 桌面图标走 [LAUNCHER_ALIAS]，禁alias 不禁 Activity 本体，否则 LSPosed 进不去
 * - 倍速文案统一 2.0x 上限（引擎硬上限）
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isSystemInDark()
            MiuixTheme(
                colors = if (dark) darkColorScheme(primary = Color(0xFF4A8CFF))
                else lightColorScheme(primary = Color(0xFF3478F6)),
            ) {
                App()
            }
        }
    }

    private fun isSystemInDark(): Boolean =
        (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
}

/* ==================== 应用骨架 ==================== */

private enum class Tab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Home("主页", Icons.Home),
    Feature("功能", Icons.Sliders),
    Settings("设置", Icons.Gear),
    About("关于", Icons.Info),
}

@Composable
private fun App() {
    var tab by remember { mutableStateOf(Tab.Home) }
    // 设置页开关改动后需要重建（状态文案是快照），用 key 强制重组
    var settingsEpoch by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = t.icon,
                        label = t.title,
                    ) {}
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.Home -> HomePage()
                Tab.Feature -> FeaturePage()
                Tab.Settings -> SettingsPage(epoch = settingsEpoch, onChanged = { settingsEpoch++ })
                Tab.About -> AboutPage()
            }
        }
    }
}

/* ==================== 通用组件 ==================== */

@Composable
private fun PageScroll(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) { content() }
}

/** 大标题（页面顶部）。 */
@Composable
private fun PageTitle(text: String) {
    Text(
        text,
        Modifier.padding(start = 4.dp, top = 18.dp, bottom = 12.dp),
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold,
    )
}

/** 分组小标。 */
@Composable
private fun GroupLabel(text: String) {
    SmallTitle(text, Modifier.padding(start = 4.dp, top = 6.dp, bottom = 4.dp))
}

/** 卡内「标题 + 说明」一行，可点。 */
@Composable
private fun Row_(
    title: String,
    subtitle: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(
                icon, null,
                Modifier.size(22.dp),
                colorFilter = ColorFilter.tint(Color(0xFF1C1C1E)),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    subtitle,
                    Modifier.padding(top = 2.dp),
                    fontSize = 13.sp,
                    color = Color(0xFF8A8A8E),
                )
            }
        }
        if (trailing != null) trailing() else if (onClick != null) {
            Text("›", color = Color(0xFF9A9A9E), fontSize = 20.sp)
        }
    }
}

@Composable
private fun LocalContentColor(): Color = Color(0xFF1C1C1E)

/** 正文段落。 */
@Composable
private fun Bullet(text: String) {
    Text(
        "· " + text,
        Modifier.padding(vertical = 3.dp),
        fontSize = 14.sp,
        color = Color(0xFF444444),
    )
}

/* ==================== 主页 ==================== */

@Composable
private fun HomePage() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    PageScroll {
        PageTitle("主页")

        /* 激活卡：绿底 + 右侧环形对勾 */
        Box(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .background(Color(0xFFDCF6E4), RoundedCornerShape(18.dp))
                .padding(18.dp),
        ) {
            Column {
                Text("模块已激活", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(
                    "v" + appVersion(ctx),
                    Modifier.padding(top = 3.dp),
                    fontSize = 13.sp,
                    color = Color(0xFF6E6E73),
                )
                Text(
                    "ReadGuo",
                    Modifier.padding(top = 10.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Image(
                Icons.RingCheck, null,
                Modifier
                    .align(Alignment.CenterEnd)
                    .size(58.dp)
                    .padding(end = 4.dp),
                colorFilter = ColorFilter.tint(Color(0xFF22C55E)),
            )
        }

        /* 设备信息卡 */
        Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            InfoRow("设备名称", Build.MODEL)
            InfoRow("设备型号", Build.MANUFACTURER + " " + Build.DEVICE)
            InfoRow("HyperOS 版本", hyperOsVersion())
            InfoRow("Android 版本", Build.VERSION.RELEASE + "（SDK " + Build.VERSION.SDK_INT + "）")
            InfoRow("红果版本", hostVersion(ctx))
            InfoRow("模块版本", appVersion(ctx))
        }

        /* 开源地址 */
        Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Row_("开源地址", GITHUB, Icons.Grid, onClick = {
                openUrl(ctx, "https://" + GITHUB)
            })
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(value, Modifier.padding(top = 2.dp, bottom = 8.dp),
            fontSize = 14.sp, color = Color(0xFF8A8A8E))
    }
}

/* ==================== 功能页 ==================== */

@Composable
private fun FeaturePage() {
    PageScroll {
        PageTitle("功能")

        FeatureCard("界面显示",
            "状态栏：隐藏系统状态栏，底色可选跟随背景 / 纯黑 / 纯白",
            "组件显隐：12 个常用组件（顶栏 / 右侧互动 / 底部条 / 标题 / 热评 / 进度条…）逐项开关",
            "自定义隐藏清单：扫描当前页面自动列出，或手动加规则（cls: / @文案 / text: / id:）",
            "底部标签栏：按标签文字勾选隐藏，剩余标签自动摊满整行",
            "底部导航栏：软件内全程隐藏小白条，退出软件才恢复",
            "叠加透明度：全局系数 × 各项独立，双层叠加")

        FeatureCard("播放器",
            "默认最高画质：起播自动选最高可用档",
            "自定义倍速：1.0 ~ 2.0 倍无级调节（0.1 步进），不依赖宿主档位",
            "倍速上限 2.0x：常规与长按是同一条播放器通路，native 引擎把超过 2.0 的请求降档——官方面板的 3.0x 实测只有约 1.5x",
            "定时关闭：到点停播并直接熄屏（需给 ReadGuo 授予 root）")

        FeatureCard("交互与自动化",
            "双击屏幕直接打开评论区（横屏三击），可吞掉第二次点击避免误触点赞",
            "清屏省心：清屏状态下 10 秒不碰屏幕，自动收起其余控件只留视频，触摸立即恢复",
            "屏蔽「发现新版本」升级弹窗")

        FeatureCard("使用方式",
            "在 LSPosed 中启用本模块，作用域勾选「红果免费短剧」",
            "强制停止红果后重新打开",
            "长按底部「首页」标签唤出设置面板，或在播放设置弹层里点「ReadGuo 设置」",
            "面板里所有改动即时生效，无需重启红果")
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun FeatureCard(title: String, vararg points: String) {
    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(
            title,
            Modifier.padding(top = 10.dp, bottom = 2.dp),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        points.forEach { Bullet(it) }
        Spacer(Modifier.height(8.dp))
    }
}

/* ==================== 设置页 ==================== */

/** 桌面图标的 LAUNCHER 入口（alias）；隐藏时禁它，不禁 Activity 本体。 */
const val LAUNCHER_ALIAS = "com.wodi.redguotools.LauncherAlias"

/** 宿主（红果）的外置私有目录，日志与视图树快照落在那里。 */
const val HOST_LOG_DIR = "/sdcard/Android/data/com.phoenix.read/files"

@Composable
private fun SettingsPage(epoch: Int, onChanged: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var hidden by remember(epoch) { mutableStateOf(iconHidden(ctx)) }

    PageScroll {
        PageTitle("设置")

        /* --- 桌面图标 --- */
        Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("隐藏桌面图标", fontSize = 16.sp)
                    Text(
                        "关闭后桌面不再显示图标，LSPosed 管理器里仍可打开本模块",
                        Modifier.padding(top = 2.dp),
                        fontSize = 13.sp, color = Color(0xFF8A8A8E),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = hidden,
                    onCheckedChange = { want ->
                        if (want) {
                            setIconHidden(ctx, true)
                            toast(ctx, if (iconHidden(ctx)) "已隐藏桌面图标" else "隐藏失败")
                        } else {
                            setIconHidden(ctx, false)
                            toast(ctx, if (iconHidden(ctx)) "恢复失败" else "已恢复桌面图标")
                        }
                        hidden = iconHidden(ctx)
                        onChanged()
                    },
                )
            }
        }

        /* --- 日志 --- */
        Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text("日志", Modifier.padding(top = 10.dp, bottom = 2.dp),
                fontSize = 15.sp, fontWeight = FontWeight.Bold)

            var dialog by remember { mutableStateOf<String?>(null) }
            Row_("运行日志", "rgtools_log.txt", Icons.Doc) {
                dialog = "rgtools_log.txt"
            }
            Row_("视图树快照", "rgtools_tree.txt", Icons.Grid) {
                dialog = "rgtools_tree.txt"
            }
            Row_("恢复桌面图标",
                if (hidden) "当前已隐藏，点此恢复" else "当前未隐藏", Icons.Grid) {
                setIconHidden(ctx, false)
                hidden = iconHidden(ctx)
                onChanged()
                toast(ctx, if (hidden) "恢复失败" else "已恢复桌面图标")
            }

            Text(
                "日志与快照写在红果的外置私有目录（$HOST_LOG_DIR/）。"
                        + "Android 11 起应用读不到别的 App 的私有目录，所以本模块用 root 读取；"
                        + "没给 ReadGuo 授权 root 时可用 adb 拉取。",
                Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp),
                fontSize = 13.sp, color = Color(0xFF8A8A8E),
            )

            dialog?.let { name ->
                LogDialog(name = name, onDismiss = { dialog = null })
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/* ==================== 关于页 ==================== */

@Composable
private fun AboutPage() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    PageScroll {
        PageTitle("关于")

        Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            InfoRow("模块名称", "ReadGuo（红果工具）")
            InfoRow("版本", appVersion(ctx))
            InfoRow("作者", AUTHOR)
            InfoRow("作用域", "红果免费短剧（com.phoenix.read）")
            InfoRow("运行框架", "LSPosed / libxposed API 102")
        }

        Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Row_("源码仓库", GITHUB, Icons.Grid, onClick = {
                openUrl(ctx, "https://" + GITHUB)
            })
        }

        GroupLabel("免责声明")
        Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text(
                "· 本模块仅供学习交流与个人技术研究使用，请勿用于任何商业或非法用途。\n\n"
                        + "· 本模块不修改、不破解、不绕过任何付费、会员或版权保护机制；"
                        + "所有功能仅调整本机的界面显示与个人偏好设置。\n\n"
                        + "· 请在遵守法律法规及平台用户协议的前提下使用；请支持正版，"
                        + "相关内容、商标与软件版权归原作者及平台所有。\n\n"
                        + "· 本模块按「现状」提供，不附带任何明示或默示的担保；"
                        + "因下载、安装或使用本模块而产生的一切后果，由使用者自行承担。\n\n"
                        + "· 若本模块侵犯了相关方的合法权益，请联系作者删除。",
                Modifier.padding(vertical = 10.dp),
                fontSize = 13.sp, color = Color(0xFF767676),
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

/* ==================== 平台工具 ==================== */

private const val AUTHOR = "酷安@残話"
private const val GITHUB = "github.com/staciepomar-spec/RedGuo"

private fun iconHidden(ctx: android.content.Context): Boolean = try {
    ctx.packageManager.getComponentEnabledSetting(
        ComponentName(ctx, LAUNCHER_ALIAS),
    ) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
} catch (t: Throwable) {
    false
}

private fun setIconHidden(ctx: android.content.Context, hidden: Boolean) {
    try {
        ctx.packageManager.setComponentEnabledSetting(
            ComponentName(ctx, LAUNCHER_ALIAS),
            if (hidden) PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
    } catch (t: Throwable) {
        toast(ctx, "系统拒绝了图标显隐：" + t.message)
    }
}

private fun appVersion(ctx: android.content.Context): String = try {
    val p = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
    p.versionName + "（" + p.versionCode + "）"
} catch (t: Throwable) {
    "?"
}

private fun hostVersion(ctx: android.content.Context): String = try {
    ctx.packageManager.getPackageInfo("com.phoenix.read", 0).versionName ?: "未安装"
} catch (t: Throwable) {
    "未安装"
}

/** HyperOS 版本在 ro.mi.os.version.incremental，Build.DISPLAY 给的是基线版本号。 */
private fun hyperOsVersion(): String {
    val s = sysProp("ro.mi.os.version.incremental")
        ?: sysProp("ro.miui.ui.version.name")
    if (!s.isNullOrEmpty()) return s
    return Build.DISPLAY?.takeIf { it.isNotEmpty() } ?: Build.VERSION.RELEASE
}

private fun sysProp(key: String): String? = try {
    val m = Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
    (m.invoke(null, key) as? String)?.takeIf { it.isNotEmpty() }
} catch (t: Throwable) {
    null
}

private fun openUrl(ctx: android.content.Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (t: Throwable) {
        toast(ctx, "没有可用的浏览器")
    }
}

private fun toast(ctx: android.content.Context, msg: String) {
    runCatching { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
}