package tk.chrk.qrloginapprover.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** The brand colour, taken from the logo. */
object Brand {
	val Primary = Color(0xFFF92F02)
	val Deep = Color(0xFF9E1206)
	val Glow = Color(0xFFFF7A4D)

	/** Warm gradient used for hero/confirm headers. */
	val Gradient = listOf(Color(0xFFF92F02), Color(0xFFB01305))
}

// Hand-tuned Material 3 schemes seeded from the brand colour (predictable, unlike Monet).
private val BrandDark = darkColorScheme(
	primary = Color(0xFFFFB4A1),
	onPrimary = Color(0xFF5A1B08),
	primaryContainer = Color(0xFF7E2A12),
	onPrimaryContainer = Color(0xFFFFDBD2),
	inversePrimary = Color(0xFFA03C1F),
	secondary = Color(0xFFE7BDB3),
	onSecondary = Color(0xFF442A23),
	secondaryContainer = Color(0xFF5D4038),
	onSecondaryContainer = Color(0xFFFFDBD2),
	tertiary = Color(0xFFB6C8E8),
	onTertiary = Color(0xFF1B324B),
	tertiaryContainer = Color(0xFF324962),
	onTertiaryContainer = Color(0xFFD6E3FF),
	error = Color(0xFFFFB4AB),
	onError = Color(0xFF690005),
	errorContainer = Color(0xFF93000A),
	onErrorContainer = Color(0xFFFFDAD6),
	background = Color(0xFF171110),
	onBackground = Color(0xFFEFE0DC),
	surface = Color(0xFF171110),
	onSurface = Color(0xFFEFE0DC),
	surfaceVariant = Color(0xFF372A27),
	onSurfaceVariant = Color(0xFFDCC2BA),
	outline = Color(0xFFA48C84),
	outlineVariant = Color(0xFF54433F),
	inverseSurface = Color(0xFFEFE0DC),
	inverseOnSurface = Color(0xFF372A27),
)

private val BrandLight = lightColorScheme(
	primary = Color(0xFFAA3316),
	onPrimary = Color(0xFFFFFFFF),
	primaryContainer = Color(0xFFFFDBD2),
	onPrimaryContainer = Color(0xFF3C0A00),
	secondary = Color(0xFF77574E),
	onSecondary = Color(0xFFFFFFFF),
	secondaryContainer = Color(0xFFFFDBD2),
	onSecondaryContainer = Color(0xFF2C1510),
	tertiary = Color(0xFF4A607C),
	onTertiary = Color(0xFFFFFFFF),
	tertiaryContainer = Color(0xFFD3E4FF),
	onTertiaryContainer = Color(0xFF041C35),
	background = Color(0xFFFFF8F6),
	onBackground = Color(0xFF231917),
	surface = Color(0xFFFFF8F6),
	onSurface = Color(0xFF231917),
	surfaceVariant = Color(0xFFF5DED8),
	onSurfaceVariant = Color(0xFF53433F),
	outline = Color(0xFF85736E),
	outlineVariant = Color(0xFFD8C2BC),
)

private val AppShapes = Shapes(
	extraSmall = RoundedCornerShape(6.dp),
	small = RoundedCornerShape(12.dp),
	medium = RoundedCornerShape(18.dp),
	large = RoundedCornerShape(26.dp),
	extraLarge = RoundedCornerShape(32.dp),
)

private val AppTypography = Typography().let { base ->
	base.copy(
		headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
		titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
		titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
		labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
	)
}

/**
 * @param useDynamicColor when true, uses Material You (Android 12+). Off by default so the app keeps its
 *        brand identity; turn it on if you prefer the system palette.
 */
@Composable
fun SteamAsf2faTheme(useDynamicColor: Boolean = false, content: @Composable () -> Unit) {
	val dark = isSystemInDarkTheme()
	val context = LocalContext.current

	val scheme = when {
		useDynamicColor && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
		dark -> BrandDark
		else -> BrandLight
	}

	MaterialTheme(
		colorScheme = scheme,
		shapes = AppShapes,
		typography = AppTypography,
		content = content,
	)
}
