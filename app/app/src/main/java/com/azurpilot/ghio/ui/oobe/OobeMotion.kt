package com.azurpilot.ghio.ui.oobe

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import kotlin.math.exp
import kotlin.math.cos

/**
 * 对齐 OS3 语言页 ViewPropertyAnimator 默认的加减速遮罩淡出。
 *
 * Matches the default accelerate-decelerate overlay fade used by OS3's ViewPropertyAnimator.
 */
internal val OobeWelcomeOverlayEasing = Easing { fraction ->
    ((cos((fraction + 1.0) * Math.PI) / 2.0) + .5).toFloat()
}

/**
 * 指示当前页面已结束转场，允许装饰动画开始；独立预览默认可播放。
 *
 * Allows decorative playback after page entry settles; standalone previews play by default.
 */
internal val LocalOobePageActive = compositionLocalOf { true }

/**
 * 从欢迎页继续按钮的圆形轮廓展开到全屏，复现 OS3 圆角视图启动的空间关系。
 * 进度在绘制阶段读取，不为每一帧重新测量页面。
 *
 * Expands the welcome button's circular outline into the full page, matching OS3's rounded-view
 * launch geometry. Reads progress while drawing without remeasuring the page on every frame.
 */
internal data class OobeWelcomeRevealShape(
    private val button: Rect,
    private val progress: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val fraction = progress.coerceIn(0f, 1f)
        val bounds = Rect(
            lerp(button.left, 0f, fraction),
            lerp(button.top, 0f, fraction),
            lerp(button.right, size.width, fraction),
            lerp(button.bottom, size.height, fraction),
        )
        val radius = minOf(button.width, button.height) * .5f * (1f - fraction)
        return Outline.Rounded(RoundRect(bounds, CornerRadius(radius)))
    }
}

/**
 * OS3 语言页的遮罩在 600ms 内淡出；首屏展开与遮罩共享时钟。
 *
 * Shares the first-page reveal clock with OS3's 600ms language-page overlay fade.
 */
internal const val OobeWelcomeRevealDuration = 600

/**
 * 使用临界阻尼的标准阶跃响应，保持整页移动不回弹。
 * response=0.55 的弹簧收敛后映射到 875ms 页面时长；数学实现由本项目独立编写。
 *
 * Uses the standard critically damped step response for page motion without overshoot.
 * Maps the settled response=0.55 spring to the 875ms page duration, implemented independently.
 */
internal val HyperOsPageEasing = Easing { fraction ->
    val time = fraction * 9.24f
    val end = 1f - 10.24f * exp(-9.24f)
    ((1f - (1f + time) * exp(-time)) / end).coerceIn(0f, 1f)
}

/**
 * 监听系统关闭动画的设置，关闭装饰入场和页面位移。
 *
 * Observes the system animation setting to disable decorative entrances and page translation.
 */
@Composable
internal fun rememberOobeReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun disabled() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember(resolver) { mutableStateOf(disabled()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = disabled() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}
