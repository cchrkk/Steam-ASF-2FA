package tk.chrk.qrloginapprover.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import tk.chrk.qrloginapprover.data.ApiClient
import tk.chrk.qrloginapprover.data.BotInfo
import tk.chrk.qrloginapprover.data.SteamProfile

@Composable
fun CodesScreen(api: ApiClient, selected: BotInfo?, profile: SteamProfile?, modifier: Modifier = Modifier) {
	var code by remember { mutableStateOf<String?>(null) }
	var seconds by remember { mutableStateOf(30) }
	var status by remember { mutableStateOf<String?>(null) }
	var epoch by remember { mutableStateOf(0) }

	val clipboard = LocalClipboardManager.current
	val target = selected?.name

	LaunchedEffect(target, epoch) {
		if (target == null) {
			return@LaunchedEffect
		}

		try {
			val result = api.code(listOf(target))
			val entry = result[target]
			code = entry?.code
			status = if (entry?.success == true) null else entry?.message
		} catch (e: Exception) {
			status = e.message
		}

		seconds = 30

		while (seconds > 0) {
			delay(1000L)
			seconds -= 1
		}

		epoch += 1
	}

	Column(
		modifier = modifier
			.fillMaxSize()
			.padding(24.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center,
	) {
		if (target == null) {
			EmptyState(Icons.Default.Key, "No accounts", "No bot with ASF 2FA found on this instance.")
			return@Column
		}

		SectionTitle("Steam Guard code")

		Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
			CircularProgressIndicator(
				progress = { seconds / 30f },
				trackColor = MaterialTheme.colorScheme.surfaceVariant,
				strokeWidth = 12.dp,
				modifier = Modifier.fillMaxSize(),
			)

			Column(horizontalAlignment = Alignment.CenterHorizontally) {
				Text(
					text = code ?: "•••••",
					fontSize = 46.sp,
					fontFamily = FontFamily.Monospace,
					fontWeight = FontWeight.Bold,
					letterSpacing = 2.sp,
					color = MaterialTheme.colorScheme.primary,
					textAlign = TextAlign.Center,
				)
				Text("$seconds s", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
		}

		Spacer(Modifier.height(24.dp))

		Row(verticalAlignment = Alignment.CenterVertically) {
			AccountAvatar(profile?.avatarUrl, 30.dp)
			Spacer(Modifier.width(10.dp))
			Column {
				Text(profile?.personaName ?: target, style = MaterialTheme.typography.titleMedium)
				Text("signs in this code", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
		}

		status?.let {
			Spacer(Modifier.height(12.dp))
			Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
		}

		Spacer(Modifier.height(32.dp))

		Button(
			onClick = { code?.let { clipboard.setText(AnnotatedString(it)) } },
			enabled = code != null,
			modifier = Modifier.fillMaxWidth(),
		) {
			Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
			Spacer(Modifier.width(8.dp))
			Text("Copy code")
		}

		TextButton(onClick = { epoch += 1 }) { Text("Refresh") }
	}
}
