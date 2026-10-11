package com.azurpilot.ghio.ui

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.azurpilot.ghio.R

/**
 * 定义主导航的稳定页序和图标；页序同时用于保存各 Tab 状态。
 *
 * Defines stable main navigation order and icons; page order also keys saved tab state.
 */
internal enum class TopDestination(
    @param:StringRes val labelRes: Int,
    val outlinedIcon: ImageVector,
    val filledIcon: ImageVector,
) {
    /** 主页。 / Home. */
    Hangar(R.string.nav_hangar, Icons.Outlined.PlayCircle, Icons.Filled.PlayCircle),
    /** 运行时控制台。 / Runtime console. */
    AzurPilot(R.string.nav_azurpilot, Icons.Outlined.Public, Icons.Filled.Public),
    /** 应用设置。 / App settings. */
    Settings(R.string.nav_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
    /** 虚拟屏。 / Virtual display. */
    Screen(R.string.nav_screen, Icons.Outlined.PhoneAndroid, Icons.Filled.PhoneAndroid),
}

/**
 * 在页面转场外维持导航栏与消息宿主；横屏使用导轨，竖屏使用底栏。
 *
 * Scaffold 统一测量并消费底栏边距，详情页仍自行处理状态栏和键盘边距。
 *
 * Keeps navigation and snackbars outside page transitions, using a rail in landscape and a bottom
 * bar in portrait. Scaffold measures and consumes bottom bar insets; detail pages still own status
 * bar and keyboard insets.
 */
@Composable
internal fun AppNavigationScaffold(
    selectedPage: Int,
    onSelectPage: (Int) -> Unit,
    snackbarHostState: SnackbarHostState,
    content: @Composable () -> Unit,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!landscape) {
                NavigationBar {
                    TopDestination.entries.forEachIndexed { index, destination ->
                        NavigationBarItem(
                            selected = selectedPage == index,
                            onClick = { onSelectPage(index) },
                            icon = { DestinationIcon(destination, selectedPage == index) },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Row(modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (landscape) {
                NavigationRail {
                    TopDestination.entries.forEachIndexed { index, destination ->
                        NavigationRailItem(
                            selected = selectedPage == index,
                            onClick = { onSelectPage(index) },
                            icon = { DestinationIcon(destination, selectedPage == index) },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxSize()) { content() }
        }
    }
}

@Composable
private fun DestinationIcon(destination: TopDestination, selected: Boolean) {
    Icon(
        imageVector = if (selected) destination.filledIcon else destination.outlinedIcon,
        contentDescription = stringResource(destination.labelRes),
    )
}
