## 1.0.0

First release, for iOS and Android.

* `SafeSDK` API: `initialize`, `initializeWithAccessToken`, `setAccessToken`, `setUserId`, `enableLogging`,
  `check`, `subscribe`, and the permission requests for location, storage and media audio.
* Results as `SafeSDKAction`, and errors as `SafeSDKException` with a `SafeSDKErrorCode`.
* iOS: iOS 15.0 or later, with GatekeeperSDK 1.2.1 or later through Swift Package Manager.
* Android: API level 24 or later, with `com.bespot.antifraud:sdk-android:1.2.5`.
  The plugin includes the R8 and resource keep rules the SDK needs in release builds.
* Built and tested with Flutter 3.47.
