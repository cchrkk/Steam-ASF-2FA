package tk.chrk.qrloginapprover.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tk.chrk.qrloginapprover.R
import tk.chrk.qrloginapprover.data.ApiClient
import tk.chrk.qrloginapprover.data.BotInfo
import tk.chrk.qrloginapprover.data.SettingsStore
import tk.chrk.qrloginapprover.data.SteamProfile

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

private enum class Tab(val label: String, val icon: ImageVector) {
	Codes("Codes", Icons.Default.Key),
	Qr("Scan", Icons.Default.QrCodeScanner),
	Confirmations("Trade", Icons.Default.VerifiedUser),
}

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

	val profiles = remember { mutableStateMapOf<String, SteamProfile?>() }

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

	// Cache the avatar/persona of the selected account
	LaunchedEffect(selected?.name) {
		val bot = selected ?: return@LaunchedEffect

		if (!profiles.containsKey(bot.name)) {
			profiles[bot.name] = api.getSteamProfile(bot.steamId.toString())
		}
	}

	val profile = selected?.let { profiles[it.name] }

	Scaffold(
		topBar = {
			TopAppBar(
				colors = TopAppBarDefaults.topAppBarColors(
					containerColor = MaterialTheme.colorScheme.surface,
					titleContentColor = MaterialTheme.colorScheme.onSurface,
				),
				navigationIcon = {
					Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.padding(start = 12.dp)) {
						Image(painter = painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.padding(7.dp).size(18.dp))
					}
				},
				title = {
					Box {
						Row(
							verticalAlignment = Alignment.CenterVertically,
							modifier = Modifier
								.clip(RoundedCornerShape(50))
								.clickable(enabled = bots.isNotEmpty()) {
									scope.launch { refreshBots() }
									accountMenu = true
								}
								.padding(horizontal = 8.dp, vertical = 4.dp),
						) {
							if (selected != null) {
								AccountAvatar(profile?.avatarUrl, 32.dp)
								Spacer(Modifier.width(10.dp))
							}

							Column {
								Text(
									text = selected?.name ?: "Steam ASF 2FA",
									style = MaterialTheme.typography.titleMedium,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
								)
								if (selected != null) {
									Text(
										text = if (selected!!.online) "online" else "offline",
										style = MaterialTheme.typography.labelSmall,
										color = if (selected!!.online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
									)
								}
							}

							if (bots.isNotEmpty()) {
								Icon(Icons.Default.ArrowDropDown, contentDescription = "Switch account", modifier = Modifier.size(20.dp))
							}
						}

						DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
							bots.forEach { bot ->
								DropdownMenuItem(
									leadingIcon = { AccountAvatar(profiles[bot.name]?.avatarUrl, 28.dp) },
									text = {
										Column {
											Text(bot.name, style = MaterialTheme.typography.bodyLarge)
											Text(
												if (bot.online) "online" else "offline",
												style = MaterialTheme.typography.labelSmall,
												color = if (bot.online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
											)
										}
									},
									onClick = {
										selected = bot
										accountMenu = false
									},
								)
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
							DropdownMenuItem(text = { Text("Unpair this device") }, onClick = { overflowMenu = false; onUnpair() })
						}
					}
				},
			)
		},
		bottomBar = {
			NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
				Tab.entries.forEach { entry ->
					NavigationBarItem(
						selected = tab == entry,
						onClick = { tab = entry },
						icon = { Icon(entry.icon, contentDescription = null) },
						label = { Text(entry.label) },
					)
				}
			}
		},
		containerColor = MaterialTheme.colorScheme.background,
	) { padding ->
		val modifier = Modifier.padding(padding)

		when (tab) {
			Tab.Codes -> CodesScreen(api, selected, profile, modifier = modifier)
			Tab.Qr -> QrScreen(api, selected, profile, modifier = modifier)
			Tab.Confirmations -> ConfirmationsScreen(api, selected, modifier = modifier)
		}
	}
}
