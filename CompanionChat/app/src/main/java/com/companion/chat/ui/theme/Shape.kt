package com.companion.chat.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

// Named shape tokens for clarity
object CompanionShapes {
    val Input = RoundedCornerShape(8.dp)
    val IconBox = RoundedCornerShape(12.dp)
    val MemoryCard = RoundedCornerShape(12.dp)
    val Card = RoundedCornerShape(16.dp)
    val Dialog = RoundedCornerShape(16.dp)
    val ProfileCard = RoundedCornerShape(16.dp)
    val BottomSheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    val Pill = RoundedCornerShape(9999.dp)
    val Avatar = RoundedCornerShape(9999.dp)
    val Toggle = RoundedCornerShape(9999.dp)
}
