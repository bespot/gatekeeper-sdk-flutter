import Flutter
import UIKit
import XCTest

@testable import gatekeeper_sdk_flutter

// Unit tests for the parts of the bridge that do not call the SDK.
class RunnerTests: XCTestCase {

  private func handle(_ method: String, arguments: Any? = nil) -> Any? {
    let plugin = GatekeeperSdkFlutterPlugin()
    var reply: Any?
    let replied = expectation(description: "result must be called")
    plugin.handle(FlutterMethodCall(methodName: method, arguments: arguments)) { result in
      reply = result
      replied.fulfill()
    }
    waitForExpectations(timeout: 1)
    return reply
  }

  func testUnknownMethodIsNotImplemented() {
    let reply = handle("getPlatformVersion")

    XCTAssertTrue(reply as AnyObject === FlutterMethodNotImplemented)
  }

  func testAndroidOnlyMethodsReplyUnimplemented() {
    for method in ["enableLogging", "askForStoragePermissions", "askForMediaAudioPermissions"] {
      let error = handle(method) as? FlutterError

      XCTAssertEqual(error?.code, "UNIMPLEMENTED", method)
    }
  }

  func testMissingArgumentRepliesInvalidArguments() {
    for method in ["initializeWithAccessToken", "setAccessToken", "setUserId"] {
      let error = handle(method, arguments: [String: Any]()) as? FlutterError

      XCTAssertEqual(error?.code, "INVALID_ARGUMENTS", method)
    }
  }
}
