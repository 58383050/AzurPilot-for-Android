@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.azurpilot.ghio.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * 补齐 MD3 的 colorScheme 没有的业务语义色角色
 *
 * MD3 的 colorScheme 只定义 primary / secondary / tertiary / error 四族，没有 success、warning 这类业务语义角色。按 MD3
 * 的自定义颜色角色（custom color role）约定补上，随明暗切换。
 *
 * 与内置角色一样成对给出：色是「容器」，[onSuccess] 是压在它上面的前景—— 少了这一半，深色档下的浅绿底就只能配上纯白前景，对比度掉到读不出。
 *
 * Adds the business-semantic color roles the MD3 colorScheme lacks.
 *
 * The MD3 colorScheme defines only the primary/secondary/tertiary/error families, with no
 * business-semantic roles such as success or warning. These follow the MD3 custom color role
 * convention and switch with dark/light.
 *
 * Like the built-in roles they come in pairs: the color is the "container" and [onSuccess] is the
 * foreground pressed onto it — without that half, the light green container in dark mode would be
 * stuck with pure-white foreground and the contrast would sink below readable.
 */
data class AppPalette(
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
)

private val LightPalette =
    AppPalette(
        success = Color(0xFF146C2E),
        onSuccess = Color(0xFFFFFFFF),
        warning = Color(0xFF8F4C00),
    )

private val DarkPalette =
    AppPalette(
        success = Color(0xFF7FDA95),
        onSuccess = Color(0xFF00391A),
        warning = Color(0xFFFFB86B),
    )

/**
 * 携带 [AppPalette] 的 CompositionLocal；未显式 provide 时默认浅色档。
 *
 * The CompositionLocal carrying [AppPalette];
 * defaults to the light palette when not explicitly provided.
 */
val LocalAppPalette = staticCompositionLocalOf { LightPalette }

/**
 * 主题扩展读取入口；Screen 不直接碰 CompositionLocal。
 *
 * The read entry for the theme extensions; Screens never
 * touch the CompositionLocal directly.
 */
object AzurPilotTheme {
    val palette: AppPalette
        @Composable @ReadOnlyComposable get() = LocalAppPalette.current
}

/**
 * 解析当前明暗档对应的 ColorScheme
 *
 * Android 12 起默认取系统动态色（Material You），其余版本回落到 MD3 基线 色板——两条路都是 MD3 规范内的方案，不再维护自定义色盘。
 *
 * Resolves the ColorScheme for the current dark/light setting.
 *
 * From Android 12 on, the system dynamic color (Material You) is the default; older versions fall
 * back to the MD3 baseline palette — both paths are within the MD3 spec, and no custom palette is
 * maintained.
 */
@Composable
private fun colorSchemeOf(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme {
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    return when {
        useDynamic && darkTheme -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
}

/**
 * 提供全 App 的 Material 3 Expressive 主题，组件统一使用原生运动方案。
 *
 * 明暗和动态色继续遵循用户设置，形状与字阶沿用现有 Material 3 基线。
 *
 * Provides the app-wide Material 3 Expressive theme with native component motion.
 * Dark mode and dynamic color follow user settings, retaining existing Material 3
 * shape and typography baselines.
 */
@Composable
fun AzurPilotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAppPalette provides if (darkTheme) DarkPalette else LightPalette
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorSchemeOf(darkTheme, dynamicColor),
            motionScheme = MotionScheme.expressive(),
            shapes = Shapes(),
            typography = Typography(),
            content = content,
        )
    }
}
