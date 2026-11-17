import 'package:flutter_test/flutter_test.dart';
import 'package:qat_ledger/core/money.dart';

import '_ci_diag_helper.dart';

void main() {
  setUpAll(emitCiEnvSnapshot);

  group('Money & Quantity Integer Math', () {
    test('parses English and Arabic-Indic decimals without float drift', () {
      runSyncWithCiDiag('money_and_invoice_test#1', () {
        expect(Money.parseMinorUnits('15000.50'), 1500050);
        expect(Money.parseMinorUnits('١٥٬٠٠٠٫٧٥'), 1500075);
        expect(Money.parseMinorUnits('100.125'), 10013); // half-up
        expect(Money.parseMinorUnits('100.124'), 10012);
        expect(QuantityMath.parseMilli('2.375'), 2375);
        expect(QuantityMath.formatMilli(2375), '2.375');
        expect(QuantityMath.formatMilli(2000), '2');
      });
    });

    test('computes invoice totals with discount and tax accurately', () {
      runSyncWithCiDiag('money_and_invoice_test#2', () {
        final line1 = InvoiceMath.lineTotalMinor(qtyMilli: 2500, unitPriceMinor: 400000); // 2.5 * 4000 = 10000.00
        expect(line1, 1000000);

        final totals = InvoiceMath.computeTotals(
          lineTotalsMinor: [line1],
          discountMinor: 50000, // -500.00
          taxBps: 500, // 5.00% on 9500.00 = 475.00
          paidMinor: 400000, // 4000.00
        );

        expect(totals.subtotalMinor, 1000000);
        expect(totals.discountMinor, 50000);
        expect(totals.taxAmountMinor, 47500);
        expect(totals.totalAmountMinor, 997500);
        expect(totals.paidAmountMinor, 400000);
        expect(totals.remainingAmountMinor, 597500);
      });
    });

    test('currency policy never mixes currencies unless explicit display rate given', () {
      runSyncWithCiDiag('money_and_invoice_test#3', () {
        const noRate = CurrencyPolicy(mode: CurrencyMode.dualCurrency);
        expect(noRate.hasExplicitDisplayRate, false);
        expect(noRate.convertSarMinorToYerPreview(10000), isNull);

        const withRate = CurrencyPolicy(
          mode: CurrencyMode.dualCurrency,
          userDisplaySarToYerMilli: 140000, // 140.000
        );
        expect(withRate.hasExplicitDisplayRate, true);
        expect(withRate.convertSarMinorToYerPreview(10000), 1400000); // 100 SAR -> 14,000 YER
      });
    });

    test('convertSarMinorToYerPreview preserves BigInt precision, half-up rounding, and int64 bounds', () {
      runSyncWithCiDiag('money_and_invoice_test#4', () {
        const ratePolicy = CurrencyPolicy(
          mode: CurrencyMode.dualCurrency,
          userDisplaySarToYerMilli: 140001, // 140.001
        );

        // Exact BigInt intermediate (> 2^63) dividing cleanly back into int64:
        // 10^14 * 140001 = 1.40001 * 10^19 (> int64_max 9.22 * 10^18), / 1000 = 1.40001 * 10^16 (valid int64)
        const bigSarMinor = 100000000000000; // 10^14
        expect(
          ratePolicy.convertSarMinorToYerPreview(bigSarMinor),
          14000100000000000,
        );

        // Half-up rounding positive & negative
        const halfRate = CurrencyPolicy(
          mode: CurrencyMode.dualCurrency,
          userDisplaySarToYerMilli: 1500, // 1.500
        );
        // 1 * 1500 / 1000 = 1.5 -> 2; 3 * 1500 / 1000 = 4.5 -> 5
        expect(halfRate.convertSarMinorToYerPreview(1), 2);
        expect(halfRate.convertSarMinorToYerPreview(3), 5);
        // Negative symmetric half-up: -1 * 1500 / 1000 = -1.5 -> -2; -3 * 1500 / 1000 = -4.5 -> -5
        expect(halfRate.convertSarMinorToYerPreview(-1), -2);
        expect(halfRate.convertSarMinorToYerPreview(-3), -5);

        const subHalfRate = CurrencyPolicy(
          mode: CurrencyMode.dualCurrency,
          userDisplaySarToYerMilli: 1499, // 1.499
        );
        expect(subHalfRate.convertSarMinorToYerPreview(1), 1);
        expect(subHalfRate.convertSarMinorToYerPreview(-1), -1);

        // Overflow beyond signed 64-bit integer returns null safely instead of silent clamp
        const largeRate = CurrencyPolicy(
          mode: CurrencyMode.dualCurrency,
          userDisplaySarToYerMilli: 140000,
        );
        expect(largeRate.convertSarMinorToYerPreview(1 << 60), isNull);
        expect(largeRate.convertSarMinorToYerPreview(-(1 << 60)), isNull);
      });
    });

    test('InvoiceMath.lineTotalMinor handles BigInt intermediates and half-up rounding cleanly', () {
      runSyncWithCiDiag('money_and_invoice_test#5', () {
        expect(InvoiceMath.lineTotalMinor(qtyMilli: 1, unitPriceMinor: 1500), 2);
        expect(InvoiceMath.lineTotalMinor(qtyMilli: -1, unitPriceMinor: 1500), -2);
        expect(
          InvoiceMath.lineTotalMinor(
            qtyMilli: 100000000000000, // 10^14
            unitPriceMinor: 140000,
          ),
          14000000000000000,
        );
      });
    });
  });
}
