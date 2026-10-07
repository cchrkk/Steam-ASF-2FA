# API reference

Base: `http://<asf-host>:1242`. Two authentication schemes.

- **IPCPassword** — ASF's own guard on everything under `/Api`. Header `Authentication: <password>` (or `?password=`).
- **Device token** — issued by this plugin, required by everything under `/SteamASF2FA/api`.
  Header `X-Auth-Token: <token>` (or `Authorization: Bearer <token>`).

## Pairing

### `POST /Api/SteamASF2FA/Pair` — IPCPassword

```json
{ "name": "My Phone" }
```

```json
{ "id": "0123456789abcdef0123456789abcdef", "name": "My Phone", "token": "<opaque device token>" }
```

The `token` is returned **once**. Only its SHA-256 hash is persisted, in `config/SteamASF2FA/devices.json`.

### `GET /Api/SteamASF2FA/Devices` — IPCPassword

```json
[ { "Id": "0123456789abcdef0123456789abcdef", "Name": "My Phone", "CreatedAt": "2026-01-01T00:00:00Z", "LastUsedAt": null } ]
```

### `DELETE /Api/SteamASF2FA/Devices/{id}` — IPCPassword

```json
{ "revoked": "0123456789abcdef0123456789abcdef" }
```

## Bots

### `GET /SteamASF2FA/api/bots`

```json
[ { "Name": "MyBot", "SteamID": 76561198000000000, "HasMobileAuthenticator": true, "Online": true } ]
```

## QR login

### `POST /SteamASF2FA/api/qr/info`

```json
{ "bot": "MyBot", "qrChallengeUrl": "https://s.team/q/1/1234567890123456789" }
```

```json
{
  "Success": true,
  "Error": null,
  "Ip": "203.0.113.10",
  "DeviceFriendlyName": "DESKTOP-XXXXXXX",
  "Info": {
    "Version": 1,
    "ClientId": "1234567890123456789",
    "City": "City",
    "State": "State",
    "Country": "IT",
    "PlatformType": 1,
    "LocationMismatch": false,
    "HighUsageLogin": false,
    "RequestedPersistence": 1
  }
}
```

`PlatformType`: `1` Steam client, `2` web browser, `3` mobile app. `ClientId` is a string because it exceeds `Int64`.

### `POST /SteamASF2FA/api/qr/approve`

```json
{ "bot": "MyBot", "qrChallengeUrl": "https://s.team/q/1/1234567890123456789", "approve": true }
```

```json
{ "Success": true, "Error": null, "Result": 1 }
```

`Result` is the Steam `EResult` (`1` = OK).

## Confirmations

### `GET /SteamASF2FA/api/confirmations?bots=MyBot`

`bots` is a comma-separated list; omit it for every bot.

```json
{
  "MyBot": {
    "success": true,
    "message": "OK",
    "confirmations": [
      { "Id": "123456", "CreatorId": "76561198000000001", "Type": 2, "TypeName": "Trade" }
    ]
  }
}
```

### `POST /SteamASF2FA/api/confirmations`

```json
{
  "bots": ["MyBot"],
  "accept": true,
  "acceptedCreatorIDs": [76561198000000001],
  "acceptedType": null,
  "waitIfNeeded": false
}
```

`acceptedCreatorIDs` selects specific confirmations (the trade-offer or market-listing ID); omit it to act on all.
`acceptedType` filters by `EMobileConfirmationType`. Response mirrors the GET, with `handled` instead of
`confirmations`.

## Codes

### `GET /SteamASF2FA/api/code?bots=MyBot`

```json
{ "MyBot": { "success": true, "message": "OK", "code": "ABC12" } }
```

## Trade offer contents

A trade confirmation only carries the **trade offer ID** (as its `creator_id`). Use it to fetch the actual items:

### `GET /SteamASF2FA/api/tradeoffer/{tradeOfferId}?bot=MyBot`

```json
{
  "TradeOfferID": "1234567890",
  "OtherSteamID64": "76561198000000001",
  "State": 9,
  "ItemsToGive": [ { "AppID": 730, "Amount": 1, "Name": "Item name", "IconURL": "https://community.cloudflare.steamstatic.com/economy/image/…" } ],
  "ItemsToReceive": []
}
```

`404` when the offer is gone (already accepted/declined or expired). Backed by `IEconService/GetTradeOffers` with
`get_descriptions`, so names and icon URLs come straight from Steam.
