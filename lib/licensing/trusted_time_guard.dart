import 'package:shared_preferences/shared_preferences.dart';

/// Monotonic offline clock guard preventing trivial date-rollback license extension.
class TrustedTimeGuard {
  TrustedTimeGuard({
    SharedPreferences? prefs,
    this.maxAllowedRollbackMs = Duration.millisecondsPerHour * 6,
  }) : _prefs = prefs;

  static const String _prefMaxSeenMs = 'qat_license_max_seen_epoch_ms';
  final SharedPreferences? _prefs;
  final int maxAllowedRollbackMs;
  int _inMemoryMaxSeenMs = 0;

  Future<ClockCheckResult> evaluateNow(
    int nowEpochMs, {
    int? anchorEpochMs,
    int? overrideMaxRollbackMs,
  }) async {
    int storedMax = _inMemoryMaxSeenMs;
    if (_prefs != null) {
      storedMax = _prefs.getInt(_prefMaxSeenMs) ?? _inMemoryMaxSeenMs;
    }
    if (anchorEpochMs != null && anchorEpochMs > storedMax) {
      storedMax = anchorEpochMs;
    }

    final allowed = overrideMaxRollbackMs ?? maxAllowedRollbackMs;
    if (storedMax > 0 && nowEpochMs + allowed < storedMax) {
      return ClockCheckResult(
        trusted: false,
        effectiveNowMs: storedMax,
        rollbackDeltaMs: storedMax - nowEpochMs,
      );
    }

    final updated = nowEpochMs > storedMax ? nowEpochMs : storedMax;
    _inMemoryMaxSeenMs = updated;
    if (_prefs != null && updated > (_prefs.getInt(_prefMaxSeenMs) ?? 0)) {
      await _prefs.setInt(_prefMaxSeenMs, updated);
    }

    return ClockCheckResult(
      trusted: true,
      effectiveNowMs: updated,
      rollbackDeltaMs: 0,
    );
  }
}

class ClockCheckResult {
  const ClockCheckResult({
    required this.trusted,
    required this.effectiveNowMs,
    required this.rollbackDeltaMs,
  });

  final bool trusted;
  final int effectiveNowMs;
  final int rollbackDeltaMs;
}
