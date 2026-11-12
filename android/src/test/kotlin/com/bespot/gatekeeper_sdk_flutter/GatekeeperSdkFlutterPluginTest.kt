package com.bespot.gatekeeper_sdk_flutter

import android.content.Context
import android.content.res.Resources
import com.bespot.shared.common.models.Action
import com.bespot.shared.core.Failure
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.mockito.Mockito
import kotlin.test.Test
import kotlin.test.assertEquals

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

    @Test
    fun actionMap_usesTheSdkActionNameAndTheSignature() {
        val plugin = GatekeeperSdkFlutterPlugin()
        val expected = listOf(
            Action.BLOCK to "BLOCK",
            Action.LIMIT_ACCESS to "LIMIT_ACCESS",
            Action.MONITOR to "MONITOR",
            Action.NOT_SAFE to "NOT_SAFE",
            Action.SAFE to "SAFE",
        )

        for ((action, type) in expected) {
            assertEquals(mapOf("type" to type, "signature" to "signature"), plugin.actionMap(action, "signature"))
        }
    }

    @Test
    fun mapFailureToCodeAndMessage_mapsEveryFailure() {
        val plugin = GatekeeperSdkFlutterPlugin()
        val expected = listOf(
            Failure.NetworkConnection to ("NETWORK_CONNECTION" to "Connection Error"),
            Failure.NoActiveApiKey to ("NO_ACTIVE_API_KEY" to "The API Key is either disabled or wrong"),
            Failure.QuotaReachedForPlan to ("QUOTA_REACHED_FOR_PLAN" to "The quota for your plan has been reached"),
            Failure.NoChecksAvailableFailure to ("NO_CHECKS_AVAILABLE" to "The server did not find available checks"),
            Failure.NoRecipeFoundFailure to ("NO_RECIPE_FOUND" to "The application does not have a valid recipe"),
            Failure.NotInitialized to ("NOT_INITIALIZED" to "The SDK is not initialized"),
            Failure.ServerError to ("SERVER_ERROR" to "Remote Server Error"),
            Failure.UnknownError to ("UNKNOWN_ERROR" to "Unknown Error (see support section)"),
            Failure.InvalidToken to ("INVALID_TOKEN" to "The access token is invalid or expired"),
            Failure.AuthError to ("AUTH_ERROR" to "Authentication failed"),
            Failure.AlreadyInitialized("OAUTH2", "ACCESS_TOKEN") to ("ALREADY_INITIALIZED" to "The SDK is already initialized"),
        )

        for ((failure, codeAndMessage) in expected) {
            assertEquals(codeAndMessage, plugin.mapFailureToCodeAndMessage(failure), failure::class.simpleName)
        }
    }

    @Test
    fun askForPermissions_withoutActivity_returnsNoActivity() {
        val plugin = GatekeeperSdkFlutterPlugin()

        for (method in listOf("askForLocationPermissions", "askForStoragePermissions", "askForMediaAudioPermissions")) {
            val result: MethodChannel.Result = Mockito.mock(MethodChannel.Result::class.java)

            plugin.onMethodCall(MethodCall(method, null), result)

            Mockito.verify(result).error("NO_ACTIVITY", "No Activity to show the permission dialog", null)
        }
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
