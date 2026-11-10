// Flutter integration test: runs inside the example app on a real device or
// simulator, so it can reach the native side of the plugin.
//
// For more information about Flutter integration tests, please see
// https://flutter.dev/to/integration-testing

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gatekeeper_sdk_flutter/gatekeeper_sdk_flutter.dart';
import 'package:integration_test/integration_test.dart';

import 'package:gatekeeper_sdk_flutter_example/main.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('example app starts', (WidgetTester tester) async {
    await tester.pumpWidget(const MyApp());

    expect(find.widgetWithText(ElevatedButton, 'Check Now'), findsOneWidget);
  });

  // Goes through the MethodChannel, the native bridge and the SDK, and back.
  // Needs no credentials and no network, because initialize is never called.
  testWidgets(
    'check before initialize throws notInitialized',
    (WidgetTester tester) async {
      await expectLater(
        SafeSDK.check().timeout(const Duration(seconds: 10)),
        throwsA(
          isA<SafeSDKException>().having((e) => e.code, 'code', SafeSDKErrorCode.notInitialized),
        ),
      );
    },
    skip: defaultTargetPlatform != TargetPlatform.iOS, // The Android bridge is not implemented yet.
  );
}
