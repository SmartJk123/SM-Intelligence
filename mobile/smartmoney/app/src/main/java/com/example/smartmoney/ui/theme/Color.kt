package com.example.smartmoney.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

object SmartMoneyColors {
    // ---------------------------------------------------------
    // Brand Color Palette (User Provided - Light Mode Makeover)
    // ---------------------------------------------------------
    val PowderBlue = Color(0xFF99CDD8)      // #99CDD8: Powder Blue / Light Blue
    val PaleMintGreen = Color(0xFFDAEBE3)   // #DAEBE3: Pale Mint Green
    val LightPeach = Color(0xFFFDE8D3)      // #FDE8D3: Light Peach / Cream
    val LightSalmon = Color(0xFFF3C3B2)     // #F3C3B2: Light Salmon / Melon Pink
    val PaleSageGreen = Color(0xFFCFD6C4)   // #CFD6C4: Pale Sage Green
    val DarkSlateGreen = Color(0xFF657166)  // #657166: Dark Slate Green / Gray-Green

    // ---------------------------------------------------------
    // Light Theme Tokens
    // ---------------------------------------------------------
    val AzurePrimary = DarkSlateGreen       // #657166: Primary brand & action tone
    val AzureDark = Color(0xFF4C584E)       // Deeper slate green for selected/active states
    val AzureLight = PaleMintGreen          // #DAEBE3: Active indicator pill & card highlight
    val SlateBackground = Color(0xFFFAF9F6) // Warm, elegant soft off-white/cream canvas
    val BorderLine = PaleSageGreen          // #CFD6C4: Subtle, natural border & divider lines
    val TextPrimary = Color(0xFF263228)     // Deep rich slate for maximum readability & contrast
    val TextMuted = DarkSlateGreen          // #657166: Secondary labels & subtitles

    // Dark Theme Tokens (Dark Mode: Pure Black canvas, Dark Slate Green cards & bottom bar)
    val DeepNavy = Color(0xFF1E2620)        // Deep forest slate for dark mode drawer/surfaces
    val DarkBackground = Color(0xFF000000)  // Pure Black for page canvas
    val DarkSurface = DarkSlateGreen        // #657166: Dark Slate Green for all cards
    val DarkSurfaceElevated = Color(0xFF263228) // Deep Forest Slate for nested cards/pills
    val DarkBorderLine = Color(0xFF7D8C7E).copy(alpha = 0.45f) // Subtle card/divider outline
    val DarkBorder = Color(0xFF4C584E)      // Subtle dark card border
    val DarkTextPrimary = Color(0xFFFFFFFF) // Crisp Pure White (21:1 on Black, 5.17:1 on DarkSlateGreen)
    val DarkTextSecondary = Color(0xFFF0FDF4) // Mint White (19.8:1 on Black, 4.87:1 on DarkSlateGreen)
    val DarkActiveCyan = Color(0xFFFFFFFF)  // High-contrast active element (replaces legacy cyan)
    val DarkInactive = Color(0xFFDAEBE3)    // Pale Mint Green (4.17:1 on DarkSlateGreen)
}