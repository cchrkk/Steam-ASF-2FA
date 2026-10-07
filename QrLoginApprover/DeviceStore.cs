using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using ArchiSteamFarm.Core;

namespace QrLoginApprover;

/// <summary>A paired device. The raw token is never stored, only its SHA-256 hash.</summary>
internal sealed class Device {
	[JsonPropertyName("id")]
	public string Id { get; set; } = "";

	[JsonPropertyName("name")]
	public string Name { get; set; } = "";

	[JsonPropertyName("tokenHash")]
	public string TokenHash { get; set; } = "";

	[JsonPropertyName("createdAt")]
	public DateTime CreatedAt { get; set; }

	[JsonPropertyName("expiresAt")]
	public DateTime ExpiresAt { get; set; }

	[JsonPropertyName("lastUsedAt")]
	public DateTime? LastUsedAt { get; set; }

	[JsonPropertyName("lastUsedIp")]
	public string? LastUsedIp { get; set; }

	/// <summary>Bots this device may act on. Empty means every bot.</summary>
	[JsonPropertyName("bots")]
	public List<string> Bots { get; set; } = [];
}

/// <summary>
///     Persistent store of paired devices, kept in <c>config/SteamASF2FA/devices.json</c> next to ASF's own files.
/// </summary>
internal static class DeviceStore {
	internal static readonly TimeSpan DefaultLifetime = TimeSpan.FromDays(90);

	private static readonly object Lock = new();
	private static readonly JsonSerializerOptions JsonOptions = new() { WriteIndented = true };
	private static readonly TimeSpan FlushInterval = TimeSpan.FromSeconds(60);

	private static readonly string FilePath = Path.Combine(Path.Combine(Path.Combine(AppContext.BaseDirectory, "config"), "SteamASF2FA"), "devices.json");

	private static DeviceFile File = Load();

	private static DateTime LastFlushUtc = DateTime.MinValue;

	private static string Hash(string token) => Convert.ToBase64String(SHA256.HashData(Encoding.UTF8.GetBytes(token)));

	private static DeviceFile Load() {
		try {
			if (System.IO.File.Exists(FilePath)) {
				return JsonSerializer.Deserialize<DeviceFile>(System.IO.File.ReadAllText(FilePath)) ?? new DeviceFile();
			}
		} catch (Exception e) {
			ASF.ArchiLogger.LogGenericWarningException(e);
			ASF.ArchiLogger.LogGenericWarning("SteamASF2FA: devices.json is unreadable, keeping a copy and starting empty (all devices must re-pair)");

			try {
				System.IO.File.Copy(FilePath, $"{FilePath}.corrupt-{DateTime.UtcNow:yyyyMMddHHmmss}", true);
			} catch { /* best effort */ }
		}

		return new DeviceFile();
	}

	private static void Save() {
		try {
			Directory.CreateDirectory(Path.GetDirectoryName(FilePath)!);
			string temp = FilePath + ".tmp";
			System.IO.File.WriteAllText(temp, JsonSerializer.Serialize(File, JsonOptions));
			System.IO.File.Move(temp, FilePath, true);
		} catch (Exception e) {
			ASF.ArchiLogger.LogGenericWarningException(e);
		}
	}

	/// <summary>Writes at most once per <see cref="FlushInterval" /> so a request flood cannot hammer the disk.</summary>
	private static void FlushIfDue() {
		if (DateTime.UtcNow - LastFlushUtc < FlushInterval) {
			return;
		}

		LastFlushUtc = DateTime.UtcNow;
		Save();
	}

	/// <summary>Creates a new paired device and returns its (id, raw token, expiry). The raw token is shown once.</summary>
	internal static (string Id, string Token, DateTime ExpiresAt) Create(string name, IEnumerable<string>? bots = null) {
		byte[] bytes = new byte[32];
		RandomNumberGenerator.Fill(bytes);
		string token = Base64UrlEncode(bytes);
		DateTime expiresAt = DateTime.UtcNow.Add(DefaultLifetime);

		Device device = new() {
			Id = Guid.NewGuid().ToString("N"),
			Name = name,
			TokenHash = Hash(token),
			CreatedAt = DateTime.UtcNow,
			ExpiresAt = expiresAt,
			Bots = bots?.Where(static bot => !string.IsNullOrWhiteSpace(bot)).Select(static bot => bot.Trim()).Distinct(StringComparer.Ordinal).ToList() ?? []
		};

		lock (Lock) {
			File.Devices.Add(device);
			Save();
		}

		return (device.Id, token, expiresAt);
	}

	/// <summary>Returns the device owning the given token, or null. Uses a constant-time comparison.</summary>
	internal static Device? Validate(string? token, string? ip = null) {
		if (string.IsNullOrEmpty(token)) {
			return null;
		}

		byte[] candidate = Encoding.UTF8.GetBytes(Hash(token));

		lock (Lock) {
			foreach (Device device in File.Devices) {
				if (!CryptographicOperations.FixedTimeEquals(Encoding.UTF8.GetBytes(device.TokenHash), candidate)) {
					continue;
				}

				if ((device.ExpiresAt != default) && (device.ExpiresAt <= DateTime.UtcNow)) {
					ASF.ArchiLogger.LogGenericWarning($"SteamASF2FA: device '{device.Name}' token expired, re-pairing required");

					return null;
				}

				if (!string.IsNullOrEmpty(ip) && !string.Equals(device.LastUsedIp, ip, StringComparison.Ordinal)) {
					if (!string.IsNullOrEmpty(device.LastUsedIp)) {
						ASF.ArchiLogger.LogGenericWarning($"SteamASF2FA: device '{device.Name}' is being used from a new IP {ip} (was {device.LastUsedIp})");
					}

					device.LastUsedIp = ip;
				}

				device.LastUsedAt = DateTime.UtcNow;
				FlushIfDue();

				return device;
			}

			return null;
		}
	}

	internal static IReadOnlyList<Device> List() {
		lock (Lock) {
			return File.Devices.ToList();
		}
	}

	internal static bool Revoke(string id) {
		lock (Lock) {
			int removed = File.Devices.RemoveAll(device => device.Id == id);

			if (removed > 0) {
				Save();
			}

			return removed > 0;
		}
	}

	/// <summary>Issues a fresh token for an existing device, invalidating the previous one. Returns null if unknown.</summary>
	internal static string? Rotate(string id) {
		lock (Lock) {
			Device? device = File.Devices.FirstOrDefault(candidate => candidate.Id == id);

			if (device == null) {
				return null;
			}

			byte[] bytes = new byte[32];
			RandomNumberGenerator.Fill(bytes);
			string token = Base64UrlEncode(bytes);

			device.TokenHash = Hash(token);
			device.CreatedAt = DateTime.UtcNow;
			device.ExpiresAt = DateTime.UtcNow.Add(DefaultLifetime);
			device.LastUsedAt = null;
			device.LastUsedIp = null;

			Save();

			return token;
		}
	}

	private static string Base64UrlEncode(byte[] bytes) => Convert.ToBase64String(bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_');

	private sealed class DeviceFile {
		[JsonPropertyName("devices")]
		public List<Device> Devices { get; set; } = [];
	}
}
