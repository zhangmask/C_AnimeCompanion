package com.companion.chat.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ── Brand Palette (from HTML mockup :root variables) ──

val BrandPrimary = Color(0xFF7C5CBF)
val BrandPrimaryLight = Color(0xFFA88DE0)
val BrandPrimaryDark = Color(0xFF6E4FB0)
val BrandPrimaryContainer = Color(0xFFEDE5FF)
val BrandOnPrimaryContainer = Color(0xFF2D1A4E)

val BrandSecondary = Color(0xFFD4688C)
val BrandSecondaryContainer = Color(0xFFFFE0EB)
val BrandOnSecondaryContainer = Color(0xFF3E0922)

val BrandSurface = Color(0xFFFFFFFF)
val BrandSurfaceDim = Color(0xFFF7F5FA)
val BrandSurfaceContainer = Color(0xFFF2EFF6)
val BrandBackground = Color(0xFFFDFCFE)

val BrandOnSurface = Color(0xFF1C1B1F)
val BrandOnSurfaceVariant = Color(0xFF49454F)
val BrandOutline = Color(0xFF79747E)
val BrandOutlineVariant = Color(0xFFCAC4D0)
val BrandOutlineLight = Color(0xFFE8E4EC)

val BrandError = Color(0xFFB3261E)
val BrandErrorContainer = Color(0xFFF9DEDC)
val BrandSuccess = Color(0xFF2E7D52)

// ── Dark Palette ──

val BrandPrimaryDark80 = Color(0xFFCDB4F5)
val BrandPrimaryContainerDark = Color(0xFF4A3380)
val BrandOnPrimaryContainerDark = Color(0xFFEDE5FF)
val BrandSecondaryDark80 = Color(0xFFF09AB8)
val BrandSecondaryContainerDark = Color(0xFF6B2848)
val BrandOnSecondaryContainerDark = Color(0xFFFFE0EB)
val BrandSurfaceDark = Color(0xFF1C1B1F)
val BrandSurfaceDimDark = Color(0xFF141318)
val BrandSurfaceContainerDark = Color(0xFF2B2930)
val BrandBackgroundDark = Color(0xFF1C1B1F)
val BrandOnSurfaceDark = Color(0xFFE6E1E5)
val BrandOnSurfaceVariantDark = Color(0xFFCAC4D0)
val BrandOutlineDark = Color(0xFF938F99)
val BrandOutlineVariantDark = Color(0xFF49454F)
val BrandOutlineLightDark = Color(0xFF36343B)
val BrandErrorDark = Color(0xFFF2B8B5)
val BrandErrorContainerDark = Color(0xFF8C1D18)

// ── Message Bubbles ──

val UserBubbleColor = BrandPrimary
val AssistantBubbleColor = BrandSurfaceContainer
val UserBubbleText = Color.White
val AssistantBubbleText = BrandOnSurface

// ── Icon Box Gradients ──

val IconGradientPurple = Brush.linearGradient(listOf(BrandPrimary, BrandPrimaryLight))
val IconGradientBlue = Brush.linearGradient(listOf(Color(0xFF5B9BD5), Color(0xFF7BB3E0)))
val IconGradientGold = Brush.linearGradient(listOf(Color(0xFFD4A03C), Color(0xFFE0B85C)))
val IconGradientGreen = Brush.linearGradient(listOf(Color(0xFF2E7D52), Color(0xFF48A06A)))
val IconGradientPink = Brush.linearGradient(listOf(BrandSecondary, Color(0xFFE088A8)))
val IconGradientGray = Brush.linearGradient(listOf(BrandOutline, Color(0xFF9E9A9F)))

// ── Avatar Gradients ──

val AvatarGradientPink = Brush.linearGradient(listOf(Color(0xFFE88BA7), BrandSecondary))
val AvatarGradientBlue = Brush.linearGradient(listOf(Color(0xFF7BA7D4), Color(0xFF5B8DB8)))
val AvatarGradientPurple = Brush.linearGradient(listOf(BrandPrimaryLight, BrandPrimary))
val AvatarGradientTeal = Brush.linearGradient(listOf(Color(0xFF6BB5A0), Color(0xFF4A9A84)))
val AvatarGradientOrange = Brush.linearGradient(listOf(Color(0xFFE0A66D), Color(0xFFD48B4A)))

val AvatarGradients = listOf(
    AvatarGradientPink,
    AvatarGradientBlue,
    AvatarGradientPurple,
    AvatarGradientTeal,
    AvatarGradientOrange
)
