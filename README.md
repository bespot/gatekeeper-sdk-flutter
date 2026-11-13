# gatekeeper-sdk-flutter
Flutter plugin for Gatekeeper SDK

## iOS

### Requirements

* iOS 15.0 or later
* Swift Package Manager (CocoaPods is not supported)
* Built and tested using Xcode 26 and Flutter 3.47

### Installation

The plugin is not published on pub.dev. Add it to your app's `pubspec.yaml` as a Git dependency:

```yaml
dependencies:
  gatekeeper_sdk_flutter:
    git:
      url: https://github.com/bespot/gatekeeper-sdk-flutter
      ref: the_release_tag
```

Then run:

```bash
flutter pub get
```

The plugin uses Swift Package Manager for iOS. When you build the app, Flutter adds the plugin
and its native dependency, the [GatekeeperSDK](https://github.com/bespot/gatekeeper-sdk-ios-release) package,
to your Xcode project. You do not need to add anything in Xcode yourself.

If your Flutter version does not enable Swift Package Manager by default, enable it first:

```bash
flutter config --enable-swift-package-manager
```

#### iOS deployment target

Your app's iOS deployment target must be at least `15.0`. Swift Package Manager fails to build if it is lower.
To check or raise it: open `ios/Runner.xcworkspace` in Xcode, select the **Runner** project in the navigator,
then **Build Settings**, search for `iOS Deployment Target` and set it to at least `15.0`
for both the **PROJECT** and the **Runner** target.

### Permissions

Some checks use the device location. Add the following to `ios/Runner/Info.plist`:

```xml
<key>NSLocationWhenInUseUsageDescription</key>
<string>Your location is required for fraud-prevention analysis.</string>
```

Then ask the user for permission at a suitable point in your app:

```dart
await SafeSDK.askForLocationPermissions();
```

iOS shows the permission dialog only the first time. After the user answers, later calls do nothing,
and the user can change the permission only from the Settings app. The call returns right away
and does not wait for the user's answer.

Without `NSLocationWhenInUseUsageDescription`, iOS does not show the dialog at all.
Until the user grants the permission, the location checks are reported to the server as not available.

### Credentials

The plugin never receives credentials from Dart. On iOS it reads them from your app's `Info.plist`.
To keep them out of your source code, store them in an `.xcconfig` file that is not committed,
and let Xcode copy them into `Info.plist` at build time.

> [!WARNING]
> This keeps the credentials out of Git, not out of your app. `Info.plist` is part of the app you ship,
> and anyone who downloads the app can read it, including `CLIENT_ID` and `CLIENT_SECRET`.
> With these values, anyone can get access tokens as your app.
> For production apps, use `initializeWithAccessToken()`: your backend keeps the client secret
> and gives the app short-lived access tokens. Use `initialize()` only if you accept this risk,
> for example during development and testing.

Which keys you need depends on how you initialize the SDK:

| Key | `initialize()` | `initializeWithAccessToken()` |
|---|---|---|
| `API_BASE_URL` | Required | Required |
| `API_KEY` | Required | Required |
| `AUTH_TOKEN_URL` | Required | Not used |
| `CLIENT_ID` | Required | Not used |
| `CLIENT_SECRET` | Required | Not used |

If a required key is missing or empty, the initialize call throws a `SafeSDKException`
with `SafeSDKErrorCode.invalidArguments`. Its message names the missing keys.

#### Step 1: Create Secrets.xcconfig

Create `ios/Flutter/Secrets.xcconfig` with the values provided by Bespot:

```
API_BASE_URL = https:/$()/the_provided_API_BASE_URL
API_KEY = the_provided_API_KEY
AUTH_TOKEN_URL = https:/$()/the_provided_AUTH_TOKEN_URL
CLIENT_ID = the_provided_CLIENT_ID
CLIENT_SECRET = the_provided_CLIENT_SECRET
```

**Important:** In `.xcconfig` files, `//` starts a comment, so `https://example.com` would be cut to `https:`.
Write URLs as `https:/$()/example.com`. The empty `$()` keeps the two slashes apart, and Xcode removes it at build time.

Create the file with a text editor, not from Xcode, and do not add it to the Xcode project.
Xcode reads it through the `#include` in the next step.

Add it to your `ios/.gitignore` so the credentials are never committed:

```
Flutter/Secrets.xcconfig
```

#### Step 2: Include it in the build configurations

Add the following line at the end of both `ios/Flutter/Debug.xcconfig` and `ios/Flutter/Release.xcconfig`:

```
#include? "Secrets.xcconfig"
```

For example, `ios/Flutter/Debug.xcconfig` becomes:

```
#include "Generated.xcconfig"
#include? "Secrets.xcconfig"
```

The `?` makes the include optional, so the app still builds on machines without the file,
for example in CI. In that case the values are empty and initialize throws `invalidArguments`.

Flutter projects already use these files for the Debug and Release build configurations, so there is nothing to link in Xcode.

#### Step 3: Add the keys to Info.plist

Add the following keys to `ios/Runner/Info.plist`. Xcode replaces each `$(...)` with the value from `Secrets.xcconfig`:

```xml
<key>API_BASE_URL</key>
<string>$(API_BASE_URL)</string>
<key>API_KEY</key>
<string>$(API_KEY)</string>
<key>AUTH_TOKEN_URL</key>
<string>$(AUTH_TOKEN_URL)</string>
<key>CLIENT_ID</key>
<string>$(CLIENT_ID)</string>
<key>CLIENT_SECRET</key>
<string>$(CLIENT_SECRET)</string>
```

If you only use `initializeWithAccessToken()`, you can add only `API_BASE_URL` and `API_KEY`.

#### Step 4: Verify

Build the app, then check the values in the built `Info.plist`:

```bash
flutter build ios --simulator --debug
plutil -p build/ios/iphonesimulator/Runner.app/Info.plist | grep -E "API_BASE_URL|AUTH_TOKEN_URL"
```

Both URLs must start with `https://`. If they show only `https:`, the `$()` from Step 1 is missing.

### Usage

Import the plugin. The examples below also use `kDebugMode` and `debugPrint` from Flutter's foundation library:

```dart
import 'package:flutter/foundation.dart';
import 'package:gatekeeper_sdk_flutter/gatekeeper_sdk_flutter.dart';
```

All methods are static methods of `SafeSDK`. On failure they throw a `SafeSDKException`.
See [Error handling](#error-handling).

#### Initialize

> [!IMPORTANT]
> Initialize the SDK only once per app launch, as early as possible after your app starts, for example in `main()`.
> Choose one of the two modes below, and do not call `initialize` or `initializeWithAccessToken` again.

**OAuth2.** The SDK gets and refreshes its access token by itself, using the credentials from `Info.plist`.
Not recommended for production apps, because the client secret ships inside the app.
See [Credentials](#credentials):

```dart
await SafeSDK.initialize(debugLoggingEnabled: kDebugMode);
```

**Access token.** Your app gets the access token from your own backend and passes it to the SDK:

```dart
await SafeSDK.initializeWithAccessToken(accessToken, debugLoggingEnabled: kDebugMode);
```

`kDebugMode` is `true` only in debug builds, so the SDK logs are off in release builds.
See [Debug logs](#debug-logs).

When the token is about to expire, give the SDK the new one. Do not call `initializeWithAccessToken` again:

```dart
await SafeSDK.setAccessToken(newAccessToken);
```

An empty token is ignored: `setAccessToken` does not throw, and the SDK keeps the previous token.

To initialize in `main()`, before `runApp()`, call `WidgetsFlutterBinding.ensureInitialized()` first.
Plugins cannot be called before Flutter is ready:

```dart
import 'package:flutter/material.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await SafeSDK.initialize(debugLoggingEnabled: kDebugMode);
  runApp(const MyApp());
}
```

Initialize returns as soon as the SDK has started. The SDK then registers the device and runs
the first checks in the background. If the registration fails, for example because of an invalid or empty access token,
a wrong API key or wrong OAuth2 credentials, the error is written only to the SDK debug logs.
`check` then throws a `SafeSDKException` with `SafeSDKErrorCode.notInitialized` until the app
is restarted. Calling `setAccessToken` does not fix this.

#### Set the user

Optionally, set the identifier of the signed-in user, so the server can link the results to that user:

```dart
await SafeSDK.setUserId('user-123');
```

#### Check on demand

Call `check` when the user is about to do something sensitive:

```dart
final action = await SafeSDK.check();
```

`action.type` is one of the following `ActionType` values:

```dart
enum ActionType {
  block,
  limitAccess,
  monitor,
  notSafe,
  safe,
  unknown,
}
```

Which action is returned, and what it means for your app, is defined by your app's recipe on the server.

`unknown` is returned when the server sends an action that this version of the plugin does not know.
Treat it as your app's default.

`action.signature` is signed by Gatekeeper. Send it to your backend and verify it there before you trust the action:
code that runs on the device can be changed.

#### Subscribe to periodic checks

After initialize, the SDK repeats the checks in the background. The interval is set by the server,
15 minutes by default. Checks run only while your app is active.
To receive the results, listen to `subscribe`:

```dart
final subscription = SafeSDK.subscribe().listen(
  (action) {
    // Handle action.type and action.signature, as with check.
  },
  onError: (Object error) {
    // A SafeSDKException. The stream stays open, so you can keep listening or cancel it.
  },
);
```

`subscribe` does not run a check by itself, so the first result arrives with the next periodic check.

To stop receiving results, cancel the subscription:

```dart
await subscription.cancel();
```

#### Error handling

Every failure is a `SafeSDKException`. Use its `code` to decide what to do. `message` is a description
for logs, and may change between versions.

```dart
try {
  final action = await SafeSDK.check();
  // Handle the action.
} on SafeSDKException catch (e) {
  switch (e.code) {
    case SafeSDKErrorCode.notInitialized:
      // Call initialize first.
      break;
    case SafeSDKErrorCode.networkConnection:
      // No connection. Try again later.
      break;
    case SafeSDKErrorCode.invalidToken:
      // Get a new token from your backend and call setAccessToken.
      break;
    default:
      debugPrint('Gatekeeper check failed: $e');
  }
}
```

| `SafeSDKErrorCode` | When |
|---|---|
| `networkConnection` | The device has no network connection. |
| `noActiveApiKey` | The API key is disabled or wrong. |
| `quotaReachedForPlan` | The quota for your plan has been reached. |
| `noChecksAvailable` | The server has no checks for this app. |
| `noRecipeFound` | The app has no valid recipe on the server. |
| `notInitialized` | A method was called before initialize, or the device registration after initialize failed. In the second case, restart the app. |
| `serverError` | The server returned an error. |
| `invalidToken` | The access token is invalid or expired. Get a new token and call `setAccessToken`. |
| `authError` | The SDK could not get an OAuth2 access token, for example because of wrong credentials or a server error. |
| `alreadyInitialized` | Initialize was called more than once. |
| `invalidArguments` | A credential is missing from `Info.plist`, or a required argument is missing. |
| `unimplemented` | The method is not supported on iOS. See [Platform differences](#platform-differences). |
| `unknownError` | Any other error. |

If one of these errors happens during the device registration after initialize,
for example `invalidToken`, `authError` or `noActiveApiKey`, it is not reported to the app.
`check` throws `notInitialized` instead. See [Initialize](#initialize).

#### Debug logs

On iOS, the only way to enable the SDK logs is the `debugLoggingEnabled` parameter of
`initialize` or `initializeWithAccessToken`. It applies from initialize on and cannot be changed later.
Use `kDebugMode` so the logs are enabled only in debug builds:

```dart
import 'package:flutter/foundation.dart';

await SafeSDK.initialize(debugLoggingEnabled: kDebugMode);
```

**Important:** The debug logs include the API key, the client secret and the access token in plain text.
Never enable them in production builds, and remove these values before you share a log.

## Android

### Requirements

* Android 7.0 (API level 24) or later
* Built and tested using Android Gradle Plugin 9.1, Kotlin 2.4 and Flutter 3.47

### Installation

Add the plugin to your app's `pubspec.yaml` as a Git dependency and run `flutter pub get`,
as described in the iOS [Installation](#installation).

The plugin depends on the GatekeeperSDK for Android, which is published in Bespot's Maven repositories.
Add them to `android/build.gradle.kts` of your app:

```kotlin
allprojects {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://artifactory.bespot.com/artifactory/bespot-antifraud")
        maven(url = "https://artifactory.bespot.com/artifactory/bespot-logger")
    }
}
```

The plugin declares these repositories too, but Gradle uses them only for the plugin itself.
Your app needs them to resolve the SDK.

If your app declares its repositories in `settings.gradle.kts`, in a `dependencyResolutionManagement` block,
add the two `maven` lines there instead.

#### Android minSdk

Your app's `minSdk` must be at least `24`. If it is lower, the build fails with a manifest merger error.
To check or raise it, open `android/app/build.gradle.kts` and look at `minSdk` in `defaultConfig`.

### Credentials

The plugin never receives credentials from Dart. On Android the SDK reads them itself, when the app starts,
from string resources of your app. To keep them out of your source code, store them in
`android/local.properties`, which is not committed, and let Gradle turn them into resources at build time.

> [!WARNING]
> This keeps the credentials out of Git, not out of your app. String resources are part of the APK you ship,
> and anyone who downloads the app can read them, including the client ID and the client secret.
> With these values, anyone can get access tokens as your app.
> For production apps, use `initializeWithAccessToken()`: your backend keeps the client secret
> and gives the app short-lived access tokens. Use OAuth2 only if you accept this risk,
> for example during development and testing.

The SDK picks the mode when the app starts, from the three OAuth2 resources:

| Resource | OAuth2 | Access token |
|---|---|---|
| `antifraud_sdk_api_url` | Required | Required |
| `antifraud_sdk_key` | Required | Required |
| `antifraud_sdk_oauth2_token_url` | Required | Empty or missing |
| `antifraud_sdk_client_id` | Required | Empty or missing |
| `antifraud_sdk_client_secret` | Required | Empty or missing |

If any of the three OAuth2 resources has a value, the SDK starts in OAuth2 mode and registers the device
as soon as the app starts. If all three are empty or missing, it waits for `initializeWithAccessToken()`.

If `antifraud_sdk_api_url` or `antifraud_sdk_key` is missing or empty, `initializeWithAccessToken()` throws
a `SafeSDKException` with `SafeSDKErrorCode.invalidArguments`.

#### Step 1: Add the values to local.properties

Add the values provided by Bespot to `android/local.properties`:

```properties
API_URL=https://the_provided_API_URL
API_KEY=the_provided_API_KEY
OAUTH2_TOKEN_URL=https://the_provided_OAUTH2_TOKEN_URL
CLIENT_ID=the_provided_CLIENT_ID
CLIENT_SECRET=the_provided_CLIENT_SECRET
```

For the access token mode, add only `API_URL` and `API_KEY`.

Flutter projects already ignore `android/local.properties` in Git. Check that `android/.gitignore` contains:

```
/local.properties
```

#### Step 2: Turn the values into resources

In `android/app/build.gradle.kts`, read the file and add one `resValue` for each value:

```kotlin
import java.util.Properties

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun secret(key: String) = "\"${localProperties.getProperty(key, "")}\""

android {
    buildFeatures {
        // Android Gradle Plugin 9 disables resValue by default. The SDK credentials below need it.
        resValues = true
    }

    defaultConfig {
        resValue("string", "antifraud_sdk_api_url", secret("API_URL"))
        resValue("string", "antifraud_sdk_key", secret("API_KEY"))
        resValue("string", "antifraud_sdk_oauth2_token_url", secret("OAUTH2_TOKEN_URL"))
        resValue("string", "antifraud_sdk_client_id", secret("CLIENT_ID"))
        resValue("string", "antifraud_sdk_client_secret", secret("CLIENT_SECRET"))
    }
}
```

Without `resValues = true`, Android Gradle Plugin 9 fails with
`defaultConfig contains custom resource values, but the feature is disabled.`

A missing key becomes an empty resource, so the app still builds on machines without the values,
for example in CI. With all three OAuth2 values empty, the SDK waits for `initializeWithAccessToken()`.

#### Step 3: Verify

Build the app:

```bash
flutter build apk --debug
```

In Android Studio, open **Build > Analyze APK** and select `build/app/outputs/flutter-apk/app-debug.apk`.
Select `resources.arsc`, then `string` under **Resource types**, and type `antifraud_sdk` in the filter field.
The five `antifraud_sdk_*` values must be there, and both URLs must start with `https://`.

### Permissions

Some checks use the device location. The SDK does not declare the location permission,
so add it to `android/app/src/main/AndroidManifest.xml`, before `<application>`:

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

Without these lines, Android does not show the dialog and the request fails right away.

Then ask the user for permission at a suitable point in your app:

```dart
try {
  await SafeSDK.askForLocationPermissions();
} on SafeSDKException catch (e) {
  // e.code is SafeSDKErrorCode.permissionDenied or SafeSDKErrorCode.noActivity.
}
```

The call waits for the user's answer. It returns when the user grants all the requested permissions,
and throws a `SafeSDKException` otherwise:

| `code` | When |
|---|---|
| `permissionDenied` | The user denied the permission. Choosing **Approximate** instead of **Precise** location also counts as denied. |
| `noActivity` | The app has no visible screen to show the dialog on, for example when the call runs in the background. |

After the user denies a permission twice, Android stops showing the dialog,
and the call throws `permissionDenied` right away. The user can then change the permission only from the system settings.

#### Storage and media-audio permissions

The SDK uses these permissions to keep the same device ID after the app is uninstalled and installed again.
It already declares them in its own manifest, so you do not need to add them.

```dart
await SafeSDK.askForStoragePermissions();
await SafeSDK.askForMediaAudioPermissions();
```

Both calls ask for the same permissions on every Android version, so on some versions they always fail:

| Method | Always throws `permissionDenied` on |
|---|---|
| `askForStoragePermissions` | Android 10 (API level 29) and later |
| `askForMediaAudioPermissions` | Android 12L (API level 32) and earlier |

In these cases Android usually does not show a dialog.

### Usage

The Dart API is the same as on iOS. See the iOS [Usage](#usage).
This section describes only what is different on Android.

#### Initialize

**OAuth2.** The SDK starts by itself when the app starts, using the credentials from the string resources.
See [Credentials](#credentials-1). Do not call `initialize()` on Android: it throws a `SafeSDKException`
with `SafeSDKErrorCode.unimplemented`. If your app runs on both platforms, call it only on iOS:

```dart
import 'dart:io';

if (Platform.isIOS) {
  await SafeSDK.initialize(debugLoggingEnabled: kDebugMode);
}
```

**Access token.** Call `initializeWithAccessToken` once per app launch, as on iOS:

```dart
await SafeSDK.initializeWithAccessToken(accessToken);
```

On Android:

* `debugLoggingEnabled` is ignored. Use `enableLogging` instead. See [Debug logs](#debug-logs-1).
* An empty `accessToken` throws a `SafeSDKException` with `SafeSDKErrorCode.invalidArguments`.

Initialize returns as soon as the SDK has started, and the SDK registers the device in the background.
If the registration fails, for example because the device has no network connection when the app starts,
or because of an invalid access token, a wrong API key or wrong OAuth2 credentials,
the error is sent to the `subscribe` stream, if your app is listening at that moment.
After that, `check` throws `notInitialized` until the app is restarted.
The SDK does not retry the registration when the network connection comes back.

#### Check on demand

`check` can wait without an answer while the SDK has no checks yet, for example in the first seconds
after the app starts, while the device is being registered. Add a timeout:

```dart
final action = await SafeSDK.check().timeout(const Duration(seconds: 10));
```

When the timeout expires, `check` throws a `TimeoutException` from `dart:async`.

#### Subscribe to periodic checks

On Android, the `subscribe` stream also receives:

* the result of every `check` call
* the result of the first checks after the device registration, if your app is already listening
* registration errors, as described in [Initialize](#initialize-1)

#### Debug logs

On Android, enable or disable the SDK logs at any time with `enableLogging`:

```dart
await SafeSDK.enableLogging(kDebugMode);
```

The SDK starts before your Dart code runs, so the logs of its first steps are not included.

**Important:** As on iOS, the debug logs include the credentials and the access token in plain text.
Never enable them in production builds, and remove these values before you share a log.

#### Error handling

Handle errors as on iOS. See the iOS [Error handling](#error-handling).
The following codes behave differently on Android:

| `SafeSDKErrorCode` | On Android |
|---|---|
| `notInitialized` | A method was called before `initializeWithAccessToken` in the access token mode, or the device registration failed. In the second case, restart the app. |
| `alreadyInitialized` | `initializeWithAccessToken` was called again after the device registration succeeded. The call itself does not throw: the error is sent to the `subscribe` stream and to a `check` that is waiting at that moment. |
| `invalidArguments` | `antifraud_sdk_api_url` or `antifraud_sdk_key` is missing or empty, the access token is empty, or a required argument is missing. |
| `unimplemented` | `initialize` was called. See [Platform differences](#platform-differences). |
| `permissionDenied` | Android only. The user denied a permission. See [Permissions](#permissions-1). |
| `noActivity` | Android only. A permission was requested without a visible screen. See [Permissions](#permissions-1). |

Unlike on iOS, errors of the device registration, for example `invalidToken`, `authError` or `noActiveApiKey`,
are sent to the `subscribe` stream if your app is listening at that moment. See [Initialize](#initialize-1).

### Backup rules

The SDK keeps its state in the shared preferences file `com.bespot.shared.settings.xml`.
It should not be restored on another device, but SDK version 1.2.5 does not exclude it from Android backups.
Exclude it in your app's backup rules.

If your app has no backup rules yet, create them and reference them in the `<application>` element
of `android/app/src/main/AndroidManifest.xml`:

```xml
<application
    android:fullBackupContent="@xml/backup_rules"
    android:dataExtractionRules="@xml/data_extraction_rules"
    ...>
```

`android/app/src/main/res/xml/backup_rules.xml`, used up to Android 11:

```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="sharedpref" path="com.bespot.shared.settings.xml" />
</full-backup-content>
```

`android/app/src/main/res/xml/data_extraction_rules.xml`, used from Android 12:

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="sharedpref" path="com.bespot.shared.settings.xml" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="sharedpref" path="com.bespot.shared.settings.xml" />
    </device-transfer>
</data-extraction-rules>
```

If your app already has backup rules, add the `exclude` lines to them.

### WorkManager

The SDK initializes WorkManager by itself when the app starts, with its own configuration.
If your app also uses WorkManager, do not initialize it yourself: `WorkManager.initialize` then fails,
and a custom configuration, for example from `Configuration.Provider`, is not used.

## Platform differences

The Dart API is the same on both platforms, but some methods behave differently:

| Method | iOS | Android |
|---|---|---|
| `initialize` | Initializes the SDK in OAuth2 mode. | Throws `unimplemented`. The SDK starts by itself when the app starts. See [Initialize](#initialize-1). |
| `debugLoggingEnabled` of `initialize` and `initializeWithAccessToken` | Enables the SDK logs. | Ignored. Use `enableLogging`. |
| `enableLogging` | Throws `unimplemented`. Use `debugLoggingEnabled`. See [Debug logs](#debug-logs). | Enables or disables the SDK logs at any time. |
| `askForLocationPermissions` | Returns right away, without waiting for the user's answer. | Waits for the user's answer, and throws `permissionDenied` if the user denies it. |
| `askForStoragePermissions` | Throws `unimplemented`. The permission is used only by Android checks. | Requests the permission. See [Permissions](#permissions-1). |
| `askForMediaAudioPermissions` | Throws `unimplemented`. The permission is used only by Android checks. | Requests the permission. See [Permissions](#permissions-1). |

`unimplemented` is a `SafeSDKException` with `SafeSDKErrorCode.unimplemented`.
If your app runs on both platforms, call these methods only on the platform that supports them:

```dart
import 'dart:io';

if (Platform.isIOS) {
  await SafeSDK.initialize(debugLoggingEnabled: kDebugMode);
}

if (Platform.isAndroid) {
  await SafeSDK.enableLogging(kDebugMode);
  await SafeSDK.askForStoragePermissions();
}
```

## Running the example app

Clone the repository into a folder named `gatekeeper_sdk_flutter`:

```bash
git clone https://github.com/bespot/gatekeeper-sdk-flutter.git gatekeeper_sdk_flutter
```

Xcode takes the identity of the plugin's Swift package from this folder name. With any other name,
for example the default `gatekeeper-sdk-flutter`, resolving the Swift packages of the example app fails with
"identity doesn't match override's identity". Apps that depend on the plugin are not affected.

Then run the example on a device or simulator:

```bash
cd gatekeeper_sdk_flutter/example
flutter run
```

To check, initialize and subscribe, the example app also needs credentials:

* **iOS:** create `example/ios/Flutter/Secrets.xcconfig`, as described in the iOS [Credentials](#credentials).
* **Android:** add the values to `example/android/local.properties`, as described in the Android [Credentials](#credentials-1).
  The example app already turns them into resources and declares the location permission.
