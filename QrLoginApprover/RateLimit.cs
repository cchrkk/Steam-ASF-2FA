namespace QrLoginApprover;

/// <summary>
///     Tiny fixed-window rate limiter for the device API. It exists to protect the ASF process and, above all, the
///     Steam account from request floods (the Steam-backed endpoints make real calls to Steam).
/// </summary>
internal static class RateLimit {
	private const int MaxTrackedKeys = 4096;

	private static readonly object Lock = new();
	private static readonly Dictionary<string, (long Window, int Count)> Counters = new(StringComparer.Ordinal);

	/// <summary>Returns true if the caller is allowed one more hit within the current minute.</summary>
	internal static bool Allow(string key, int limitPerMinute) {
		long window = DateTimeOffset.UtcNow.ToUnixTimeSeconds() / 60;

		lock (Lock) {
			if (Counters.Count > MaxTrackedKeys) {
				foreach (string stale in Counters.Where(pair => pair.Value.Window != window).Select(static pair => pair.Key).ToList()) {
					Counters.Remove(stale);
				}
			}

			if (Counters.TryGetValue(key, out (long Window, int Count) entry) && (entry.Window == window)) {
				if (entry.Count >= limitPerMinute) {
					return false;
				}

				Counters[key] = (window, entry.Count + 1);
			} else {
				Counters[key] = (window, 1);
			}

			return true;
		}
	}
}
