package tk.chrk.qrloginapprover.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ApiException(message: String) : Exception(message)

/**
 * Talks to the ASF plugin. Paired-device calls go to `/SteamASF2FA/api` and carry a device token;
 * pairing itself goes to `/Api/QrLoginApprover/Pair` with the IPCPassword.
 */
class ApiClient(private val baseUrl: String, private val token: String? = null) {
	private val json = Json {
		ignoreUnknownKeys = true
		explicitNulls = false
	}

	private val client = OkHttpClient.Builder()
		.connectTimeout(10, TimeUnit.SECONDS)
		.readTimeout(60, TimeUnit.SECONDS)
		.build()

	val normalizedBaseUrl: String = baseUrl.trim().trimEnd('/').let {
		if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it"
	}

	private fun build(path: String, method: String, body: String?, password: String? = null): Request {
		val builder = Request.Builder().url(normalizedBaseUrl + path)

		if (body != null) {
			builder.method(method, body.toRequestBody(JSON_MEDIA))
		} else {
			builder.method(method, null)
		}

		token?.let { builder.header("X-Auth-Token", it) }
		password?.let { builder.header("Authentication", it) }

		return builder.build()
	}

	private suspend inline fun <reified T> call(request: Request): T = withContext(Dispatchers.IO) {
		client.newCall(request).execute().use { response ->
			val text = response.body?.string().orEmpty()

			if (!response.isSuccessful) {
				val message = try {
					json.decodeFromString<ApiError>(text).error
				} catch (_: Exception) {
					null
				}

				throw ApiException(message ?: "HTTP ${response.code}")
			}

			json.decodeFromString<T>(text)
		}
	}

	suspend fun getBots(): List<BotInfo> = call(build("/SteamASF2FA/api/bots", "GET", null))

	suspend fun qrInfo(bot: String, challengeUrl: String): QrInfo =
		call(build("/SteamASF2FA/api/qr/info", "POST", json.encodeToString(QrScanRequest(bot, challengeUrl))))

	suspend fun qrApprove(bot: String, challengeUrl: String, approve: Boolean): ApproveResult =
		call(build("/SteamASF2FA/api/qr/approve", "POST", json.encodeToString(QrApproveRequest(bot, challengeUrl, approve))))

	suspend fun confirmations(bots: List<String>): Map<String, ConfirmationsResult> =
		call(build("/SteamASF2FA/api/confirmations?bots=${bots.joinToString(",")}", "GET", null))

	suspend fun handleConfirmations(bots: List<String>, accept: Boolean, creatorIds: List<Long>? = null, type: Int? = null): Map<String, ConfirmationsResult> =
		call(build("/SteamASF2FA/api/confirmations", "POST", json.encodeToString(HandleConfirmationsRequest(bots, accept, creatorIds, type))))

	suspend fun code(bots: List<String>): Map<String, CodeResult> =
		call(build("/SteamASF2FA/api/code?bots=${bots.joinToString(",")}", "GET", null))

	suspend fun tradeOffer(bot: String, tradeOfferId: String): TradeOfferDetails =
		call(build("/SteamASF2FA/api/tradeoffer/$tradeOfferId?bot=$bot", "GET", null))

	/** Fetches the bot's Steam community profile (persona name + avatar) from the public profile XML. */
	suspend fun getSteamProfile(steamId: String): SteamProfile? = withContext(Dispatchers.IO) {
		try {
			val request = Request.Builder().url("https://steamcommunity.com/profiles/$steamId?xml=1").build()

			client.newCall(request).execute().use { response ->
				val text = response.body?.string().orEmpty()

				if (!response.isSuccessful || text.isBlank()) {
					null
				} else {
					SteamProfile(
						personaName = Regex("<steamID><!\\[CDATA\\[(.*?)\\]\\]></steamID>").find(text)?.groupValues?.get(1),
						avatarUrl = Regex("<avatarMedium><!\\[CDATA\\[(.*?)\\]\\]></avatarMedium>").find(text)?.groupValues?.get(1),
					)
				}
			}
		} catch (_: Exception) {
			null
		}
	}

	companion object {
		private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

		private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

		private val client = OkHttpClient.Builder()
			.connectTimeout(10, TimeUnit.SECONDS)
			.readTimeout(30, TimeUnit.SECONDS)
			.build()

		/** Pairs a new device using the ASF IPCPassword and returns its token. */
		suspend fun pair(baseUrl: String, password: String, deviceName: String): PairResponse {
			val normalized = baseUrl.trim().trimEnd('/').let {
				if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it"
			}

			val request = Request.Builder()
				.url("$normalized/Api/SteamASF2FA/Pair")
				.post(json.encodeToString(PairRequest(deviceName)).toRequestBody(JSON_MEDIA))
				.header("Authentication", password)
				.build()

			return withContext(Dispatchers.IO) {
				client.newCall(request).execute().use { response ->
					val text = response.body?.string().orEmpty()

					if (!response.isSuccessful) {
						val message = try {
							json.decodeFromString<ApiError>(text).error
						} catch (_: Exception) {
							null
						}

						throw ApiException(message ?: "Pairing failed (HTTP ${response.code})")
					}

					json.decodeFromString<PairResponse>(text)
				}
			}
		}
	}
}
