using System.Composition;
using ArchiSteamFarm.Core;
using ArchiSteamFarm.Plugins.Interfaces;

namespace QrLoginApprover;

/// <summary>
///     Lets ASF act as the "Steam mobile app" for QR logins and trade confirmations, driven by the companion
///     Android app. The app pairs once (getting its own device token) and then talks only to this plugin.
/// </summary>
[Export(typeof(IPlugin))]
internal sealed class SteamAsf2faPlugin : IPlugin, IGitHubPluginUpdates {
	public string Name => "SteamASF2FA";

	public Version Version => typeof(SteamAsf2faPlugin).Assembly.GetName().Version ?? new Version(1, 0, 0, 0);

	/// <summary>GitHub repository used by ASF for automatic plugin updates. Releases must be tagged with the plugin version.</summary>
	string IGitHubPluginUpdates.RepositoryName => "cchrkk/Steam-ASF-2FA";

	public Task OnLoaded() {
		ASF.ArchiLogger.LogGenericInfo($"{Name} loaded - pair the Android app via POST /Api/SteamASF2FA/Pair");

		return Task.CompletedTask;
	}
}
