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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import tk.chrk.qrloginapprover.data.ApiClient
import tk.chrk.qrloginapprover.data.BotInfo
import tk.chrk.qrloginapprover.data.ConfirmationInfo
import tk.chrk.qrloginapprover.data.TradeItem
import tk.chrk.qrloginapprover.data.TradeOfferDetails

private const val TYPE_TRADE = 2

@Composable
fun ConfirmationsScreen(api: ApiClient, selected: BotInfo?, modifier: Modifier = Modifier) {
	val scope = rememberCoroutineScope()

	var confirmations by remember { mutableStateOf<List<Pair<String, ConfirmationInfo>>>(emptyList()) }
	val details = remember { mutableStateMapOf<String, TradeOfferDetails>() }
	val errors = remember { mutableStateMapOf<String, String>() }
	var loading by remember { mutableStateOf<Set<String>>(emptySet()) }
	var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }
	var busy by remember { mutableStateOf(false) }
	var loaded by remember { mutableStateOf(false) }
	var status by remember { mutableStateOf<String?>(null) }

	val targets = remember(selected) { selected?.let { listOf(it.name) } ?: emptyList() }

	fun refresh() {
		if (targets.isEmpty()) {
			return
		}

		busy = true
		status = null
		scope.launch {
			try {
				val result = api.confirmations(targets)
				confirmations = result.flatMap { (botName, value) -> value.confirmations.map { botName to it } }
				details.clear()
				errors.clear()
			} catch (e: Exception) {
				status = e.message
			} finally {
				busy = false
				loaded = true
			}
		}
	}

	LaunchedEffect(targets) { refresh() }

	fun decide(accept: Boolean, botNames: List<String>, creatorIds: List<Long>?) {
		if (botNames.isEmpty()) {
			return
		}

		busy = true
		scope.launch {
			try {
				api.handleConfirmations(botNames, accept, creatorIds)
				status = if (accept) "Accepted" else "Declined"
				refresh()
			} catch (e: Exception) {
				status = e.message
			} finally {
				busy = false
			}
		}
	}

	fun loadItems(botName: String, confirmation: ConfirmationInfo) {
		if (details.containsKey(confirmation.id) || loading.contains(confirmation.id)) {
			return
		}

		loading = loading + confirmation.id
		errors.remove(confirmation.id)

		scope.launch {
			try {
				details[confirmation.id] = api.tradeOffer(botName, confirmation.creatorId)
			} catch (e: Exception) {
				errors[confirmation.id] = e.message ?: "Could not load items"
			} finally {
				loading = loading - confirmation.id
			}
		}
	}

	fun toggleItems(botName: String, confirmation: ConfirmationInfo) {
		val nowExpanded = !expanded.contains(confirmation.id)
		expanded = if (nowExpanded) expanded + confirmation.id else expanded - confirmation.id

		if (nowExpanded) {
			loadItems(botName, confirmation)
		}
	}

	Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
		Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
			Text("Pending confirmations", style = MaterialTheme.typography.titleLarge)
			Spacer(Modifier.weight(1f))
			IconButton(onClick = { refresh() }, enabled = !busy) {
				Icon(Icons.Default.Refresh, contentDescription = "Refresh")
			}
		}

		status?.let {
			Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
			Spacer(Modifier.height(8.dp))
		}

		when {
			(busy && confirmations.isEmpty()) || (!loaded && confirmations.isEmpty()) -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
			loaded && confirmations.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
				EmptyState(Icons.Default.VerifiedUser, "All clear", "No pending trade or market confirmations.")
			}
			else -> {
				LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
					items(confirmations, key = { it.second.id }) { (botName, confirmation) ->
						ConfirmationCard(
							confirmation = confirmation,
							expanded = expanded.contains(confirmation.id),
							loading = loading.contains(confirmation.id),
							offer = details[confirmation.id],
							error = errors[confirmation.id],
							busy = busy,
							onToggle = { toggleItems(botName, confirmation) },
							onRetry = { loadItems(botName, confirmation) },
							onAccept = { decide(true, listOf(botName), listOfNotNull(confirmation.creatorId.toLongOrNull())) },
							onDeny = { decide(false, listOf(botName), listOfNotNull(confirmation.creatorId.toLongOrNull())) },
						)
					}
				}

				if (confirmations.isNotEmpty()) {
					Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
						OutlinedButton(onClick = { decide(false, targets, null) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Deny all") }
						Button(onClick = { decide(true, targets, null) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Accept all") }
					}
				}
			}
		}
	}
}

@Composable
private fun ConfirmationCard(
	confirmation: ConfirmationInfo,
	expanded: Boolean,
	loading: Boolean,
	offer: TradeOfferDetails?,
	error: String?,
	busy: Boolean,
	onToggle: () -> Unit,
	onRetry: () -> Unit,
	onAccept: () -> Unit,
	onDeny: () -> Unit,
) {
	Card(
		colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
		modifier = Modifier.fillMaxWidth(),
	) {
		Column(modifier = Modifier.padding(14.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				LeadingIconBadge(if (confirmation.type == TYPE_TRADE) Icons.Default.SwapHoriz else Icons.Default.Storefront)
				Spacer(Modifier.width(12.dp))
				Column(modifier = Modifier.weight(1f)) {
					Text(confirmation.typeName ?: "Confirmation", style = MaterialTheme.typography.titleMedium)
					Text("#${confirmation.creatorId}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
				if (confirmation.type == TYPE_TRADE) {
					TextButton(onClick = onToggle) { Text(if (expanded) "Hide" else "Items") }
				}
			}

			if (expanded) {
				Spacer(Modifier.height(4.dp))

				when {
					loading -> CircularProgressIndicator(modifier = Modifier.height(20.dp))
					offer != null -> {
						ItemList("You give", offer.itemsToGive, emptyLabel = "nothing")
						ItemList("You receive", offer.itemsToReceive, emptyLabel = "nothing")
					}
					error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
						Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
						TextButton(onClick = onRetry) { Text("Retry") }
					}
				}
			}

			Spacer(Modifier.height(10.dp))
			Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
				Button(onClick = onAccept, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Accept") }
				OutlinedButton(onClick = onDeny, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Deny") }
			}
		}
	}
}

@Composable
private fun ItemList(title: String, items: List<TradeItem>, emptyLabel: String) {
	Column(modifier = Modifier.padding(vertical = 4.dp)) {
		SectionTitle(title)

		if (items.isEmpty()) {
			Text(emptyLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
			return
		}

		items.forEach { item ->
			Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
				if (item.iconUrl.isNotBlank()) {
					AsyncImage(model = item.iconUrl, contentDescription = null, modifier = Modifier.size(36.dp))
					Spacer(Modifier.width(10.dp))
				}
				Text(if (item.amount > 1) "${item.name} ×${item.amount}" else item.name, style = MaterialTheme.typography.bodyMedium)
			}
		}
	}
}
