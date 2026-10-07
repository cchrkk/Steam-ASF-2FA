package tk.chrk.qrloginapprover.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tk.chrk.qrloginapprover.R
import tk.chrk.qrloginapprover.data.ApiClient
import tk.chrk.qrloginapprover.data.BotInfo
import tk.chrk.qrloginapprover.data.SettingsStore

@Composable
fun App() {
	val context = LocalContext.current
	val settings = remember { SettingsStore(context) }
	var paired by remember { mutableStateOf(settings.isPaired) }

	if (!paired) {
		SetupScreen(settings) { paired = true }
	} else {
		MainScreen(settings) {
			settings.unpair()
			paired = false
		}
	}
}

private enum class Tab(val label: String) { Codes("Codes"), Qr("QR"), Confirmations("Trade") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(settings: SettingsStore, onUnpair: () -> Unit) {
	val api = remember(settings.baseUrl, settings.token) { ApiClient(settings.baseUrl.orEmpty(), settings.token) }
	val scope = rememberCoroutineScope()

	var bots by remember { mutableStateOf<List<BotInfo>>(emptyList()) }
	var selected by remember { mutableStateOf<BotInfo?>(null) }
	var tab by remember { mutableStateOf(Tab.Codes) }
	var accountMenu by remember { mutableStateOf(false) }
	var overflowMenu by remember { mutableStateOf(false) }

	suspend fun refreshBots() {
		runCatching { api.getBots() }
			.onSuccess { list ->
				val usable = list.filter { it.hasMobileAuthenticator }
				val currentName = selected?.name
				bots = usable
				selected = usable.firstOrNull { it.name == currentName } ?: usable.firstOrNull { it.online } ?: usable.firstOrNull()
			}
	}

	LaunchedEffect(api) {
		while (true) {
			refreshBots()
			delay(15_000L)
		}
	}

	Scaffold(
		topBar = {
			CenterAlignedTopAppBar(
				title = {
					Column(horizontalAlignment = Alignment.CenterHorizontally) {
						Image(painter = painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(22.dp))
						Box {
							Row(
								verticalAlignment = Alignment.CenterVertically,
								modifier = Modifier.clickable(enabled = bots.isNotEmpty()) {
									scope.launch { refreshBots() }
									accountMenu = true
								},
							) {
								Text(selected?.name ?: "Steam ASF 2FA", style = MaterialTheme.typography.titleMedium)
								if (bots.isNotEmpty()) {
									Icon(Icons.Default.ArrowDropDown, contentDescription = "Switch account", modifier = Modifier.size(18.dp))
								}
							}
							DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
								bots.forEach { bot ->
									DropdownMenuItem(
										text = { Text(if (bot.online) bot.name else "${bot.name} (offline)") },
										onClick = {
											selected = bot
											accountMenu = false
										},
									)
								}
							}
						}
					}
				},
				actions = {
					Box {
						IconButton(onClick = { overflowMenu = true }) {
							Icon(Icons.Default.MoreVert, contentDescription = "Menu")
						}
						DropdownMenu(expanded = overflowMenu, onDismissRequest = { overflowMenu = false }) {
							DropdownMenuItem(text = { Text("Unpair") }, onClick = { overflowMenu = false; onUnpair() })
						}
					}
				},
			)
		},
		bottomBar = {
			NavigationBar {
				NavigationBarItem(
					selected = tab == Tab.Codes,
					onClick = { tab = Tab.Codes },
					icon = { Icon(Icons.Default.Key, contentDescription = null) },
					label = { Text("Codes") },
				)
				NavigationBarItem(
					selected = tab == Tab.Qr,
					onClick = { tab = Tab.Qr },
					icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
					label = { Text("QR") },
				)
				NavigationBarItem(
					selected = tab == Tab.Confirmations,
					onClick = { tab = Tab.Confirmations },
					icon = { Icon(Icons.Default.VerifiedUser, contentDescription = null) },
					label = { Text("Trade") },
				)
			}
		},
	) { padding ->
		val modifier = Modifier.padding(padding)

		when (tab) {
			Tab.Codes -> CodesScreen(api, selected, modifier = modifier)
			Tab.Qr -> QrScreen(api, selected, modifier = modifier)
			Tab.Confirmations -> ConfirmationsScreen(api, selected, modifier = modifier)
		}
	}
}
