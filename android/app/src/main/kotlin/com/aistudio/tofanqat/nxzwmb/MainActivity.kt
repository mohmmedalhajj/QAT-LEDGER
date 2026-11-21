package com.aistudio.tofanqat.nxzwmb

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class MainActivity : FlutterActivity() {
    companion object {
        private const val CHANNEL = "com.qatledger.app/platform"
        private const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
        private const val KEYSTORE_ALIAS = "qat_ledger_dev_seed_wrap_v2"
        private const val REQ_BT_CONNECT = 4101
        private const val REQ_READ_CONTACTS = 4102
        private const val REQ_PICK_CONTACT = 4103
    }

    private var pendingContactResult: MethodChannel.Result? = null
    private var pendingBluetoothResult: MethodChannel.Result? = null
    private var pendingBluetoothArgs: ByteArray? = null
    private var pendingPrinterAddress: String? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "getEditionConfig" -> {
                        result.success(
                            mapOf(
                                "edition" to BuildConfig.APP_EDITION,
                                "publicKeysCsv" to BuildConfig.QAT_LICENSE_PUBLIC_KEYS,
                                "applicationId" to BuildConfig.APPLICATION_ID,
                                "versionName" to BuildConfig.VERSION_NAME,
                                "versionCode" to BuildConfig.VERSION_CODE
                            )
                        )
                    }
                    "getInstallationHardwareAnchor" -> {
                        val androidId = Settings.Secure.getString(
                            contentResolver,
                            Settings.Secure.ANDROID_ID
                        ) ?: ""
                        result.success(
                            mapOf(
                                "androidId" to androidId,
                                "brand" to Build.BRAND,
                                "model" to Build.MODEL
                            )
                        )
                    }
                    "getLegacyMigrationSources" -> {
                        result.success(collectLegacySources())
                    }
                    "keystoreSeal" -> {
                        val plainB64 = call.argument<String>("plaintextB64")
                        if (plainB64.isNullOrEmpty()) {
                            result.error("ARG", "Missing plaintextB64", null)
                        } else {
                            try {
                                val sealed = sealWithKeystore(Base64.decode(plainB64, Base64.NO_WRAP))
                                result.success(sealed)
                            } catch (e: Exception) {
                                result.error("KEYSTORE", e.message, null)
                            }
                        }
                    }
                    "keystoreUnseal" -> {
                        val sealed = call.argument<String>("sealedPayload")
                        if (sealed.isNullOrEmpty()) {
                            result.error("ARG", "Missing sealedPayload", null)
                        } else {
                            try {
                                val raw = unsealWithKeystore(sealed)
                                result.success(Base64.encodeToString(raw, Base64.NO_WRAP))
                            } catch (e: Exception) {
                                result.error("KEYSTORE", e.message, null)
                            }
                        }
                    }
                    "getBondedPrinters" -> {
                        handleGetBondedPrinters(result)
                    }
                    "printEscPosBytes" -> {
                        val address = call.argument<String>("address")
                        val bytes = call.argument<ByteArray>("bytes")
                        if (address.isNullOrBlank() || bytes == null || bytes.isEmpty()) {
                            result.error("ARG", "Invalid printer address or empty bytes", null)
                        } else {
                            handlePrintEscPos(address, bytes, result)
                        }
                    }
                    "pickPhoneContact" -> {
                        handlePickPhoneContact(result)
                    }
                    "applyLauncherIdentity" -> {
                        val appName = call.argument<String>("appName") ?: "ميزان القات"
                        val logoPath = call.argument<String>("logoPath") ?: ""
                        result.success(applyLauncherIdentity(appName, logoPath))
                    }
                    "decodeQrFromImagePath" -> {
                        val path = call.argument<String>("path")
                        if (path.isNullOrBlank()) {
                            result.success(null)
                        } else {
                            Thread {
                                val decoded = decodeQrFromFile(path)
                                runOnUiThread { result.success(decoded) }
                            }.start()
                        }
                    }
                    "getDeveloperBootstrapSeedB64" -> {
                        result.success(
                            FlavorPlatformDelegate.getDeveloperBootstrapSeedB64(applicationContext)
                        )
                    }
                    else -> result.notImplemented()
                }
            }
    }

    private fun collectLegacySources(): Map<String, Any?> {
        val legacyRoomDb: File = getDatabasePath("tofan_al_aqsa_qat_db")
        val prefsMap = mutableMapOf<String, Map<String, Any?>>()
        val prefNames = listOf(
            "app_settings",
            "branding_prefs",
            "app_universal_theme_prefs",
            "printer_prefs",
            "qat_license_install_prefs",
            "qat_active_license_store",
            "qat_license_time_guard",
            "qat_agency_prefs",
            "app_theme_prefs",
            "qat_ledger_license_store"
        ) + FlavorPlatformDelegate.extraLegacyPrefNames()
        for (name in prefNames) {
            val sp = getSharedPreferences(name, Context.MODE_PRIVATE)
            val entries = mutableMapOf<String, Any?>()
            for ((k, v) in sp.all) {
                when (v) {
                    is String, is Int, is Long, is Float, is Boolean -> entries[k] = v
                    is Double -> entries[k] = v
                }
            }
            if (entries.isNotEmpty()) {
                prefsMap[name] = entries
            }
        }
        val legacyLicenseFile = File(noBackupFilesDir, "active_license.qlic")
        val legacyActiveLicenseDoc = try {
            if (legacyLicenseFile.exists()) legacyLicenseFile.readText().trim() else ""
        } catch (_: Exception) {
            ""
        }
        return mapOf(
            "legacyRoomDbExists" to legacyRoomDb.exists(),
            "legacyRoomDbPath" to legacyRoomDb.absolutePath,
            "legacyActiveLicenseDoc" to legacyActiveLicenseDoc,
            "legacyPrefs" to prefsMap
        ) + FlavorPlatformDelegate.extraLegacyDbInfo(applicationContext)
    }

    private fun applyLauncherIdentity(appName: String, logoPath: String): Boolean {
        return try {
            val cleanName = appName.trim().ifEmpty { "ميزان القات" }
            val customBmp: Bitmap? = if (logoPath.isNotBlank() && File(logoPath).exists()) {
                BitmapFactory.decodeFile(logoPath)
            } else {
                null
            }
            val size = 256
            val iconBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(iconBmp)
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#173D2E")
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), size * 0.22f, size * 0.22f, bgPaint)
            if (customBmp != null) {
                val pad = (size * 0.16f).toInt()
                val dest = Rect(pad, pad, size - pad, size - pad)
                canvas.drawBitmap(customBmp, null, dest, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            }
            title = cleanName
            try {
                @Suppress("DEPRECATION")
                setTaskDescription(
                    ActivityManager.TaskDescription(
                        cleanName,
                        if (customBmp != null) iconBmp else null,
                        Color.parseColor("#173B2F")
                    )
                )
            } catch (_: Exception) {
            }
            if (customBmp != null) {
                val shortcutIntent = Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_MAIN
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val shortcut = ShortcutInfoCompat.Builder(this, "qat_ledger_main_identity")
                    .setShortLabel(cleanName)
                    .setLongLabel(cleanName)
                    .setIcon(IconCompat.createWithBitmap(iconBmp))
                    .setIntent(shortcutIntent)
                    .build()
                ShortcutManagerCompat.removeAllDynamicShortcuts(this)
                ShortcutManagerCompat.addDynamicShortcuts(this, listOf(shortcut))
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun getOrCreateKeystoreSecret(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        gen.init(spec)
        return gen.generateKey()
    }

    private fun sealWithKeystore(plain: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKeystoreSecret())
        val ct = cipher.doFinal(plain)
        val ivB64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val ctB64 = Base64.encodeToString(ct, Base64.NO_WRAP)
        return "$ivB64:$ctB64"
    }

    private fun unsealWithKeystore(payload: String): ByteArray {
        val parts = payload.split(":")
        require(parts.size == 2) { "Malformed keystore payload" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ct = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKeystoreSecret(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ct)
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleGetBondedPrinters(result: MethodChannel.Result) {
        if (!hasBluetoothPermission()) {
            pendingBluetoothResult = result
            pendingPrinterAddress = null
            pendingBluetoothArgs = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    REQ_BT_CONNECT
                )
            }
            return
        }
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter? = manager?.adapter
        if (adapter == null) {
            result.error("BT_UNSUPPORTED", "هذا الجهاز لا يدعم البلوتوث أو لا يتوفر محول بلوتوث فعلي.", null)
            return
        }
        if (!adapter.isEnabled) {
            result.error("BT_OFF", "البلوتوث متوقف حالياً. قم بتفعيل البلوتوث ثم أعد المحاولة.", null)
            return
        }
        val list = adapter.bondedDevices.orEmpty().map { dev: BluetoothDevice ->
            val btClass = dev.bluetoothClass
            val majorClass = btClass?.majorDeviceClass ?: BluetoothClass.Device.Major.UNCATEGORIZED
            val deviceClass = btClass?.deviceClass ?: 0
            val isImaging = majorClass == BluetoothClass.Device.Major.IMAGING
            val isAudioOrWearable = majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO ||
                majorClass == BluetoothClass.Device.Major.WEARABLE ||
                majorClass == BluetoothClass.Device.Major.HEALTH ||
                majorClass == BluetoothClass.Device.Major.PHONE ||
                majorClass == BluetoothClass.Device.Major.TOY
            val supportsSpp = try {
                dev.uuids?.any { it.uuid.toString().equals(SPP_UUID, ignoreCase = true) } == true
            } catch (_: Exception) {
                false
            }
            mapOf(
                "name" to (dev.name ?: "طابعة بلوتوث"),
                "address" to dev.address,
                "majorDeviceClass" to majorClass,
                "deviceClass" to deviceClass,
                "isImagingDevice" to isImaging,
                "supportsSppUuid" to supportsSpp,
                "isAudioOrWearable" to isAudioOrWearable
            )
        }
        result.success(list)
    }

    @SuppressLint("MissingPermission")
    private fun handlePrintEscPos(address: String, bytes: ByteArray, result: MethodChannel.Result) {
        if (!hasBluetoothPermission()) {
            pendingBluetoothResult = result
            pendingPrinterAddress = address
            pendingBluetoothArgs = bytes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    REQ_BT_CONNECT
                )
            }
            return
        }
        Thread {
            val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = manager?.adapter
            if (adapter == null || !adapter.isEnabled) {
                runOnUiThread { result.error("BT_OFF", "البلوتوث غير مفعل", null) }
                return@Thread
            }
            try {
                adapter.cancelDiscovery()
                val device = adapter.getRemoteDevice(address)
                val majorClass = device.bluetoothClass?.majorDeviceClass
                    ?: BluetoothClass.Device.Major.UNCATEGORIZED
                if (majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO ||
                    majorClass == BluetoothClass.Device.Major.WEARABLE ||
                    majorClass == BluetoothClass.Device.Major.PHONE
                ) {
                    runOnUiThread {
                        result.error(
                            "BT_NOT_PRINTER",
                            "الجهاز المختار (${device.name ?: address}) مصنف كجهاز صوتي/هاتف وليس طابعة حرارية.",
                            null
                        )
                    }
                    return@Thread
                }

                val uuid = UUID.fromString(SPP_UUID)
                var socket: BluetoothSocket? = null
                try {
                    socket = device.createRfcommSocketToServiceRecord(uuid)
                    socket.connect()
                } catch (_: Exception) {
                    try {
                        socket?.close()
                    } catch (_: Exception) {
                    }
                    socket = device.createInsecureRfcommSocketToServiceRecord(uuid)
                    socket.connect()
                }

                socket.use { sock ->
                    val os = sock.outputStream
                    // Stream in 1KB chunks with brief flush pacing for thermal buffers
                    var offset = 0
                    val chunkSize = 1024
                    while (offset < bytes.size) {
                        val count = minOf(chunkSize, bytes.size - offset)
                        os.write(bytes, offset, count)
                        os.flush()
                        offset += count
                        Thread.sleep(15)
                    }
                    Thread.sleep(180)
                }
                runOnUiThread { result.success(true) }
            } catch (e: Exception) {
                runOnUiThread {
                    result.error(
                        "BT_PRINT",
                        e.message ?: "تعذر الاتصال بالطابعة عبر منفذ Bluetooth SPP. تأكد من تشغيل الطابعة وتقريبها.",
                        null
                    )
                }
            }
        }.start()
    }

    private fun handlePickPhoneContact(result: MethodChannel.Result) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingContactResult = result
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_CONTACTS),
                REQ_READ_CONTACTS
            )
            return
        }
        launchContactPicker(result)
    }

    private fun launchContactPicker(result: MethodChannel.Result) {
        pendingContactResult = result
        try {
            val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            startActivityForResult(intent, REQ_PICK_CONTACT)
        } catch (_: Exception) {
            pendingContactResult = null
            result.success(null)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        when (requestCode) {
            REQ_BT_CONNECT -> {
                val res = pendingBluetoothResult ?: return
                val addr = pendingPrinterAddress
                val data = pendingBluetoothArgs
                pendingBluetoothResult = null
                pendingPrinterAddress = null
                pendingBluetoothArgs = null
                if (!granted) {
                    res.error("BT_PERM", "لم يتم منح إذن البلوتوث", null)
                } else if (addr != null && data != null) {
                    handlePrintEscPos(addr, data, res)
                } else {
                    handleGetBondedPrinters(res)
                }
            }
            REQ_READ_CONTACTS -> {
                val res = pendingContactResult ?: return
                pendingContactResult = null
                if (!granted) {
                    res.error("CONTACT_PERM", "لم يتم منح إذن جهات الاتصال", null)
                } else {
                    launchContactPicker(res)
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_CONTACT) {
            val res = pendingContactResult ?: return
            pendingContactResult = null
            if (resultCode != Activity.RESULT_OK || data?.data == null) {
                res.success(null)
                return
            }
            val uri = data.data!!
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0).orEmpty().trim()
                    val rawPhone = cursor.getString(1).orEmpty()
                    val cleanedPhone = buildString {
                        for (ch in rawPhone) {
                            when {
                                ch in '٠'..'٩' -> append(('0'.code + (ch.code - '٠'.code)).toChar())
                                ch in '۰'..'۹' -> append(('0'.code + (ch.code - '۰'.code)).toChar())
                                ch in '0'..'9' -> append(ch)
                                ch == '+' && isEmpty() -> append(ch)
                            }
                        }
                    }
                    res.success(mapOf("name" to name, "phone" to cleanedPhone))
                } else {
                    res.success(null)
                }
            } ?: res.success(null)
        }
    }

    private fun decodeQrFromFile(path: String): String? {
        var bmp: Bitmap? = null
        return try {
            val file = File(path)
            if (!file.exists()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sampleSize = 1
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            while (maxDim / sampleSize > 1600) {
                sampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            bmp = BitmapFactory.decodeFile(path, opts) ?: return null
            val width = bmp.width
            val height = bmp.height
            val pixels = IntArray(width * height)
            bmp.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = RGBLuminanceSource(width, height, pixels)
            val hints = mapOf(
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.CHARACTER_SET to "UTF-8"
            )
            val reader = MultiFormatReader().apply { setHints(hints) }
            try {
                reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
            } catch (_: Exception) {
                reader.reset()
                reader.decodeWithState(BinaryBitmap(GlobalHistogramBinarizer(source))).text
            }
        } catch (_: Exception) {
            null
        } finally {
            try {
                bmp?.recycle()
            } catch (_: Exception) {
            }
        }
    }
}
