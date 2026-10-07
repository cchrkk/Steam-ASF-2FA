using Microsoft.AspNetCore.Mvc;

namespace QrLoginApprover;

/// <summary>
///     Pairing endpoints, protected by ASF's <c>IPCPassword</c> (everything under <c>/Api</c> is). The app uses these
///     once to obtain its device token; all day-to-day calls go to <see cref="DeviceApiController" /> with that token.
/// </summary>
[ApiController]
[Route("Api/SteamASF2FA")]
public sealed class QrLoginApproverController : ControllerBase {
	/// <summary>Pairs a new app device, returning its token once.</summary>
	[HttpPost("Pair")]
	public IActionResult PostPair([FromBody] PairRequest request) {
		string name = string.IsNullOrWhiteSpace(request?.Name) ? "Unnamed device" : request!.Name!.Trim();

		(string id, string token) = DeviceStore.Create(name);

		return Ok(new { id, name, token });
	}

	/// <summary>Lists paired devices (never their tokens).</summary>
	[HttpGet("Devices")]
	public IActionResult GetDevices() => Ok(DeviceStore.List().Select(static device => new { device.Id, device.Name, device.CreatedAt, device.LastUsedAt }));

	/// <summary>Revokes a paired device.</summary>
	[HttpDelete("Devices/{id}")]
	public IActionResult DeleteDevice(string id) => DeviceStore.Revoke(id) ? Ok(new { revoked = id }) : NotFound(new { error = $"Unknown device '{id}'" });
}

public sealed class PairRequest {
	public string? Name { get; set; }
}
