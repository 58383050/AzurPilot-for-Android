package com.azurpilot.ghio.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.azurpilot.ghio.R
import com.azurpilot.ghio.ui.ksu.AppDialog
import com.azurpilot.ghio.ui.ksu.AppSwitch
import com.azurpilot.ghio.ui.ksu.AppTextField
import com.azurpilot.ghio.ui.ksu.AppTextButton
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
    icon: AppSymbol = AppSymbol.Options,
    iconActive: Boolean? = null,
    trailing: @Composable () -> Unit,
) {
    if (LocalUiMode.current == UiMode.Miuix) {
        top.yukonga.miuix.kmp.basic.BasicComponent(
            title = label,
            startAction = { AppPreferenceIcon(icon, active = iconActive) },
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
            leadingContent = { AppPreferenceIcon(icon, active = iconActive) },
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
    icon: AppSymbol = AppSymbol.Options,
) {
    var activation by remember { mutableIntStateOf(0) }
    val open = { activation++; onClick() }
    if (LocalUiMode.current == UiMode.Miuix) {
        top.yukonga.miuix.kmp.preference.ArrowPreference(
            title = label,
            summary = description,
            onClick = open,
            startAction = { AppPreferenceIcon(icon, animationKey = activation) },
            modifier = modifier,
        )
    } else
        SegmentedListItem(
            modifier = modifier,
            onClick = open,
            leadingContent = { AppPreferenceIcon(icon, animationKey = activation) },
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



// 保持所有 AzurPilot 参数与应用设置使用同一组主题原生偏好行。
/**
 * 复用 Miuix SwitchPreference / Material SegmentedListItem 的整行可点开关。
 *
 * Uses the platform-specific settings switch row instead of a free-standing custom switch.
 */
@Composable
fun AppSwitchPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    icon: AppSymbol = AppSymbol.Power,
) {
    if (LocalUiMode.current == UiMode.Miuix) {
        top.yukonga.miuix.kmp.preference.SwitchPreference(
            title = title,
            summary = summary,
            checked = checked,
            startAction = { AppPreferenceIcon(icon, active = checked) },
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
        )
    } else {
        SegmentedListItem(
            modifier = modifier,
            onClick = { if (enabled) onCheckedChange(!checked) },
            headlineContent = { Text(title) },
            leadingContent = { AppPreferenceIcon(icon, active = checked) },
            supportingContent = summary?.let { { Text(it) } },
            trailingContent = {
                AppSwitch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                )
            },
        )
    }
}

/**
 * 使用主题原生复选设置行统一点击与无障碍状态，整行只触发一次值变更。
 *
 * Uses themed checkbox preference rows with unified clicks and accessibility state, triggering
 * exactly one value change per row activation.
 */
@Composable
fun AppCheckboxPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: AppSymbol = AppSymbol.Check,
) {
    if (LocalUiMode.current == UiMode.Miuix) {
        top.yukonga.miuix.kmp.preference.CheckboxPreference(
            title = title,
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            checkboxLocation = top.yukonga.miuix.kmp.preference.CheckboxLocation.End,
            startAction = { AppPreferenceIcon(icon, active = checked) },
        )
    } else {
        SegmentedListItem(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            headlineContent = { Text(title) },
            leadingContent = { AppPreferenceIcon(icon, active = checked) },
            trailingContent = {
                com.azurpilot.ghio.ui.ksu.AppCheckbox(checked, onCheckedChange = null)
            },
        )
    }
}

/**
 * 常规字符串与数字设置：列表只占一行，编辑时使用与主设置相同的原生弹窗和文本框。
 *
 * Simple string/number preference edited in a themed dialog. Validation happens before dismissal.
 */
@Composable
fun AppEditablePreference(
    title: String,
    value: String,
    onSave: (String) -> Boolean,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    icon: AppSymbol = if (password) AppSymbol.Lock else AppSymbol.Edit,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(value) }
    var invalid by remember { mutableStateOf(false) }
    val detail = summary ?: if (password && value.isNotEmpty()) "••••••" else value.ifEmpty { "—" }
    if (enabled) {
        AppNavigationRow(
            label = title,
            description = detail,
            icon = icon,
            modifier = modifier,
            onClick = {
                draft = value
                invalid = false
                editing = true
            },
        )
    } else {
        AppInfoRow(label = title, value = detail, icon = icon)
    }
    if (editing) {
        AppDialog(
            onDismissRequest = { editing = false },
            title = { Text(title) },
            text = {
                AppTextField(
                    value = draft,
                    onValueChange = {
                        draft = it
                        invalid = false
                    },
                    labelText = title,
                    leadingIcon = { AppSymbolIcon(icon) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    visualTransformation =
                        if (password) PasswordVisualTransformation() else VisualTransformation.None,
                    isError = invalid,
                    supportingText = {
                        if (invalid) {
                            Text(
                                stringResource(R.string.ap_field_invalid),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                AppTextButton(onClick = {
                    if (onSave(draft)) {
                        editing = false
                    } else {
                        invalid = true
                    }
                }) { Text(stringResource(R.string.ap_deploy_save)) }
            },
            dismissButton = {
                AppTextButton(onClick = { editing = false }) {
                    Text(stringResource(R.string.ap_cancel))
                }
            },
        )
    }
}

/** 信息通过主题列表行展示。 / Displays information through themed list rows. */
@Composable
fun AppInfoRow(label: String, value: String, icon: AppSymbol = AppSymbol.Info) {
    if (LocalUiMode.current == UiMode.Miuix)
        top.yukonga.miuix.kmp.basic.BasicComponent(
            title = label,
            summary = value,
            startAction = { AppPreferenceIcon(icon) },
            insideMargin =
                if (LocalInsideAppCard.current) PaddingValues(vertical = 12.dp)
                else top.yukonga.miuix.kmp.basic.BasicComponentDefaults.InsideMargin,
        )
    else SegmentedListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(value) },
        leadingContent = { AppPreferenceIcon(icon) },
    )
}
