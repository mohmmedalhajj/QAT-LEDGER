import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:qat_ledger/app.dart';
import 'package:qat_ledger/state/app_controller.dart';

void registerFlavorSmokeTest({
  required String expectedEdition,
  required String expectedApplicationId,
  required Future<void> Function() launchApp,
}) {
  testWidgets('$expectedEdition Android flavor boots, onboards and stays isolated',
      (tester) async {
    await launchApp();

    AppController? controller;
    for (var attempt = 0; attempt < 90; attempt++) {
      await tester.pump(const Duration(seconds: 1));
      final appFinder = find.byType(QatLedgerApp);
      if (appFinder.evaluate().isNotEmpty) {
        controller = Provider.of<AppController>(
          tester.element(appFinder),
          listen: false,
        );
        if (controller.initialized) break;
      }
    }

    expect(controller, isNotNull, reason: 'The flavor app did not mount.');
    expect(controller!.initialized, isTrue, reason: 'SQLite/platform startup did not finish.');
    expect(controller.editionConfig.edition, expectedEdition);
    expect(controller.editionConfig.applicationId, expectedApplicationId);
    expect(controller.needsOnboarding, isTrue,
        reason: 'CI emulator is wiped before running these fresh-install checks.');
    expect(find.byKey(const Key('onboarding_username')), findsOneWidget);
    expect(find.byKey(const Key('onboarding_password')), findsOneWidget);
    expect(await controller.login('admin', '123456'), isFalse,
        reason: 'A fresh installation must not accept default credentials.');

    const syntheticInputs = <String, String>{
      'onboarding_agency_name': 'Arena Synthetic Agency',
      'onboarding_owner_name': 'QA Owner',
      'onboarding_username': 'qa_owner',
      'onboarding_password': 'SyntheticPass#2026',
      'onboarding_confirm_password': 'SyntheticPass#2026',
    };
    for (final entry in syntheticInputs.entries) {
      final field = find.byKey(Key(entry.key));
      await tester.ensureVisible(field);
      await tester.enterText(field, entry.value);
    }

    final submit = find.byKey(const Key('onboarding_submit_button'));
    await tester.ensureVisible(submit);
    await tester.tap(submit);
    for (var attempt = 0; attempt < 60 && !controller.isLoggedIn; attempt++) {
      await tester.pump(const Duration(milliseconds: 500));
    }

    expect(controller.isLoggedIn, isTrue,
        reason: 'Synthetic first-run setup did not create the owner session.');
    expect(controller.needsOnboarding, isFalse);

    controller.setSection(4, subTab: 3);
    await tester.pump(const Duration(seconds: 1));
    final developerConsole = find.text('وحدة تحكم المطور (Developer Edition)');
    if (expectedEdition == 'developer') {
      expect(developerConsole, findsOneWidget);
    } else {
      expect(developerConsole, findsNothing,
          reason: 'Customer must not expose the Developer console.');
    }
    expect(tester.takeException(), isNull);
  });
}
