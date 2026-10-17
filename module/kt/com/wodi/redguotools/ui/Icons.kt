package com.wodi.redguotools.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 底栏 / 列表用的图标。
 *
 * <p>miuix 自带图标只有 7 个（箭头、勾、关闭、搜索等），不含导航类图标，
 * 而 `NavigationBarItem` 的 `icon` 是必传参数，所以这里用 [ImageVector.Builder]
 * 现绘——网格与 v2.80 的 Canvas 版一致（24）。
 *
 * <p>注意 [path] 的语义：`fill` 是填充画刷（默认实心黑），`stroke` 是描边画刷
 * （默认无描边）。要画描边图标必须同时给 `fill = null` 与 `stroke = SolidColor(...)`，
 * 否则会得到一个实心块。
 */
object Icons {

    private fun icon(name: String, block: Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(block).build()

    /** 描边路径。 */
    private fun Builder.strokePath(
        color: Color = Color.Black,
        width: Float = 1.9f,
        block: PathBuilder.() -> Unit,
    ) = path(fill = null, stroke = SolidColor(color),
        strokeLineWidth = width, strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round, pathBuilder = block)

    /** 实心路径。 */
    private fun Builder.fillPath(
        color: Color = Color.Black,
        block: PathBuilder.() -> Unit,
    ) = path(fill = SolidColor(color), pathBuilder = block)

    /** 一条描边直线。 */
    private fun Builder.line(
        x1: Float, y1: Float, x2: Float, y2: Float,
        color: Color = Color.Black, width: Float = 1.9f,
    ) = strokePath(color, width) { moveTo(x1, y1); lineTo(x2, y2) }

    /** 描边圆（四段贝塞尔近似，k=0.5523）。 */
    private fun Builder.ring(
        cx: Float, cy: Float, r: Float,
        color: Color = Color.Black, width: Float = 1.9f,
    ) = strokePath(color, width) { circle(cx, cy, r) }

    /** 实心圆。 */
    private fun Builder.dot(cx: Float, cy: Float, r: Float) = fillPath { circle(cx, cy, r) }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        val k = r * 0.5523f
        moveTo(cx + r, cy)
        curveToRelative(0f, k, -k, r, -r, r)
        curveToRelative(-k, 0f, -r, -k, -r, -r)
        curveToRelative(0f, -k, k, -r, r, -r)
        curveToRelative(k, 0f, r, k, r, r)
        close()
    }

    /** 房子：屋顶折线 + 墙体 + 门。 */
    val Home: ImageVector by lazy {
        icon("Home") {
            line(3.6f, 11.3f, 12f, 4.6f)
            line(12f, 4.6f, 20.4f, 11.3f)
            line(6.1f, 10f, 6.1f, 19.5f)
            line(6.1f, 19.5f, 17.9f, 19.5f)
            line(17.9f, 19.5f, 17.9f, 10f)
            line(10f, 19.5f, 10f, 14.7f)
            line(10f, 14.7f, 14f, 14.7f)
            line(14f, 14.7f, 14f, 19.5f)
        }
    }

    /** 滑块：三条横线 + 三个实心滑点（功能页）。 */
    val Sliders: ImageVector by lazy {
        icon("Sliders") {
            line(3.6f, 6.8f, 20.4f, 6.8f)
            line(3.6f, 12f, 20.4f, 12f)
            line(3.6f, 17.2f, 20.4f, 17.2f)
            dot(9.2f, 6.8f, 2.1f)
            dot(15.2f, 12f, 2.1f)
            dot(7.6f, 17.2f, 2.1f)
        }
    }

    /** 齿轮：双圆 + 8 根齿（设置页）。 */
    val Gear: ImageVector by lazy {
        icon("Gear") {
            ring(12f, 12f, 6.1f)
            ring(12f, 12f, 2.5f)
            for (i in 0 until 8) {
                val a = Math.PI * 2 * i / 8
                val cos = Math.cos(a).toFloat()
                val sin = Math.sin(a).toFloat()
                line(12f + cos * 6.4f, 12f + sin * 6.4f, 12f + cos * 8.7f, 12f + sin * 8.7f)
            }
        }
    }

    /** 圆圈 + i（关于页）。 */
    val Info: ImageVector by lazy {
        icon("Info") {
            ring(12f, 12f, 7.6f)
            dot(12f, 7.7f, 1.15f)
            line(12f, 10.4f, 12f, 16.2f, width = 2.1f)
        }
    }

    /** 环形对勾（主页激活卡）。 */
    val RingCheck: ImageVector by lazy {
        icon("RingCheck") {
            ring(12f, 12f, 9f, width = 3.4f)
            strokePath(width = 3.4f) {
                moveTo(7.6f, 12.2f); lineTo(10.8f, 15.4f); lineTo(16.6f, 8.6f)
            }
        }
    }

    /** 文档（日志）。 */
    val Doc: ImageVector by lazy {
        icon("Doc") {
            strokePath {
                moveTo(6f, 3.4f); lineTo(13.4f, 3.4f); lineTo(18.4f, 8.4f)
                lineTo(18.4f, 20.6f); lineTo(6f, 20.6f); close()
            }
            line(13.2f, 3.6f, 13.2f, 8.6f)
            line(13.2f, 8.6f, 18.2f, 8.6f)
            line(8.6f, 12.4f, 15.4f, 12.4f, width = 1.6f)
            line(8.6f, 15.6f, 15.4f, 15.6f, width = 1.6f)
            line(8.6f, 18.8f, 12.6f, 18.8f, width = 1.6f)
        }
    }

    /** 九宫格（恢复桌面图标）。 */
    val Grid: ImageVector by lazy {
        icon("Grid") {
            for (gy in 0 until 2) {
                for (gx in 0 until 2) {
                    val l = 4.2f + gx * 9.2f
                    val t = 4.2f + gy * 9.2f
                    strokePath(width = 1.8f) {
                        moveTo(l + 1.6f, t)
                        lineTo(l + 5.6f, t); lineTo(l + 5.6f, t + 7.2f)
                        lineTo(l + 1.6f, t + 7.2f); close()
                    }
                }
            }
        }
    }

    /** 刷新（主页右上角）。 */
    val Refresh: ImageVector by lazy {
        icon("Refresh") {
            strokePath {
                moveTo(20.2f, 12f)
                arcToRelative(7.8f, 7.8f, 0f, true, true, 0f, -15.6f)
                arcToRelative(7.8f, 7.8f, 0f, true, true, -13.6f, 11.4f)
            }
            strokePath {
                moveTo(19.9f, 3.4f); lineTo(20.4f, 8.5f); lineTo(15.4f, 7.8f)
            }
        }
    }
}