using System.Buffers.Binary;
using System.Globalization;
using System.Reflection;
using System.Security.Cryptography;
using System.Text.RegularExpressions;
using ArchiSteamFarm.Steam;
using SteamKit2;
using SteamKit2.Internal;

namespace QrLoginApprover;

/// <summary>
///     QR-login approval, done the way the Steam mobile app does it: parse the challenge URL, sign it with
///     the account's TOTP shared secret, then call <c>Authentication.UpdateAuthSessionWithMobileConfirmation</c>.
/// </summary>
internal static partial class QrApprovalService {
	/// <summary>Device confirmation polls the session every 5s by default; the URL lives longer than that.</summary>
	private const string AuthenticationService = "IAuthenticationService";

	/// <summary>Matches the QR challenge URL, e.g. <c>https://s.team/q/1/5558820178574646580</c>.</summary>
	[GeneratedRegex(@"^https?://s\.team/q/(?<version>\d+)/(?<clientid>\d+)", RegexOptions.CultureInvariant | RegexOptions.IgnoreCase)]
	private static partial Regex ChallengeUrlRegex { get; }

	internal static bool TryParseChallengeUrl(string? url, out int version, out ulong clientId) {
		version = 0;
		clientId = 0;

		if (string.IsNullOrWhiteSpace(url)) {
			return false;
		}

		Match match = ChallengeUrlRegex.Match(url.Trim());

		if (!match.Success) {
			return false;
		}

		if (!int.TryParse(match.Groups["version"].Value, NumberStyles.None, CultureInfo.InvariantCulture, out version)) {
			return false;
		}

		return ulong.TryParse(match.Groups["clientid"].Value, NumberStyles.None, CultureInfo.InvariantCulture, out clientId);
	}

	/// <summary>
	///     Computes the HMAC-SHA256 signature Steam expects: over <c>version(2 LE) || clientId(8 LE) || steamId(8 LE)</c>,
	///     keyed with the account's TOTP shared secret.
	/// </summary>
	internal static byte[] ComputeSignature(byte[] sharedSecret, int version, ulong clientId, ulong steamId) {
		Span<byte> data = stackalloc byte[2 + sizeof(ulong) + sizeof(ulong)];

		BinaryPrimitives.WriteUInt16LittleEndian(data, (ushort) version);
		BinaryPrimitives.WriteUInt64LittleEndian(data[2..], clientId);
		BinaryPrimitives.WriteUInt64LittleEndian(data[10..], steamId);

		return HmacSha256(sharedSecret, data);
	}

	/// <summary>
	///     HMAC-SHA256 built on <see cref="SHA256.HashData(byte[])" /> (which ASF itself uses), so the plugin does not
	///     depend on <c>HMACSHA256</c> that trimmed ASF builds may remove.
	/// </summary>
	private static byte[] HmacSha256(byte[] key, ReadOnlySpan<byte> data) {
		const int blockSize = 64;

		if (key.Length > blockSize) {
			key = SHA256.HashData(key);
		}

		Span<byte> innerPad = stackalloc byte[blockSize];
		Span<byte> outerPad = stackalloc byte[blockSize];

		for (int i = 0; i < blockSize; i++) {
			byte k = i < key.Length ? key[i] : (byte) 0;
			innerPad[i] = (byte) (k ^ 0x36);
			outerPad[i] = (byte) (k ^ 0x5C);
		}

		byte[] inner = new byte[blockSize + data.Length];
		innerPad.CopyTo(inner);
		data.CopyTo(inner.AsSpan(blockSize));
		byte[] innerHash = SHA256.HashData(inner);

		byte[] outer = new byte[blockSize + innerHash.Length];
		outerPad.CopyTo(outer);
		innerHash.CopyTo(outer.AsSpan(blockSize));

		return SHA256.HashData(outer);
	}

	/// <summary>
	///     Reads the bot's TOTP shared secret out of its ASF-mobile-authenticator database.
	/// </summary>
	/// <remarks>
	///     ASF keeps <c>SharedSecret</c> private and <c>BotDatabase.MobileAuthenticator</c> internal, so there is no
	///     public API for it. We read it via reflection; this is a community plugin, not part of ASF core.
	/// </remarks>
	internal static byte[]? TryGetSharedSecret(Bot bot) {
		try {
			object? database = bot.BotDatabase;

			PropertyInfo? authenticatorProperty = database.GetType().GetProperty("MobileAuthenticator", BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic);
			object? authenticator = authenticatorProperty?.GetValue(database);

			if (authenticator == null) {
				return null;
			}

			PropertyInfo? sharedSecretProperty = authenticator.GetType().GetProperty("SharedSecret", BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic);
			string? base64 = sharedSecretProperty?.GetValue(authenticator) as string;

			return string.IsNullOrEmpty(base64) ? null : Convert.FromBase64String(base64);
		} catch (Exception e) {
			bot.ArchiLogger.LogGenericWarningException(e);

			return null;
		}
	}

	internal static async Task<AuthSessionInfoResult> GetSessionInfoAsync(Bot bot, int version, ulong clientId) {
		CAuthentication_GetAuthSessionInfo_Request request = new() { client_id = clientId };

		WebAPI.WebAPIResponse<CAuthentication_GetAuthSessionInfo_Response>? response = await CallAsync<CAuthentication_GetAuthSessionInfo_Response, CAuthentication_GetAuthSessionInfo_Request>(bot, "GetAuthSessionInfo", request).ConfigureAwait(false);

		if (response?.Body == null) {
			return new AuthSessionInfoResult(false, "IAuthenticationService/GetAuthSessionInfo failed", null, null, null);
		}

		CAuthentication_GetAuthSessionInfo_Response body = response.Body;

		return new AuthSessionInfoResult(
			true,
			null,
			body.ip,
			body.device_friendly_name,
			new AuthSessionInfo(version, clientId.ToString(CultureInfo.InvariantCulture), body.city, body.state, body.country, body.platform_type, body.requestor_location_mismatch, body.high_usage_login, body.requested_persistence)
		);
	}

	private static async Task<ESessionPersistence> ResolveRequestedPersistenceAsync(Bot bot, int version, ulong clientId) {
		AuthSessionInfoResult result = await GetSessionInfoAsync(bot, version, clientId).ConfigureAwait(false);

		return result.Info?.RequestedPersistence ?? ESessionPersistence.k_ESessionPersistence_Persistent;
	}

	internal static async Task<ApproveResult> ApproveAsync(Bot bot, int version, ulong clientId, bool approve, ESessionPersistence? persistence = null) {
		byte[]? sharedSecret = TryGetSharedSecret(bot);

		if (sharedSecret == null) {
			return new ApproveResult(false, "This bot has no ASF 2FA (missing shared_secret), cannot approve QR logins", null);
		}

		// Honour the persistence the requesting client asked for, instead of forcing a remembered session.
		ESessionPersistence effectivePersistence = persistence ?? await ResolveRequestedPersistenceAsync(bot, version, clientId).ConfigureAwait(false);

		byte[] signature = ComputeSignature(sharedSecret, version, clientId, bot.SteamID);

		CAuthentication_UpdateAuthSessionWithMobileConfirmation_Request request = new() {
			version = version,
			client_id = clientId,
			steamid = bot.SteamID,
			signature = signature,
			confirm = approve,
			persistence = effectivePersistence
		};

		WebAPI.WebAPIResponse<CAuthentication_UpdateAuthSessionWithMobileConfirmation_Response>? response = await CallAsync<CAuthentication_UpdateAuthSessionWithMobileConfirmation_Response, CAuthentication_UpdateAuthSessionWithMobileConfirmation_Request>(bot, "UpdateAuthSessionWithMobileConfirmation", request).ConfigureAwait(false);

		if (response == null) {
			return new ApproveResult(false, "IAuthenticationService/UpdateAuthSessionWithMobileConfirmation failed", null);
		}

		return new ApproveResult(response.Result == EResult.OK, response.Result == EResult.OK ? null : $"Steam returned {response.Result}", response.Result);
	}

	private static async Task<WebAPI.WebAPIResponse<TResponse>?> CallAsync<TResponse, TRequest>(Bot bot, string method, TRequest request) where TResponse : ProtoBuf.IExtensible, new() where TRequest : ProtoBuf.IExtensible, new() {
		string? accessToken = bot.AccessToken;

		if (string.IsNullOrEmpty(accessToken)) {
			bot.ArchiLogger.LogGenericWarning("No access token available, is the bot logged on?");

			return null;
		}

		using WebAPI.AsyncInterface service = bot.SteamConfiguration.GetAsyncWebAPIInterface(AuthenticationService);

		Dictionary<string, object?> extraArgs = new(1, StringComparer.Ordinal) {
			{ "access_token", accessToken }
		};

		return await service.CallProtobufAsync<TResponse, TRequest>(HttpMethod.Post, method, request, extraArgs: extraArgs).ConfigureAwait(false);
	}
}

internal sealed record AuthSessionInfoResult(bool Success, string? Error, string? Ip, string? DeviceFriendlyName, AuthSessionInfo? Info);

internal sealed record AuthSessionInfo(int Version, string ClientId, string City, string State, string Country, EAuthTokenPlatformType PlatformType, bool LocationMismatch, bool HighUsageLogin, ESessionPersistence RequestedPersistence);

internal sealed record ApproveResult(bool Success, string? Error, EResult? Result);
