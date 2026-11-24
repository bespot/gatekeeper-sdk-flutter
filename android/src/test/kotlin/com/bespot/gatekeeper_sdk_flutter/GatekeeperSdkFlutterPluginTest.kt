package com.bespot.gatekeeper_sdk_flutter

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import com.bespot.shared.common.models.Action
import com.bespot.shared.core.Failure
import com.bespot.shared.core.FraudulentCheckObserver
import com.bespot.shared.core.SafeSdk
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.mockito.Mockito
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals

private const val CHECK_TIMEOUT = "CHECK_TIMEOUT"
private const val CHECK_TIMEOUT_MESSAGE = "The SDK did not answer the check within 25 seconds"
private const val CHECK_TIMEOUT_MILLIS = 25_000L

/*
 * This demonstrates a simple unit test of the Kotlin portion of this plugin's implementation.
 *
 * Once you have built the plugin's example app, you can run these tests from the command
 * line by running `./gradlew testDebugUnitTest` in the `example/android/` directory, or
 * you can run them directly from IDEs that support JUnit such as Android Studio.
 */

/*
 * Follows the SDK's observer the way the SDK does. subscribe and unsubscribe emit the observer on one
 * flow, and check emits it on another. The initialized SDK collects both and keeps the last one as its
 * observer. The SDK is not initialized in unit tests, so this collects both flows instead.
 * The flow getters are internal to the SDK, so reflection is used.
 */
private object SdkObserverMirror {
    @Volatile
    var observer: FraudulentCheckObserver? = null
        private set

    // The observer of every SDK check call, in order. Each plugin has its own observer, so a test
    // counts only its own plugin's calls, even if an earlier test's call arrives late.
    val checkCalls = CopyOnWriteArrayList<FraudulentCheckObserver>()

    init {
        // Undispatched, so both collectors are subscribed before any test calls the SDK.
        val scope = CoroutineScope(Dispatchers.Unconfined)
        for (getter in listOf("getCheckObserver\$shared_release", "getDetectTriggerWithCallback\$shared_release")) {
            @Suppress("UNCHECKED_CAST")
            val flow = SafeSdk::class.java.getMethod(getter).invoke(SafeSdk) as SharedFlow<FraudulentCheckObserver>
            val isCheck = getter.startsWith("getDetectTrigger")
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                flow.collect {
                    observer = it
                    if (isCheck) checkCalls.add(it)
                }
            }
        }
    }
}

/*
 * Runs posted tasks only when the test asks, on the test thread, with a virtual clock for delayed tasks.
 * The plugin posts every SDK result to the main thread, so a test calls runDue() after an SDK result.
 */
private class FakeMainThread : MainThread {
    private class Task(
        val runnable: Runnable,
        val atMillis: Long,
    )

    private val tasks = mutableListOf<Task>()
    private var nowMillis = 0L

    override fun post(task: Runnable) {
        tasks.add(Task(task, nowMillis))
    }

    override fun postDelayed(
        task: Runnable,
        delayMillis: Long,
    ) {
        tasks.add(Task(task, nowMillis + delayMillis))
    }

    override fun removeCallbacks(task: Runnable) {
        tasks.removeAll { it.runnable === task }
    }

    // Runs the tasks that are due now, including tasks they post.
    fun runDue() = advanceBy(0)

    // Moves the clock forward and runs every task that becomes due, in time order.
    fun advanceBy(millis: Long) {
        val target = nowMillis + millis
        while (true) {
            val next = tasks.filter { it.atMillis <= target }.minByOrNull { it.atMillis } ?: break
            tasks.remove(next)
            nowMillis = next.atMillis
            next.runnable.run()
        }
        nowMillis = target
    }
}

internal class GatekeeperSdkFlutterPluginTest {
    init {
        // Starts the mirror before the test calls the SDK.
        SdkObserverMirror.observer
    }

    @Test
    fun onMethodCall_unknownMethod_returnsNotImplemented() {
        val plugin = GatekeeperSdkFlutterPlugin(FakeMainThread())
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
        val plugin = GatekeeperSdkFlutterPlugin(FakeMainThread())
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
        val plugin = GatekeeperSdkFlutterPlugin(FakeMainThread())
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
        val plugin = GatekeeperSdkFlutterPlugin(FakeMainThread())
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
        val plugin = GatekeeperSdkFlutterPlugin(FakeMainThread())

        for (method in listOf("askForLocationPermissions", "askForStoragePermissions", "askForMediaAudioPermissions")) {
            val result: MethodChannel.Result = Mockito.mock(MethodChannel.Result::class.java)

            plugin.onMethodCall(MethodCall(method, null), result)

            Mockito.verify(result).error("NO_ACTIVITY", "No Activity to show the permission dialog", null)
        }
    }

    @Test
    fun engineDetach_repliesToWaitingChecks() {
        val binding = engineBinding()
        val plugin = GatekeeperSdkFlutterPlugin(FakeMainThread()).also { it.onAttachedToEngine(binding) }
        val result: MethodChannel.Result = Mockito.mock(MethodChannel.Result::class.java)
        plugin.onMethodCall(MethodCall("check", null), result)

        plugin.onDetachedFromEngine(binding)

        // The Dart future must complete, so the waiting check gets an error.
        Mockito.verify(result).error("ENGINE_DETACHED", "The Flutter engine was detached", null)
    }

    @Test
    fun engineDetach_repliesToWaitingPermissionRequests() {
        val binding = engineBinding()
        Mockito.`when`(binding.applicationContext.checkSelfPermission(Mockito.anyString()))
            .thenReturn(PackageManager.PERMISSION_DENIED)
        val plugin = GatekeeperSdkFlutterPlugin(FakeMainThread()).also { it.onAttachedToEngine(binding) }
        val activityBinding = Mockito.mock(ActivityPluginBinding::class.java)
        Mockito.`when`(activityBinding.activity).thenReturn(Mockito.mock(Activity::class.java))
        plugin.onAttachedToActivity(activityBinding)
        val result: MethodChannel.Result = Mockito.mock(MethodChannel.Result::class.java)
        plugin.onMethodCall(MethodCall("askForLocationPermissions", null), result)

        // The engine goes away without onDetachedFromActivity.
        plugin.onDetachedFromEngine(binding)

        Mockito.verify(result).error("NO_ACTIVITY", "No Activity to show the permission dialog", null)
    }

    @Test
    fun engineDetach_unsubscribesFromTheSdk() {
        val binding = engineBinding()
        val plugin = GatekeeperSdkFlutterPlugin(FakeMainThread()).also { it.onAttachedToEngine(binding) }
        plugin.onListen(null, Mockito.mock(EventChannel.EventSink::class.java))
        // subscribe runs in an SDK coroutine, so wait until the SDK holds this plugin's observer.
        // The SDK is shared by all tests, so an observer left by another test does not count.
        val pluginObserver = GatekeeperSdkFlutterPlugin::class.java.getDeclaredField("sdkObserver")
            .apply { isAccessible = true }
            .get(plugin)
        assertEquals(pluginObserver, awaitSdkObserver { it === pluginObserver })

        plugin.onDetachedFromEngine(binding)

        assertEquals(FraudulentCheckObserver.empty, awaitSdkObserver { it == FraudulentCheckObserver.empty })
    }

    @Test
    fun engineDetach_afterACheckWithoutStream_unsubscribesFromTheSdk() {
        val binding = engineBinding()
        val plugin = GatekeeperSdkFlutterPlugin(FakeMainThread()).also { it.onAttachedToEngine(binding) }
        plugin.onMethodCall(MethodCall("check", null), Mockito.mock(MethodChannel.Result::class.java))
        // check also leaves this plugin's observer in the SDK.
        assertEquals(observerOf(plugin), awaitSdkObserver { it === observerOf(plugin) })

        plugin.onDetachedFromEngine(binding)

        assertEquals(FraudulentCheckObserver.empty, awaitSdkObserver { it == FraudulentCheckObserver.empty })
    }

    @Test
    fun engineDetach_ofAnEngineThatNeverUsedTheSdk_keepsTheOtherEnginesObserver() {
        val mainBinding = engineBinding()
        val mainPlugin = GatekeeperSdkFlutterPlugin(FakeMainThread()).also { it.onAttachedToEngine(mainBinding) }
        mainPlugin.onListen(null, Mockito.mock(EventChannel.EventSink::class.java))
        assertEquals(observerOf(mainPlugin), awaitSdkObserver { it === observerOf(mainPlugin) })
        // For example a background engine, which also gets the plugin through the GeneratedPluginRegistrant.
        val backgroundBinding = engineBinding()
        val backgroundPlugin = GatekeeperSdkFlutterPlugin(FakeMainThread()).also { it.onAttachedToEngine(backgroundBinding) }

        backgroundPlugin.onDetachedFromEngine(backgroundBinding)

        assertSdkObserverStays(observerOf(mainPlugin))
        mainPlugin.onDetachedFromEngine(mainBinding)
    }

    @Test
    fun cancel_afterAnotherEngineChecked_keepsThatEnginesObserver() {
        val firstBinding = engineBinding()
        val firstPlugin = GatekeeperSdkFlutterPlugin(FakeMainThread()).also { it.onAttachedToEngine(firstBinding) }
        firstPlugin.onListen(null, Mockito.mock(EventChannel.EventSink::class.java))
        assertEquals(observerOf(firstPlugin), awaitSdkObserver { it === observerOf(firstPlugin) })
        val secondBinding = engineBinding()
        val secondPlugin = GatekeeperSdkFlutterPlugin(FakeMainThread()).also { it.onAttachedToEngine(secondBinding) }
        secondPlugin.onMethodCall(MethodCall("check", null), Mockito.mock(MethodChannel.Result::class.java))
        assertEquals(observerOf(secondPlugin), awaitSdkObserver { it === observerOf(secondPlugin) })

        firstPlugin.onCancel(null)

        assertSdkObserverStays(observerOf(secondPlugin))
        secondPlugin.onDetachedFromEngine(secondBinding)
        firstPlugin.onDetachedFromEngine(firstBinding)
    }

    @Test
    fun engineDetach_thenANewEngine_canSubscribeAgain() {
        val oldBinding = engineBinding()
        val oldPlugin = GatekeeperSdkFlutterPlugin(FakeMainThread()).also { it.onAttachedToEngine(oldBinding) }
        oldPlugin.onListen(null, Mockito.mock(EventChannel.EventSink::class.java))
        assertEquals(observerOf(oldPlugin), awaitSdkObserver { it === observerOf(oldPlugin) })
        oldPlugin.onDetachedFromEngine(oldBinding)
        assertEquals(FraudulentCheckObserver.empty, awaitSdkObserver { it == FraudulentCheckObserver.empty })

        val newBinding = engineBinding()
        val newPlugin = GatekeeperSdkFlutterPlugin(FakeMainThread()).also { it.onAttachedToEngine(newBinding) }
        newPlugin.onListen(null, Mockito.mock(EventChannel.EventSink::class.java))

        assertEquals(observerOf(newPlugin), awaitSdkObserver { it === observerOf(newPlugin) })
        newPlugin.onDetachedFromEngine(newBinding)
    }

    @Test
    fun check_isAnsweredByTheSdkResult_andDoesNotTimeOutLater() {
        val (plugin, mainThread) = checkPlugin()
        val result = check(plugin)

        sdkSuccess(plugin, mainThread, Action.SAFE, "sig-1")
        mainThread.advanceBy(CHECK_TIMEOUT_MILLIS)

        Mockito.verify(result).success(actionMap("SAFE", "sig-1"))
        Mockito.verifyNoMoreInteractions(result)
        assertSdkCheckCalls(plugin, 1)
    }

    @Test
    fun checkWhileAnSdkCheckIsRunning_getsTheResultOfANewSdkCheck() {
        val (plugin, mainThread) = checkPlugin()
        val first = check(plugin)
        val second = check(plugin)
        // The second call does not call the SDK while the first SDK check is running.
        assertSdkCheckCalls(plugin, 1)

        sdkSuccess(plugin, mainThread, Action.SAFE, "sig-1")

        Mockito.verify(first).success(actionMap("SAFE", "sig-1"))
        Mockito.verifyNoInteractions(second)
        // The first result started the second round.
        assertSdkCheckCalls(plugin, 2)

        sdkSuccess(plugin, mainThread, Action.BLOCK, "sig-2")

        Mockito.verify(second).success(actionMap("BLOCK", "sig-2"))
        Mockito.verifyNoMoreInteractions(first)
    }

    @Test
    fun checksCalledDuringOneSdkCheck_shareTheNextSdkCheck() {
        val (plugin, mainThread) = checkPlugin()
        val first = check(plugin)
        val second = check(plugin)
        val third = check(plugin)

        sdkSuccess(plugin, mainThread, Action.SAFE, "sig-1")
        sdkSuccess(plugin, mainThread, Action.MONITOR, "sig-2")

        Mockito.verify(first).success(actionMap("SAFE", "sig-1"))
        Mockito.verify(second).success(actionMap("MONITOR", "sig-2"))
        Mockito.verify(third).success(actionMap("MONITOR", "sig-2"))
        Mockito.verifyNoMoreInteractions(first, second, third)
        // One SDK check for each round, and no third round.
        assertSdkCheckCalls(plugin, 2)
    }

    @Test
    fun check_afterTheRoundFinished_startsANewSdkCheckRightAway() {
        val (plugin, mainThread) = checkPlugin()
        val first = check(plugin)
        sdkSuccess(plugin, mainThread, Action.SAFE, "sig-1")

        val second = check(plugin)
        assertSdkCheckCalls(plugin, 2)
        sdkSuccess(plugin, mainThread, Action.BLOCK, "sig-2")

        Mockito.verify(first).success(actionMap("SAFE", "sig-1"))
        Mockito.verify(second).success(actionMap("BLOCK", "sig-2"))
    }

    @Test
    fun sdkError_answersTheCurrentRound_andStartsTheNextRound() {
        val (plugin, mainThread) = checkPlugin()
        val first = check(plugin)
        val second = check(plugin)

        observerOf(plugin).onError(Failure.NotInitialized)
        mainThread.runDue()

        Mockito.verify(first).error("NOT_INITIALIZED", "The SDK is not initialized", null)
        Mockito.verifyNoInteractions(second)
        assertSdkCheckCalls(plugin, 2)

        observerOf(plugin).onError(Failure.NetworkConnection)
        mainThread.runDue()

        Mockito.verify(second).error("NETWORK_CONNECTION", "Connection Error", null)
    }

    @Test
    fun check_withoutAnSdkResult_timesOutAfterTheCheckTimeout() {
        val (plugin, mainThread) = checkPlugin()
        val result = check(plugin)

        mainThread.advanceBy(CHECK_TIMEOUT_MILLIS - 1)
        Mockito.verifyNoInteractions(result)

        mainThread.advanceBy(1)
        Mockito.verify(result).error(CHECK_TIMEOUT, CHECK_TIMEOUT_MESSAGE, null)
    }

    @Test
    fun timeout_countsFromTheStartOfEachRound() {
        val (plugin, mainThread) = checkPlugin()
        val first = check(plugin)
        mainThread.advanceBy(CHECK_TIMEOUT_MILLIS - 1_000)
        val second = check(plugin)

        mainThread.advanceBy(1_000)

        Mockito.verify(first).error(CHECK_TIMEOUT, CHECK_TIMEOUT_MESSAGE, null)
        Mockito.verifyNoInteractions(second)
        // The timeout also starts the next round.
        assertSdkCheckCalls(plugin, 2)

        mainThread.advanceBy(CHECK_TIMEOUT_MILLIS - 1)
        Mockito.verifyNoInteractions(second)

        mainThread.advanceBy(1)
        Mockito.verify(second).error(CHECK_TIMEOUT, CHECK_TIMEOUT_MESSAGE, null)
    }

    @Test
    fun timeout_isNotSentToTheStream_andALateResultGoesOnlyToTheStream() {
        val (plugin, mainThread) = checkPlugin()
        val sink = Mockito.mock(EventChannel.EventSink::class.java)
        plugin.onListen(null, sink)
        val result = check(plugin)

        mainThread.advanceBy(CHECK_TIMEOUT_MILLIS)
        Mockito.verify(result).error(CHECK_TIMEOUT, CHECK_TIMEOUT_MESSAGE, null)
        Mockito.verifyNoInteractions(sink)

        sdkSuccess(plugin, mainThread, Action.SAFE, "late")

        Mockito.verify(sink).success(actionMap("SAFE", "late"))
        Mockito.verifyNoMoreInteractions(result)
    }

    @Test
    fun everySdkResult_isSentToTheStream() {
        val (plugin, mainThread) = checkPlugin()
        val sink = Mockito.mock(EventChannel.EventSink::class.java)
        plugin.onListen(null, sink)
        check(plugin)
        check(plugin)

        sdkSuccess(plugin, mainThread, Action.SAFE, "sig-1")
        sdkSuccess(plugin, mainThread, Action.BLOCK, "sig-2")

        Mockito.verify(sink).success(actionMap("SAFE", "sig-1"))
        Mockito.verify(sink).success(actionMap("BLOCK", "sig-2"))
    }

    @Test
    fun engineDetach_repliesToBothRounds_andStartsNoNewSdkCheck() {
        val binding = engineBinding()
        val mainThread = FakeMainThread()
        val plugin = GatekeeperSdkFlutterPlugin(mainThread).also { it.onAttachedToEngine(binding) }
        val first = check(plugin)
        val second = check(plugin)

        plugin.onDetachedFromEngine(binding)
        mainThread.advanceBy(CHECK_TIMEOUT_MILLIS)
        sdkSuccess(plugin, mainThread, Action.SAFE, "late")

        Mockito.verify(first).error("ENGINE_DETACHED", "The Flutter engine was detached", null)
        Mockito.verify(second).error("ENGINE_DETACHED", "The Flutter engine was detached", null)
        Mockito.verifyNoMoreInteractions(first, second)
        assertSdkCheckCalls(plugin, 1)
    }

    @Test
    fun cancel_whileTwoRoundsAreWaiting_unsubscribesAfterTheLastRound() {
        val (plugin, mainThread) = checkPlugin()
        plugin.onListen(null, Mockito.mock(EventChannel.EventSink::class.java))
        assertEquals(observerOf(plugin), awaitSdkObserver { it === observerOf(plugin) })
        check(plugin)
        check(plugin)
        plugin.onCancel(null)

        sdkSuccess(plugin, mainThread, Action.SAFE, "sig-1")
        // The second round is running, so the SDK must keep this plugin's observer.
        assertSdkObserverStays(observerOf(plugin))

        sdkSuccess(plugin, mainThread, Action.SAFE, "sig-2")
        assertEquals(FraudulentCheckObserver.empty, awaitSdkObserver { it == FraudulentCheckObserver.empty })
    }

    @Test
    fun checksOfTwoEnginesAtOnce_theFirstEngineTimesOutInsteadOfWaitingForever() {
        val (firstPlugin, firstMainThread) = checkPlugin()
        val (secondPlugin, secondMainThread) = checkPlugin()
        val first = check(firstPlugin)
        assertEquals(observerOf(firstPlugin), awaitSdkObserver { it === observerOf(firstPlugin) })
        // Each engine has its own plugin instance and its own rounds, so the second engine calls the SDK too,
        // and its observer replaces the first engine's.
        val second = check(secondPlugin)
        assertEquals(observerOf(secondPlugin), awaitSdkObserver { it === observerOf(secondPlugin) })

        // The SDK sends every result to the observer it holds when the result arrives.
        sdkObserver()!!.onSuccess(Action.SAFE, "sig-1")
        secondMainThread.runDue()
        sdkObserver()!!.onSuccess(Action.SAFE, "sig-2")
        secondMainThread.runDue()
        firstMainThread.advanceBy(CHECK_TIMEOUT_MILLIS)

        Mockito.verify(second).success(actionMap("SAFE", "sig-1"))
        Mockito.verify(first).error(CHECK_TIMEOUT, CHECK_TIMEOUT_MESSAGE, null)
        Mockito.verifyNoMoreInteractions(first, second)
    }

    private fun checkPlugin(): Pair<GatekeeperSdkFlutterPlugin, FakeMainThread> {
        val mainThread = FakeMainThread()
        val plugin = GatekeeperSdkFlutterPlugin(mainThread).also { it.onAttachedToEngine(engineBinding()) }
        return plugin to mainThread
    }

    private fun check(plugin: GatekeeperSdkFlutterPlugin): MethodChannel.Result {
        val result: MethodChannel.Result = Mockito.mock(MethodChannel.Result::class.java)
        plugin.onMethodCall(MethodCall("check", null), result)
        return result
    }

    // An SDK result, as the SDK delivers it: on the plugin's observer, then posted to the main thread.
    private fun sdkSuccess(
        plugin: GatekeeperSdkFlutterPlugin,
        mainThread: FakeMainThread,
        action: Action,
        signature: String,
    ) {
        observerOf(plugin).onSuccess(action, signature)
        mainThread.runDue()
    }

    private fun actionMap(
        type: String,
        signature: String,
    ) = mapOf("type" to type, "signature" to signature)

    // The SDK applies check in a coroutine, so wait for the calls, then check that no more arrive.
    private fun assertSdkCheckCalls(
        plugin: GatekeeperSdkFlutterPlugin,
        expected: Int,
    ) {
        val observer = observerOf(plugin)
        fun calls() = SdkObserverMirror.checkCalls.count { it === observer }
        val deadline = System.currentTimeMillis() + 2_000
        while (calls() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        Thread.sleep(300)
        assertEquals(expected, calls(), "SDK check calls")
    }

    // The observer a plugin gives to the SDK. It is private to the plugin, so reflection is used.
    private fun observerOf(plugin: GatekeeperSdkFlutterPlugin): FraudulentCheckObserver =
        GatekeeperSdkFlutterPlugin::class.java.getDeclaredField("sdkObserver")
            .apply { isAccessible = true }
            .get(plugin) as FraudulentCheckObserver

    // The SDK applies unsubscribe in a coroutine, so check for a while that the observer does not change.
    private fun assertSdkObserverStays(expected: FraudulentCheckObserver) {
        val deadline = System.currentTimeMillis() + 300
        while (System.currentTimeMillis() < deadline) {
            assertEquals(expected, sdkObserver())
            Thread.sleep(20)
        }
    }

    // The SDK's current observer, as SdkObserverMirror sees it.
    private fun sdkObserver(): FraudulentCheckObserver? = SdkObserverMirror.observer

    // Waits up to 2 seconds for the SDK's observer to match, then returns the last value seen.
    private fun awaitSdkObserver(matches: (FraudulentCheckObserver?) -> Boolean): FraudulentCheckObserver? {
        val deadline = System.currentTimeMillis() + 2_000
        var observer = sdkObserver()
        while (!matches(observer) && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            observer = sdkObserver()
        }
        return observer
    }

    private fun engineBinding(): FlutterPlugin.FlutterPluginBinding {
        val context = Mockito.mock(Context::class.java)
        val binding = Mockito.mock(FlutterPlugin.FlutterPluginBinding::class.java)
        Mockito.`when`(binding.applicationContext).thenReturn(context)
        Mockito.`when`(binding.binaryMessenger).thenReturn(Mockito.mock(BinaryMessenger::class.java))
        return binding
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

        return GatekeeperSdkFlutterPlugin(FakeMainThread()).also { it.onAttachedToEngine(binding) }
    }

    private fun assertMissingArgument(
        method: String,
        key: String,
    ) {
        val plugin = GatekeeperSdkFlutterPlugin(FakeMainThread())
        val result: MethodChannel.Result = Mockito.mock(MethodChannel.Result::class.java)

        plugin.onMethodCall(MethodCall(method, null), result)

        Mockito.verify(result).error("INVALID_ARGUMENTS", "Missing required $key parameter", null)
    }
}
