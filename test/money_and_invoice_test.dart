import 'package:flutter_test/flutter_test.dart';
import 'package:qat_ledger/core/money.dart';

void main() {
  group('Money & Quantity Integer Math', () {
    test('parses English and Arabic-Indic decimals without float drift', () {
      expect(Money.parseMinorUnits('15000.50'), 1500050);
      expect(Money.parseMinorUnits('١٥٬٠٠٠٫٧٥'), 1500075);
      expect(Money.parseMinorUnits('100.125'), 10013); // half-up
      expect(Money.parseMinorUnits('100.124'), 10012);
      expect(QuantityMath.parseMilli('2.375'), 2375);
      expect(QuantityMath.formatMilli(2375), '2.375');
      expect(QuantityMath.formatMilli(2000), '2');
    });

    test('computes invoice totals with discount and tax accurately', () {
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

    test('currency policy never mixes currencies unless explicit display rate given', () {
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
}
