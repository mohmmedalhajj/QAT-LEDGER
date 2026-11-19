import 'dart:io';

import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

/// Persists picked agency logo images inside permanent application storage so
/// cached gallery picks never disappear after OS cache cleanup.
abstract final class LogoStorage {
  static Future<String> persistLogoFile(
    String sourcePath, {
    String? customDirectoryPath,
  }) async {
    final clean = sourcePath.trim();
    if (clean.isEmpty) {
      throw ArgumentError('مسار الشعار المختار غير صالح.');
    }
    final src = File(clean);
    if (!src.existsSync()) {
      throw StateError('ملف الصورة المختار غير موجود أو تعذر الوصول إليه.');
    }
    final bytes = await src.readAsBytes();
    if (bytes.isEmpty) {
      throw StateError('ملف الصورة المختار فارغ أو تالف.');
    }

    final baseDir = customDirectoryPath ?? (await getApplicationSupportDirectory()).path;
    final logoDir = Directory(p.join(baseDir, 'agency_assets'));
    await logoDir.create(recursive: true);

    final ext = p.extension(clean).toLowerCase();
    final safeExt = (ext == '.png' || ext == '.jpg' || ext == '.jpeg' || ext == '.webp')
        ? ext
        : '.png';
    final destPath = p.join(
      logoDir.path,
      'agency_logo_${DateTime.now().millisecondsSinceEpoch}$safeExt',
    );
    final tmpPath = '$destPath.tmp';
    await File(tmpPath).writeAsBytes(bytes, flush: true);
    await File(tmpPath).rename(destPath);

    // Clean up older logo copies inside agency_assets
    for (final entity in logoDir.listSync()) {
      if (entity is File &&
          entity.path != destPath &&
          p.basename(entity.path).startsWith('agency_logo_')) {
        try {
          await entity.delete();
        } catch (_) {}
      }
    }
    return destPath;
  }
}
