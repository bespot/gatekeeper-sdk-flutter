import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'gatekeeper_sdk_flutter_method_channel.dart';
import 'src/models.dart';

abstract class GatekeeperSdkFlutterPlatform extends PlatformInterface {
  /// Constructs a GatekeeperSdkFlutterPlatform.
  GatekeeperSdkFlutterPlatform() : super(token: _token);

  static final Object _token = Object();

  static GatekeeperSdkFlutterPlatform _instance = MethodChannelGatekeeperSdkFlutter();

  /// The default instance of [GatekeeperSdkFlutterPlatform] to use.
  ///
  /// Defaults to [MethodChannelGatekeeperSdkFlutter].
  static GatekeeperSdkFlutterPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [GatekeeperSdkFlutterPlatform] when
  /// they register themselves.
  static set instance(GatekeeperSdkFlutterPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  /// Initializes the SDK in OAuth mode.
  ///
  /// Credentials are read natively from `Info.plist` (iOS) and `resValues` (Android).
  Future<void> initialize({bool debugLoggingEnabled = false}) {
    throw UnimplementedError('initialize() has not been implemented.');
  }

  /// Initializes the SDK with a bearer token supplied by your app.
  ///
  /// API key and base URL are read natively from `Info.plist` (iOS) and `resValues` (Android).
  Future<void> initializeWithAccessToken(String accessToken, {bool debugLoggingEnabled = false}) {
    throw UnimplementedError('initializeWithAccessToken() has not been implemented.');
  }

  /// Updates the bearer token used on subsequent requests,
  /// without re-registering the device or restarting periodic checks.
  Future<void> setAccessToken(String accessToken) {
    throw UnimplementedError('setAccessToken() has not been implemented.');
  }

  /// Sets the identifier of the current user.
  Future<void> setUserId(String userId) {
    throw UnimplementedError('setUserId() has not been implemented.');
  }

  /// Enables or disables debug logs. Do not enable in production builds.
  Future<void> enableLogging(bool enabled) {
    throw UnimplementedError('enableLogging() has not been implemented.');
  }

  /// Performs a single on-demand check.
  ///
  /// Throws [SafeSDKException] on failure.
  Future<SafeSDKAction> check() {
    throw UnimplementedError('check() has not been implemented.');
  }

  /// Results of the periodic checks. Call an initialize method first.
  ///
  /// Errors are emitted as [SafeSDKException]. Cancelling the
  /// subscription stops the native periodic checks.
  Stream<SafeSDKAction> subscribe() {
    throw UnimplementedError('subscribe() has not been implemented.');
  }

  /// Stops the periodic checks started by [subscribe].
  Future<void> unsubscribe() {
    throw UnimplementedError('unsubscribe() has not been implemented.');
  }

  /// Requests "When In Use" location permission (iOS & Android).
  Future<void> askForLocationPermissions() {
    throw UnimplementedError('askForLocationPermissions() has not been implemented.');
  }

  /// Requests the storage permission used for the device seed (Android only).
  Future<void> askForStoragePermissions() {
    throw UnimplementedError('askForStoragePermissions() has not been implemented.');
  }

  /// Requests the media-audio permission used for the device seed on Android 13+ (Android only).
  Future<void> askForMediaAudioPermissions() {
    throw UnimplementedError('askForMediaAudioPermissions() has not been implemented.');
  }
}
