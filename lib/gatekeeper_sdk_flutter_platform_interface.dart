import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'gatekeeper_sdk_flutter_method_channel.dart';

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

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
