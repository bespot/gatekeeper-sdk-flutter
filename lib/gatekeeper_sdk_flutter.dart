import 'gatekeeper_sdk_flutter_platform_interface.dart';
import 'src/models.dart';

export 'src/models.dart';

/// Entry point to Bespot GatekeeperSDK.
///
/// Credentials (API key, base URL, OAuth) are read by the native layer from
/// `Info.plist` (iOS) and `resValues` (Android).
class SafeSDK {
  SafeSDK._();

  static GatekeeperSdkFlutterPlatform get _platform => GatekeeperSdkFlutterPlatform.instance;

  /// Initializes the SDK in OAuth mode.
  static Future<void> initialize({bool debugLoggingEnabled = false}) =>
      _platform.initialize(debugLoggingEnabled: debugLoggingEnabled);

  /// Initializes the SDK with a bearer token supplied by your app.
  ///
  /// To refresh the token later, use [setAccessToken] instead of calling this again.
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
  static Future<SafeSDKAction> check() => _platform.check();

  /// Results of the periodic checks. Call an initialize method first.
  ///
  /// Errors are emitted as [SafeSDKException]. Cancelling the
  /// subscription stops the native periodic checks.
  static Stream<SafeSDKAction> subscribe() => _platform.subscribe();

  /// Stops the periodic checks started by [subscribe].
  static Future<void> unsubscribe() => _platform.unsubscribe();

  /// Requests "When In Use" location permission (iOS & Android).
  static Future<void> askForLocationPermissions() => _platform.askForLocationPermissions();

  /// Requests the storage permission used for the device seed (Android only).
  ///
  /// On iOS it throws [SafeSDKException] with [SafeSDKErrorCode.unimplemented].
  static Future<void> askForStoragePermissions() => _platform.askForStoragePermissions();

  /// Requests the media-audio permission used for the device seed on Android 13+ (Android only).
  ///
  /// On iOS it throws [SafeSDKException] with [SafeSDKErrorCode.unimplemented].
  static Future<void> askForMediaAudioPermissions() => _platform.askForMediaAudioPermissions();
}
