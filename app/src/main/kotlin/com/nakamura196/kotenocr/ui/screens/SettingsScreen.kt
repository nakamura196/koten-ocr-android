package com.nakamura196.kotenocr.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nakamura196.kotenocr.R
import com.nakamura196.kotenocr.data.AppLanguage
import com.nakamura196.kotenocr.data.SettingsManager
import com.nakamura196.kotenocr.data.ThemeMode
import com.nakamura196.kotenocr.ui.theme.KotenColor
import com.nakamura196.kotenocr.ui.theme.KotenGlassButton
import com.nakamura196.kotenocr.ui.theme.KotenSeal
import com.nakamura196.kotenocr.ui.theme.WashiBackground
import com.nakamura196.kotenocr.ui.theme.minchoStyle

/**
 * Settings screen (`SettingsView.swift` v2.1 port).
 * Includes:
 * - About (`KotenOCR` + version)
 * - Modern Translation settings navigation
 * - Camera settings: Permission status, Save to Photo Library, Show Tilt Guide (`傾きの案内を表示`), Auto-correct Tilt (`傾きを自動補正`)
 * - Appearance (`システム / ライト / ダーク`)
 * - Tips & Guides: Replay Introduction (`使い方をもう一度見る`), Show Tips Again (`操作のヒントをもう一度表示`)
 * - Language (`システム / 日本語 / English`)
 * - Feedback email
 * - Licenses (NDL古典籍OCR-Lite, NDLOCR-Lite, ONNX Runtime, Sample manuscript & sample pages credits)
 * - Links (NDL Lab GitHub, Privacy Policy)
 */
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    onThemeChanged: (ThemeMode) -> Unit,
    onNavigateToTranslationSettings: () -> Unit,
    onShowOnboarding: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var currentTheme by remember { mutableStateOf(settingsManager.themeMode) }
    var currentLanguage by remember { mutableStateOf(settingsManager.appLanguage) }
    var saveToGallery by remember { mutableStateOf(settingsManager.saveToGallery) }
    var showTiltGuide by remember { mutableStateOf(settingsManager.showTiltGuide) }
    var autoDeskew by remember { mutableStateOf(settingsManager.autoDeskew) }
    var tipsReset by remember { mutableStateOf(false) }

    val hasCameraPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    val appVersion = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "2.1.0"
        } catch (_: Exception) {
            "2.1.0"
        }
    }

    WashiBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(40.dp))
                Text(
                    text = stringResource(R.string.settings_title),
                    style = minchoStyle(size = 20.sp, bold = true, color = KotenColor.ink),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                KotenGlassButton(
                    onClick = onClose,
                    isCircle = true,
                    modifier = Modifier.size(40.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // About
                SettingsCardSection(title = stringResource(R.string.settings_about)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        KotenSeal(size = 42.dp)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "KotenOCR",
                                style = minchoStyle(size = 18.sp, bold = true, color = KotenColor.ink)
                            )
                            Text(
                                text = "${stringResource(R.string.settings_version)} $appVersion",
                                fontSize = 13.sp,
                                color = KotenColor.inkSecondary
                            )
                        }
                    }
                }

                // Modern Translation API
                SettingsCardSection(title = stringResource(R.string.settings_translation_api)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNavigateToTranslationSettings)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = KotenColor.shu,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.settings_translation_nav),
                            fontSize = 15.sp,
                            color = KotenColor.ink,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = KotenColor.inkSecondary
                        )
                    }
                }

                // Camera & OCR Assist
                SettingsCardSection(title = stringResource(R.string.settings_camera)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_camera_permission),
                            fontSize = 15.sp,
                            color = KotenColor.ink,
                            modifier = Modifier.weight(1f)
                        )
                        if (hasCameraPermission) {
                            Text(
                                text = stringResource(R.string.camera_status_authorized),
                                fontSize = 14.sp,
                                color = KotenColor.inkSecondary
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.camera_open_settings),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = KotenColor.shu,
                                modifier = Modifier.clickable {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }

                    HorizontalDivider(color = KotenColor.rule.copy(alpha = 0.5f))

                    SettingsToggleRow(
                        label = stringResource(R.string.settings_save_to_library),
                        checked = saveToGallery,
                        onCheckedChange = {
                            saveToGallery = it
                            settingsManager.saveToGallery = it
                        }
                    )

                    HorizontalDivider(color = KotenColor.rule.copy(alpha = 0.5f))

                    SettingsToggleRow(
                        label = stringResource(R.string.settings_show_tilt_guide),
                        checked = showTiltGuide,
                        onCheckedChange = {
                            showTiltGuide = it
                            settingsManager.showTiltGuide = it
                        }
                    )

                    HorizontalDivider(color = KotenColor.rule.copy(alpha = 0.5f))

                    SettingsToggleRow(
                        label = stringResource(R.string.settings_auto_deskew),
                        checked = autoDeskew,
                        onCheckedChange = {
                            autoDeskew = it
                            settingsManager.autoDeskew = it
                        }
                    )
                }

                // Appearance
                SettingsCardSection(title = stringResource(R.string.settings_theme)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val themes = listOf(
                            ThemeMode.SYSTEM to R.string.theme_system,
                            ThemeMode.LIGHT to R.string.theme_light,
                            ThemeMode.DARK to R.string.theme_dark
                        )
                        themes.forEach { (mode, labelRes) ->
                            val selected = currentTheme == mode
                            Surface(
                                onClick = {
                                    currentTheme = mode
                                    settingsManager.themeMode = mode
                                    onThemeChanged(mode)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) KotenColor.shuFill else KotenColor.mount,
                                border = BorderStroke(1.dp, if (selected) KotenColor.shuFill else KotenColor.rule)
                            ) {
                                Text(
                                    text = stringResource(labelRes),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) Color.White else KotenColor.ink,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                // Tips & Guides
                SettingsCardSection(
                    title = null,
                    footer = stringResource(R.string.settings_tips_footer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onShowOnboarding)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_show_onboarding),
                            fontSize = 15.sp,
                            color = KotenColor.shu,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = KotenColor.inkSecondary
                        )
                    }

                    HorizontalDivider(color = KotenColor.rule.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                settingsManager.resetCoachMarks()
                                tipsReset = true
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_show_tips_again),
                            fontSize = 15.sp,
                            color = KotenColor.shu,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        if (tipsReset) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = KotenColor.shu,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Language
                SettingsCardSection(title = stringResource(R.string.settings_language)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val languages = listOf(
                            AppLanguage.SYSTEM to R.string.language_system,
                            AppLanguage.JA to R.string.language_ja,
                            AppLanguage.EN to R.string.language_en
                        )
                        languages.forEach { (lang, labelRes) ->
                            val selected = currentLanguage == lang
                            Surface(
                                onClick = {
                                    currentLanguage = lang
                                    settingsManager.appLanguage = lang
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) KotenColor.shuFill else KotenColor.mount,
                                border = BorderStroke(1.dp, if (selected) KotenColor.shuFill else KotenColor.rule)
                            ) {
                                Text(
                                    text = stringResource(labelRes),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) Color.White else KotenColor.ink,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                // Feedback
                SettingsCardSection(title = stringResource(R.string.settings_feedback)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val subject = Uri.encode("KotenOCR Android Feedback (v$appVersion)")
                                val body = Uri.encode(
                                    "\n\n---\nDevice: ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE}\nApp: $appVersion"
                                )
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:na.kamura.1263@gmail.com?subject=$subject&body=$body")
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                }
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = KotenColor.shu,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.settings_send_feedback),
                            fontSize = 15.sp,
                            color = KotenColor.ink
                        )
                    }
                }

                // Licenses & Sample Image Credits
                SettingsCardSection(title = stringResource(R.string.settings_licenses)) {
                    LicenseRow(
                        title = "NDL古典籍OCR-Lite",
                        subtitle = "CC-BY-4.0 — 国立国会図書館",
                        url = "https://github.com/ndl-lab/ndlkotenocr-lite"
                    )
                    HorizontalDivider(color = KotenColor.rule.copy(alpha = 0.5f))
                    LicenseRow(
                        title = "NDLOCR-Lite",
                        subtitle = "CC-BY-4.0 — 国立国会図書館",
                        url = "https://github.com/ndl-lab/ndlocr-lite"
                    )
                    HorizontalDivider(color = KotenColor.rule.copy(alpha = 0.5f))
                    LicenseRow(
                        title = "ONNX Runtime",
                        subtitle = "MIT License — Microsoft",
                        url = "https://github.com/microsoft/onnxruntime"
                    )
                    HorizontalDivider(color = KotenColor.rule.copy(alpha = 0.5f))
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.settings_sample_image_title),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = KotenColor.ink
                        )
                        Text(
                            text = stringResource(R.string.onboarding_sample_credit),
                            fontSize = 12.sp,
                            color = KotenColor.inkSecondary
                        )
                    }
                    HorizontalDivider(color = KotenColor.rule.copy(alpha = 0.5f))
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.settings_sample_pages_title),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = KotenColor.ink
                        )
                        Text(
                            text = stringResource(R.string.settings_sample_pages_credit),
                            fontSize = 12.sp,
                            color = KotenColor.inkSecondary
                        )
                    }
                }

                // Links
                SettingsCardSection(title = stringResource(R.string.settings_links)) {
                    LinkRow(
                        title = "NDL Lab GitHub",
                        url = "https://github.com/ndl-lab/ndlkotenocr-lite"
                    )
                    HorizontalDivider(color = KotenColor.rule.copy(alpha = 0.5f))
                    LinkRow(
                        title = stringResource(R.string.settings_privacy_policy),
                        url = "https://kotenocr.ldas.jp/privacy/"
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SettingsCardSection(
    title: String?,
    footer: String? = null,
    content: @Composable () -> Unit
) {
    Column {
        if (title != null) {
            Text(
                text = title,
                style = minchoStyle(size = 14.sp, bold = true, color = KotenColor.inkSecondary),
                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
            )
        }
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = KotenColor.washiCard,
            border = BorderStroke(1.dp, KotenColor.rule)
        ) {
            Column {
                content()
            }
        }
        if (footer != null) {
            Text(
                text = footer,
                fontSize = 12.sp,
                color = KotenColor.inkSecondary,
                modifier = Modifier.padding(start = 6.dp, top = 6.dp)
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = KotenColor.ink,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = KotenColor.shu
            )
        )
    }
}

@Composable
private fun LicenseRow(
    title: String,
    subtitle: String,
    url: String
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {
                }
            }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = KotenColor.ink
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = KotenColor.inkSecondary
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = KotenColor.inkSecondary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun LinkRow(
    title: String,
    url: String
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {
                }
            }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            color = KotenColor.ink,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = KotenColor.inkSecondary,
            modifier = Modifier.size(16.dp)
        )
    }
}
