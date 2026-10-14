package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Premium White UI Palette
val White = Color(0xFFFFFFFF)
val SecondaryBackground = Color(0xFFF8F9FC)
val SurfaceCard = Color(0xFFFFFFFF)
val BorderColor = Color(0xFFECECF3)

val PrimaryText = Color(0xFF151522)
val SecondaryText = Color(0xFF777787)
val MutedText = Color(0xFFA0A0B2)

val PurpleAccent = Color(0xFF8B35F0)
val PurpleAccentLight = Color(0xFFF3EAFF)
val BlueAccent = Color(0xFF536DFF)
val BlueAccentLight = Color(0xFFEEF1FF)

val SuccessGreen = Color(0xFF10B981)
val WarningAmber = Color(0xFFF59E0B)
val ErrorRed = Color(0xFFEF4444)

// Dark preview viewport only for video
val VideoViewportDark = Color(0xFF0E0F17)
val VideoViewportDarker = Color(0xFF07070B)

val PrimaryGradient = Brush.horizontalGradient(
    colors = listOf(PurpleAccent, BlueAccent)
)

val CardGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFFFFFFFF), Color(0xFFFBFBFE))
)
