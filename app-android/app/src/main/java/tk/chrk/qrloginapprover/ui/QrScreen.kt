package tk.chrk.qrloginapprover.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import tk.chrk.qrloginapprover.R
import tk.chrk.qrloginapprover.data.ApiClient
import tk.chrk.qrloginapprover.data.BotInfo
import tk.chrk.qrloginapprover.data.QrInfo
import tk.chrk.qrloginapprover.data.SteamProfile

private val ChallengeRegex = Regex("""https?://s\.team/q/\d+/\d+""", RegexOption.IGNORE_CASE)

@Composable
fun QrScreen(api: ApiClient, selected: BotInfo?, profile: SteamProfile?, modifier: Modifier = Modifier) {
	val scope = rememberCoroutineScope()

	var cameraOn by remember { mutableStateOf(false) }
	var busy by remember { mutableStateOf(false) }
	var status by remember { mutableStateOf<String?>(null) }
	var pending by remember { mutableStateOf<String?>(null) }
	var info by remember { mutableStateOf<QrInfo?>(null) }

	fun reset() {
		pending = null
		info = null
		busy = false
	}

	fun handleScan(raw: String) {
		if (busy || (pending != null)) {
			return
		}

		val match = ChallengeRegex.find(raw)?.value ?: return
		val bot = selected ?: return

		cameraOn = false
		busy = true
		status = null

		scope.launch {
			try {
				val result = api.qrInfo(bot.name, match)

				if (result.success) {
					info = result
					pending = match
				} else {
					status = result.error ?: "Could not read the request"
				}
			} catch (e: Exception) {
				status = e.message
			} finally {
				busy = false
			}
		}
	}

	fun decide(approve: Boolean) {
		val url = pending ?: return
		val bot = selected ?: return

		busy = true
		scope.launch {
			try {
				val result = api.qrApprove(bot.name, url, approve)
				status = if (result.success) (if (approve) "Approved" else "Denied") else (result.error ?: "Failed")
			} catch (e: Exception) {
				status = e.message
			} finally {
				reset()
			}
		}
	}

	if (selected == null) {
		EmptyState(Icons.Default.QrCodeScanner, "No accounts", "No bot with ASF 2FA found on this instance.", modifier.padding(16.dp))
		return
	}

	if (pending != null) {
		Dialog(
			onDismissRequest = { },
			properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
		) {
			Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
				ConfirmPage(info, profile, selected, busy, onApprove = { decide(true) }, onDeny = { decide(false) }, onCancel = { reset(); status = null })
			}
		}

		return
	}

	Column(
		modifier = modifier
			.fillMaxSize()
			.padding(24.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		if (!cameraOn) {
			Column(
				modifier = Modifier.fillMaxSize(),
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.Center,
			) {
				Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(148.dp)) {
					Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
						Image(painter = painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(78.dp))
					}
				}

				Spacer(Modifier.height(28.dp))
				Text("Sign in on another device", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
				Spacer(Modifier.height(8.dp))
				Text(
					"Open Steam on the new PC, choose \"Sign in with QR\", then scan it here. ASF approves the login for ${selected.name}.",
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					textAlign = TextAlign.Center,
				)

				Spacer(Modifier.height(32.dp))
				Button(onClick = { cameraOn = true; status = null }, modifier = Modifier.height(52.dp)) {
					Icon(Icons.Default.QrCodeScanner, contentDescription = null)
					Spacer(Modifier.width(10.dp))
					Text("Scan QR code", style = MaterialTheme.typography.titleMedium)
				}

				status?.let {
					Spacer(Modifier.height(16.dp))
					Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
				}
			}
		} else {
			Surface(
				shape = MaterialTheme.shapes.large,
				color = MaterialTheme.colorScheme.surfaceVariant,
				modifier = Modifier
					.fillMaxWidth()
					.weight(1f),
			) {
				CameraPreview(onQrDetected = { handleScan(it) }, modifier = Modifier.fillMaxSize())
			}

			Spacer(Modifier.height(12.dp))

			if (busy) {
				CircularProgressIndicator(modifier = Modifier.height(24.dp))
			} else {
				Text(
					status ?: "Point the camera at the QR code.",
					style = MaterialTheme.typography.bodyMedium,
					color = if (status != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
					textAlign = TextAlign.Center,
				)
			}

			Spacer(Modifier.height(12.dp))
			TextButton(onClick = { cameraOn = false; status = null }) { Text("Stop camera") }
		}
	}
}

@Composable
private fun ConfirmPage(
	info: QrInfo?,
	profile: SteamProfile?,
	bot: BotInfo,
	busy: Boolean,
	onApprove: () -> Unit,
	onDeny: () -> Unit,
	onCancel: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val details = info?.info

	Column(modifier = modifier.fillMaxSize()) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.background(Brush.linearGradient(Brand.Gradient))
				.padding(vertical = 28.dp, horizontal = 24.dp),
		) {
			Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
				AccountAvatar(profile?.avatarUrl, 84.dp, Modifier.clip(CircleShape))
				Spacer(Modifier.height(14.dp))
				Text(profile?.personaName ?: bot.name, style = MaterialTheme.typography.headlineSmall, color = Color.White)
				Text("will be signed in on this device", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
				Spacer(Modifier.height(4.dp))
				Text(bot.steamId.toString(), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = Color.White.copy(alpha = 0.7f))
			}
		}

		Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
			SectionTitle("Login request")

			Card(
				colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
				modifier = Modifier.fillMaxWidth(),
			) {
				Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
					DetailRow(Icons.Default.Devices, "Device", info?.deviceFriendlyName ?: "—")
					DetailRow(Icons.Default.Language, "IP", info?.ip ?: "—")
					DetailRow(Icons.Default.Place, "Location", listOfNotNull(details?.city, details?.state, details?.country).joinToString(", ").ifBlank { "—" })
					DetailRow(Icons.Default.Computer, "Platform", platformName(details?.platformType))
					DetailRow(Icons.Default.VerifiedUser, "Same location", if (details?.locationMismatch == true) "mismatch" else "yes")
					DetailRow(Icons.Default.Warning, "Usage", if (details?.highUsageLogin == true) "unusual" else "normal")
				}
			}

			Spacer(Modifier.weight(1f))

			if (busy) {
				CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
			} else {
				Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
					Button(onClick = onApprove, modifier = Modifier.weight(1.4f).height(50.dp)) { Text("Approve", style = MaterialTheme.typography.titleMedium) }
					OutlinedButton(onClick = onDeny, modifier = Modifier.weight(1f).height(50.dp)) { Text("Deny") }
				}
				TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Cancel") }
			}
		}
	}
}

private fun platformName(type: Int?): String = when (type) {
	1 -> "Steam client"
	2 -> "Web browser"
	3 -> "Mobile app"
	else -> "Unknown"
}
