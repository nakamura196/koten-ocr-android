package com.nakamura196.kotenocr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.nakamura196.kotenocr.data.ThemeMode

/**
 * Color tokens for the KotenOCR v2 "Washi / Ink / Shu (和紙・墨・朱)" aesthetic.
 * Matches KotenOCR Assets.xcassets colorsets and KotenStyle.swift.
 */
data class KotenPalette(
    val isDark: Boolean,
    val washi: Color,
    val washiCard: Color,
    val ink: Color,
    val inkSecondary: Color,
    val shu: Color,
    val shuFill: Color,
    val mount: Color,
    val rule: Color,
    val docBackground: Color
)

val LightKotenPalette = KotenPalette(
    isDark = false,
    washi = Color(0.953f, 0.922f, 0.863f),
    washiCard = Color(0.984f, 0.965f, 0.925f),
    ink = Color(0.169f, 0.149f, 0.133f),
    inkSecondary = Color(0.420f, 0.373f, 0.322f),
    shu = Color(0.710f, 0.271f, 0.184f),
    shuFill = Color(0.710f, 0.271f, 0.184f),
    mount = Color(0.910f, 0.863f, 0.776f),
    rule = Color(0.796f, 0.722f, 0.612f),
    docBackground = Color(0.114f, 0.106f, 0.098f) // iOS: dark board in both themes
)

val DarkKotenPalette = KotenPalette(
    isDark = true,
    washi = Color(0.114f, 0.102f, 0.090f),
    washiCard = Color(0.165f, 0.149f, 0.133f),
    ink = Color(0.945f, 0.902f, 0.824f),
    inkSecondary = Color(0.737f, 0.682f, 0.596f),
    shu = Color(0.878f, 0.408f, 0.298f),
    shuFill = Color(0.769f, 0.322f, 0.227f),
    mount = Color(0.208f, 0.188f, 0.165f),
    rule = Color(0.302f, 0.275f, 0.239f),
    docBackground = Color(0.114f, 0.106f, 0.098f)
)

val LocalKotenColors = staticCompositionLocalOf { LightKotenPalette }

object KotenColor {
    val current: KotenPalette
        @Composable
        get() = LocalKotenColors.current

    val washi: Color
        @Composable
        get() = LocalKotenColors.current.washi

    val washiCard: Color
        @Composable
        get() = LocalKotenColors.current.washiCard

    val ink: Color
        @Composable
        get() = LocalKotenColors.current.ink

    val inkSecondary: Color
        @Composable
        get() = LocalKotenColors.current.inkSecondary

    val shu: Color
        @Composable
        get() = LocalKotenColors.current.shu

    val shuFill: Color
        @Composable
        get() = LocalKotenColors.current.shuFill

    val mount: Color
        @Composable
        get() = LocalKotenColors.current.mount

    val rule: Color
        @Composable
        get() = LocalKotenColors.current.rule

    val docBackground: Color
        @Composable
        get() = LocalKotenColors.current.docBackground
}

private val LightColorScheme = lightColorScheme(
    primary = LightKotenPalette.shu,
    onPrimary = Color.White,
    primaryContainer = LightKotenPalette.mount,
    onPrimaryContainer = LightKotenPalette.ink,
    secondary = LightKotenPalette.inkSecondary,
    onSecondary = Color.White,
    secondaryContainer = LightKotenPalette.mount,
    onSecondaryContainer = LightKotenPalette.ink,
    background = LightKotenPalette.washi,
    onBackground = LightKotenPalette.ink,
    surface = LightKotenPalette.washiCard,
    onSurface = LightKotenPalette.ink,
    surfaceVariant = LightKotenPalette.mount,
    onSurfaceVariant = LightKotenPalette.inkSecondary,
    outline = LightKotenPalette.rule
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkKotenPalette.shu,
    onPrimary = Color.White,
    primaryContainer = DarkKotenPalette.mount,
    onPrimaryContainer = DarkKotenPalette.ink,
    secondary = DarkKotenPalette.inkSecondary,
    onSecondary = DarkKotenPalette.washi,
    secondaryContainer = DarkKotenPalette.mount,
    onSecondaryContainer = DarkKotenPalette.ink,
    background = DarkKotenPalette.washi,
    onBackground = DarkKotenPalette.ink,
    surface = DarkKotenPalette.washiCard,
    onSurface = DarkKotenPalette.ink,
    surfaceVariant = DarkKotenPalette.mount,
    onSurfaceVariant = DarkKotenPalette.inkSecondary,
    outline = DarkKotenPalette.rule
)

@Composable
fun KotenOCRTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val palette = if (darkTheme) DarkKotenPalette else LightKotenPalette
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalKotenColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
