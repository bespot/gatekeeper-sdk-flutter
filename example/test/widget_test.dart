import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:gatekeeper_sdk_flutter_example/main.dart';

void main() {
  testWidgets('shows the same controls as the Capacitor example', (WidgetTester tester) async {
    await tester.pumpWidget(const MyApp());

    for (final label in [
      'Enable Logging',
      'Disable Logging',
      'Initialize with Access Token',
      'Update Access Token',
      'Initialize',
      'Check Now',
      'Subscribe',
      'Unsubscribe',
      'UserId 1',
      'UserId 2',
      'UserId 3',
      'Ask for location permissions',
      'Ask for storage permissions (Android)',
      'Ask for media-audio permissions (Android 13+)',
    ]) {
      expect(find.widgetWithText(ElevatedButton, label), findsOneWidget, reason: label);
    }
    expect(find.text('Enter access token here'), findsOneWidget);
    expect(find.text('Action: No action result yet', findRichText: true), findsOneWidget);
  });

  testWidgets('starts with Update Access Token and Unsubscribe disabled', (WidgetTester tester) async {
    await tester.pumpWidget(const MyApp());

    bool isEnabled(String label) => tester.widget<ElevatedButton>(find.widgetWithText(ElevatedButton, label)).enabled;

    expect(isEnabled('Update Access Token'), isFalse);
    expect(isEnabled('Unsubscribe'), isFalse);
    expect(isEnabled('Initialize with Access Token'), isTrue);
    expect(isEnabled('Subscribe'), isTrue);
  });
}
