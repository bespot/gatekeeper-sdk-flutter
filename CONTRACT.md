# Platform channel contract

This document describes how the Dart side of the plugin talks to the native bridges:
`GatekeeperSdkFlutterPlugin.swift` on iOS and `GatekeeperSdkFlutterPlugin.kt` on Android.
The bridges must follow it exactly. Where the platforms differ, the section says so.

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
| `askForLocationPermissions` | none | `null` |
| `askForStoragePermissions` | none | `null` |
| `askForMediaAudioPermissions` | none | `null` |

Rules:
- Reply **exactly once** to every call, either with success or with an error.
- Reply on the **main thread** (main queue).
- `params.debugLoggingEnabled` enables SDK logging before the SDK is initialized.
  Android ignores it; apps use `enableLogging` there.
- Credentials are **never** sent from Dart. The bridges read them from the host app:

| Value | iOS `Info.plist` key | Android string resource |
|---|---|---|
| API base URL | `API_BASE_URL` | `antifraud_sdk_api_url` |
| API key | `API_KEY` | `antifraud_sdk_key` |
| OAuth2 token URL | `AUTH_TOKEN_URL` | `antifraud_sdk_oauth2_token_url` |
| Client ID | `CLIENT_ID` | `antifraud_sdk_client_id` |
| Client secret | `CLIENT_SECRET` | `antifraud_sdk_client_secret` |

  On iOS, `initialize` uses the OAuth2 values and `initializeWithAccessToken` uses the API base URL and the API key.
  On Android, the SDK reads the OAuth2 values itself when the app starts (see [Platform-specific methods](#platform-specific-methods)),
  and the bridge reads only the API base URL and the API key, for `initializeWithAccessToken`.
- A missing argument or a missing credential is an `INVALID_ARGUMENTS` error. The message names what is missing,
  with one exception: on Android, a missing or empty credential in `initializeWithAccessToken` has the message
  `Missing required initialize parameters`.
- On Android, an empty `accessToken` in `initializeWithAccessToken` is also an `INVALID_ARGUMENTS` error.
  iOS passes it to the SDK. `setAccessToken` passes an empty token to the SDK on both platforms.

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
| anything else | `DEFAULT_UNKNOWN_ACTION` on iOS; see [Unknown values](#unknown-values) for Android |

The map is sent directly. It is not wrapped in an `action` key.

## Errors

Errors are sent as `FlutterError(code:message:details:)` on iOS and `Result.error(code, message, details)` on Android.

| SDK failure | `code` | `message` |
|---|---|---|
| network connection | `NETWORK_CONNECTION` | `Connection Error` |
| no active API key | `NO_ACTIVE_API_KEY` | `The API Key is either disabled or wrong` |
| quota reached for plan | `QUOTA_REACHED_FOR_PLAN` | `The quota for your plan has been reached` |
| no checks available | `NO_CHECKS_AVAILABLE` | `The server did not find available checks` |
| no recipe found | `NO_RECIPE_FOUND` | `The application does not have a valid recipe` |
| not initialized | `NOT_INITIALIZED` | `The SDK is not initialized` |
| server error | `SERVER_ERROR` | `Remote Server Error` |
| invalid token | `INVALID_TOKEN` | `The access token is invalid or expired` |
| auth error | `AUTH_ERROR` | `Authentication failed` |
| already initialized | `ALREADY_INITIALIZED` | `The SDK is already initialized` |
| unknown error | `UNKNOWN_ERROR` | `Unknown Error (see support section)` |
| missing argument or credential (bridge only) | `INVALID_ARGUMENTS` | Names what is missing |
| method not supported on this platform (bridge only) | `UNIMPLEMENTED` | See [Platform-specific methods](#platform-specific-methods) |
| permission not granted (Android bridge only) | `PERMISSION_DENIED` | See [Permissions](#permissions) |
| no Activity to show a permission dialog (Android bridge only) | `NO_ACTIVITY` | `No Activity to show the permission dialog` |
| anything else | `DEFAULT_UNKNOWN_ERROR` | `Unknown Error (see support section)` |

`message` is the text in the table above and must be the same on every platform.
Apps must use `code`, not `message`, to decide what to do. `details` is optional and may be `null`.
Android always sends `null`.

## Platform-specific methods

iOS does not support these methods. The bridge replies with an
`UNIMPLEMENTED` error and does nothing else:

| Method | `message` |
|---|---|
| `enableLogging` | `Not implemented on iOS. Use the debugLoggingEnabled parameter of initialize to enable debug logging.` |
| `askForStoragePermissions` | `Not implemented on iOS.` |
| `askForMediaAudioPermissions` | `Not implemented on iOS.` |

Android does not support this method. The bridge replies with an `UNIMPLEMENTED` error and does nothing else:

| Method | `message` |
|---|---|
| `initialize` | `Not implemented on Android. Initialization is done at application launch.` |

On Android, the SDK initializes itself in OAuth2 mode when the app starts, if any of the OAuth2 string resources is set.
When all three are blank, apps use `initializeWithAccessToken`.

## Permissions

On iOS, `askForLocationPermissions` asks for "When In Use" location permission and replies `null` right away,
without waiting for the user's answer.

On Android, the `askFor*` methods wait for the user's answer:
- They reply `null` only if every permission of the group is granted, and `PERMISSION_DENIED` otherwise.
- They ask for the same permissions on every Android version:

| Method | Permissions | `PERMISSION_DENIED` message |
|---|---|---|
| `askForLocationPermissions` | `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | `Location permission denied` |
| `askForStoragePermissions` | `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` | `Storage permission denied` |
| `askForMediaAudioPermissions` | `READ_MEDIA_AUDIO` | `Media-audio permission denied` |

- Approximate location only counts as denied.
- Storage is always denied on Android 10 and later, and media audio below Android 13.
- If all permissions of the group are already granted, they reply `null` without a dialog.
- A second call for the same group while its dialog is open waits and gets the same answer.
- Without an Activity, they reply `NO_ACTIVITY`. Calls that are waiting also get `NO_ACTIVITY` when the Activity is destroyed.
  A configuration change, such as a rotation, does not cancel them.

## Unknown values

When the SDK adds a new action or failure that the bridge does not know yet, the bridge must still send something:
- iOS maps it in `@unknown default` to `DEFAULT_UNKNOWN_ACTION` / `DEFAULT_UNKNOWN_ERROR`.
- Android maps an unknown failure to `DEFAULT_UNKNOWN_ERROR`. It sends the `type` of an action as the name the SDK gives it,
  so an unknown action arrives under its own name, not as `DEFAULT_UNKNOWN_ACTION`.

Dart maps any unknown `type` to `ActionType.unknown` and any unknown `code` to `SafeSDKErrorCode.unknownError`.

## Subscribe (`EventChannel`)

The bridge registers a stream handler on the `EventChannel`:
- `onListen` → the bridge calls the SDK's `subscribe`.
- `onCancel` → the bridge calls the SDK's `unsubscribe`.
- Each result is sent to the event sink as an action map.
- Each failure is sent to the event sink as an error. The stream stays open; the Dart side decides whether to cancel.
- Send events on the main thread.

Dart shares a single native subscription between all listeners. `onCancel` arrives when the last listener cancels.

There is no `unsubscribe` method on the `MethodChannel`. Apps stop receiving results by cancelling the stream subscription.

### Android

The Android SDK keeps a single observer for `check` and `subscribe`, so the bridge uses one observer for both:
- Every SDK result answers all `check` calls that are waiting, and is also sent to the stream if Dart is listening.
  Stream listeners therefore also receive the results of `check`.
- `onCancel` calls the SDK's `unsubscribe` only after the waiting `check` calls are answered.
  The SDK applies `unsubscribe` and `check` without a fixed order, so a `check` called right after the stream is cancelled may not be answered.

## SDK results outside the method call (Android)

On Android, some results do not reach the method call that caused them. The method replies `null`,
and the SDK sends the result to its observer, so it reaches the stream (and any waiting `check`) instead:
- **Device registration.** `initializeWithAccessToken`, and the automatic OAuth2 initialization at app launch, return before
  the device is registered. A registration failure (for example `INVALID_TOKEN`, `NO_ACTIVE_API_KEY`, `AUTH_ERROR`, or no network)
  is sent to the stream. After it, `check` replies `NOT_INITIALIZED` until the app restarts; the SDK does not retry.
  The first checks after a successful registration are also sent to the stream.
- **`ALREADY_INITIALIZED`.** `initializeWithAccessToken` after the SDK is initialized.
- **`INVALID_TOKEN` from `setAccessToken`.** The SDK rejects an invalid token, such as an empty one, and keeps the previous one.

The SDK also runs checks on its own (periodically and when the app returns to the foreground) and sends the results to the stream.

`check` can stay unanswered when the SDK has no checks to run. The bridge has no timeout; apps add one with `.timeout()`.
