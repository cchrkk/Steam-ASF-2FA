using ArchiSteamFarm.Steam;
using ArchiSteamFarm.Steam.Data;

namespace QrLoginApprover;

/// <summary>
///     Resolves the contents of a trade offer from its ID, using ASF's own authenticated web handler.
///     The confirmation only carries the trade offer ID (as its creator ID), so this is the lookup that turns
///     "a trade needs confirming" into "you give these items, you receive these".
/// </summary>
internal static class TradeOfferService {
	internal static async Task<TradeOfferDetails?> GetAsync(Bot bot, ulong tradeOfferId) {
		HashSet<TradeOffer>? offers = await bot.ArchiWebHandler.GetTradeOffers(receivedOffers: true, sentOffers: true, withDescriptions: true).ConfigureAwait(false);

		if (offers == null) {
			return null;
		}

		TradeOffer? offer = offers.FirstOrDefault(candidate => candidate.TradeOfferID == tradeOfferId);

		return offer == null ? null : new TradeOfferDetails(
			offer.TradeOfferID.ToString(),
			offer.OtherSteamID64.ToString(),
			(int) offer.State,
			offer.ItemsToGiveReadOnly.Select(Map).ToList(),
			offer.ItemsToReceiveReadOnly.Select(Map).ToList()
		);
	}

	private const string EconomyImageBase = "https://community.cloudflare.steamstatic.com/economy/image/";

	private static TradeItem Map(Asset asset) {
		string icon = asset.Description?.IconURL ?? "";

		if (icon.Length > 0 && !icon.StartsWith("http", StringComparison.OrdinalIgnoreCase)) {
			icon = EconomyImageBase + icon;
		}

		return new TradeItem(
			asset.AppID,
			asset.Amount,
			asset.Description?.Name ?? asset.Description?.MarketHashName ?? "Unknown item",
			icon
		);
	}

	/// <summary>Lists the trade offers this bot currently sees. Returns null if the web call failed.</summary>
	internal static async Task<IReadOnlyList<TradeOfferSummary>?> ListAsync(Bot bot) {
		HashSet<TradeOffer>? offers = await bot.ArchiWebHandler.GetTradeOffers(receivedOffers: true, sentOffers: true, withDescriptions: true).ConfigureAwait(false);

		return offers?.Select(static offer => new TradeOfferSummary(offer.TradeOfferID.ToString(), offer.OtherSteamID64.ToString(), (int) offer.State, offer.ItemsToGiveReadOnly.Count, offer.ItemsToReceiveReadOnly.Count)).ToList();
	}
}

internal sealed record TradeOfferSummary(string TradeOfferID, string OtherSteamID64, int State, int ItemsToGiveCount, int ItemsToReceiveCount);

internal sealed record TradeItem(uint AppID, uint Amount, string Name, string IconURL);

internal sealed record TradeOfferDetails(string TradeOfferID, string OtherSteamID64, int State, IReadOnlyList<TradeItem> ItemsToGive, IReadOnlyList<TradeItem> ItemsToReceive);
