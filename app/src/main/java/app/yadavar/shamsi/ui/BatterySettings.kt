package app.yadavar.shamsi.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Battery optimization of the phone. The app should be "Unrestricted" (Android: Settings > Apps > this app >
 * Battery > Unrestricted) so that alarms and the widget run on time without any delay.
 */
object BatterySettings {
    fun isUnrestricted(context: Context): Boolean {
        val manager = context.getSystemService(PowerManager::class.java) ?: return false
        return runCatching { manager.isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(false)
    }

    private fun launch(context: Context, intent: Intent): Boolean =
        runCatching { context.startActivity(intent); true }.getOrDefault(false)

    /** The system dialog "let the app run without battery restrictions"; falls back to the settings lists. */
    fun requestUnrestricted(context: Context) {
        val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        if (launch(context, request)) return
        if (launch(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) return
        openAppBattery(context)
    }

    /** The app's own settings page, where Battery > Unrestricted can be chosen. */
    fun openAppBattery(context: Context) {
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        if (launch(context, details)) return
        launch(context, Intent(Settings.ACTION_SETTINGS))
    }
}
