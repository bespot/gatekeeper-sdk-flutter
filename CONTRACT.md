# Platform channel contract

This document describes how the Dart side of the plugin talks to the iOS bridge
(`GatekeeperSdkFlutterPlugin.swift`). The bridge must follow it exactly.

App developers do not need this document; they only use the `SafeSDK` class.

The Dart side of the contract lives in `lib/gatekeeper_sdk_flutter_method_channel.dart` and
`lib/src/models.dart`, and is covered by `test/gatekeeper_sdk_flutter_method_channel_test.dart`.
If you change anything here, update those files and tests too.

## Channels

| Channel | Type | Used for |
|---|---|---|
| `com.bespot/gatekeeper_sdk_flutter` | `MethodChannel` | All calls that return one result |
| `com.bespot/gatekeeper_sdk_flutter/subscribe` | `EventChannel` | The results of `subscribe` (many results over time) |

## Methods (`MethodChannel`)

Method names and argument keys are case-sensitive. Arguments arrive as a map (`null` when a method has none).

| Method | Arguments | Success result |
|---|---|---|
| `initialize` | `{"params": {"debugLoggingEnabled": Bool}}` | `null` |
| `initializeWithAccessToken` | `{"accessToken": String, "params": {"debugLoggingEnabled": Bool}}` | `null` |
| `setAccessToken` | `{"accessToken": String}` | `null` |
| `setUserId` | `{"userId": String}` | `null` |
| `enableLogging` | `{"debugLoggingEnabled": Bool}` | `null` |
| `check` | none | Action map (see below) |
| `unsubscribe` | none | `null` |
| `askForLocationPermissions` | none | `null` |
| `askForStoragePermissions` | none | `null` |
| `askForMediaAudioPermissions` | none | `null` |

Rules:
- Reply **exactly once** to every call, either with success or with an error.
- Reply on the **main thread** (main queue).
- `params.debugLoggingEnabled` enables SDK logging before the SDK is initialized.
- Credentials are **never** sent from Dart. The bridge reads them from the host app's `Info.plist`:

| Value | `Info.plist` key |
|---|---|
| API base URL | `API_BASE_URL` |
| API key | `API_KEY` |
| OAuth2 token URL | `AUTH_TOKEN_URL` |
| Client ID | `CLIENT_ID` |
| Client secret | `CLIENT_SECRET` |

  `initialize` uses the OAuth2 values. `initializeWithAccessToken` uses the API base URL and the API key.
- A missing argument or a missing credential is an `INVALID_ARGUMENTS` error. The message names what is missing.

## Action map

`check` returns, and the `EventChannel` emits, a map:

```json
{ "type": "LIMIT_ACCESS", "signature": "<server-verifiable signature>" }
```

| SDK action | `type` |
|---|---|
| block | `BLOCK` |
| limit access | `LIMIT_ACCESS` |
| monitor | `MONITOR` |
| not safe | `NOT_SAFE` |
| safe | `SAFE` |
| anything else | `DEFAULT_UNKNOWN_ACTION` |

The map is sent directly. It is not wrapped in an `action` key, unlike the Capacitor plugin.

## Errors

Errors are sent as `FlutterError(code:message:details:)`.

| SDK failure | `code` |
|---|---|
| network connection | `NETWORK_CONNECTION` |
| no active API key | `NO_ACTIVE_API_KEY` |
| quota reached for plan | `QUOTA_REACHED_FOR_PLAN` |
| no checks available | `NO_CHECKS_AVAILABLE` |
| no recipe found | `NO_RECIPE_FOUND` |
| not initialized | `NOT_INITIALIZED` |
| server error | `SERVER_ERROR` |
| invalid token | `INVALID_TOKEN` |
| auth error | `AUTH_ERROR` |
| already initialized | `ALREADY_INITIALIZED` |
| unknown error | `UNKNOWN_ERROR` |
| missing argument or credential (bridge only) | `INVALID_ARGUMENTS` |
| method not supported on this platform (bridge only) | `UNIMPLEMENTED` |
| anything else | `DEFAULT_UNKNOWN_ERROR` |

`message` is a human-readable description. `details` is optional and may be `null`.

## Platform-specific methods

Like the Capacitor plugin, iOS does not support these methods. The bridge replies with an
`UNIMPLEMENTED` error and does nothing else:

| Method | `message` |
|---|---|
| `enableLogging` | `Not implemented on iOS. Use the debugLoggingEnabled parameter of initialize to enable debug logging.` |
| `askForStoragePermissions` | `Not implemented on iOS.` |
| `askForMediaAudioPermissions` | `Not implemented on iOS.` |

## Unknown values

When the SDK adds a new action or failure that the bridge does not know yet, the bridge must still send something:
map it in `@unknown default` to `DEFAULT_UNKNOWN_ACTION` / `DEFAULT_UNKNOWN_ERROR`.

Dart maps any unknown `type` to `ActionType.unknown` and any unknown `code` to `SafeSDKErrorCode.unknownError`.

## Subscribe (`EventChannel`)

The bridge registers a `FlutterStreamHandler` on the `EventChannel`:
- `onListen(withArguments:eventSink:)` → the bridge calls the SDK's `subscribe`.
- `onCancel(withArguments:)` → the bridge calls the SDK's `unsubscribe`.
- Each result is sent as `eventSink(<action map>)`.
- Each failure is sent as `eventSink(FlutterError(code:message:details:))`. The stream stays open; the Dart side decides whether to cancel.
- Send events on the main thread.

Dart shares a single native subscription between all listeners. `onCancel` arrives when the last listener cancels.
