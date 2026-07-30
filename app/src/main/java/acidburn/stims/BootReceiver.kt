package acidburn.stims

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Restarts the monitoring service after a reboot. Without this the service only exists
 * once MainActivity has been opened at least once since boot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in BOOT_ACTIONS) return

        val prefs = context.getSharedPreferences(StimsService.PREFS_NAME, Context.MODE_PRIVATE)
        val stimmed = prefs.getStringSet(StimsService.KEY_STIMMED_APPS, emptySet()) ?: emptySet()

        if (stimmed.isEmpty()) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Boot received, no stimmed apps — not starting service")
            return
        }

        if (BuildConfig.DEBUG) Log.d(TAG, "Boot received, restarting service for ${stimmed.size} apps")

        // No extras: the service reads the saved selection from SharedPreferences itself.
        val serviceIntent = Intent(context, StimsService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    companion object {
        private const val TAG = "StimsBootReceiver"

        private val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            // Some OEMs (HTC, older Xiaomi/Realme ROMs) send these instead on fast boot
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )
    }
}
