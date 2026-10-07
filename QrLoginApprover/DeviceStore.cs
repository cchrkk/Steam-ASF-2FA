using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

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

	[JsonPropertyName("lastUsedAt")]
	public DateTime? LastUsedAt { get; set; }
}

/// <summary>
///     Persistent store of paired devices, kept in <c>config/QrLoginApprover.json</c> next to ASF's own files.
/// </summary>
internal static class DeviceStore {
	private static readonly object Lock = new();

	private static readonly string FilePath = Path.Combine(Path.Combine(Path.Combine(AppContext.BaseDirectory, "config"), "SteamASF2FA"), "devices.json");

	private static readonly JsonSerializerOptions JsonOptions = new() { WriteIndented = true };

	private static DeviceFile File = Load();

	private static string Hash(string token) => Convert.ToBase64String(SHA256.HashData(Encoding.UTF8.GetBytes(token)));

	private static DeviceFile Load() {
		try {
			if (System.IO.File.Exists(FilePath)) {
				return JsonSerializer.Deserialize<DeviceFile>(System.IO.File.ReadAllText(FilePath)) ?? new DeviceFile();
			}
		} catch { /* corrupted file, start clean */ }

		return new DeviceFile();
	}

	private static void Save() {
		Directory.CreateDirectory(Path.GetDirectoryName(FilePath)!);
		System.IO.File.WriteAllText(FilePath, JsonSerializer.Serialize(File, JsonOptions));
	}

	/// <summary>Creates a new paired device and returns its (id, raw token). The raw token is shown once.</summary>
	internal static (string Id, string Token) Create(string name) {
		byte[] bytes = new byte[32];
		RandomNumberGenerator.Fill(bytes);
		string token = Base64UrlEncode(bytes);

		Device device = new() {
			Id = Guid.NewGuid().ToString("N"),
			Name = name,
			TokenHash = Hash(token),
			CreatedAt = DateTime.UtcNow
		};

		lock (Lock) {
			File.Devices.Add(device);
			Save();
		}

		return (device.Id, token);
	}

	/// <summary>Returns the device owning the given token, or null. Uses a constant-time comparison.</summary>
	internal static Device? Validate(string? token) {
		if (string.IsNullOrEmpty(token)) {
			return null;
		}

		byte[] candidate = Encoding.UTF8.GetBytes(Hash(token));

		lock (Lock) {
			Device? match = null;

			foreach (Device device in File.Devices) {
				if (CryptographicOperations.FixedTimeEquals(Encoding.UTF8.GetBytes(device.TokenHash), candidate)) {
					match = device;

					break;
				}
			}

			if (match != null) {
				match.LastUsedAt = DateTime.UtcNow;
				Save();
			}

			return match;
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

	private static string Base64UrlEncode(byte[] bytes) => Convert.ToBase64String(bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_');

	private sealed class DeviceFile {
		[JsonPropertyName("devices")]
		public List<Device> Devices { get; set; } = [];
	}
}
