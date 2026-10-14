import 'package:flutter_test/flutter_test.dart';
import 'package:gatekeeper_sdk_flutter/gatekeeper_sdk_flutter.dart';
import 'package:gatekeeper_sdk_flutter/gatekeeper_sdk_flutter_platform_interface.dart';
import 'package:gatekeeper_sdk_flutter/gatekeeper_sdk_flutter_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockGatekeeperSdkFlutterPlatform
    with MockPlatformInterfaceMixin
    implements GatekeeperSdkFlutterPlatform {
  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final GatekeeperSdkFlutterPlatform initialPlatform = GatekeeperSdkFlutterPlatform.instance;

  test('$MethodChannelGatekeeperSdkFlutter is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelGatekeeperSdkFlutter>());
  });

  test('getPlatformVersion', () async {
    GatekeeperSdkFlutter gatekeeperSdkFlutterPlugin = GatekeeperSdkFlutter();
    MockGatekeeperSdkFlutterPlatform fakePlatform = MockGatekeeperSdkFlutterPlatform();
    GatekeeperSdkFlutterPlatform.instance = fakePlatform;

    expect(await gatekeeperSdkFlutterPlugin.getPlatformVersion(), '42');
  });
}
