import 'dart:async';

import 'package:flutter/material.dart';
import 'package:gatekeeper_sdk_flutter/gatekeeper_sdk_flutter.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(debugShowCheckedModeBanner: false, home: SafeSDKDemoPage());
  }
}

/// Mirrors the Gatekeeper Capacitor example app.
class SafeSDKDemoPage extends StatefulWidget {
  const SafeSDKDemoPage({super.key});

  @override
  State<SafeSDKDemoPage> createState() => _SafeSDKDemoPageState();
}

class _SafeSDKDemoPageState extends State<SafeSDKDemoPage> {
  static const _green = Color(0xFF28A745);
  static const _red = Color(0xFFFA120A);
  static const _blue = Color(0xFF007BFF);
  static const _teal = Color(0xFF17A2B8);
  static const _grey = Color(0xFF808080);
  static const _darkTeal = Color(0xFF1E3F4A);

  final _tokenController = TextEditingController();
  StreamSubscription<SafeSDKAction>? _subscription;

  bool _initialized = false;
  bool _initializedWithToken = false;
  bool _initializedWithOAuth = false;
  String _action = 'No action result yet';
  String _signature = '-';

  @override
  void dispose() {
    _subscription?.cancel();
    _tokenController.dispose();
    super.dispose();
  }

  void _showResult(String action, [String signature = '-']) {
    if (!mounted) return;
    setState(() {
      _action = action;
      _signature = signature;
    });
  }

  String _errorText(Object error) => error is SafeSDKException
      ? '${error.code.wireValue}${error.message != null ? ': ${error.message}' : ''}'
      : error.toString();

  Future<void> _setLogging(bool enabled) async {
    try {
      await SafeSDK.enableLogging(enabled);
      debugPrint('Logging ${enabled ? 'enabled' : 'disabled'}');
    } catch (e) {
      debugPrint('SafeSDK.enableLogging failed: $e');
    }
  }

  Future<void> _initializeWithAccessToken() async {
    _showResult('No action result yet');
    try {
      await SafeSDK.initializeWithAccessToken(_tokenController.text.trim(), debugLoggingEnabled: true);
      setState(() {
        _initialized = true;
        _initializedWithToken = true;
      });
      debugPrint('Initialized with access token done');
    } catch (e) {
      debugPrint('SafeSDK.initializeWithAccessToken failed: $e');
      _showResult(_errorText(e));
    }
  }

  Future<void> _updateAccessToken() async {
    try {
      await SafeSDK.setAccessToken(_tokenController.text.trim());
      _showResult('Access token updated');
      debugPrint('Access token updated');
    } catch (e) {
      debugPrint('SafeSDK.setAccessToken failed: $e');
      _showResult(_errorText(e));
    }
  }

  Future<void> _initialize() async {
    _showResult('No action result yet');
    try {
      await SafeSDK.initialize(debugLoggingEnabled: true);
      setState(() {
        _initialized = true;
        _initializedWithOAuth = true;
      });
      debugPrint('Initialized done');
    } catch (e) {
      debugPrint('SafeSDK.initialize failed: $e');
      _showResult(_errorText(e));
    }
  }

  Future<void> _check() async {
    try {
      final action = await SafeSDK.check();
      debugPrint('Check action: ${action.type.wireValue} ${action.signature}');
      _showResult(action.type.wireValue, action.signature);
    } catch (e) {
      debugPrint('SafeSDK.check failed: $e');
      _showResult(_errorText(e));
    }
  }

  void _subscribe() {
    setState(() {
      _subscription = SafeSDK.subscribe().listen(
        (action) => _showResult(action.type.wireValue, action.signature),
        onError: (Object e) {
          debugPrint('SafeSDK subscribe failed: $e');
          _showResult(_errorText(e));
          _unsubscribe();
        },
      );
    });
  }

  void _unsubscribe() {
    // Cancelling the stream triggers the native unsubscribe.
    _subscription?.cancel();
    if (!mounted) return;
    setState(() => _subscription = null);
    debugPrint('SafeSDK.unsubscribe is done!');
  }

  Future<void> _setUserId(String userId) async {
    try {
      await SafeSDK.setUserId(userId);
      debugPrint('Set userId to: $userId');
    } catch (e) {
      debugPrint('SafeSDK.setUserId failed: $e');
    }
  }

  Future<void> _askForPermission(String name, Future<void> Function() request) async {
    try {
      await request();
      debugPrint('$name requested');
    } catch (e) {
      debugPrint('SafeSDK.askFor$name failed: $e');
    }
  }

  Widget _button(String label, Color color, VoidCallback? onPressed) {
    return SizedBox(
      height: 38,
      child: ElevatedButton(
        onPressed: onPressed,
        style: ElevatedButton.styleFrom(
          backgroundColor: color,
          foregroundColor: Colors.white,
          disabledBackgroundColor: const Color(0xFFD0D0D0),
          disabledForegroundColor: Colors.white,
          elevation: 0,
          padding: const EdgeInsets.symmetric(horizontal: 8),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        ),
        child: Text(label, textAlign: TextAlign.center),
      ),
    );
  }

  Widget _row(List<Widget> buttons) {
    return Row(
      children: [
        for (var i = 0; i < buttons.length; i++) ...[if (i > 0) const SizedBox(width: 10), Expanded(child: buttons[i])],
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    const gap = SizedBox(height: 16);
    const smallGap = SizedBox(height: 8);
    final isSubscribed = _subscription != null;

    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(23, 9, 23, 23),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _row([
                _button('Enable Logging', _green, () => _setLogging(true)),
                _button('Disable Logging', _red, () => _setLogging(false)),
              ]),
              gap,
              TextField(
                controller: _tokenController,
                enabled: !_initializedWithOAuth,
                autocorrect: false,
                enableSuggestions: false,
                decoration: InputDecoration(
                  hintText: 'Enter access token here',
                  isDense: true,
                  contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
                ),
              ),
              smallGap,
              _button('Initialize with Access Token', _blue, _initialized ? null : _initializeWithAccessToken),
              gap,
              _button('Update Access Token', _teal, _initializedWithToken ? _updateAccessToken : null),
              gap,
              _button('Initialize', _blue, _initialized ? null : _initialize),
              gap,
              _button('Check Now', _blue, _check),
              gap,
              _row([
                _button('Subscribe', _green, isSubscribed ? null : _subscribe),
                _button('Unsubscribe', _red, isSubscribed ? _unsubscribe : null),
              ]),
              gap,
              _row([
                for (final userId in ['UserId 1', 'UserId 2', 'UserId 3'])
                  _button(userId, _grey, () => _setUserId(userId)),
              ]),
              gap,
              _button(
                'Ask for location permissions',
                _darkTeal,
                () => _askForPermission('LocationPermissions', SafeSDK.askForLocationPermissions),
              ),
              smallGap,
              _button(
                'Ask for storage permissions (Android)',
                _darkTeal,
                () => _askForPermission('StoragePermissions', SafeSDK.askForStoragePermissions),
              ),
              smallGap,
              _button(
                'Ask for media-audio permissions (Android 13+)',
                _darkTeal,
                () => _askForPermission('MediaAudioPermissions', SafeSDK.askForMediaAudioPermissions),
              ),
              const SizedBox(height: 32),
              Text.rich(
                TextSpan(
                  text: 'Action: ',
                  children: [
                    TextSpan(
                      text: _action,
                      style: const TextStyle(fontWeight: FontWeight.bold),
                    ),
                  ],
                ),
              ),
              Text.rich(
                TextSpan(
                  text: 'Signature: ',
                  children: [
                    TextSpan(
                      text: _signature,
                      style: const TextStyle(fontWeight: FontWeight.bold),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
