package com.example.util

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.MainActivity
import com.example.R
import java.io.File
import java.io.FileOutputStream

object LauncherIdentityManager {

    private const val SHORTCUT_ID = "qat_ledger_main_identity"
    private const val FOREST_GREEN_HEX = "#173D2E"
    private const val MUTED_GOLD_HEX = "#C7A45B"

    /**
     * Creates a 512x512 adaptive icon bitmap conforming to Android specifications:
     * - 512x512 canvas
     * - Deep forest green background (#173D2E) matching ic_launcher_background
     * - Centered Qat Ledger icon within the Android adaptive safe zone
     */
    fun createAdaptiveLauncherIcon(context: Context, sourceBitmap: Bitmap?): Bitmap {
        val size = 512
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(FOREST_GREEN_HEX)
            style = Paint.Style.FILL
        }
        val bgRect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        val cornerRadius = size * 0.22f
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(MUTED_GOLD_HEX)
            alpha = 90
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        val borderRect = RectF(4f, 4f, size - 4f, size - 4f)
        canvas.drawRoundRect(borderRect, cornerRadius - 4f, cornerRadius - 4f, borderPaint)

        // Always fall back to official Qat Ledger icon when no custom agency icon was provided
        val logoBitmap = sourceBitmap ?: try {
            BitmapFactory.decodeResource(context.resources, R.drawable.ic_qat_ledger_icon)
        } catch (e: Exception) {
            null
        }

        if (logoBitmap != null) {
            val safeZoneSize = if (sourceBitmap != null) (size * 0.66f).toInt() else (size * 0.86f).toInt()
            val leftOffset = (size - safeZoneSize) / 2
            val topOffset = (size - safeZoneSize) / 2

            val scale = minOf(
                safeZoneSize.toFloat() / logoBitmap.width,
                safeZoneSize.toFloat() / logoBitmap.height
            )
            val destWidth = (logoBitmap.width * scale).toInt()
            val destHeight = (logoBitmap.height * scale).toInt()
            val destLeft = leftOffset + (safeZoneSize - destWidth) / 2
            val destTop = topOffset + (safeZoneSize - destHeight) / 2

            val destRect = Rect(destLeft, destTop, destLeft + destWidth, destTop + destHeight)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(logoBitmap, null, destRect, paint)
        }

        return result
    }

    /**
     * Creates a circular masked version of the launcher icon for roundIcon requirements.
     */
    fun createRoundLauncherIcon(adaptiveBitmap: Bitmap): Bitmap {
        val size = adaptiveBitmap.width
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, size, size)

        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(adaptiveBitmap, rect, rect, paint)
        return output
    }

    /**
     * Applies product identity to Android TaskDescription, Activity title, and Shortcuts,
     * while ensuring MainActivityAlias stays disabled to prevent duplicate launcher icons.
     */
    fun applyIdentityToAndroid(
        context: Context,
        appName: String,
        logoBitmap: Bitmap?,
        showNotificationToast: Boolean = true
    ): Boolean {
        return try {
            val effectiveAppName = appName.trim().ifEmpty { BrandingManager.DEFAULT_APP_NAME }
            val adaptiveIcon = createAdaptiveLauncherIcon(context, logoBitmap)
            val roundIcon = createRoundLauncherIcon(adaptiveIcon)

            saveBitmapToInternal(context, adaptiveIcon, "custom_adaptive_icon.png")
            saveBitmapToInternal(context, roundIcon, "custom_round_icon.png")

            // 1. Update Android TaskDescription & Title for Recents screen
            if (context is Activity) {
                context.title = effectiveAppName
                try {
                    @Suppress("DEPRECATION")
                    context.setTaskDescription(
                        ActivityManager.TaskDescription(
                            effectiveAppName,
                            adaptiveIcon,
                            Color.parseColor("#173B2F")
                        )
                    )
                } catch (_: Exception) {
                }
            }

            // 2. Ensure MainActivityAlias remains disabled so only ONE app icon appears in launcher
            try {
                val aliasComponent = ComponentName(context, "com.example.MainActivityAlias")
                val pm = context.packageManager
                if (pm.getComponentEnabledSetting(aliasComponent) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                    pm.setComponentEnabledSetting(
                        aliasComponent,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            } catch (_: Exception) {
            }

            // 3. Update dynamic shortcut with current Qat Ledger identity
            val shortcutIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val iconCompat = IconCompat.createWithBitmap(adaptiveIcon)
            val shortcutInfo = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
                .setShortLabel(effectiveAppName)
                .setLongLabel(effectiveAppName)
                .setIcon(iconCompat)
                .setIntent(shortcutIntent)
                .setAlwaysBadged()
                .build()

            ShortcutManagerCompat.removeAllDynamicShortcuts(context)
            ShortcutManagerCompat.addDynamicShortcuts(context, listOf(shortcutInfo))

            var pinnedSuccess = false
            if (showNotificationToast && ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                pinnedSuccess = ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
            }

            if (showNotificationToast) {
                val msg = if (pinnedSuccess) {
                    "تم تحديث هوية التطبيق وأيقونته بنجاح"
                } else {
                    "تم حفظ وتطبيق هوية النظام بنجاح"
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            if (showNotificationToast) {
                Toast.makeText(context, "تم حفظ بيانات الهوية داخل التطبيق", Toast.LENGTH_SHORT).show()
            }
            false
        }
    }

    private fun saveBitmapToInternal(context: Context, bitmap: Bitmap, fileName: String) {
        val file = File(context.filesDir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    fun getSavedAdaptiveIcon(context: Context): Bitmap? {
        val file = File(context.filesDir, "custom_adaptive_icon.png")
        return if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)
        } else null
    }
}
