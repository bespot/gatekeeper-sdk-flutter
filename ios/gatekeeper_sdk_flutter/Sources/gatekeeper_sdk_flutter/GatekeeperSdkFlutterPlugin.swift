import Flutter
import UIKit
import CoreLocation
import AntifraudSDK

public class GatekeeperSdkFlutterPlugin: NSObject, FlutterPlugin, FlutterStreamHandler {
    private static let methodChannelName = "com.bespot/gatekeeper_sdk_flutter"
    private static let eventChannelName = "com.bespot/gatekeeper_sdk_flutter/subscribe"
    private let locationManager = CLLocationManager()
    private var eventSink: FlutterEventSink?

    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: methodChannelName, binaryMessenger: registrar.messenger())
        let instance = GatekeeperSdkFlutterPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)

        let eventChannel = FlutterEventChannel(name: eventChannelName, binaryMessenger: registrar.messenger())
        eventChannel.setStreamHandler(instance)
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "initialize":
            guard let apiBaseUrl = SecretsConfigHelper.apiBaseUrl,
                  let apiKey = SecretsConfigHelper.apiKey,
                  let authTokenUrl = SecretsConfigHelper.authTokenUrl,
                  let clientId = SecretsConfigHelper.clientId,
                  let clientSecret = SecretsConfigHelper.clientSecret else {
                result(FlutterError(
                    code: "INVALID_ARGUMENTS",
                    message: "Missing API_BASE_URL, API_KEY, AUTH_TOKEN_URL, CLIENT_ID or CLIENT_SECRET in Info.plist",
                    details: nil))
                return
            }
            SafeSDK.sharedSafeSDK.initialize(
                apiBaseUrl: apiBaseUrl,
                apiKey: apiKey,
                authTokenUrl: authTokenUrl,
                clientId: clientId,
                clientSecret: clientSecret,
                params: Self.params(from: call)
            )
            result(nil)
        case "initializeWithAccessToken":
            guard let accessToken = Self.string("accessToken", from: call) else {
                result(Self.missingArgument("accessToken"))
                return
            }
            guard let apiBaseUrl = SecretsConfigHelper.apiBaseUrl,
                  let apiKey = SecretsConfigHelper.apiKey else {
                result(FlutterError(
                    code: "INVALID_ARGUMENTS",
                    message: "Missing API_BASE_URL or API_KEY in Info.plist",
                    details: nil))
                return
            }
            SafeSDK.sharedSafeSDK.initialize(
                apiBaseUrl: apiBaseUrl,
                apiKey: apiKey,
                accessToken: accessToken,
                params: Self.params(from: call)
            )
            result(nil)
        case "check":
            check(result: result)
        case "enableLogging":
            result(FlutterError(
                code: "UNIMPLEMENTED",
                message: "Not implemented on iOS. Use the debugLoggingEnabled parameter of initialize to enable debug logging.",
                details: nil)
            )
        case "askForStoragePermissions", "askForMediaAudioPermissions":
            result(FlutterError(
                code: "UNIMPLEMENTED",
                message: "Not implemented on iOS.",
                details: nil)
            )
        case "setUserId":
            guard let userId = Self.string("userId", from: call) else {
                result(Self.missingArgument("userId"))
                return
            }
            SafeSDK.sharedSafeSDK.setUserId(userId)
            result(nil)
        case "setAccessToken":
            guard let accessToken = Self.string("accessToken", from: call) else {
                result(Self.missingArgument("accessToken"))
                return
            }
            SafeSDK.sharedSafeSDK.setAccessToken(accessToken)
            result(nil)
        case "unsubscribe":
            SafeSDK.sharedSafeSDK.unsubscribe()
            result(nil)
        case "askForLocationPermissions":
            locationManager.requestWhenInUseAuthorization()
            result(nil)
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
                                        message: error.rawValue,
                                        details: nil
                                       )
                    )
                }
            }
        }
    }

    private static func params(from call: FlutterMethodCall) -> [String: Any]? {
        (call.arguments as? [String: Any])?["params"] as? [String: Any]
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

    private static func string(_ key: String, from call: FlutterMethodCall) -> String? {
        guard let arguments = call.arguments as? [String: Any] else { return nil }
        return arguments[key] as? String
    }

    private static func missingArgument(_ key: String) -> FlutterError {
        FlutterError(code: "INVALID_ARGUMENTS", message: "Missing required \(key) parameter", details: nil)
    }

    public func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
        eventSink = events
        SafeSDK.sharedSafeSDK.subscribe { [weak self] subscribeResult in
            DispatchQueue.main.async {
                guard let eventSink = self?.eventSink else { return }
                switch subscribeResult {
                case .success(let action):
                    eventSink([
                        "type": Self.wireValue(of: action.actionType),
                        "signature": action.signature,
                    ])
                case .failure(let error):
                    eventSink(FlutterError(code: Self.wireValue(of: error), message: error.rawValue, details: nil))
                }
            }
        }
        return nil
    }

    public func onCancel(withArguments arguments: Any?) -> FlutterError? {
        eventSink = nil
        SafeSDK.sharedSafeSDK.unsubscribe()
        return nil
    }
}
