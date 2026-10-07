package tk.chrk.qrloginapprover.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import tk.chrk.qrloginapprover.R
import tk.chrk.qrloginapprover.data.ApiClient
import tk.chrk.qrloginapprover.data.BotInfo
import tk.chrk.qrloginapprover.data.QrInfo
import tk.chrk.qrloginapprover.data.SteamProfile

private val ChallengeRegex = Regex("""https?://s\.team/q/\d+/\d+""", RegexOption.IGNORE_CASE)

@Composable
fun QrScreen(api: ApiClient, selected: BotInfo?, modifier: Modifier = Modifier) {
	val scope = rememberCoroutineScope()

	var cameraOn by remember { mutableStateOf(false) }
	var busy by remember { mutableStateOf(false) }
	var status by remember { mutableStateOf<String?>(null) }
	var pending by remember { mutableStateOf<String?>(null) }
	var info by remember { mutableStateOf<QrInfo?>(null) }
	var profile by remember { mutableStateOf<SteamProfile?>(null) }
	var profileLoaded by remember { mutableStateOf(false) }

	fun reset() {
		pending = null
		info = null
		profile = null
		profileLoaded = false
		busy = false
	}

	fun refreshProfile(bot: BotInfo) {
		scope.launch {
			profile = api.getSteamProfile(bot.steamId.toString())
			profileLoaded = true
		}
	}

	// Load the account profile as soon as the selected bot changes
	LaunchedEffect(selected?.name) {
		val bot = selected ?: return@LaunchedEffect
		profileLoaded = false
		refreshProfile(bot)
	}

	fun handleScan(raw: String) {
		if (busy || (pending != null)) {
			return
		}

		val match = ChallengeRegex.find(raw)?.value ?: return
		val bot = selected ?: return

		cameraOn = false // stop scanning
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
				status = if (result.success) (if (approve) "Approved ✅" else "Denied") else (result.error ?: "Failed")
			} catch (e: Exception) {
				status = e.message
			} finally {
				reset()
			}
		}
	}

	Column(
		modifier = modifier
			.fillMaxSize()
			.padding(16.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		val bot = selected

		if (bot == null) {
			Text("No bot with ASF 2FA found.", color = MaterialTheme.colorScheme.error)
			return@Column
		}

		if (pending != null) {
			// Confirmation page (camera is off)
			val details = info?.info

			Column(
				modifier = Modifier.fillMaxSize(),
				horizontalAlignment = Alignment.CenterHorizontally,
			) {
				Spacer(Modifier.height(8.dp))

				if (profileLoaded && profile?.avatarUrl != null) {
					AsyncImage(
						model = profile!!.avatarUrl,
						contentDescription = null,
						modifier = Modifier.size(96.dp).clip(CircleShape),
					)
				} else {
					Image(painter = painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(96.dp))
				}

				Spacer(Modifier.height(12.dp))
				Text(profile?.personaName ?: bot.name, style = MaterialTheme.typography.headlineSmall)
				Text("Account being signed in", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
				Text(bot.steamId.toString(), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)

				Spacer(Modifier.height(20.dp))

				Card(
					modifier = Modifier.fillMaxWidth(),
					colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
				) {
					Column(modifier = Modifier.padding(16.dp)) {
						DetailRow("Device", info?.deviceFriendlyName ?: "—")
						DetailRow("IP", info?.ip ?: "—")
						DetailRow("Location", listOfNotNull(details?.city, details?.state, details?.country).joinToString(", ").ifBlank { "—" })
						DetailRow("Platform", platformName(details?.platformType))
						DetailRow("Same location", if (details?.locationMismatch == true) "⚠️ mismatch" else "yes")
						DetailRow("High usage", if (details?.highUsageLogin == true) "⚠️ unusual" else "no")
					}
				}

				Spacer(Modifier.weight(1f))

				if (busy) {
					CircularProgressIndicator()
				} else {
					Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
						Button(onClick = { decide(true) }, modifier = Modifier.weight(1f)) { Text("Approve") }
						OutlinedButton(onClick = { decide(false) }, modifier = Modifier.weight(1f)) { Text("Deny") }
					}
					TextButton(onClick = { reset(); status = null }) { Text("Cancel") }
				}
			}

			return@Column
		}

		// Start / camera
		if (!cameraOn) {
			Column(
				modifier = Modifier.fillMaxSize(),
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.Center,
			) {
				Image(painter = painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.height(72.dp))
				Spacer(Modifier.height(24.dp))
				Text("Sign in on another device", style = MaterialTheme.typography.headlineSmall)
				Spacer(Modifier.height(8.dp))
				Text(
					"Open Steam on the new PC, choose \"Sign in with QR\", then scan it here. ASF will approve the login for ${bot.name}.",
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					textAlign = TextAlign.Center,
				)
				Spacer(Modifier.height(32.dp))
				Button(onClick = { cameraOn = true; status = null }) { Text("Start camera") }

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
private fun DetailRow(label: String, value: String) {
	Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
		Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
		Spacer(Modifier.weight(1f))
		Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
	}
}

private fun platformName(type: Int?): String = when (type) {
	1 -> "Steam client"
	2 -> "Web browser"
	3 -> "Mobile app"
	else -> "Unknown"
}
