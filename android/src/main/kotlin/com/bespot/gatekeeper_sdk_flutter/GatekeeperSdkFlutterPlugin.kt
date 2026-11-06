package com.bespot.gatekeeper_sdk_flutter

import android.content.Context
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
    }

    override fun onMethodCall(
        call: MethodCall,
        result: Result,
    ) {
        when (call.method) {
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
    }

    override fun onCancel(arguments: Any?) {
        eventSink = null
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

    private companion object {
        // These names must match the Dart and iOS sides.
        const val METHOD_CHANNEL_NAME = "com.bespot/gatekeeper_sdk_flutter"
        const val EVENT_CHANNEL_NAME = "com.bespot/gatekeeper_sdk_flutter/subscribe"
    }
}