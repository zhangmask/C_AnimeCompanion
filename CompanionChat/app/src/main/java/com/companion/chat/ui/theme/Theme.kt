package com.companion.chat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = BrandPrimaryContainer,
    onPrimaryContainer = BrandOnPrimaryContainer,
    secondary = BrandSecondary,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = BrandSecondaryContainer,
    onSecondaryContainer = BrandOnSecondaryContainer,
    tertiary = BrandSuccess,
    surface = BrandSurface,
    onSurface = BrandOnSurface,
    surfaceVariant = BrandSurfaceContainer,
    onSurfaceVariant = BrandOnSurfaceVariant,
    surfaceContainerHigh = BrandSurfaceContainer,
    background = BrandBackground,
    onBackground = BrandOnSurface,
    outline = BrandOutline,
    outlineVariant = BrandOutlineVariant,
    error = BrandError,
    errorContainer = BrandErrorContainer,
    onError = Color(0xFFFFFFFF),
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandPrimaryDark80,
    onPrimary = Color(0xFF3E2572),
    primaryContainer = BrandPrimaryContainerDark,
    onPrimaryContainer = BrandOnPrimaryContainerDark,
    secondary = BrandSecondaryDark80,
    onSecondary = Color(0xFF5C1B3B),
    secondaryContainer = BrandSecondaryContainerDark,
    onSecondaryContainer = BrandOnSecondaryContainerDark,
    tertiary = Color(0xFF7ED4A6),
    surface = BrandSurfaceDark,
    onSurface = BrandOnSurfaceDark,
    surfaceVariant = BrandSurfaceContainerDark,
    onSurfaceVariant = BrandOnSurfaceVariantDark,
    surfaceContainerHigh = BrandSurfaceContainerDark,
    background = BrandBackgroundDark,
    onBackground = BrandOnSurfaceDark,
    outline = BrandOutlineDark,
    outlineVariant = BrandOutlineVariantDark,
    error = BrandErrorDark,
    errorContainer = BrandErrorContainerDark,
    onError = Color(0xFF690005),
)

@Composable
fun CompanionChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
