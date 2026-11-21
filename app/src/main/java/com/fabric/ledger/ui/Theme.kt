package com.fabric.ledger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

// ---------- الألوان: أخضر مزرق عميق + نحاسي (طابع تجاري راقٍ) ----------
val Teal = Color(0xFF0B4F5C)
val TealLight = Color(0xFF1A8A96)
val Copper = Color(0xFFD9822B)
val Mist = Color(0xFFF2F7F7)

private val AppColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EEF0),
    onPrimaryContainer = Color(0xFF06303A),
    secondary = Color(0xFF8A5A00),
    secondaryContainer = Color(0xFFFFE9CC),
    onSecondaryContainer = Color(0xFF3D2600),
    tertiary = TealLight,
    background = Mist,
    surface = Color.White,
    surfaceVariant = Color(0xFFE6F0F1),
    errorContainer = Color(0xFFFFDDD8),
    onErrorContainer = Color(0xFF5C0A0A),
    outline = Color(0xFF9DB8BC)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, shapes = AppShapes, content = content)
}

/** بطاقة مجسّمة: ظل ملوّن + تدرّج خفيف + حافة مضيئة من الأعلى */
@Composable
fun Card3D(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    tint: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    val base = tint ?: Color.White
    val fill = Brush.verticalGradient(listOf(lerp(base, Color.White, 0.6f), base))
    val rim = Brush.verticalGradient(listOf(Color.White, Teal.copy(alpha = 0.18f)))
    var m = modifier
        .shadow(10.dp, shape, ambientColor = Teal.copy(alpha = 0.25f), spotColor = Teal.copy(alpha = 0.4f))
        .clip(shape)
        .background(fill)
        .border(1.2.dp, rim, shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m, content = content)
}

/** بطاقة رئيسية داكنة متدرّجة للأرقام المهمة (إجمالي الدين، رصيد الزبون) */
@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier
            .shadow(16.dp, shape, ambientColor = Teal.copy(alpha = 0.4f), spotColor = Teal.copy(alpha = 0.6f))
            .clip(shape)
            .background(Brush.linearGradient(listOf(Teal, TealLight)))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.5f), Color.Transparent)), shape)
            .padding(20.dp)
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) { content() }
    }
}

/** زر مجسّم بتدرّج وظل */
@Composable
fun Button3D(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val bg = if (enabled) Brush.verticalGradient(listOf(TealLight, Teal))
    else Brush.verticalGradient(listOf(Color(0xFFC4D2D4), Color(0xFFB2C3C6)))
    Button(
        onClick = onClick, enabled = enabled, shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent,
            contentColor = Color.White, disabledContentColor = Color.White),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .shadow(if (enabled) 8.dp else 0.dp, shape, ambientColor = Teal.copy(alpha = 0.3f), spotColor = Teal.copy(alpha = 0.5f))
            .background(bg, shape)
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent)), shape)
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** شريط علوي متدرّج بزوايا سفلية مدورة وظل */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradientTopBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    val shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
    Box(
        Modifier
            .shadow(12.dp, shape, ambientColor = Teal.copy(alpha = 0.4f), spotColor = Teal.copy(alpha = 0.5f))
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Teal, TealLight)))
    ) {
        TopAppBar(
            title = title, navigationIcon = navigationIcon, actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent, titleContentColor = Color.White,
                navigationIconContentColor = Color.White, actionIconContentColor = Color.White
            )
        )
    }
}

val NavShape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
