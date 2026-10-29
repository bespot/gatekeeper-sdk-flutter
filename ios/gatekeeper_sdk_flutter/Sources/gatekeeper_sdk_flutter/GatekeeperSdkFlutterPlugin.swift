import Flutter
import UIKit
import AntifraudSDK

public class GatekeeperSdkFlutterPlugin: NSObject, FlutterPlugin {
    private static let methodChannelName = "com.bespot/gatekeeper_sdk_flutter"

    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: methodChannelName, binaryMessenger: registrar.messenger())
        let instance = GatekeeperSdkFlutterPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "check":
            check(result: result)
        default:
            result(FlutterMethodNotImplemented)
        }
    }

    private func check(result: @escaping FlutterResult) {
        SafeSDK.sharedSafeSDK.check { checkResult in
            DispatchQueue.main.async {
                switch checkResult {
                case .success(let action):
                    result([
                        "type": Self.wireValue(of: action.actionType),
                        "signature": action.signature
                    ])
                    case .failure(let error):
                    result(FlutterError(code: Self.wireValue(of: error),
                                        message: "SafeSDK check failed",
                                        details: nil
                                       )
                    )
                }
            }
        }
    }

    private static func wireValue(of type: ActionType) -> String {
        switch type {
        case .block:
            return "BLOCK"
        case .limitAccess:
            return "LIMIT_ACCESS"
        case .monitor:
            return "MONITOR"
        case .notSafe:
            return "NOT_SAFE"
        case .safe:
            return "SAFE"
        @unknown default:
            return "DEFAULT_UNKNOWN_ACTION"
        }
    }

    private static func wireValue(of error: SDKError) -> String {
        switch error {
        case .networkConnection:
            return "NETWORK_CONNECTION"
        case .noActiveApiKey:
            return "NO_ACTIVE_API_KEY"
        case .quotaReachedForPlan:
            return "QUOTA_REACHED_FOR_PLAN"
        case .noChecksAvailableFailure:
            return "NO_CHECKS_AVAILABLE"
        case .noRecipeFoundFailure:
            return "NO_RECIPE_FOUND"
        case .notInitialized:
            return "NOT_INITIALIZED"
        case .invalidToken:
            return "INVALID_TOKEN"
        case .authError:
            return "AUTH_ERROR"
        case .alreadyInitialized:
            return "ALREADY_INITIALIZED"
        case .serverError:
            return "SERVER_ERROR"
        case .unknownError:
            return "UNKNOWN_ERROR"
        @unknown default:
            return "DEFAULT_UNKNOWN_ERROR"
        }
    }
}
