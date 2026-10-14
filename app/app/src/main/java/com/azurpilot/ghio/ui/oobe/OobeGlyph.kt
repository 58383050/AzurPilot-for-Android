package com.azurpilot.ghio.ui.oobe

import androidx.annotation.RawRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.azurpilot.ghio.R
import kotlin.math.roundToInt

/**
 * 映射从用户连接的 OS3 Provision.apk 提取的原始矢量动画。
 *
 * Maps original vector animations extracted from the user's connected OS3 Provision.apk.
 */
internal enum class OobeGlyph(@param:RawRes val resource: Int) {
    LANGUAGE(R.raw.oobe_os3_language),
    AGREEMENT(R.raw.oobe_os3_terms),
    APPEARANCE(R.raw.oobe_os3_settings),
    PRIVILEGE(R.raw.oobe_os3_password),
    TOUR(R.raw.oobe_os3_settings),
}

/**
 * 直接播放原始 70dp Lottie；保留资源的路径、渐变和关键帧，不动态重着色。
 * 返回页面或关闭动画时停在完整图形，进入后台停止播放。
 *
 * Plays the original 70dp Lottie, preserving paths, gradients, and keyframes without recoloring.
 * Returning or reduced motion shows the complete glyph; playback stops in the background.
 */
@Composable
internal fun OobeActivationGlyph(glyph: OobeGlyph, modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(glyph.resource))
    val reduced = rememberOobeReducedMotion()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    var introduced by rememberSaveable(glyph) { mutableStateOf(false) }
    val progress = remember(glyph) { Animatable(0f) }
    LaunchedEffect(composition, reduced, lifecycle) {
        val loaded = composition ?: return@LaunchedEffect
        // 末尾排他帧可能不显示图层，使用最后一个有效整帧作为静止图形。
        val complete = ((loaded.endFrame - 1f - loaded.startFrame) /
            (loaded.endFrame - loaded.startFrame)).coerceIn(0f, 1f)
        if (reduced || introduced || !lifecycle.isAtLeast(Lifecycle.State.RESUMED)) progress.snapTo(complete)
        else {
            introduced = true
            progress.animateTo(complete, tween(loaded.duration.roundToInt().coerceAtLeast(1), easing = LinearEasing))
        }
    }
    LottieAnimation(composition, progress = { progress.value }, modifier = modifier.size(70.dp),
        enableMergePaths = true)
}
