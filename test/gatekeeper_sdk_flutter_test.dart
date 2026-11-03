import 'package:flutter_test/flutter_test.dart';
import 'package:gatekeeper_sdk_flutter/gatekeeper_sdk_flutter.dart';
import 'package:gatekeeper_sdk_flutter/gatekeeper_sdk_flutter_platform_interface.dart';
import 'package:gatekeeper_sdk_flutter/gatekeeper_sdk_flutter_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

/// Fake platform that records the calls made by [SafeSDK].
class MockGatekeeperSdkFlutterPlatform with MockPlatformInterfaceMixin implements GatekeeperSdkFlutterPlatform {
  final calls = <String>[];

  static const action = SafeSDKAction(type: ActionType.block, signature: 'test-signature');

  @override
  Future<void> initialize({bool debugLoggingEnabled = false}) async =>
      calls.add('initialize(debugLoggingEnabled: $debugLoggingEnabled)');

  @override
  Future<void> initializeWithAccessToken(String accessToken, {bool debugLoggingEnabled = false}) async =>
      calls.add('initializeWithAccessToken($accessToken, debugLoggingEnabled: $debugLoggingEnabled)');

  @override
  Future<void> setAccessToken(String accessToken) async => calls.add('setAccessToken($accessToken)');

  @override
  Future<void> setUserId(String userId) async => calls.add('setUserId($userId)');

  @override
  Future<void> enableLogging(bool enabled) async => calls.add('enableLogging($enabled)');

  @override
  Future<SafeSDKAction> check() async {
    calls.add('check');
    return action;
  }

  @override
  Stream<SafeSDKAction> subscribe() {
    calls.add('subscribe');
    return Stream.value(action);
  }

  @override
  Future<void> unsubscribe() async => calls.add('unsubscribe');

  @override
  Future<void> askForLocationPermissions() async => calls.add('askForLocationPermissions');

  @override
  Future<void> askForStoragePermissions() async => calls.add('askForStoragePermissions');

  @override
  Future<void> askForMediaAudioPermissions() async => calls.add('askForMediaAudioPermissions');
}

void main() {
  final GatekeeperSdkFlutterPlatform initialPlatform = GatekeeperSdkFlutterPlatform.instance;

  test('$MethodChannelGatekeeperSdkFlutter is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelGatekeeperSdkFlutter>());
  });

  group('SafeSDK forwards calls to the platform', () {
    late MockGatekeeperSdkFlutterPlatform fakePlatform;

    setUp(() {
      fakePlatform = MockGatekeeperSdkFlutterPlatform();
      GatekeeperSdkFlutterPlatform.instance = fakePlatform;
    });

    test('initialize and initializeWithAccessToken', () async {
      await SafeSDK.initialize(debugLoggingEnabled: true);
      await SafeSDK.initializeWithAccessToken('token');

      expect(fakePlatform.calls, [
        'initialize(debugLoggingEnabled: true)',
        'initializeWithAccessToken(token, debugLoggingEnabled: false)',
      ]);
    });

    test('setAccessToken, setUserId and enableLogging', () async {
      await SafeSDK.setAccessToken('new-token');
      await SafeSDK.setUserId('user-1');
      await SafeSDK.enableLogging(true);

      expect(fakePlatform.calls, ['setAccessToken(new-token)', 'setUserId(user-1)', 'enableLogging(true)']);
    });

    test('check returns the platform result', () async {
      final action = await SafeSDK.check();

      expect(action.type, ActionType.block);
      expect(action.signature, 'test-signature');
    });

    test('subscribe and unsubscribe', () async {
      expect(await SafeSDK.subscribe().first, MockGatekeeperSdkFlutterPlatform.action);
      await SafeSDK.unsubscribe();

      expect(fakePlatform.calls, ['subscribe', 'unsubscribe']);
    });

    test('permission requests', () async {
      await SafeSDK.askForLocationPermissions();
      await SafeSDK.askForStoragePermissions();
      await SafeSDK.askForMediaAudioPermissions();

      expect(fakePlatform.calls, [
        'askForLocationPermissions',
        'askForStoragePermissions',
        'askForMediaAudioPermissions',
      ]);
    });
  });
}
