package com.bespot.gatekeeper_sdk_flutter

import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.mockito.Mockito
import kotlin.test.Test

/*
 * This demonstrates a simple unit test of the Kotlin portion of this plugin's implementation.
 *
 * Once you have built the plugin's example app, you can run these tests from the command
 * line by running `./gradlew testDebugUnitTest` in the `example/android/` directory, or
 * you can run them directly from IDEs that support JUnit such as Android Studio.
 */

internal class GatekeeperSdkFlutterPluginTest {
    @Test
    fun onMethodCall_unknownMethod_returnsNotImplemented() {
        val plugin = GatekeeperSdkFlutterPlugin()
        val result: MethodChannel.Result = Mockito.mock(MethodChannel.Result::class.java)

        plugin.onMethodCall(MethodCall("unknownMethod", null), result)

        Mockito.verify(result).notImplemented()
    }
    @Test
    fun onMethodCall_setUserIdWithoutArgument_returnsInvalidArguments() {
        assertMissingArgument("setUserId", "userId")
    }

    @Test
    fun onMethodCall_setAccessTokenWithoutArgument_returnsInvalidArguments() {
        assertMissingArgument("setAccessToken", "accessToken")
    }

    @Test
    fun onMethodCall_enableLoggingWithoutArgument_returnsInvalidArguments() {
        assertMissingArgument("enableLogging", "debugLoggingEnabled")
    }

    private fun assertMissingArgument(
        method: String,
        key: String,
    ) {
        val plugin = GatekeeperSdkFlutterPlugin()
        val result: MethodChannel.Result = Mockito.mock(MethodChannel.Result::class.java)

        plugin.onMethodCall(MethodCall(method, null), result)

        Mockito.verify(result).error("INVALID_ARGUMENTS", "Missing required $key parameter", null)
    }
}
