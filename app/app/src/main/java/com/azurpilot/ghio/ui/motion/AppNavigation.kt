@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.azurpilot.ghio.ui.motion

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.azurpilot.ghio.ui.navigation.AppNavigator
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection

/**
 * 在主线程组合统一导航，使用 KernelSU 的 Miuix 默认转场与设备圆角。
 *
 * 手势进度、取消、提交和页面生命周期由框架维护；根页面返回交给系统。 滑动关闭方向随 RTL 镜像，子页面滚动与返回手势的冲突由框架协调。
 *
 * Composes navigation on the main thread using KernelSU's Miuix default transition and device
 * corner radius. The framework owns gesture progress, cancellation, completion, and entry
 * lifecycle; root back falls through to the system. Swipe dismissal mirrors in RTL, with scroll and
 * gesture conflicts coordinated by the framework.
 */
@Composable
fun AppNavigation(
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    content: NavEntryBuilder.(NavSwipeDirection) -> Unit,
) {
    val swipeDirection =
        if (LocalLayoutDirection.current == LayoutDirection.Ltr) {
            NavSwipeDirection.LeftToRight
        } else {
            NavSwipeDirection.RightToLeft
        }
    NavDisplay(
        backStack = navigator.backStack,
        // 导轨和底栏在宿主外，限制退场图层避免越界覆盖固定导航。
        modifier = modifier.clipToBounds(),
        onBack = navigator::pop,
        effects =
            NavDisplayEffects(
                cornerClipRadius = rememberNavSystemCornerRadius(),
                backdropColor = MaterialTheme.colorScheme.background,
            ),
    ) {
        content(swipeDirection)
    }
}

/**
 * 返回平级内容的主题淡化转场，固定容器尺寸避免额外布局动画。
 *
 * Returns the theme fade for peer content without additional container size animation.
 */
@Composable
fun peerContentTransform(): ContentTransform {
    val effects: FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.fastEffectsSpec()
    return fadeIn(effects) togetherWith fadeOut(effects)
}
