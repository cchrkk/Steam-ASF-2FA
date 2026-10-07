package tk.chrk.qrloginapprover.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tk.chrk.qrloginapprover.R
import tk.chrk.qrloginapprover.data.ApiClient
import tk.chrk.qrloginapprover.data.SettingsStore

@Composable
fun SetupScreen(settings: SettingsStore, onPaired: () -> Unit) {
	var baseUrl by remember { mutableStateOf(settings.baseUrl.orEmpty()) }
	var password by remember { mutableStateOf("") }
	var deviceName by remember { mutableStateOf(settings.deviceName) }
	var busy by remember { mutableStateOf(false) }
	var error by remember { mutableStateOf<String?>(null) }

	val scope = rememberCoroutineScope()

	Column(
		modifier = Modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(24.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		Spacer(Modifier.height(24.dp))

		Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(96.dp)) {
			Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
				Image(painter = painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(52.dp))
			}
		}

		Spacer(Modifier.height(20.dp))
		Text("Steam ASF 2FA", style = MaterialTheme.typography.headlineSmall)
		Spacer(Modifier.height(6.dp))
		Text(
			"Pair once with your ASF instance. The app gets its own device token and never keeps the password.",
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			textAlign = TextAlign.Center,
		)

		Spacer(Modifier.height(28.dp))

		OutlinedTextField(
			value = baseUrl,
			onValueChange = { baseUrl = it },
			label = { Text("ASF address") },
			placeholder = { Text("192.168.1.x:1242") },
			singleLine = true,
			modifier = Modifier.fillMaxWidth(),
		)
		Spacer(Modifier.height(12.dp))

		OutlinedTextField(
			value = password,
			onValueChange = { password = it },
			label = { Text("IPCPassword") },
			singleLine = true,
			visualTransformation = PasswordVisualTransformation(),
			modifier = Modifier.fillMaxWidth(),
		)
		Spacer(Modifier.height(12.dp))

		OutlinedTextField(
			value = deviceName,
			onValueChange = { deviceName = it },
			label = { Text("Device name") },
			singleLine = true,
			modifier = Modifier.fillMaxWidth(),
		)

		error?.let {
			Spacer(Modifier.height(12.dp))
			Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
		}

		Spacer(Modifier.height(24.dp))

		Button(
			onClick = {
				busy = true
				error = null
				scope.launch {
					try {
						val response = ApiClient.pair(baseUrl, password, deviceName)
						settings.baseUrl = baseUrl
						settings.token = response.token
						settings.deviceName = deviceName
						onPaired()
					} catch (e: Exception) {
						error = e.message
					} finally {
						busy = false
					}
				}
			},
			enabled = !busy && baseUrl.isNotBlank() && password.isNotBlank(),
			modifier = Modifier.fillMaxWidth().height(52.dp),
		) {
			if (busy) {
				CircularProgressIndicator(modifier = Modifier.size(20.dp))
			} else {
				Text("Pair", style = MaterialTheme.typography.titleMedium)
			}
		}
	}
}
