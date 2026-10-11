package com.azurpilot.ghio.ui.ksu

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.azurpilot.ghio.theme.ksu.LocalUiMode
import com.azurpilot.ghio.theme.ksu.UiMode

/** KSU 两种主题的下拉设置行。 / KSU dropdown preference for both themes. */
@Composable
fun AppDropdownPreference(
    title: String,
    modifier: Modifier = Modifier,
    items: List<String>,
    selectedIndex: Int,
    enabled: Boolean = true,
    onSelectedIndexChange: (Int) -> Unit,
) {
    if (items.isEmpty()) return
    androidx.compose.foundation.layout.Box(modifier) {
        if (LocalUiMode.current == UiMode.Miuix)
            top.yukonga.miuix.kmp.preference.OverlayDropdownPreference(
                title = title,
                items = items,
                selectedIndex = selectedIndex,
                enabled = enabled,
                onSelectedIndexChange = onSelectedIndexChange,
            )
        else
            com.azurpilot.ghio.ui.ksu.material.SegmentedDropdownItem(
                title = title,
                items = items,
                selectedIndex = selectedIndex,
                enabled = enabled,
                onItemSelected = onSelectedIndexChange,
            )
    }
}
