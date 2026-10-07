package tk.chrk.qrloginapprover.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PairResponse(
	@SerialName("id") val id: String = "",
	@SerialName("name") val name: String = "",
	@SerialName("token") val token: String = "",
)

@Serializable
data class PairRequest(
	@SerialName("name") val name: String,
)

@Serializable
data class BotInfo(
	@SerialName("Name") val name: String = "",
	@SerialName("SteamID") val steamId: Long = 0,
	@SerialName("HasMobileAuthenticator") val hasMobileAuthenticator: Boolean = false,
	@SerialName("Online") val online: Boolean = false,
)

@Serializable
data class QrInfo(
	@SerialName("Success") val success: Boolean = false,
	@SerialName("Error") val error: String? = null,
	@SerialName("Ip") val ip: String? = null,
	@SerialName("DeviceFriendlyName") val deviceFriendlyName: String? = null,
	@SerialName("Info") val info: QrInfoDetails? = null,
)

@Serializable
data class QrInfoDetails(
	@SerialName("Version") val version: Int = 0,
	@SerialName("ClientId") val clientId: String = "",
	@SerialName("City") val city: String? = null,
	@SerialName("State") val state: String? = null,
	@SerialName("Country") val country: String? = null,
	@SerialName("PlatformType") val platformType: Int = 0,
	@SerialName("LocationMismatch") val locationMismatch: Boolean = false,
	@SerialName("HighUsageLogin") val highUsageLogin: Boolean = false,
)

@Serializable
data class QrScanRequest(
	@SerialName("bot") val bot: String,
	@SerialName("qrChallengeUrl") val qrChallengeUrl: String,
)

@Serializable
data class QrApproveRequest(
	@SerialName("bot") val bot: String,
	@SerialName("qrChallengeUrl") val qrChallengeUrl: String,
	@SerialName("approve") val approve: Boolean,
)

@Serializable
data class ApproveResult(
	@SerialName("Success") val success: Boolean = false,
	@SerialName("Error") val error: String? = null,
	@SerialName("Result") val result: Int? = null,
)

@Serializable
data class ConfirmationInfo(
	@SerialName("Id") val id: String = "",
	@SerialName("CreatorId") val creatorId: String = "",
	@SerialName("Type") val type: Int = 0,
	@SerialName("TypeName") val typeName: String? = null,
)

@Serializable
data class ConfirmationsResult(
	@SerialName("success") val success: Boolean = false,
	@SerialName("message") val message: String? = null,
	@SerialName("confirmations") val confirmations: List<ConfirmationInfo> = emptyList(),
)

@Serializable
data class HandleConfirmationsRequest(
	@SerialName("bots") val bots: List<String>,
	@SerialName("accept") val accept: Boolean,
	@SerialName("acceptedCreatorIDs") val acceptedCreatorIDs: List<Long>? = null,
	@SerialName("acceptedType") val acceptedType: Int? = null,
	@SerialName("waitIfNeeded") val waitIfNeeded: Boolean = false,
)

@Serializable
data class CodeResult(
	@SerialName("success") val success: Boolean = false,
	@SerialName("message") val message: String? = null,
	@SerialName("code") val code: String? = null,
)

@Serializable
data class ApiError(
	@SerialName("error") val error: String? = null,
)

/** Steam community profile (persona + avatar), fetched from the public profile XML. */
data class SteamProfile(
	val personaName: String?,
	val avatarUrl: String?,
)

@Serializable
data class TradeItem(
	@SerialName("AppID") val appId: Int = 0,
	@SerialName("Amount") val amount: Int = 0,
	@SerialName("Name") val name: String = "",
	@SerialName("IconURL") val iconUrl: String = "",
)

@Serializable
data class TradeOfferDetails(
	@SerialName("TradeOfferID") val tradeOfferId: String = "",
	@SerialName("OtherSteamID64") val otherSteamId: String = "",
	@SerialName("State") val state: Int = 0,
	@SerialName("ItemsToGive") val itemsToGive: List<TradeItem> = emptyList(),
	@SerialName("ItemsToReceive") val itemsToReceive: List<TradeItem> = emptyList(),
)
