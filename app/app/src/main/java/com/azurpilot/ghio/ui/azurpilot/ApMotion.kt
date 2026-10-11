@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.azurpilot.ghio.ui.azurpilot

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * AzurPilot 页的动效词汇表
 *
 * 页面里不再各写 `tween(300)`——**缓动与时长的取值只此一处**，这样全页的「快慢手感」是一套。 取值全部来自 MD3 的 motion
 * token（下方常量即规范里的贝塞尔控制点与时长），只按语义分成三档：
 *
 * - [spatial] 空间动效：位移、尺寸这类「东西在动」。直接使用主题的空间弹簧， 与原生 Material 3 组件保持一致。
 * - [resize] 尺寸变化与列表落位：不加回弹。尺寸过冲像控件在抽搐，列表落位过冲则显得没对齐。
 * - [effects] 效果动效：颜色、透明度这类「样子在变」。比空间快，用缓动曲线—— 淡入淡出没有惯性可循，给它弹簧只会显得拖。
 *
 * Motion vocabulary for the AzurPilot page.
 *
 * Pages never hand-write `tween(300)` — **easings and durations are defined only here**, so the
 * whole page shares one sense of timing. Every value comes from the MD3 motion tokens (the
 * constants below are the spec's bezier control points and durations), grouped into three semantic
 * tiers:
 *
 * - [spatial] spatial motion: things that move — position, size. Uses the theme spatial spring to
 *   match native Material 3 components.
 * - [resize] size changes and list placement: no bounce. An overshooting size looks like a
 *   twitching control, and overshooting list placement reads as misalignment.
 * - [effects] effect motion: things that change appearance — color, alpha. Faster than spatial,
 *   driven by easing curves — a fade has no inertia to follow, and a spring only makes it drag.
 */
object ApMotion {

    /**
     * MD3 emphasized-decelerate：进场的减速段 / MD3 "emphasized decelerate": the decelerating half of an
     * entrance.
     */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /**
     * MD3 emphasized-accelerate：出场的加速段 / MD3 "emphasized accelerate": the accelerating half of an
     * exit.
     */
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /**
     * MD3 standard：一般过渡曲线，[effects] 的默认档 / MD3 "standard": the general transition curve and the
     * default easing of [effects].
     */
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** MD3 standard-accelerate / MD3 "standard accelerate". */
    val StandardAccelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    // 以下为 MD3 duration token，单位毫秒；数值照抄规范，勿凭手感调整。
    const val Short2 = 100
    const val Short4 = 200
    const val Medium2 = 300
    const val Medium4 = 400
    const val Long2 = 500

    /** 位移、尺寸：弹簧，轻回弹 / Position and size: a spring with a light bounce. */
    @Composable
    fun <T> spatial(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultSpatialSpec()

    /**
     * 尺寸变化与列表落位：不要回弹——过冲会让「对齐」这件事显得不确定
     *
     * Size changes and list placement: no bounce — overshoot makes alignment look uncertain.
     */
    fun <T> resize(): FiniteAnimationSpec<T> =
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )

    /** 颜色、透明度：缓动曲线，比空间动效快 / Color and alpha: an easing curve, faster than spatial motion. */
    fun <T> effects(
        durationMillis: Int = Short4,
        easing: Easing = Standard,
    ): FiniteAnimationSpec<T> = tween(durationMillis, easing = easing)
}
