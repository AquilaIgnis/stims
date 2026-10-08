package acidburn.stims

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowUsageStatsManager

/**
 * Helpers shared by the Stims unit-test suite. Everything here talks to the same
 * SharedPreferences file / PackageManager / UsageStatsManager that production code uses, so
 * tests exercise the real wiring rather than a parallel fake.
 */

internal const val PKG_ZEBRA = "com.example.zebra"
internal const val PKG_APPLE = "com.example.apple"
internal const val PKG_MANGO = "com.example.mango"

internal val appContext: Context
    get() = ApplicationProvider.getApplicationContext()

internal fun stimsPrefs(): SharedPreferences =
    appContext.getSharedPreferences(StimsService.PREFS_NAME, Context.MODE_PRIVATE)

internal fun persistStimmed(vararg packages: String) {
    stimsPrefs().edit().putStringSet(StimsService.KEY_STIMMED_APPS, packages.toSet()).commit()
}

internal fun persistedStimmed(): Set<String> =
    stimsPrefs().getStringSet(StimsService.KEY_STIMMED_APPS, emptySet()).orEmpty()

internal fun persistForceOverlay(enabled: Boolean) {
    stimsPrefs().edit().putBoolean(StimsService.KEY_FORCE_OVERLAY, enabled).commit()
}

/**
 * Registers [packageName] as a launchable app whose visible label is [label], with a launcher
 * activity called [activityName].
 */
internal fun installLauncherApp(
    packageName: String,
    label: String,
    activityName: String = "$packageName.MainActivity",
) {
    val component = ComponentName(packageName, activityName)
    shadowOf(appContext.packageManager).apply {
        addActivityIfNotPresent(component)
        addOrUpdateActivity(
            ActivityInfo().apply {
                this.packageName = packageName
                name = activityName
                nonLocalizedLabel = label
                applicationInfo = ApplicationInfo().apply {
                    this.packageName = packageName
                    flags = ApplicationInfo.FLAG_INSTALLED
                }
            }
        )
        addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )
    }
}

/** Builds the intent MainActivity sends to the service. */
internal fun serviceIntent(
    vararg packages: String,
    forceOverlay: Boolean? = null,
): Intent = Intent(appContext, StimsService::class.java).apply {
    putStringArrayListExtra(StimsService.EXTRA_SELECTED_PACKAGES, ArrayList(packages.toList()))
    if (forceOverlay != null) putExtra(StimsService.EXTRA_FORCE_OVERLAY, forceOverlay)
}

/** Records that [packageName] came to the foreground [millisAgo] milliseconds ago. */
internal fun Context.recordForegroundApp(packageName: String, millisAgo: Long) {
    val usageStatsManager =
        getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    shadowOf(usageStatsManager).addEvent(
        ShadowUsageStatsManager.EventBuilder.buildEvent()
            .setPackage(packageName)
            .setTimeStamp(System.currentTimeMillis() - millisAgo)
            .setEventType(UsageEvents.Event.ACTIVITY_RESUMED)
            .build()
    )
}

/**
 * Reads the service's private overlay view. Asserting on it keeps the production class free of
 * test-only accessors while still pinning down which strategy the service picked.
 */
internal fun StimsService.overlayViewForTest(): View? =
    StimsService::class.java.getDeclaredField("overlayView")
        .apply { isAccessible = true }
        .get(this) as View?
