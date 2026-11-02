package com.nakamura196.kotenocr.ui.screens

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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nakamura196.kotenocr.R
import com.nakamura196.kotenocr.data.ApiProvider
import com.nakamura196.kotenocr.data.LocalAIStatus
import com.nakamura196.kotenocr.data.OnDeviceTranslator
import com.nakamura196.kotenocr.data.SettingsManager
import com.nakamura196.kotenocr.data.TargetLanguage
import com.nakamura196.kotenocr.data.TranslationLevel
import com.nakamura196.kotenocr.ui.theme.KotenColor
import com.nakamura196.kotenocr.ui.theme.KotenGlassButton
import com.nakamura196.kotenocr.ui.theme.KotenProminentButton
import com.nakamura196.kotenocr.ui.theme.WashiBackground
import com.nakamura196.kotenocr.ui.theme.minchoStyle
import kotlinx.coroutines.launch

/**
 * Modern Translation Settings screen (`TranslationSettingsView.swift` v2.1 port) styled with Washi/Ink/Shu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationSettingsScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var selectedProvider by remember { mutableStateOf(settingsManager.apiProvider) }
    var apiKey by remember { mutableStateOf(settingsManager.apiKey) }
    var apiEndpoint by remember { mutableStateOf(settingsManager.apiEndpoint) }
    var apiModel by remember { mutableStateOf(settingsManager.apiModel) }
    var showApiKey by remember { mutableStateOf(false) }
    var targetLanguage by remember { mutableStateOf(settingsManager.targetLanguage) }
    var translationLevel by remember { mutableStateOf(settingsManager.translationLevel) }

    var providerMenuExpanded by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }

    val savedTitle = stringResource(R.string.api_key_saved_title)

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
                KotenGlassButton(
                    onClick = onBack,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.back),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = stringResource(R.string.settings_translation_nav),
                    style = minchoStyle(size = 19.sp, bold = true, color = KotenColor.ink),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.width(64.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Translation options card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = KotenColor.washiCard,
                    border = BorderStroke(1.dp, KotenColor.rule)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_target_language),
                            style = minchoStyle(size = 15.sp, bold = true, color = KotenColor.ink)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val targets = listOf(
                                TargetLanguage.JA to R.string.target_lang_ja,
                                TargetLanguage.EN to R.string.target_lang_en
                            )
                            targets.forEach { (lang, labelRes) ->
                                val selected = targetLanguage == lang
                                Surface(
                                    onClick = {
                                        targetLanguage = lang
                                        settingsManager.targetLanguage = lang
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

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = stringResource(R.string.settings_translation_level),
                            style = minchoStyle(size = 15.sp, bold = true, color = KotenColor.ink)
                        )

                        val levels = listOf(
                            Triple(TranslationLevel.GENERAL, R.string.level_general, R.string.level_general_desc),
                            Triple(TranslationLevel.STUDENT, R.string.level_student, R.string.level_student_desc),
                            Triple(TranslationLevel.RESEARCHER, R.string.level_researcher, R.string.level_researcher_desc)
                        )
                        levels.forEach { (lvl, labelRes, descRes) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        translationLevel = lvl
                                        settingsManager.translationLevel = lvl
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = translationLevel == lvl,
                                    onClick = {
                                        translationLevel = lvl
                                        settingsManager.translationLevel = lvl
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = KotenColor.shu)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = stringResource(labelRes),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = KotenColor.ink
                                    )
                                    Text(
                                        text = stringResource(descRes),
                                        fontSize = 12.sp,
                                        color = KotenColor.inkSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // API Configuration card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = KotenColor.washiCard,
                    border = BorderStroke(1.dp, KotenColor.rule)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_api_config),
                            style = minchoStyle(size = 15.sp, bold = true, color = KotenColor.ink)
                        )

                        ExposedDropdownMenuBox(
                            expanded = providerMenuExpanded,
                            onExpandedChange = { providerMenuExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = providerName(selectedProvider),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.settings_api_provider)) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerMenuExpanded)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(
                                expanded = providerMenuExpanded,
                                onDismissRequest = { providerMenuExpanded = false }
                            ) {
                                // Like iOS hiding it below iOS 26: offered only where it works
                                // (kept in the list if it is the current choice).
                                ApiProvider.entries.filter {
                                    it != ApiProvider.LOCAL_AI || settingsManager.localAISupported ||
                                        selectedProvider == ApiProvider.LOCAL_AI
                                }.forEach { provider ->
                                    DropdownMenuItem(
                                        text = { Text(providerName(provider)) },
                                        onClick = {
                                            selectedProvider = provider
                                            providerMenuExpanded = false
                                            // Nothing else to enter, so it takes effect at once
                                            // (iOS saves every provider change immediately).
                                            if (provider == ApiProvider.LOCAL_AI) {
                                                settingsManager.apiProvider = provider
                                            } else if (provider != ApiProvider.CUSTOM) {
                                                apiEndpoint = provider.endpoint
                                                if (provider.models.none { it.first == apiModel }) {
                                                    apiModel = provider.models.first().first
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        if (selectedProvider == ApiProvider.LOCAL_AI) {
                            LocalAIStatusRow()
                        } else if (selectedProvider != ApiProvider.CUSTOM) {
                            val currentModelDisplay = selectedProvider.models
                                .firstOrNull { it.first == apiModel }?.second ?: apiModel

                            ExposedDropdownMenuBox(
                                expanded = modelMenuExpanded,
                                onExpandedChange = { modelMenuExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = currentModelDisplay,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(R.string.settings_api_model)) },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuExpanded)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                )
                                ExposedDropdownMenu(
                                    expanded = modelMenuExpanded,
                                    onDismissRequest = { modelMenuExpanded = false }
                                ) {
                                    selectedProvider.models.forEach { (id, display) ->
                                        DropdownMenuItem(
                                            text = { Text(display) },
                                            onClick = {
                                                apiModel = id
                                                modelMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = apiEndpoint,
                                onValueChange = { apiEndpoint = it },
                                label = { Text(stringResource(R.string.settings_api_endpoint)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = apiModel,
                                onValueChange = { apiModel = it },
                                label = { Text(stringResource(R.string.settings_api_model)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (selectedProvider.requiresApiKey) {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text(stringResource(R.string.settings_api_key)) },
                            singleLine = true,
                            visualTransformation = if (showApiKey) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        imageVector = if (showApiKey) {
                                            Icons.Default.VisibilityOff
                                        } else {
                                            Icons.Default.Visibility
                                        },
                                        contentDescription = null
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        KotenProminentButton(
                            onClick = {
                                settingsManager.apiProvider = selectedProvider
                                settingsManager.apiKey = apiKey.trim()
                                settingsManager.apiEndpoint = apiEndpoint.trim().ifEmpty {
                                    SettingsManager.DEFAULT_ENDPOINT
                                }
                                settingsManager.apiModel = apiModel.trim().ifEmpty {
                                    SettingsManager.DEFAULT_MODEL
                                }
                                scope.launch {
                                    snackbarHostState.showSnackbar(savedTitle)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_api_save),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        }

                        Text(
                            text = stringResource(
                                if (selectedProvider == ApiProvider.LOCAL_AI) R.string.local_ai_footer
                                else R.string.settings_api_footer
                            ),
                            fontSize = 12.sp,
                            color = KotenColor.inkSecondary
                        )
                    }
                }
            }

            SnackbarHost(hostState = snackbarHostState)
        }
    }
}

@Composable
private fun providerName(provider: ApiProvider): String = when (provider) {
    ApiProvider.LOCAL_AI -> stringResource(R.string.provider_local_ai)
    ApiProvider.CUSTOM -> stringResource(R.string.provider_custom)
    else -> provider.displayName
}

/** iOS shows the same line under the picker (`TranslationSettingsView`, `localAIStatus.message`). */
@Composable
private fun LocalAIStatusRow() {
    var status by remember { mutableStateOf<LocalAIStatus?>(null) }
    LaunchedEffect(Unit) { status = OnDeviceTranslator.status() }
    val (textRes, color) = when (status) {
        null -> R.string.local_ai_checking to KotenColor.inkSecondary
        LocalAIStatus.AVAILABLE -> R.string.local_ai_ready to KotenColor.ink
        LocalAIStatus.DOWNLOADABLE -> R.string.local_ai_downloadable to KotenColor.ink
        LocalAIStatus.DOWNLOADING -> R.string.local_ai_downloading to KotenColor.ink
        LocalAIStatus.UNAVAILABLE -> R.string.local_ai_unavailable to KotenColor.shu
    }
    Text(text = stringResource(textRes), fontSize = 13.sp, color = color)
}
