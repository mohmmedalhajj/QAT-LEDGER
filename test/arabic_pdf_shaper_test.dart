import 'package:flutter_test/flutter_test.dart';
import 'package:qat_ledger/services/arabic_pdf_shaper.dart';

import '_ci_diag_helper.dart';

void main() {
  test('shapes Arabic letters to Presentation Forms and preserves LTR numbers/codes', () {
    runSyncWithCiDiag('arabic_pdf_shaper_test#1', () {
      final shaped = ArabicPdfShaper.shapeForPdf('فاتورة INV-2026 بقيمة 15,500 ر.ي');
      expect(shaped.contains('INV-2026'), true);
      expect(shaped.contains('15,500'), true);
      // Presentation forms B codepoints are in U+FE70..U+FEFC
      expect(shaped.runes.any((r) => r >= 0xFE70 && r <= 0xFEFC), true);
    });
  });

  test('applies Lam-Alef ligatures properly', () {
    runSyncWithCiDiag('arabic_pdf_shaper_test#2', () {
      final shaped = ArabicPdfShaper.shapeForPdf('لا توجد ديون');
      expect(shaped.runes.contains(0xFEFB), true); // Isolated Lam-Alef
    });
  });
}
