import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'gatekeeper_sdk_flutter_platform_interface.dart';

/// An implementation of [GatekeeperSdkFlutterPlatform] that uses method channels.
class MethodChannelGatekeeperSdkFlutter extends GatekeeperSdkFlutterPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('gatekeeper_sdk_flutter');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>(
      'getPlatformVersion',
    );
    return version;
  }
}
