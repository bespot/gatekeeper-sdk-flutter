import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gatekeeper_sdk_flutter/gatekeeper_sdk_flutter.dart';
import 'package:gatekeeper_sdk_flutter/gatekeeper_sdk_flutter_method_channel.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  late MethodChannelGatekeeperSdkFlutter platform;

  /// The calls received by the fake native side.
  late List<MethodCall> calls;

  /// What the fake native side answers (or throws) for the next call.
  late Object? Function(MethodCall call) nativeResponse;

  setUp(() {
    platform = MethodChannelGatekeeperSdkFlutter();
    calls = [];
    nativeResponse = (_) => null;
    messenger.setMockMethodCallHandler(platform.methodChannel, (call) async {
      calls.add(call);
      return nativeResponse(call);
    });
  });

  tearDown(() {
    messenger.setMockMethodCallHandler(platform.methodChannel, null);
    messenger.setMockStreamHandler(platform.eventChannel, null);
  });

  test('uses the contract channel names', () {
    expect(platform.methodChannel.name, 'com.bespot/gatekeeper_sdk_flutter');
    expect(platform.eventChannel.name, 'com.bespot/gatekeeper_sdk_flutter/subscribe');
  });

  group('sends the contract method names and arguments', () {
    test('initialize', () async {
      await platform.initialize(debugLoggingEnabled: true);

      expect(calls.single.method, 'initialize');
      expect(calls.single.arguments, {
        'params': {'debugLoggingEnabled': true},
      });
    });

    test('initializeWithAccessToken', () async {
      await platform.initializeWithAccessToken('token');

      expect(calls.single.method, 'initializeWithAccessToken');
      expect(calls.single.arguments, {
        'accessToken': 'token',
        'params': {'debugLoggingEnabled': false},
      });
    });

    test('setAccessToken, setUserId and enableLogging', () async {
      await platform.setAccessToken('new-token');
      await platform.setUserId('user-1');
      await platform.enableLogging(true);

      expect(calls.map((c) => c.method), ['setAccessToken', 'setUserId', 'enableLogging']);
      expect(calls[0].arguments, {'accessToken': 'new-token'});
      expect(calls[1].arguments, {'userId': 'user-1'});
      expect(calls[2].arguments, {'debugLoggingEnabled': true});
    });

    test('calls without arguments', () async {
      await platform.unsubscribe();
      await platform.askForLocationPermissions();
      await platform.askForStoragePermissions();
      await platform.askForMediaAudioPermissions();

      expect(calls.map((c) => c.method), [
        'unsubscribe',
        'askForLocationPermissions',
        'askForStoragePermissions',
        'askForMediaAudioPermissions',
      ]);
      expect(calls.every((c) => c.arguments == null), isTrue);
    });
  });

  group('check', () {
    test('converts the native result to a SafeSDKAction', () async {
      nativeResponse = (_) => {'type': 'LIMIT_ACCESS', 'signature': 'abc'};

      final action = await platform.check();

      expect(action.type, ActionType.limitAccess);
      expect(action.signature, 'abc');
    });

    test('maps unknown action types to ActionType.unknown', () async {
      nativeResponse = (_) => {'type': 'DEFAULT_UNKNOWN_ACTION', 'signature': 'abc'};
      expect((await platform.check()).type, ActionType.unknown);

      nativeResponse = (_) => {'type': 'SOMETHING_NEW', 'signature': 'abc'};
      expect((await platform.check()).type, ActionType.unknown);
    });

    test('converts native errors to SafeSDKException', () async {
      nativeResponse = (_) => throw PlatformException(
        code: 'ALREADY_INITIALIZED',
        message: 'Already initialized',
        details: {'initializedWith': 'OAUTH2', 'requestedWith': 'ACCESS_TOKEN'},
      );

      await expectLater(
        platform.check(),
        throwsA(
          isA<SafeSDKException>()
              .having((e) => e.code, 'code', SafeSDKErrorCode.alreadyInitialized)
              .having((e) => e.message, 'message', 'Already initialized')
              .having((e) => e.details, 'details', {'initializedWith': 'OAUTH2', 'requestedWith': 'ACCESS_TOKEN'}),
        ),
      );
    });

    test('converts UNIMPLEMENTED for platform-specific methods', () async {
      nativeResponse = (_) => throw PlatformException(code: 'UNIMPLEMENTED', message: 'Not implemented on iOS.');

      await expectLater(
        platform.askForStoragePermissions(),
        throwsA(isA<SafeSDKException>().having((e) => e.code, 'code', SafeSDKErrorCode.unimplemented)),
      );
    });

    test('maps unknown error codes to SafeSDKErrorCode.unknownError', () async {
      nativeResponse = (_) => throw PlatformException(code: 'DEFAULT_UNKNOWN_ERROR');

      await expectLater(
        platform.check(),
        throwsA(isA<SafeSDKException>().having((e) => e.code, 'code', SafeSDKErrorCode.unknownError)),
      );
    });
  });

  test('subscribe emits actions and SafeSDKException errors', () async {
    messenger.setMockStreamHandler(
      platform.eventChannel,
      MockStreamHandler.inline(
        onListen: (arguments, events) {
          events.success({'type': 'SAFE', 'signature': 'sig-1'});
          events.error(code: 'INVALID_TOKEN', message: 'Token is blank');
          events.endOfStream();
        },
      ),
    );

    final results = <Object>[];
    await platform.subscribe().handleError(results.add).forEach(results.add);

    expect(results, hasLength(2));
    expect((results[0] as SafeSDKAction).type, ActionType.safe);
    expect((results[1] as SafeSDKException).code, SafeSDKErrorCode.invalidToken);
  });
}
