import 'dart:typed_data';

import 'package:image/image.dart' as img;
import 'package:pdf/pdf.dart';
import 'package:printing/printing.dart';

import '../core/platform_bridge.dart';
import '../data/models.dart';
import 'pdf_export_service.dart';

/// Builds crisp 58mm/80mm thermal receipts by rasterizing Arabic PDF layouts
/// into ESC/POS `GS v 0` monochrome bitmap packets so Arabic renders accurately
/// on thermal Bluetooth printers regardless of hardware codepage support.
class EscPosService {
  EscPosService._();
  static final EscPosService instance = EscPosService._();

  /// Encodes an RGBA/grayscale image into ESC/POS `GS v 0` raster command + paper feed/cut.
  static Uint8List encodeImageToEscPosRaster(img.Image source, {int targetWidthPx = 384}) {
    final resized = source.width == targetWidthPx
        ? source
        : img.copyResize(source, width: targetWidthPx);
    final widthBytes = (resized.width + 7) ~/ 8;
    final height = resized.height;

    final out = BytesBuilder();
    // Initialize printer: ESC @
    out.add(const [0x1B, 0x40]);
    // GS v 0 m xL xH yL yH
    out.add([
      0x1D,
      0x76,
      0x30,
      0x00,
      widthBytes & 0xFF,
      (widthBytes >> 8) & 0xFF,
      height & 0xFF,
      (height >> 8) & 0xFF,
    ]);

    for (int y = 0; y < height; y++) {
      for (int bx = 0; bx < widthBytes; bx++) {
        int byteVal = 0;
        for (int bit = 0; bit < 8; bit++) {
          final x = bx * 8 + bit;
          if (x < resized.width) {
            final pixel = resized.getPixel(x, y);
            final luma = img.getLuminance(pixel);
            // White background -> 0, dark ink -> 1
            if (luma < 145) {
              byteVal |= (1 << (7 - bit));
            }
          }
        }
        out.addByte(byteVal);
      }
    }

    // Feed 4 lines + partial cut (GS V 66 0)
    out.add(const [0x1B, 0x64, 0x04, 0x1D, 0x56, 0x42, 0x00]);
    return out.toBytes();
  }

  Future<bool> printSaleReceipt({
    required AgencySettings agency,
    required Sale sale,
    required String printerAddress,
    int paperMm = 80,
  }) async {
    final rollFormat = paperMm <= 58 ? PdfPageFormat.roll57 : PdfPageFormat.roll80;
    final pdfBytes = await PdfExportService.instance.buildSaleInvoicePdf(
      agency: agency,
      sale: sale,
      format: rollFormat,
    );

    final targetWidthPx = paperMm <= 58 ? 384 : 576;
    final escPosBuilder = BytesBuilder();

    await for (final page in Printing.raster(pdfBytes, dpi: 203)) {
      final pngBytes = await page.toPng();
      final decoded = img.decodePng(pngBytes);
      if (decoded != null) {
        escPosBuilder.add(
          encodeImageToEscPosRaster(decoded, targetWidthPx: targetWidthPx),
        );
      }
    }

    final payload = escPosBuilder.toBytes();
    if (payload.isEmpty) {
      throw StateError('فشل تجهيز بيانات الطباعة الحرارية.');
    }
    return PlatformBridge.instance.printEscPosBytes(printerAddress, payload);
  }
}
