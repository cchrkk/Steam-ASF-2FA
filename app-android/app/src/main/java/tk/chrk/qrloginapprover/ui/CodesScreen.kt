package tk.chrk.qrloginapprover.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import tk.chrk.qrloginapprover.data.ApiClient
import tk.chrk.qrloginapprover.data.BotInfo

@Composable
fun CodesScreen(api: ApiClient, selected: BotInfo?, modifier: Modifier = Modifier) {
	var code by remember { mutableStateOf<String?>(null) }
	var seconds by remember { mutableStateOf(30) }
	var status by remember { mutableStateOf<String?>(null) }
	var epoch by remember { mutableStateOf(0) }

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
			Text("No bot with ASF 2FA found.", color = MaterialTheme.colorScheme.error)
			return@Column
		}

		Card(
			modifier = Modifier.fillMaxWidth(),
			colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(24.dp),
				horizontalAlignment = Alignment.CenterHorizontally,
			) {
				Text("Steam Guard code", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
				Spacer(Modifier.height(16.dp))

				if (code == null) {
					CircularProgressIndicator()
				} else {
					Text(
						text = code!!,
						fontSize = 44.sp,
						fontFamily = FontFamily.Monospace,
						letterSpacing = 4.sp,
						textAlign = TextAlign.Center,
						color = MaterialTheme.colorScheme.primary,
					)
				}

				Spacer(Modifier.height(20.dp))

				LinearProgressIndicator(
					progress = { seconds / 30f },
					modifier = Modifier.fillMaxWidth(),
				)

				Spacer(Modifier.height(8.dp))
				Text("$seconds s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
		}

		status?.let {
			Spacer(Modifier.height(12.dp))
			Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
		}

		Spacer(Modifier.height(24.dp))

		Button(onClick = { epoch += 1 }, modifier = Modifier.fillMaxWidth()) {
			Text("Refresh")
		}
	}
}
