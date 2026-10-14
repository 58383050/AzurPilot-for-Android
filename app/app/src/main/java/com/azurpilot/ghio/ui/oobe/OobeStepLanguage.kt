package com.azurpilot.ghio.ui.oobe

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.azurpilot.ghio.R
import com.azurpilot.ghio.i18n.AppLocales

/**
 * 单独呈现语言选择，系统重建后由向导保存当前步骤。
 *
 * Presents language choices separately, retaining the wizard step across locale recreation.
 */
@Composable
internal fun OobeStepLanguage() {
    val options = listOf(null to stringResource(R.string.settings_follow_system),
        "zh-CN" to "简体中文", "zh-TW" to "繁體中文", "en" to "English", "ja" to "日本語")
    val raw = AppLocales.currentTag()?.replace('_', '-')?.lowercase()
    val selected = when {
        raw == null -> null
        raw.startsWith("zh") -> if (raw.contains("hant") || raw.endsWith("-tw") || raw.endsWith("-hk")) "zh-TW" else "zh-CN"
        else -> raw.substringBefore('-')
    }
    Column(Modifier.fillMaxWidth()) {
        OobePageHeader(stringResource(R.string.oobe_pref_lang_title), null, Icons.Outlined.Translate, OobeGlyph.LANGUAGE)
        OobeChoiceGroup(null, options, selected, onSelect = { if (it != selected) AppLocales.apply(it) })
    }
}
