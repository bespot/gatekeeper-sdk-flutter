// Flutter integration test: runs inside the example app on a real device or
// simulator, so it can reach the native side of the plugin.
//
// For more information about Flutter integration tests, please see
// https://flutter.dev/to/integration-testing

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

import 'package:gatekeeper_sdk_flutter_example/main.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  // TODO: Once the native bridges exist, add tests that call SafeSDK on the
  // device (e.g. `SafeSDK.check()` before initialization → NOT_INITIALIZED).
  testWidgets('example app starts', (WidgetTester tester) async {
    await tester.pumpWidget(const MyApp());

    expect(find.widgetWithText(ElevatedButton, 'Check Now'), findsOneWidget);
  });
}
