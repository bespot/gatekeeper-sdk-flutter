package com.bespot.gatekeeper_sdk_flutter

import android.content.Context
import android.content.res.Resources
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.BinaryMessenger
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

    @Test
    fun onMethodCall_initialize_returnsUnimplemented() {
        val plugin = GatekeeperSdkFlutterPlugin()
        val result: MethodChannel.Result = Mockito.mock(MethodChannel.Result::class.java)

        plugin.onMethodCall(MethodCall("initialize", null), result)

        Mockito.verify(result).error(
            "UNIMPLEMENTED",
            "Not implemented on Android. Initialization is done at application launch.",
            null,
        )
    }

    @Test
    fun initializeWithAccessToken_missingResources_returnsInvalidArguments() {
        val result = callInitializeWithAccessToken(attachedPlugin(), "token")

        Mockito.verify(result).error("INVALID_ARGUMENTS", "Missing required initialize parameters", null)
    }

    @Test
    fun initializeWithAccessToken_missingToken_returnsInvalidArguments() {
        val result = callInitializeWithAccessToken(attachedPluginWithResources(), null)

        Mockito.verify(result).error("INVALID_ARGUMENTS", "Missing required accessToken parameter", null)
    }

    @Test
    fun initializeWithAccessToken_emptyToken_returnsInvalidArguments() {
        val result = callInitializeWithAccessToken(attachedPluginWithResources(), "")

        Mockito.verify(result).error("INVALID_ARGUMENTS", "Missing required accessToken parameter", null)
    }

    private fun callInitializeWithAccessToken(
        plugin: GatekeeperSdkFlutterPlugin,
        accessToken: String?,
    ): MethodChannel.Result {
        val result: MethodChannel.Result = Mockito.mock(MethodChannel.Result::class.java)
        plugin.onMethodCall(MethodCall("initializeWithAccessToken", mapOf("accessToken" to accessToken)), result)
        return result
    }

    private fun attachedPluginWithResources() =
        attachedPlugin("antifraud_sdk_api_url" to "https://example.test", "antifraud_sdk_key" to "key")

    private fun attachedPlugin(vararg strings: Pair<String, String>): GatekeeperSdkFlutterPlugin {
        val resources = Mockito.mock(Resources::class.java)
        val context = Mockito.mock(Context::class.java)
        Mockito.`when`(context.resources).thenReturn(resources)
        Mockito.`when`(context.packageName).thenReturn("test.package")
        strings.forEachIndexed { index, (name, value) ->
            val id = index + 1
            Mockito.`when`(resources.getIdentifier(name, "string", "test.package")).thenReturn(id)
            Mockito.`when`(context.getString(id)).thenReturn(value)
        }

        val binding = Mockito.mock(FlutterPlugin.FlutterPluginBinding::class.java)
        Mockito.`when`(binding.applicationContext).thenReturn(context)
        Mockito.`when`(binding.binaryMessenger).thenReturn(Mockito.mock(BinaryMessenger::class.java))

        return GatekeeperSdkFlutterPlugin().also { it.onAttachedToEngine(binding) }
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
