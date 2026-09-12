package acidburn.stims

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream

/** Helpers shared by the on-device tests. */

internal val targetContext: Context
    get() = InstrumentationRegistry.getInstrumentation().targetContext

internal fun stimsPrefs(): SharedPreferences =
    targetContext.getSharedPreferences(StimsService.PREFS_NAME, Context.MODE_PRIVATE)

internal fun clearStimsPrefs() {
    stimsPrefs().edit().clear().commit()
}

/** Runs a shell command with the instrumentation's elevated permissions. */
internal fun shell(command: String): String {
    val descriptor = InstrumentationRegistry.getInstrumentation()
        .uiAutomation
        .executeShellCommand(command)
    return FileInputStream(descriptor.fileDescriptor).use { it.readBytes().decodeToString() }
}

/**
 * Usage Access cannot be granted with a runtime permission dialog, so tests flip the app op
 * directly — otherwise the service can never see a foreground app.
 */
internal fun grantUsageAccess() {
    shell("appops set ${targetContext.packageName} android:get_usage_stats allow")
    // The foreground-service notification has to be visible for the tests to observe it.
    shell("pm grant ${targetContext.packageName} android.permission.POST_NOTIFICATIONS")
}

internal fun hasUsageAccess(): Boolean =
    shell("appops get ${targetContext.packageName} android:get_usage_stats").contains("allow")

@Suppress("DEPRECATION")
private fun runningStimsService() =
    targetContext.getSystemService(ActivityManager::class.java)
        // From API 26 this only reports the caller's own services, which is all we need.
        .getRunningServices(Int.MAX_VALUE)
        .firstOrNull { it.service.className == StimsService::class.java.name }

internal fun isStimsServiceRunning(): Boolean = runningStimsService() != null

/** True once the service has actually called startForeground(). */
internal fun isStimsServiceForeground(): Boolean = runningStimsService()?.foreground == true

/** Polls [condition] until it holds or [timeoutMillis] elapses. Returns whether it held. */
internal fun waitFor(timeoutMillis: Long = 5_000, condition: () -> Boolean): Boolean {
    val deadline = SystemClock.uptimeMillis() + timeoutMillis
    while (SystemClock.uptimeMillis() < deadline) {
        if (condition()) return true
        SystemClock.sleep(100)
    }
    return condition()
}

/** The service's foreground notification, or null while it is not in the foreground. */
internal fun stimsNotification(): StatusBarNotification? =
    targetContext.getSystemService(NotificationManager::class.java)
        .activeNotifications
        .firstOrNull { it.id == StimsService.NOTIFICATION_ID }

/**
 * Waits until the service has genuinely entered the foreground. This is a stronger signal than
 * [isStimsServiceRunning]: the system creates the ServiceRecord as soon as
 * startForegroundService() is called, well before onStartCommand() has run. It is also
 * independent of POST_NOTIFICATIONS, which takes a moment to take effect after being granted.
 */
internal fun waitForServiceForeground(timeoutMillis: Long = 10_000): Boolean =
    waitFor(timeoutMillis) { isStimsServiceForeground() }

/**
 * Tears the service down without tripping the platform's start-foreground watchdog: calling
 * stopService() while a startForegroundService() is still in flight makes Android throw
 * ForegroundServiceDidNotStartInTimeException at the whole process.
 */
internal fun stopStimsServiceAndWait() {
    waitFor(5_000) { isStimsServiceForeground() }
    targetContext.stopService(Intent(targetContext, StimsService::class.java))
    waitFor(5_000) { !isStimsServiceRunning() && stimsNotification() == null }
}
