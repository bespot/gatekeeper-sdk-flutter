import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'gatekeeper_sdk_flutter_platform_interface.dart';
import 'src/models.dart';

/// An implementation of [GatekeeperSdkFlutterPlatform] that uses platform channels.
class MethodChannelGatekeeperSdkFlutter extends GatekeeperSdkFlutterPlatform {
  /// The method channel used for one-off calls to the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('com.bespot/gatekeeper_sdk_flutter');

  /// The event channel used for the periodic check results of [subscribe].
  @visibleForTesting
  final eventChannel = const EventChannel('com.bespot/gatekeeper_sdk_flutter/subscribe');

  // One shared native subscription; cancelling the last listener triggers native unsubscribe.
  late final Stream<SafeSDKAction> _subscription = eventChannel
      .receiveBroadcastStream()
      .map((event) => SafeSDKAction.fromMap(event as Map<Object?, Object?>))
      .transform(
        StreamTransformer<SafeSDKAction, SafeSDKAction>.fromHandlers(
          handleError: (error, stackTrace, sink) => sink.addError(_toSafeSDKError(error), stackTrace),
        ),
      );

  @override
  Future<void> initialize({bool debugLoggingEnabled = false}) => _invoke<void>('initialize', {
    'params': {'debugLoggingEnabled': debugLoggingEnabled},
  });

  @override
  Future<void> initializeWithAccessToken(String accessToken, {bool debugLoggingEnabled = false}) =>
      _invoke<void>('initializeWithAccessToken', {
        'accessToken': accessToken,
        'params': {'debugLoggingEnabled': debugLoggingEnabled},
      });

  @override
  Future<void> setAccessToken(String accessToken) => _invoke<void>('setAccessToken', {'accessToken': accessToken});

  @override
  Future<void> setUserId(String userId) => _invoke<void>('setUserId', {'userId': userId});

  @override
  Future<void> enableLogging(bool enabled) => _invoke<void>('enableLogging', {'debugLoggingEnabled': enabled});

  @override
  Future<SafeSDKAction> check() async {
    final result = await _invoke<Map<Object?, Object?>>('check');
    return SafeSDKAction.fromMap(result!);
  }

  @override
  Stream<SafeSDKAction> subscribe() => _subscription;

  @override
  Future<void> unsubscribe() => _invoke<void>('unsubscribe');

  @override
  Future<void> askForLocationPermissions() => _invoke<void>('askForLocationPermissions');

  @override
  Future<void> askForStoragePermissions() => _invoke<void>('askForStoragePermissions');

  @override
  Future<void> askForMediaAudioPermissions() => _invoke<void>('askForMediaAudioPermissions');

  /// Calls [method] on the native side and converts platform errors to [SafeSDKException].
  Future<T?> _invoke<T>(String method, [Map<String, Object?>? arguments]) async {
    try {
      return await methodChannel.invokeMethod<T>(method, arguments);
    } on PlatformException catch (e) {
      throw _toSafeSDKError(e);
    }
  }

  Object _toSafeSDKError(Object error) => error is PlatformException
      ? SafeSDKException(SafeSDKErrorCode.fromWire(error.code), message: error.message, details: error.details)
      : error;
}
