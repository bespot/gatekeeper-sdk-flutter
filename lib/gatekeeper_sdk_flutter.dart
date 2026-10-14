
import 'gatekeeper_sdk_flutter_platform_interface.dart';

class GatekeeperSdkFlutter {
  Future<String?> getPlatformVersion() {
    return GatekeeperSdkFlutterPlatform.instance.getPlatformVersion();
  }
}
