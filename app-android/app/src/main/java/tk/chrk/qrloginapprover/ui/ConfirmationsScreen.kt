package tk.chrk.qrloginapprover.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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

	// (botName, confirmation) pairs, flattened across the queried bots
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
		Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
			Button(onClick = { refresh() }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Refresh") }
			OutlinedButton(onClick = { decide(true, targets, null) }, enabled = !busy && confirmations.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Accept all") }
			OutlinedButton(onClick = { decide(false, targets, null) }, enabled = !busy && confirmations.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Deny all") }
		}

		Spacer(Modifier.height(8.dp))
		status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
		Spacer(Modifier.height(8.dp))

		if (busy && confirmations.isEmpty()) {
			CircularProgressIndicator()
		} else if (loaded && confirmations.isEmpty()) {
			Text("No pending confirmations.", color = MaterialTheme.colorScheme.onSurfaceVariant)
		} else {
			LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				items(confirmations, key = { it.second.id }) { (botName, confirmation) ->
					Card(modifier = Modifier.fillMaxWidth()) {
						Column(modifier = Modifier.padding(12.dp)) {
							Text(confirmation.typeName ?: "Confirmation", style = MaterialTheme.typography.titleMedium)
							Text("#${confirmation.creatorId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

							if (confirmation.type == TYPE_TRADE) {
								TextButton(onClick = { toggleItems(botName, confirmation) }) {
									Text(if (expanded.contains(confirmation.id)) "Hide items" else "Show items")
								}

								if (expanded.contains(confirmation.id)) {
									val offer = details[confirmation.id]
									val error = errors[confirmation.id]

									when {
										loading.contains(confirmation.id) -> CircularProgressIndicator(modifier = Modifier.height(20.dp))
										offer != null -> {
											ItemList("You give", offer.itemsToGive)
											ItemList("You receive", offer.itemsToReceive)
										}
										error != null -> Column {
											Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
											TextButton(onClick = { loadItems(botName, confirmation) }) { Text("Retry") }
										}
									}
								}
							}

							Spacer(Modifier.height(8.dp))
							Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
								Button(
									onClick = { decide(true, listOf(botName), listOfNotNull(confirmation.creatorId.toLongOrNull())) },
									enabled = !busy,
									modifier = Modifier.weight(1f),
								) { Text("Accept") }
								OutlinedButton(
									onClick = { decide(false, listOf(botName), listOfNotNull(confirmation.creatorId.toLongOrNull())) },
									enabled = !busy,
									modifier = Modifier.weight(1f),
								) { Text("Deny") }
							}
						}
					}
				}
			}
		}
	}
}

@Composable
private fun ItemList(title: String, items: List<TradeItem>) {
	if (items.isEmpty()) {
		return
	}

	Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
	items.forEach { item ->
		Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
			if (item.iconUrl.isNotBlank()) {
				AsyncImage(model = item.iconUrl, contentDescription = null, modifier = Modifier.size(36.dp))
				Spacer(Modifier.size(8.dp))
			}
			Text(if (item.amount > 1) "${item.name} x${item.amount}" else item.name, style = MaterialTheme.typography.bodyMedium)
		}
	}
}
