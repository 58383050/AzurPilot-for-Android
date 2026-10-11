package com.azurpilot.ghio.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.azurpilot.ghio.theme.ksu.LocalUiMode
import com.azurpilot.ghio.theme.ksu.UiMode
import com.azurpilot.ghio.ui.ksu.AppIcon as Icon
import com.azurpilot.ghio.ui.ksu.AppText as Text
import com.azurpilot.ghio.ui.ksu.material.SegmentedListItem
import com.azurpilot.ghio.ui.ksu.material.TonalCard

private val LocalInsideAppCard = compositionLocalOf { false }

/** KSU 卡片的业务内容适配器。 / Business content adapter for KSU cards. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    collapsible: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) AppFieldLabel(title, Modifier.padding(start = 16.dp, bottom = 8.dp))
        if (LocalUiMode.current == UiMode.Miuix) {
            top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(contentPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = {
                        CompositionLocalProvider(LocalInsideAppCard provides true) { content() }
                    },
                )
            }
        } else
            TonalCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(contentPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = {
                        CompositionLocalProvider(LocalInsideAppCard provides true) { content() }
                    },
                )
            }
    }
}

/** 使用 KSU 列表行的尾部控件槽。 / Uses KSU list rows with a trailing control. */
@Composable
fun AppLabeledControlRow(
    label: String,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    labelColor: Color = Color.Unspecified,
    trailing: @Composable () -> Unit,
) {
    if (LocalUiMode.current == UiMode.Miuix) {
        top.yukonga.miuix.kmp.basic.BasicComponent(
            title = label,
            insideMargin =
                if (LocalInsideAppCard.current) PaddingValues(vertical = 12.dp)
                else top.yukonga.miuix.kmp.basic.BasicComponentDefaults.InsideMargin,
            modifier = modifier,
            endActions = { trailing() },
        )
    } else
        SegmentedListItem(
            modifier = modifier,
            headlineContent = { Text(label, style = labelStyle, color = labelColor) },
            trailingContent = trailing,
        )
}

/** 使用 KSU 的主题导航行。 / Uses KSU's themed navigation row. */
@Composable
fun AppNavigationRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    if (LocalUiMode.current == UiMode.Miuix) {
        top.yukonga.miuix.kmp.preference.ArrowPreference(
            title = label,
            summary = description,
            onClick = onClick,
            modifier = modifier,
        )
    } else
        SegmentedListItem(
            modifier = modifier,
            onClick = onClick,
            headlineContent = { Text(label) },
            supportingContent = description?.let { { Text(it) } },
            trailingContent = {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            },
        )
}

/** KSU 无边框卡片。 / KSU tonal card. */
@Composable
fun AppCardSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (LocalUiMode.current == UiMode.Miuix)
        top.yukonga.miuix.kmp.basic.Card(modifier = modifier) { content() }
    else TonalCard(modifier = modifier, content = content)
}

/** KSU 设置分组标题。 / KSU settings group heading. */
@Composable
fun AppFieldLabel(text: String, modifier: Modifier = Modifier) {
    if (LocalUiMode.current == UiMode.Miuix)
        if (LocalInsideAppCard.current)
            top.yukonga.miuix.kmp.basic.Text(
                text = text,
                modifier = modifier,
                style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body2,
                color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        else top.yukonga.miuix.kmp.basic.SmallTitle(text = text, modifier = modifier)
    else
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier,
        )
}

/** 信息通过主题列表行展示。 / Displays information through themed list rows. */
@Composable
fun AppInfoRow(label: String, value: String) {
    if (LocalUiMode.current == UiMode.Miuix)
        top.yukonga.miuix.kmp.basic.BasicComponent(
            title = label,
            summary = value,
            insideMargin =
                if (LocalInsideAppCard.current) PaddingValues(vertical = 12.dp)
                else top.yukonga.miuix.kmp.basic.BasicComponentDefaults.InsideMargin,
        )
    else SegmentedListItem(headlineContent = { Text(label) }, supportingContent = { Text(value) })
}
