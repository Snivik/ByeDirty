package dev.snivik.byedirty.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

private val Default = Typography()

// Material 3 defaults, with the big figures set a touch tighter.
val Typography = Typography(
    displayLarge = Default.displayLarge.copy(fontSize = 64.sp, lineHeight = 68.sp, letterSpacing = (-1.5).sp),
    displayMedium = Default.displayMedium.copy(letterSpacing = (-1).sp),
    displaySmall = Default.displaySmall.copy(letterSpacing = (-0.5).sp),
)

/** Small all-caps labels such as "EST. REFILL DATE". */
val Overline = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.8.sp)
