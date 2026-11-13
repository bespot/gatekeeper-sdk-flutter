import 'gatekeeper_sdk_flutter_platform_interface.dart';
import 'src/models.dart';

export 'src/models.dart';

/// Entry point to Bespot GatekeeperSDK.
///
/// Credentials (API key, base URL, OAuth) are read by the native layer from
/// `Info.plist` (iOS) and string resources, for example created with `resValue` (Android).
class SafeSDK {
  SafeSDK._();

  static GatekeeperSdkFlutterPlatform get _platform => GatekeeperSdkFlutterPlatform.instance;

  /// Initializes the SDK in OAuth mode.
  ///
  /// On Android the SDK initializes itself at app launch, and this method throws
  /// [SafeSDKException] with [SafeSDKErrorCode.unimplemented].
  static Future<void> initialize({bool debugLoggingEnabled = false}) =>
      _platform.initialize(debugLoggingEnabled: debugLoggingEnabled);

  /// Initializes the SDK with a bearer token supplied by your app.
  ///
  /// To refresh the token later, use [setAccessToken] instead of calling this again.
  ///
  /// On Android, `debugLoggingEnabled` is ignored (use [enableLogging]), and an empty
  /// [accessToken] throws [SafeSDKException] with [SafeSDKErrorCode.invalidArguments].
  static Future<void> initializeWithAccessToken(String accessToken, {bool debugLoggingEnabled = false}) =>
      _platform.initializeWithAccessToken(accessToken, debugLoggingEnabled: debugLoggingEnabled);

  /// Updates the bearer token without re-registering the device.
  static Future<void> setAccessToken(String accessToken) => _platform.setAccessToken(accessToken);

  /// Sets the identifier of the current user.
  static Future<void> setUserId(String userId) => _platform.setUserId(userId);

  /// Enables or disables debug logs. Do not enable in production builds.
  ///
  /// Android only. On iOS it throws [SafeSDKException] with [SafeSDKErrorCode.unimplemented];
  /// use `debugLoggingEnabled` of [initialize] or [initializeWithAccessToken] instead.
  static Future<void> enableLogging(bool enabled) => _platform.enableLogging(enabled);

  /// Performs a single on-demand check, e.g. when the user performs a sensitive action.
  ///
  /// Throws [SafeSDKException] on failure.
  ///
  /// On Android the result is also emitted on [subscribe], and the call may not complete
  /// while the device is being registered, so add a timeout.
  static Future<SafeSDKAction> check() => _platform.check();

  /// Results of the periodic checks. Call an initialize method first.
  ///
  /// Errors are emitted as [SafeSDKException]. Cancelling the subscription
  /// stops delivering results. The periodic checks keep running in the SDK.
  ///
  /// On Android the stream also emits the results of [check] and the device registration errors.
  static Stream<SafeSDKAction> subscribe() => _platform.subscribe();

  /// Requests location permission (iOS & Android).
  ///
  /// On iOS it requests "When In Use" permission and returns without waiting for the user's answer.
  /// On Android it waits for the answer and throws [SafeSDKException] with
  /// [SafeSDKErrorCode.permissionDenied] if the user denies it or grants only approximate
  /// location, or [SafeSDKErrorCode.noActivity] if there is no Activity to show the dialog.
  static Future<void> askForLocationPermissions() => _platform.askForLocationPermissions();

  /// Requests the storage permission used for the device seed (Android only).
  ///
  /// Throws [SafeSDKErrorCode.permissionDenied] or [SafeSDKErrorCode.noActivity] as
  /// [askForLocationPermissions] does, and always throws `permissionDenied` on Android 10+.
  /// On iOS it throws [SafeSDKException] with [SafeSDKErrorCode.unimplemented].
  static Future<void> askForStoragePermissions() => _platform.askForStoragePermissions();

  /// Requests the media-audio permission used for the device seed on Android 13+ (Android only).
  ///
  /// Throws [SafeSDKErrorCode.permissionDenied] or [SafeSDKErrorCode.noActivity] as
  /// [askForLocationPermissions] does, and always throws `permissionDenied` below Android 13.
  /// On iOS it throws [SafeSDKException] with [SafeSDKErrorCode.unimplemented].
  static Future<void> askForMediaAudioPermissions() => _platform.askForMediaAudioPermissions();
}
