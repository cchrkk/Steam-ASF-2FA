using System.Diagnostics.CodeAnalysis;
using ArchiSteamFarm.Steam;
using ArchiSteamFarm.Steam.Data;
using Microsoft.AspNetCore.Mvc;
using SteamKit2;

namespace QrLoginApprover;

/// <summary>
///     API for paired devices. It lives outside <c>/Api</c> on purpose: ASF only guards <c>/Api</c> with the
///     IPCPassword, while this controller authenticates each request with a per-device token, so the app never has to
///     hold the master IPC password.
/// </summary>
[ApiController]
[Route("SteamASF2FA/api")]
public sealed class DeviceApiController : ControllerBase {
	private const int CheapLimitPerMinute = 120;
	private const int SteamLimitPerMinute = 30;

	[HttpGet("bots")]
	public IActionResult GetBots() {
		if (!TryAuthorize(out Device? device, out IActionResult? error)) {
			return error;
		}

		IReadOnlyDictionary<string, Bot>? bots = Bot.BotsReadOnly;

		IEnumerable<BotInfo> result = bots == null
			? []
			: bots.Values.Where(bot => IsAllowed(device, bot.BotName)).Select(static bot => new BotInfo(bot.BotName, bot.SteamID, bot.HasMobileAuthenticator, bot.IsConnectedAndLoggedOn));

		return Ok(result);
	}

	[HttpPost("qr/info")]
	public async Task<IActionResult> PostQrInfo([FromBody] ScanRequest request) {
		if (!TryAuthorize(out Device? device, out IActionResult? error)) {
			return error;
		}

		if (!TryResolveBot(device, request, out Bot? bot, out int version, out ulong clientId, out error)) {
			return error!;
		}

		AuthSessionInfoResult result = await QrApprovalService.GetSessionInfoAsync(bot!, version, clientId).ConfigureAwait(false);

		return result.Success ? Ok(result) : BadRequest(result);
	}

	[HttpPost("qr/approve")]
	public async Task<IActionResult> PostQrApprove([FromBody] ApproveRequest request) {
		if (!TryAuthorize(out Device? device, out IActionResult? error)) {
			return error;
		}

		if (!TryResolveBot(device, request, out Bot? bot, out int version, out ulong clientId, out error)) {
			return error!;
		}

		ApproveResult result = await QrApprovalService.ApproveAsync(bot!, version, clientId, request.Approve).ConfigureAwait(false);

		return result.Success ? Ok(result) : BadRequest(result);
	}

	[HttpGet("confirmations")]
	public async Task<IActionResult> GetConfirmations([FromQuery] string? bots) {
		if (!TryAuthorize(out Device? device, out IActionResult? error)) {
			return error;
		}

		List<Bot> resolved = ResolveBots(device, bots);
		Dictionary<string, object> response = [];

		foreach (Bot bot in resolved) {
			(bool success, IReadOnlyCollection<Confirmation>? confirmations, string message) = await bot.Actions.GetConfirmations().ConfigureAwait(false);

			response[bot.BotName] = new {
				success,
				message,
				confirmations = (confirmations ?? []).Select(static c => new ConfirmationInfo(c.ID.ToString(), c.CreatorID.ToString(), (int) c.ConfirmationType, c.ConfirmationTypeName))
			};
		}

		return Ok(response);
	}

	[HttpPost("confirmations")]
	public async Task<IActionResult> PostConfirmations([FromBody] ConfirmationsRequest request) {
		if (!TryAuthorize(out Device? device, out IActionResult? error)) {
			return error;
		}

		List<Bot> resolved = ResolveBots(device, request.Bots is { Count: > 0 } ? string.Join(',', request.Bots) : null);
		EMobileConfirmationType? acceptedType = request.AcceptedType.HasValue ? (EMobileConfirmationType) request.AcceptedType.Value : null;
		IReadOnlyCollection<ulong>? acceptedCreatorIDs = request.AcceptedCreatorIDs is { Count: > 0 } ? request.AcceptedCreatorIDs : null;

		if ((request.AcceptedType.HasValue) && !Enum.IsDefined(acceptedType!.Value)) {
			return BadRequest(new { error = $"Unknown confirmation type {request.AcceptedType.Value}" });
		}

		Dictionary<string, object> response = [];

		foreach (Bot bot in resolved) {
			(bool success, IReadOnlyCollection<Confirmation>? handled, string message) = await bot.Actions.HandleTwoFactorAuthenticationConfirmations(request.Accept, acceptedType, acceptedCreatorIDs, request.WaitIfNeeded).ConfigureAwait(false);

			response[bot.BotName] = new {
				success,
				message,
				handled = (handled ?? []).Select(static c => new ConfirmationInfo(c.ID.ToString(), c.CreatorID.ToString(), (int) c.ConfirmationType, c.ConfirmationTypeName))
			};
		}

		return Ok(response);
	}

	[HttpGet("code")]
	public async Task<IActionResult> GetCode([FromQuery] string? bots) {
		if (!TryAuthorize(out Device? device, out IActionResult? error, CheapLimitPerMinute)) {
			return error;
		}

		List<Bot> resolved = ResolveBots(device, bots);
		Dictionary<string, object> response = [];

		foreach (Bot bot in resolved) {
			(bool success, string? token, string message) = await bot.Actions.GenerateTwoFactorAuthenticationToken().ConfigureAwait(false);

			response[bot.BotName] = new { success, message, code = token };
		}

		return Ok(response);
	}

	[HttpGet("tradeoffers")]
	public async Task<IActionResult> GetTradeOffers([FromQuery] string? bot) {
		if (!TryAuthorize(out Device? device, out IActionResult? error)) {
			return error;
		}

		Bot? resolved = ResolveBot(device, bot);

		if (resolved == null) {
			return BadRequest(new { error = $"Unknown bot '{bot}'" });
		}

		IReadOnlyList<TradeOfferSummary>? offers = await TradeOfferService.ListAsync(resolved).ConfigureAwait(false);

		return offers == null ? StatusCode(502, new { error = "Could not fetch trade offers from Steam" }) : Ok(offers);
	}

	[HttpGet("tradeoffer/{id:long}")]
	public async Task<IActionResult> GetTradeOffer(long id, [FromQuery] string? bot) {
		if (!TryAuthorize(out Device? device, out IActionResult? error, CheapLimitPerMinute)) {
			return error;
		}

		Bot? resolved = ResolveBot(device, bot);

		if (resolved == null) {
			return BadRequest(new { error = $"Unknown bot '{bot}'" });
		}

		if (!resolved.IsConnectedAndLoggedOn) {
			return BadRequest(new { error = $"Bot '{bot}' is offline" });
		}

		TradeOfferDetails? details = await TradeOfferService.GetAsync(resolved, (ulong) id).ConfigureAwait(false);

		return details == null ? NotFound(new { error = $"Trade offer {id} not found" }) : Ok(details);
	}

	private bool TryAuthorize([NotNullWhen(true)] out Device? device, [NotNullWhen(false)] out IActionResult? error, int limitPerMinute = SteamLimitPerMinute) {
		error = null;

		string? token = Request.Headers["X-Auth-Token"].FirstOrDefault();

		if (string.IsNullOrEmpty(token)) {
			string? authorization = Request.Headers.Authorization.FirstOrDefault();

			if (authorization?.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase) == true) {
				token = authorization["Bearer ".Length..].Trim();
			}
		}

		string? ip = HttpContext.Connection.RemoteIpAddress?.ToString();
		device = DeviceStore.Validate(token, ip);

		if (device == null) {
			// Always write a body, otherwise ASF's status-code re-execution would serve the ASF-ui page instead
			error = Unauthorized(new { error = "Invalid, missing or expired device token" });

			return false;
		}

		if (!RateLimit.Allow(device.Id, limitPerMinute)) {
			error = StatusCode(429, new { error = "Too many requests" });

			return false;
		}

		return true;
	}

	private bool TryResolveBot(Device device, ScanRequest? request, [NotNullWhen(true)] out Bot? bot, out int version, out ulong clientId, [NotNullWhen(false)] out IActionResult? error) {
		bot = null;
		version = 0;
		clientId = 0;
		error = null;

		if ((request == null) || !QrApprovalService.TryParseChallengeUrl(request.QrChallengeUrl, out version, out clientId)) {
			error = BadRequest(new { error = "Invalid QR challenge URL" });

			return false;
		}

		bot = ResolveBot(device, request.Bot);

		if (bot == null) {
			error = BadRequest(new { error = $"Unknown or not allowed bot '{request.Bot}'" });

			return false;
		}

		if (!bot.HasMobileAuthenticator) {
			error = BadRequest(new { error = $"Bot '{request.Bot}' has no ASF 2FA (shared_secret)" });

			return false;
		}

		if (!bot.IsConnectedAndLoggedOn) {
			error = BadRequest(new { error = $"Bot '{request.Bot}' is offline" });

			return false;
		}

		return true;
	}

	private static bool IsAllowed(Device? device, string botName) => (device == null) || (device.Bots.Count == 0) || device.Bots.Contains(botName, StringComparer.Ordinal);

	private static Bot? ResolveBot(Device? device, string? name) {
		if (!IsAllowed(device, name ?? "")) {
			return null;
		}

		IReadOnlyDictionary<string, Bot>? bots = Bot.BotsReadOnly;

		return (bots != null) && !string.IsNullOrEmpty(name) && bots.TryGetValue(name, out Bot? bot) ? bot : null;
	}

	private static List<Bot> ResolveBots(Device device, string? bots) {
		IReadOnlyDictionary<string, Bot>? all = Bot.BotsReadOnly;

		if (all == null) {
			return [];
		}

		if (string.IsNullOrWhiteSpace(bots)) {
			return all.Values.Where(bot => IsAllowed(device, bot.BotName)).ToList();
		}

		List<Bot> result = [];

		foreach (string name in bots.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)) {
			if (IsAllowed(device, name) && all.TryGetValue(name, out Bot? bot)) {
				result.Add(bot);
			}
		}

		return result;
	}
}

public sealed record BotInfo(string Name, ulong SteamID, bool HasMobileAuthenticator, bool Online);

public sealed record ConfirmationInfo(string Id, string CreatorId, int Type, string? TypeName);

public sealed class ConfirmationsRequest {
	public List<string>? Bots { get; set; }

	public bool Accept { get; set; }

	public List<ulong>? AcceptedCreatorIDs { get; set; }

	public int? AcceptedType { get; set; }

	public bool WaitIfNeeded { get; set; }
}

public class ScanRequest {
	public string? Bot { get; set; }

	public string? QrChallengeUrl { get; set; }
}

public sealed class ApproveRequest : ScanRequest {
	public bool Approve { get; set; } = true;
}
