package com.azurpilot.ghio.ui.oobe

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlin.math.exp

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
