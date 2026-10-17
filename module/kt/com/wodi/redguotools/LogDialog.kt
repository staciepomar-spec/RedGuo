package com.wodi.redguotools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton

/**
 * 日志查看弹窗。
 *
 * <p>日志与视图树快照写在**红果**的外置私有目录下（Android 11 起普通应用
 * 连 stat 都不允许，{@code File.exists()} 恒false），所以只能用 root 读。
 * 读取是同步阻塞的（su 可能弹授权框），必须放后台线程。
 */
@Composable
fun LogDialog(name: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember(name) { mutableStateOf("正在读取…") }
    var loading by remember(name) { mutableStateOf(true) }

    suspend fun reload() {
        loading = true
        text = "正在读取…"
        val r = withContext(Dispatchers.IO) { readTail(name) }
        text = r
        loading = false
    }

    LaunchedEffect(name) { reload() }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .background(Color(0xFFFFFFFF), RoundedCornerShape(20.dp))
                .padding(18.dp),
        ) {
            Text(name, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                "$HOST_LOG_DIR/$name",
                Modifier.padding(top = 2.dp, bottom = 8.dp),
                fontSize = 11.sp,
                color = Color(0xFF9A9A9E),
            )

            Box(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Text(
                    text,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (loading) Color(0xFF9A9A9E) else Color(0xFF444444),
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton("重新读取", onClick = { scope.launch { reload() } })
                TextButton("复制", onClick = { copyText(ctx, text) })
                TextButton("关闭", onClick = onDismiss)
            }
        }
    }
}

/**
 * root 读文件：先 `ls -l` 拿状态，再 `tail` 取末尾若干行。
 *
 * <p>只取尾部是因为 rgtools_tree.txt 能到几 MB，整读会顶爆内存也拖慢界面。
 */
private fun readTail(name: String): String {
    val path = "$HOST_LOG_DIR/$name"
    val lines = if (name == "rgtools_tree.txt") 400 else 600

    val stat = RootShell.run("ls -l '$path' 2>&1")
    val statText = if (stat.ok() && stat.out.isNotEmpty()) {
        stat.out.trim()
    } else {
        "（文件不存在，红果跑起来之后才会生成）"
    }

    val r = RootShell.run("tail -n $lines '$path' 2>/dev/null", 200000)
    if (!r.ok()) {
        return statText + "\n\n读取失败：" + r.describe() +
                "\n\n若提示找不到 su，说明 ReadGuo 还没被授予 root（KernelSU 里放行即可）。" +
                "\n也可以用 adb 手动拉取：\nadb pull " + path
    }
    return statText + "\n——— 以下为末尾 $lines 行 ———\n" + r.out.ifEmpty { "（文件为空）" }
}

private fun copyText(ctx: android.content.Context, s: String) {
    runCatching {
        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("ReadGuo", s))
        Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show()
    }
}