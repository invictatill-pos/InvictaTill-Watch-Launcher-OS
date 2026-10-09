package com.healthsync.phone.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val BgDeep = Color(0xFFF4F6F1)
val BgBase = Color(0xFFECEFE8)
val BgSurface = Color(0xFFFFFFFF)
val BgCard = Color(0xFFFFFFFF)
val BgCardHover = Color(0xFFF8FAF6)
val BgGlass = Color(0xCCFFFFFF)
val BgInput = Color(0xFFF0F3ED)

val AccentCyan = Color(0xFF1F8A70)
val AccentBlue = Color(0xFF3066BE)
val AccentPurple = Color(0xFF7B61FF)
val AccentElectric = Color(0xFF0077B6)

val MetricHeart = Color(0xFFE6475F)
val MetricSteps = Color(0xFFE4A11B)
val MetricSpO2 = Color(0xFF168AAD)
val MetricSleep = Color(0xFF6D5BD0)
val MetricWater = Color(0xFF2D9CDB)
val MetricCalories = Color(0xFFE26D2E)
val MetricMood = Color(0xFFC05299)

val SleepDeep = Color(0xFF3A5A9F)
val SleepLight = Color(0xFF2D9CDB)
val SleepRem = Color(0xFFC05299)
val SleepAwake = Color(0xFF55A66F)

val StatusSuccess = Color(0xFF2F9E68)
val StatusWarning = Color(0xFFD9971B)
val StatusError = Color(0xFFD64550)
val StatusInfo = Color(0xFF168AAD)

val TextWhite = Color(0xFF18201B)
val TextPrimary = Color(0xFF222A24)
val TextSecondary = Color(0xFF667267)
val TextDim = Color(0xFF8A938A)
val TextPlaceholder = Color(0xFF9EA89F)

val BorderSubtle = Color(0x1F18201B)
val BorderDefault = Color(0x3318201B)
val BorderStrong = Color(0x5518201B)

val NeonCyan = AccentCyan
val NeonGreen = StatusSuccess
val NeonPink = MetricHeart
val DeepPurple = AccentPurple
val ElectricBlue = AccentElectric
val NeonAmber = MetricSteps
val HeartRed = MetricHeart
val StepsOrange = MetricSteps
val SpO2Blue = MetricSpO2
val SleepIndigo = MetricSleep
val DeepSleepColor = SleepDeep
val LightSleepColor = SleepLight
val RemSleepColor = SleepRem
val AwakeColor = SleepAwake
val OnSurfaceDim = TextDim
val OnSurfaceMid = TextSecondary
val SurfaceDark = BgDeep
val SurfaceCard = BgCard
val SurfaceCard2 = BgCardHover
val BgCard2 = BgCardHover
val BgCardGlow = BgCardHover

object Shapes {
    val xs = RoundedCornerShape(8.dp)
    val sm = RoundedCornerShape(12.dp)
    val md = RoundedCornerShape(16.dp)
    val lg = RoundedCornerShape(20.dp)
    val xl = RoundedCornerShape(28.dp)
    val pill = RoundedCornerShape(50.dp)
}

private val AppColorScheme = lightColorScheme(
    primary = AccentCyan,
    onPrimary = Color.White,
    primaryContainer = AccentCyan.copy(alpha = 0.14f),
    onPrimaryContainer = AccentCyan,
    secondary = AccentPurple,
    onSecondary = Color.White,
    secondaryContainer = AccentPurple.copy(alpha = 0.14f),
    onSecondaryContainer = AccentPurple,
    tertiary = MetricHeart,
    onTertiary = Color.White,
    background = BgDeep,
    onBackground = TextPrimary,
    surface = BgSurface,
    onSurface = TextPrimary,
    surfaceVariant = BgCardHover,
    onSurfaceVariant = TextSecondary,
    outline = BorderDefault,
    outlineVariant = BorderSubtle,
    error = StatusError,
    onError = Color.White,
    errorContainer = StatusError.copy(alpha = 0.12f)
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Black, fontSize = 54.sp, lineHeight = 60.sp, letterSpacing = 0.sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Black, fontSize = 42.sp, lineHeight = 48.sp, letterSpacing = 0.sp),
    displaySmall = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.sp)
)

@Composable
fun HealthSyncTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        content = content
    )
}
