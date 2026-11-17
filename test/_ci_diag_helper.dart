import 'dart:io';

void emitCiAnnotation(String level, String title, String message) {
  final safeTitle = title.replaceAll(RegExp(r'[\r\n:,]'), ' ').trim();
  const chunkSize = 3500;
  final total = message.isEmpty ? 1 : ((message.length + chunkSize - 1) ~/ chunkSize);
  for (int i = 0; i < total && i < 8; i++) {
    final start = i * chunkSize;
    final end = (start + chunkSize) > message.length ? message.length : (start + chunkSize);
    final chunk = message.isEmpty ? '(empty)' : message.substring(start, end);
    final escaped = chunk
        .replaceAll('%', '%25')
        .replaceAll('\r', '%0D')
        .replaceAll('\n', '%0A');
    final suffix = total > 1 ? ' (${i + 1}/$total)' : '';
    print('::$level title=$safeTitle$suffix::$escaped');
  }
}

Future<void> runWithCiDiag(
  String testLabel,
  Future<void> Function() body,
) async {
  try {
    await body();
  } catch (e, st) {
    emitCiAnnotation('error', testLabel, '$e\n$st');
    rethrow;
  }
}

void runSyncWithCiDiag(
  String testLabel,
  void Function() body,
) {
  try {
    body();
  } catch (e, st) {
    emitCiAnnotation('error', testLabel, '$e\n$st');
    rethrow;
  }
}

void emitCiEnvSnapshot() {
  final buf = StringBuffer()
    ..writeln('Dart: ${Platform.version}')
    ..writeln('OS: ${Platform.operatingSystem} ${Platform.operatingSystemVersion}');
  final lockFile = File('pubspec.lock');
  if (lockFile.existsSync()) {
    final lines = lockFile.readAsLinesSync();
    String? pkg;
    const wanted = <String>{
      'pdf',
      'printing',
      'material_ui',
      'archive',
      'excel',
      'image',
      'cryptography',
      'sqflite',
      'sqflite_common',
      'sqflite_common_ffi',
      'sqlite3',
      'shared_preferences',
      'qr_flutter',
      'provider',
      'intl',
      'flutter_lints',
    };
    for (final line in lines) {
      final m = RegExp(r'^  ([a-zA-Z0-9_]+):\s*$').firstMatch(line);
      if (m != null) {
        pkg = m.group(1);
      } else if (pkg != null && line.startsWith('    version: ')) {
        if (wanted.contains(pkg)) {
          buf.writeln('$pkg: ${line.substring('    version: '.length).trim()}');
        }
        pkg = null;
      }
    }
  } else {
    buf.writeln('pubspec.lock: NOT FOUND in ${Directory.current.path}');
  }
  emitCiAnnotation('notice', 'CI Environment Snapshot', buf.toString().trim());
}
