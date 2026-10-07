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

// Fallback palette for devices without Monet (Android < 12). Neutral, one cool accent.
private val FallbackDark = darkColorScheme(
	primary = Color(0xFFA8C7EC),
	onPrimary = Color(0xFF0C1B2B),
	primaryContainer = Color(0xFF24384D),
	onPrimaryContainer = Color(0xFFD6E4F7),
	secondaryContainer = Color(0xFF262C35),
	onSecondaryContainer = Color(0xFFDCE1E8),
	background = Color(0xFF0D1014),
	onBackground = Color(0xFFE4E7EB),
	surface = Color(0xFF12161B),
	onSurface = Color(0xFFE4E7EB),
	surfaceVariant = Color(0xFF1C222A),
	onSurfaceVariant = Color(0xFFAAB3BF),
	outline = Color(0xFF3A424E),
	outlineVariant = Color(0xFF262C35),
)

private val FallbackLight = lightColorScheme(
	primary = Color(0xFF3B5B7E),
	onPrimary = Color(0xFFFFFFFF),
	primaryContainer = Color(0xFFD3E3F9),
	onPrimaryContainer = Color(0xFF0A1B2C),
	background = Color(0xFFFBFCFF),
	onBackground = Color(0xFF17191C),
	surface = Color(0xFFFBFCFF),
	onSurface = Color(0xFF17191C),
	surfaceVariant = Color(0xFFE0E3E8),
	onSurfaceVariant = Color(0xFF43474E),
	outline = Color(0xFF73777F),
	outlineVariant = Color(0xFFC3C7CF),
)

private val AppShapes = Shapes(
	extraSmall = RoundedCornerShape(6.dp),
	small = RoundedCornerShape(10.dp),
	medium = RoundedCornerShape(14.dp),
	large = RoundedCornerShape(20.dp),
	extraLarge = RoundedCornerShape(28.dp),
)

private val AppTypography = Typography().let { base ->
	base.copy(
		headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
		titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
		labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
	)
}

@Composable
fun SteamAsf2faTheme(content: @Composable () -> Unit) {
	val dark = isSystemInDarkTheme()
	val context = LocalContext.current

	val scheme = when {
		Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
		dark -> FallbackDark
		else -> FallbackLight
	}

	MaterialTheme(
		colorScheme = scheme,
		shapes = AppShapes,
		typography = AppTypography,
		content = content,
	)
}
