package tk.chrk.qrloginapprover.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import tk.chrk.qrloginapprover.R

/** The Steam avatar of an account, falling back to the app mark. */
@Composable
fun AccountAvatar(avatarUrl: String?, size: Dp, modifier: Modifier = Modifier) {
	Surface(
		shape = CircleShape,
		color = MaterialTheme.colorScheme.surfaceVariant,
		modifier = modifier.size(size),
	) {
		if (!avatarUrl.isNullOrBlank()) {
			AsyncImage(
				model = avatarUrl,
				contentDescription = null,
				contentScale = ContentScale.Crop,
				modifier = Modifier.fillMaxSize(),
			)
		} else {
			Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
				Image(painter = painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(size * 0.6f))
			}
		}
	}
}

/** A label/value row with a leading icon. */
@Composable
fun DetailRow(icon: ImageVector, label: String, value: String, emphasized: Boolean = false) {
	Row(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
		Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
		Spacer(Modifier.width(12.dp))
		Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
		Spacer(Modifier.weight(1f))
		Text(
			text = value,
			style = MaterialTheme.typography.bodyMedium,
			fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
			textAlign = TextAlign.End,
			color = MaterialTheme.colorScheme.onSurface,
		)
	}
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
	Text(
		text = text.uppercase(),
		style = MaterialTheme.typography.labelMedium,
		color = MaterialTheme.colorScheme.primary,
		modifier = modifier.padding(bottom = 8.dp),
	)
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
	Column(
		modifier = modifier.fillMaxWidth().padding(32.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center,
	) {
		Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
			Icon(icon, contentDescription = null, modifier = Modifier.padding(20.dp).size(40.dp), tint = MaterialTheme.colorScheme.primary)
		}
		Spacer(Modifier.height(16.dp))
		Text(title, style = MaterialTheme.typography.titleMedium)
		Spacer(Modifier.height(4.dp))
		Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
	}
}

/** Rounded square tonal icon used as a leading element in list cards. */
@Composable
fun LeadingIconBadge(icon: ImageVector, modifier: Modifier = Modifier) {
	Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer, modifier = modifier.size(40.dp)) {
		Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
			Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
		}
	}
}
