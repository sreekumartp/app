package app.anchor.data

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import app.anchor.guard.AnchorAccessibilityService

/** Each permission is requested one at a time, after its own disclosure screen (Journey A step 4). */
object Permissions {

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun hasAccessibility(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(context, AnchorAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun hasNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun hasNfc(context: Context): Boolean = NfcAdapter.getDefaultAdapter(context) != null

    fun usageAccessIntent(context: Context) = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        .setData(Uri.parse("package:${context.packageName}"))

    fun usageAccessFallbackIntent() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun overlayIntent(context: Context) =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun accessibilityIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun batteryIntent() = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun exactAlarmIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
        } else null

    fun appDetailsIntent(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    /** NFR-04: guided autostart / battery setup for OEMs that kill background services. */
    fun oemGuidance(): String? = when (Build.MANUFACTURER.lowercase()) {
        "xiaomi", "redmi", "poco" -> "HyperOS / MIUI: Settings → Apps → Manage apps → Anchor → turn on Autostart, then Battery saver → No restrictions. Also lock Anchor in Recents (long-press its card → lock)."
        "oppo", "realme", "oneplus" -> "ColorOS / Realme UI: Settings → Apps → App management → Anchor → Battery usage → allow background activity and Auto launch."
        "vivo", "iqoo" -> "Funtouch / OriginOS: Settings → Battery → Background power consumption management → Anchor → Allow. Also enable Autostart in i Manager."
        "samsung" -> "One UI: Settings → Apps → Anchor → Battery → Unrestricted. Also remove Anchor from Sleeping apps in Device care."
        "huawei", "honor" -> "Settings → Battery → App launch → Anchor → Manage manually, and allow all three options."
        else -> null
    }
}
