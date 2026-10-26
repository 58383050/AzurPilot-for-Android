package top.yukonga.miuix.kmp.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证平移跨越游戏边界时不改变起点决定，且下一次手势重新判断。
 *
 * Verifies origin-based pan ownership across boundaries and reset between gestures.
 */
class PagerPanExclusionTest {
    @Test fun legacyJvmSignaturesAndDefaultBridgesRemainAvailable() {
        val api = Class.forName(
            "top.yukonga.miuix.kmp.utils.PagerGestureUtilsKt", false, javaClass.classLoader,
        )
        val modifier = androidx.compose.ui.Modifier::class.java
        val pager = androidx.compose.foundation.pager.PagerState::class.java
        val boolean = Boolean::class.javaPrimitiveType!!
        val callback = kotlin.jvm.functions.Function0::class.java
        val tracker = PagerFlingTrackerConnection::class.java
        val signatures = listOf(
            "horizontalPagerSwipeOverride" to arrayOf(modifier, pager, boolean, callback),
            "iosStyleMomentumHalt" to arrayOf(modifier, tracker, boolean, callback),
            "pagerGestureOverride" to arrayOf(
                modifier, pager, PagerInterceptionMode::class.java, boolean, tracker, callback,
            ),
            "pagerGestureOverride" to arrayOf(
                modifier, pager, Int::class.javaPrimitiveType!!, boolean, tracker, callback,
            ),
        )
        signatures.forEach { (name, parameters) ->
            api.getDeclaredMethod(name, *parameters)
            api.getDeclaredMethod(
                "${name}\$default", *parameters, Int::class.javaPrimitiveType!!, Any::class.java,
            )
        }
    }

    // 保持旧尾随回调写法可以编译，不执行任何分页。
    private fun legacyTrailingCallbacks(
        pager: androidx.compose.foundation.pager.PagerState,
        tracker: PagerFlingTrackerConnection,
    ) = androidx.compose.ui.Modifier
        .horizontalPagerSwipeOverride(pager) { Unit }
        .iosStyleMomentumHalt(tracker) { Unit }
        .pagerGestureOverride(pager, flingTracker = tracker) { Unit }
        .pagerGestureOverride(pager, 1, flingTracker = tracker) { Unit }

    @Test fun panStartingInGameStaysExcludedOutsideGame() {
        val pan = PagerPanExclusion()
        assertTrue(pan.start(true))
        assertTrue(pan.move { false })
        assertTrue(pan.end())
        assertFalse(pan.start(false))
        assertFalse(pan.move { true })
        assertFalse(pan.end())
    }

    @Test fun panStartingOutsideGameKeepsPagingAcrossGame() {
        val pan = PagerPanExclusion()
        assertFalse(pan.start(false))
        assertFalse(pan.move { true })
        assertFalse(pan.end())
        assertTrue(pan.start(true))
        assertTrue(pan.end())
    }

    @Test fun missingStartUsesFirstMoveUntilEnd() {
        val pan = PagerPanExclusion()
        assertTrue(pan.move { true })
        assertTrue(pan.move { false })
        assertTrue(pan.end())
        assertFalse(pan.move { false })
        assertFalse(pan.move { true })
    }

    @Test fun cancellationAndNewStartDiscardPreviousOrigin() {
        val pan = PagerPanExclusion()
        assertTrue(pan.start(true))
        pan.cancel()
        assertFalse(pan.move { false })
        assertTrue(pan.start(true))
        assertTrue(pan.move { false })
    }
}
