/// Pure-Dart Unicode Arabic contextual glyph shaper + visual BiDi orderer for `package:pdf`.
///
/// Maps Arabic base letters (U+0621..U+064A) and Alef-Lam ligatures into Arabic
/// Presentation Forms-B (U+FE70..U+FEFC) supported directly by `IBMPlexSansArabic`,
/// and reverses RTL Arabic segments into visual left-to-right order while preserving
/// LTR numbers, dates, and invoice/currency codes.
abstract final class ArabicPdfShaper {
  static const Map<int, List<int>> _forms = {
    // codePoint: [isolated, final, initial, medial] (0 = unsupported)
    0x0621: [0xFE80, 0xFE80, 0, 0], // HAMZA
    0x0622: [0xFE81, 0xFE82, 0, 0], // ALEF MADDA
    0x0623: [0xFE83, 0xFE84, 0, 0], // ALEF HAMZA ABOVE
    0x0624: [0xFE85, 0xFE86, 0, 0], // WAW HAMZA
    0x0625: [0xFE87, 0xFE88, 0, 0], // ALEF HAMZA BELOW
    0x0626: [0xFE89, 0xFE8A, 0xFE8B, 0xFE8C], // YEH HAMZA
    0x0627: [0xFE8D, 0xFE8E, 0, 0], // ALEF
    0x0628: [0xFE8F, 0xFE90, 0xFE91, 0xFE92], // BEH
    0x0629: [0xFE93, 0xFE94, 0, 0], // TEH MARBUTA
    0x062A: [0xFE95, 0xFE96, 0xFE97, 0xFE98], // TEH
    0x062B: [0xFE99, 0xFE9A, 0xFE9B, 0xFE9C], // THEH
    0x062C: [0xFE9D, 0xFE9E, 0xFE9F, 0xFEA0], // JEEM
    0x062D: [0xFEA1, 0xFEA2, 0xFEA3, 0xFEA4], // HAH
    0x062E: [0xFEA5, 0xFEA6, 0xFEA7, 0xFEA8], // KHAH
    0x062F: [0xFEA9, 0xFEAA, 0, 0], // DAL
    0x0630: [0xFEAB, 0xFEAC, 0, 0], // THAL
    0x0631: [0xFEAD, 0xFEAE, 0, 0], // REH
    0x0632: [0xFEAF, 0xFEB0, 0, 0], // ZAIN
    0x0633: [0xFEB1, 0xFEB2, 0xFEB3, 0xFEB4], // SEEN
    0x0634: [0xFEB5, 0xFEB6, 0xFEB7, 0xFEB8], // SHEEN
    0x0635: [0xFEB9, 0xFEBA, 0xFEBB, 0xFEBC], // SAD
    0x0636: [0xFEBD, 0xFEBE, 0xFEBF, 0xFEC0], // DAD
    0x0637: [0xFEC1, 0xFEC2, 0xFEC3, 0xFEC4], // TAH
    0x0638: [0xFEC5, 0xFEC6, 0xFEC7, 0xFEC8], // ZAH
    0x0639: [0xFEC9, 0xFECA, 0xFECB, 0xFECC], // AIN
    0x063A: [0xFECD, 0xFECE, 0xFECF, 0xFED0], // GHAIN
    0x0641: [0xFED1, 0xFED2, 0xFED3, 0xFED4], // FEH
    0x0642: [0xFED5, 0xFED6, 0xFED7, 0xFED8], // QAF
    0x0643: [0xFED9, 0xFEDA, 0xFEDB, 0xFEDC], // KAF
    0x0644: [0xFEDD, 0xFEDE, 0xFEDF, 0xFEE0], // LAM
    0x0645: [0xFEE1, 0xFEE2, 0xFEE3, 0xFEE4], // MEEM
    0x0646: [0xFEE5, 0xFEE6, 0xFEE7, 0xFEE8], // NOON
    0x0647: [0xFEE9, 0xFEEA, 0xFEEB, 0xFEEC], // HEH
    0x0648: [0xFEED, 0xFEEE, 0, 0], // WAW
    0x0649: [0xFEEF, 0xFEF0, 0, 0], // ALEF MAKSURA
    0x064A: [0xFEF1, 0xFEF2, 0xFEF3, 0xFEF4], // YEH
    0x0640: [0x0640, 0x0640, 0x0640, 0x0640], // TATWEEL
  };

  static const Map<int, List<int>> _lamAlefLigatures = {
    0x0622: [0xFEF5, 0xFEF6], // LAM + ALEF MADDA
    0x0623: [0xFEF7, 0xFEF8], // LAM + ALEF HAMZA ABOVE
    0x0625: [0xFEF9, 0xFEFA], // LAM + ALEF HAMZA BELOW
    0x0627: [0xFEFB, 0xFEFC], // LAM + ALEF
  };

  static bool _isTashkeel(int cp) =>
      (cp >= 0x064B && cp <= 0x065F) || cp == 0x0670;

  static bool _connectsLeft(int cp) {
    final f = _forms[cp];
    return f != null && f[2] != 0;
  }

  static bool _connectsRight(int cp) {
    final f = _forms[cp];
    return f != null && f[1] != 0;
  }

  static bool _hasArabic(String s) =>
      s.runes.any((r) => (r >= 0x0600 && r <= 0x06FF) || (r >= 0xFE70 && r <= 0xFEFF));

  /// Shapes Arabic characters into Presentation Forms and reorders mixed RTL/LTR text visually.
  static String shapeForPdf(String input) {
    if (input.isEmpty || !_hasArabic(input)) return input;
    return input.split('\n').map(_shapeLine).join('\n');
  }

  static String _shapeLine(String line) {
    if (!_hasArabic(line)) return line;
    final runes = line.runes.where((r) => !_isTashkeel(r)).toList();
    final shaped = <int>[];

    for (int i = 0; i < runes.length; i++) {
      final curr = runes[i];
      if (!_forms.containsKey(curr)) {
        shaped.add(curr);
        continue;
      }

      // Check Lam + Alef ligature
      if (curr == 0x0644 && i + 1 < runes.length && _lamAlefLigatures.containsKey(runes[i + 1])) {
        final prevConnects = i > 0 && _connectsLeft(runes[i - 1]);
        final lig = _lamAlefLigatures[runes[i + 1]]!;
        shaped.add(prevConnects ? lig[1] : lig[0]);
        i++; // skip paired alef
        continue;
      }

      final prevConnects = i > 0 && _connectsLeft(runes[i - 1]);
      final nextConnects = i + 1 < runes.length && _connectsRight(runes[i + 1]);
      final forms = _forms[curr]!;

      final int glyph;
      if (prevConnects && nextConnects && forms[3] != 0) {
        glyph = forms[3]; // medial
      } else if (prevConnects && forms[1] != 0) {
        glyph = forms[1]; // final
      } else if (nextConnects && forms[2] != 0) {
        glyph = forms[2]; // initial
      } else {
        glyph = forms[0]; // isolated
      }
      shaped.add(glyph);
    }

    // Visual BiDi reordering for RTL base line:
    // Segment runs into LTR tokens (ASCII letters/digits/symbols inside numbers) vs RTL/neutral tokens.
    return _reorderRtlVisual(shaped);
  }

  static bool _isLtrChar(int cp) {
    return (cp >= 0x30 && cp <= 0x39) || // ASCII 0-9
        (cp >= 0x41 && cp <= 0x5A) || // A-Z
        (cp >= 0x61 && cp <= 0x7A) || // a-z
        (cp >= 0x0660 && cp <= 0x0669); // Arabic-Indic digits
  }

  static bool _isLtrConnector(int cp) {
    return cp == 0x2E || // .
        cp == 0x2C || // ,
        cp == 0x2D || // -
        cp == 0x2F || // /
        cp == 0x3A || // :
        cp == 0x25;   // %
  }

  static int _mirrorBracket(int cp) => switch (cp) {
        0x28 => 0x29,
        0x29 => 0x28,
        0x5B => 0x5D,
        0x5D => 0x5B,
        _ => cp,
      };

  static String _reorderRtlVisual(List<int> shapedRunes) {
    final segments = <({bool isLtr, List<int> cps})>[];
    int i = 0;
    while (i < shapedRunes.length) {
      if (_isLtrChar(shapedRunes[i])) {
        final start = i;
        i++;
        while (i < shapedRunes.length) {
          final cp = shapedRunes[i];
          if (_isLtrChar(cp)) {
            i++;
          } else if ((_isLtrConnector(cp) || cp == 0x20) &&
              i + 1 < shapedRunes.length &&
              _isLtrChar(shapedRunes[i + 1])) {
            // Keep spaces between Latin words ("QAT LEDGER") or separators in numbers ("15,500.00")
            i++;
          } else {
            break;
          }
        }
        segments.add((isLtr: true, cps: shapedRunes.sublist(start, i)));
      } else {
        final start = i;
        while (i < shapedRunes.length && !_isLtrChar(shapedRunes[i])) {
          i++;
        }
        segments.add((
          isLtr: false,
          cps: shapedRunes.sublist(start, i).map(_mirrorBracket).toList(),
        ));
      }
    }

    // Reverse overall segment order (RTL base direction) and reverse characters inside RTL segments
    final visual = StringBuffer();
    for (final seg in segments.reversed) {
      if (seg.isLtr) {
        visual.write(String.fromCharCodes(seg.cps));
      } else {
        visual.write(String.fromCharCodes(seg.cps.reversed));
      }
    }
    return visual.toString();
  }
}
