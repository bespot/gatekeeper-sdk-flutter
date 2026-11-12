package com.bespot.gatekeeper_sdk_flutter

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.bespot.shared.common.models.Action
import com.bespot.shared.core.Failure
import com.bespot.shared.core.FraudulentCheckObserver
import com.bespot.shared.core.SafeSdk
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

/** Connects the Dart API to the Android SDK. See CONTRACT.md for the channel contract. */
class GatekeeperSdkFlutterPlugin :
    FlutterPlugin,
    MethodCallHandler,
    EventChannel.StreamHandler,
    ActivityAware {
    private lateinit var methodChannel: MethodChannel
    private lateinit var eventChannel: EventChannel
    private lateinit var applicationContext: Context
    // Null while Dart is not listening to the stream.
    private var eventSink: EventChannel.EventSink? = null
    // The binding, not only the Activity, because permission results need a listener on it.
    private var activityBinding: ActivityPluginBinding? = null

    // Checks waiting for the next SDK result. Used only on the main thread.
    private val pendingChecks = mutableListOf<Result>()

    // onCancel arrived while checks were waiting, so the SDK unsubscribe waits for them.
    private var unsubscribeWhenChecksFinish = false

    // Created on first use, because unit tests have no main Looper.
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    // One observer for both check and subscribe. The SDK keeps a single observer,
    // so a check would otherwise replace the one set by subscribe.
    private val sdkObserver =
        object : FraudulentCheckObserver {
            override fun onSuccess(
                action: Action,
                signature: String,
            ) {
                val map = actionMap(action, signature)
                mainHandler.post { deliverSuccess(map) }
            }

            override fun onError(error: Failure) {
                val (code, message) = mapFailureToCodeAndMessage(error)
                mainHandler.post { deliverError(code, message) }
            }
        }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        applicationContext = binding.applicationContext

        methodChannel = MethodChannel(binding.binaryMessenger, METHOD_CHANNEL_NAME)
        methodChannel.setMethodCallHandler(this)

        eventChannel = EventChannel(binding.binaryMessenger, EVENT_CHANNEL_NAME)
        eventChannel.setStreamHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
        eventSink = null
        pendingChecks.clear()
    }

    override fun onMethodCall(
        call: MethodCall,
        result: Result,
    ) {
        when (call.method) {
            "initialize" -> result.error(
                "UNIMPLEMENTED",
                "Not implemented on Android. Initialization is done at application launch.",
                null
            )
            "initializeWithAccessToken" -> {
                val accessToken = call.argument<String>("accessToken")
                val apiBaseUrl = resString("antifraud_sdk_api_url")
                val apiKey = resString("antifraud_sdk_key")
                if (apiBaseUrl.isNullOrEmpty() || apiKey.isNullOrEmpty()) {
                    return result.error("INVALID_ARGUMENTS", "Missing required initialize parameters", null)
                }
                if (accessToken.isNullOrEmpty()) {
                    return result.missingArgument("accessToken")
                }
                SafeSdk.initWithAccessToken(apiBaseUrl, apiKey, accessToken)
                result.success(null)
            }
            "check" -> {
                pendingChecks.add(result)
                SafeSdk.check(sdkObserver)
            }
            "setUserId" -> {
                val userId = call.argument<String>("userId") ?: return result.missingArgument("userId")
                SafeSdk.setUserID(userId)
                result.success(null)
            }
            "setAccessToken" -> {
                val accessToken = call.argument<String>("accessToken") ?: return result.missingArgument("accessToken")
                SafeSdk.setAccessToken(accessToken)
                result.success(null)
            }
            "enableLogging" -> {
                val enabled = call.argument<Boolean>("debugLoggingEnabled") ?: return result.missingArgument("debugLoggingEnabled")
                SafeSdk.logging(enabled)
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

    override fun onListen(
        arguments: Any?,
        events: EventChannel.EventSink,
    ) {
        eventSink = events
        unsubscribeWhenChecksFinish = false
        SafeSdk.subscribe(sdkObserver)
    }

    override fun onCancel(arguments: Any?) {
        eventSink = null
        if (pendingChecks.isEmpty()) {
            SafeSdk.unsubscribe()
        } else {
            unsubscribeWhenChecksFinish = true
        }
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activityBinding = binding
    }

    override fun onDetachedFromActivityForConfigChanges() {
        // The Activity is being recreated, for example on rotation. The new one arrives in
        // onReattachedToActivityForConfigChanges.
        activityBinding = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activityBinding = binding
    }

    override fun onDetachedFromActivity() {
        activityBinding = null
    }

    private fun Result.missingArgument(key: String) {
        error("INVALID_ARGUMENTS", "Missing required $key parameter", null)
    }

    private fun resString(name: String): String? {
        val id = applicationContext.resources.getIdentifier(name, "string", applicationContext.packageName)
        return if (id != 0) applicationContext.getString(id) else null
    }

    internal fun actionMap(
        action: Action,
        signature: String,
    ): Map<String, String> = mapOf("type" to action.toString(), "signature" to signature)

    internal fun mapFailureToCodeAndMessage(error: Failure): Pair<String, String> =
        when (error) {
            is Failure.NetworkConnection -> "NETWORK_CONNECTION" to "Connection Error"
            is Failure.NoActiveApiKey -> "NO_ACTIVE_API_KEY" to "The API Key is either disabled or wrong"
            is Failure.QuotaReachedForPlan -> "QUOTA_REACHED_FOR_PLAN" to "The quota for your plan has been reached"
            is Failure.NoChecksAvailableFailure -> "NO_CHECKS_AVAILABLE" to "The server did not find available checks"
            is Failure.NoRecipeFoundFailure -> "NO_RECIPE_FOUND" to "The application does not have a valid recipe"
            is Failure.NotInitialized -> "NOT_INITIALIZED" to "The SDK is not initialized"
            is Failure.ServerError -> "SERVER_ERROR" to "Remote Server Error"
            is Failure.UnknownError -> "UNKNOWN_ERROR" to "Unknown Error (see support section)"
            is Failure.InvalidToken -> "INVALID_TOKEN" to "The access token is invalid or expired"
            is Failure.AuthError -> "AUTH_ERROR" to "Authentication failed"
            is Failure.AlreadyInitialized -> "ALREADY_INITIALIZED" to "The SDK is already initialized"
            // Kept for SDK versions newer than the one this plugin is built with.
            else -> "DEFAULT_UNKNOWN_ERROR" to "Unknown Error (see support section)"
        }

    private fun deliverSuccess(action: Map<String, String>) {
        val checks = pendingChecks.toList()
        pendingChecks.clear()
        checks.forEach { it.success(action) }
        eventSink?.success(action)
        unsubscribeIfWaiting()
    }

    private fun deliverError(
        code: String,
        message: String,
    ) {
        val checks = pendingChecks.toList()
        pendingChecks.clear()
        checks.forEach { it.error(code, message, null) }
        eventSink?.error(code, message, null)
        unsubscribeIfWaiting()
    }

    private fun unsubscribeIfWaiting() {
        if (unsubscribeWhenChecksFinish && pendingChecks.isEmpty()) {
            unsubscribeWhenChecksFinish = false
            SafeSdk.unsubscribe()
        }
    }

    private companion object {
        // These names must match the Dart and iOS sides.
        const val METHOD_CHANNEL_NAME = "com.bespot/gatekeeper_sdk_flutter"
        const val EVENT_CHANNEL_NAME = "com.bespot/gatekeeper_sdk_flutter/subscribe"
    }
}