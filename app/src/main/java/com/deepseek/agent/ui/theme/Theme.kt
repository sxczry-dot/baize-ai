package com.deepseek.agent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 浅色：冷灰白背景（类 ChatGPT）+ 蓝紫主色（DeepSeek 感）
private val LightColors = lightColorScheme(
    primary = Color(0xFF4D6BFE),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E9FF),
    onPrimaryContainer = Color(0xFF1A2B8F),
    secondary = Color(0xFF6E7BC4),
    onSecondary = Color.White,
    tertiary = Color(0xFF8B5CF6),
    background = Color(0xFFF7F7FA),
    onBackground = Color(0xFF1A1B22),
    surface = Color.White,
    onSurface = Color(0xFF1A1B22),
    surfaceVariant = Color(0xFFF0F0F5),
    onSurfaceVariant = Color(0xFF6B6E7D),
    outline = Color(0xFFE2E3EA),
    error = Color(0xFFDC3E3E)
)

// 深色：低饱和深蓝灰
private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FA5FF),
    onPrimary = Color(0xFF10205C),
    primaryContainer = Color(0xFF2A3B7E),
    onPrimaryContainer = Color(0xFFDCE3FF),
    secondary = Color(0xFFAEB8E8),
    onSecondary = Color(0xFF232D55),
    tertiary = Color(0xFFC3AEFF),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE4E4EB),
    surface = Color(0xFF1B1C23),
    onSurface = Color(0xFFE4E4EB),
    surfaceVariant = Color(0xFF26272F),
    onSurfaceVariant = Color(0xFF9A9CA8),
    outline = Color(0xFF34353E),
    error = Color(0xFFFF7A7A)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val AppTypography = Typography(
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp, letterSpacing = 0.2.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 24.sp, letterSpacing = 0.2.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
    titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
)

@Composable
fun DeepSeekAgentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content
    )
}
