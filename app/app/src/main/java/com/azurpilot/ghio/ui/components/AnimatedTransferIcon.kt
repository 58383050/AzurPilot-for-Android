package com.azurpilot.ghio.ui.components

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.azurpilot.ghio.theme.ksu.LocalUiMode
import com.azurpilot.ghio.theme.ksu.UiMode

/**
 * 区分文件传输与处理的当前步骤，图标仅作装饰，步骤文案由调用方提供。
 *
 * Identifies the active transfer or processing step. Icons are decorative; callers provide labels.
 */
enum class TransferStage {
    /** 下载文件或源码。 / Downloading files or source code. */
    DOWNLOAD,
    /** 解压归档。 / Extracting an archive. */
    EXTRACT,
    /** 检查资源或校验文件。 / Checking resources or verifying files. */
    VERIFY,
    /** 构建资源、应用更新或重启。 / Building resources, applying updates, or restarting. */
    APPLY,
}

/**
 * 显示下载、解压、校验或应用更新的循环图标，两套 UI 共用同一组图形。
 *
 * 固定尺寸避免步骤切换影响布局；动画值仅在绘制阶段读取。离开前台或系统关闭动画时，
 * 移除循环动画并显示静态图标；系统动画设置变化立即生效。状态说明由相邻文案承担。
 *
 * Shows looping download, extraction, verification, or update icons in both UI styles.
 *
 * Fixed dimensions keep stage changes from moving layout; animation values are read only while
 * drawing. Background pages or disabled system animations remove the loop and show static icons.
 * System animation setting changes take effect immediately. Adjacent text describes the state.
 */
@Composable
fun AnimatedTransferIcon(
    stage: TransferStage,
    modifier: Modifier = Modifier,
    color: Color = if (LocalUiMode.current == UiMode.Miuix) {
        top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.primary
    } else {
        androidx.compose.material3.MaterialTheme.colorScheme.primary
    },
) {
    val resolver = LocalContext.current.contentResolver
    val lifecycleOwner = LocalLifecycleOwner.current
    var foreground by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    var animatorsEnabled by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(lifecycleOwner, resolver) {
        val observer = LifecycleEventObserver { _, event ->
            foreground = event.targetState.isAtLeast(Lifecycle.State.STARTED)
            animatorsEnabled = ValueAnimator.areAnimatorsEnabled()
        }
        val animationSettings = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                animatorsEnabled = Settings.Global.getFloat(
                    resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
                ) > 0f
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            animationSettings,
        )
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            resolver.unregisterContentObserver(animationSettings)
        }
    }
    val phase = if (foreground && animatorsEnabled) {
        rememberInfiniteTransition(label = "transferIconLoop").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                // 与运行状态图标保持相同的两秒节奏，循环首尾透明以隐藏归位。
                animation = tween(durationMillis = 2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "transferIconPhase",
        )
    } else {
        remember { mutableFloatStateOf(0.5f) }
    }
    Crossfade(
        targetState = stage,
        modifier = modifier.size(48.dp),
        animationSpec = tween(
            durationMillis = if (foreground && animatorsEnabled) 150 else 0,
            easing = LinearEasing,
        ),
        label = "transferIconStage",
    ) { visibleStage ->
        Canvas(Modifier.size(48.dp)) {
            val unit = size.minDimension / 24f
            withTransform({
                translate((size.width - 24f * unit) / 2, (size.height - 24f * unit) / 2)
                scale(unit, unit, pivot = Offset.Zero)
            }) {
                val progress = phase.value
                val visibility = minOf(progress / 0.15f, (1f - progress) / 0.25f, 1f)
                    .coerceIn(0f, 1f)
                when (visibleStage) {
                    TransferStage.DOWNLOAD -> drawDownload(color, progress, visibility)
                    TransferStage.EXTRACT -> drawExtraction(color, progress, visibility)
                    TransferStage.VERIFY -> drawVerification(color, progress, visibility)
                    TransferStage.APPLY -> drawUpdate(color, progress)
                }
            }
        }
    }
}

private val TransferStroke = Stroke(width = 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.drawDownload(color: Color, phase: Float, visibility: Float) {
    drawPath(Path().apply {
        moveTo(4f, 16f)
        lineTo(4f, 20f)
        lineTo(20f, 20f)
        lineTo(20f, 16f)
    }, color, style = TransferStroke)
    withTransform({ translate(top = phase * 5f) }) {
        drawPath(Path().apply {
            moveTo(12f, 3f)
            lineTo(12f, 12f)
            moveTo(8f, 8f)
            lineTo(12f, 12f)
            lineTo(16f, 8f)
        }, color, alpha = visibility, style = TransferStroke)
    }
}

private fun DrawScope.drawExtraction(color: Color, phase: Float, visibility: Float) {
    withTransform({ translate(top = -phase * 7f) }) {
        drawPath(Path().apply {
            moveTo(8f, 9f)
            lineTo(13f, 9f)
            lineTo(16f, 12f)
            lineTo(16f, 18f)
            lineTo(8f, 18f)
            close()
            moveTo(13f, 9f)
            lineTo(13f, 12f)
            lineTo(16f, 12f)
        }, color, alpha = visibility, style = TransferStroke)
    }
    drawPath(Path().apply {
        moveTo(4f, 12f)
        lineTo(4f, 21f)
        lineTo(20f, 21f)
        lineTo(20f, 12f)
        moveTo(4f, 12f)
        lineTo(2f, 8f)
        lineTo(10f, 10f)
        lineTo(12f, 13f)
        lineTo(14f, 10f)
        lineTo(22f, 8f)
        lineTo(20f, 12f)
    }, color, style = TransferStroke)
}

private fun DrawScope.drawVerification(color: Color, phase: Float, visibility: Float) {
    drawPath(Path().apply {
        moveTo(12f, 3f)
        lineTo(20f, 6f)
        lineTo(20f, 12f)
        cubicTo(20f, 16f, 17f, 19f, 12f, 21f)
        cubicTo(7f, 19f, 4f, 16f, 4f, 12f)
        lineTo(4f, 6f)
        close()
        moveTo(8f, 12f)
        lineTo(11f, 15f)
        lineTo(16f, 10f)
    }, color, style = TransferStroke)
    drawLine(
        color, Offset(7f, 6f + phase * 11f), Offset(17f, 6f + phase * 11f),
        strokeWidth = 1.2f, cap = StrokeCap.Round, alpha = visibility * 0.65f,
    )
}

private fun DrawScope.drawUpdate(color: Color, phase: Float) {
    rotate(phase * 360f, pivot = Offset(12f, 12f)) {
        drawArc(color, -65f, 135f, false, Offset(4f, 4f), Size(16f, 16f), style = TransferStroke)
        drawArc(color, 115f, 135f, false, Offset(4f, 4f), Size(16f, 16f), style = TransferStroke)
        drawPath(Path().apply {
            moveTo(18f, 16f)
            lineTo(15f, 20f)
            lineTo(20f, 20f)
            moveTo(6f, 8f)
            lineTo(9f, 4f)
            lineTo(4f, 4f)
        }, color, style = TransferStroke)
    }
}
