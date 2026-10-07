using Microsoft.AspNetCore.Mvc;

namespace QrLoginApprover;

/// <summary>
///     Pairing endpoints, protected by ASF's <c>IPCPassword</c> (everything under <c>/Api</c> is). The app uses these
///     once to obtain its device token; all day-to-day calls go to <see cref="DeviceApiController" /> with that token.
/// </summary>
[ApiController]
[Route("Api/SteamASF2FA")]
public sealed class QrLoginApproverController : ControllerBase {
	/// <summary>Pairs a new app device, returning its token once. Optionally restrict it to a set of bots.</summary>
	[HttpPost("Pair")]
	public IActionResult PostPair([FromBody] PairRequest request) {
		string name = string.IsNullOrWhiteSpace(request?.Name) ? "Unnamed device" : request!.Name!.Trim();

		(string id, string token, DateTime expiresAt) = DeviceStore.Create(name, request?.Bots);

		return Ok(new { id, name, token, expiresAt });
	}

	/// <summary>Lists paired devices (never their tokens).</summary>
	[HttpGet("Devices")]
	public IActionResult GetDevices() => Ok(DeviceStore.List().Select(static device => new { device.Id, device.Name, device.CreatedAt, device.ExpiresAt, device.LastUsedAt, device.LastUsedIp, device.Bots }));

	/// <summary>Revokes a paired device.</summary>
	[HttpDelete("Devices/{id}")]
	public IActionResult DeleteDevice(string id) => DeviceStore.Revoke(id) ? Ok(new { revoked = id }) : NotFound(new { error = $"Unknown device '{id}'" });

	/// <summary>Issues a fresh token for a device, invalidating the previous one.</summary>
	[HttpPost("Devices/{id}/Rotate")]
	public IActionResult RotateDevice(string id) {
		string? token = DeviceStore.Rotate(id);

		return token == null ? NotFound(new { error = $"Unknown device '{id}'" }) : Ok(new { id, token });
	}
}

public sealed class PairRequest {
	public string? Name { get; set; }

	/// <summary>Optional allowlist of bots this device may act on. Empty means every bot.</summary>
	public List<string>? Bots { get; set; }
}
