package acidburn.stims

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf

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

/** The exact intent [AppListScreen] uses to enumerate launchable apps. */
internal fun launcherProbeIntent(): Intent =
    Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }

/** Registers [packageName] as a launchable app whose visible label is [label]. */
internal fun installLauncherApp(packageName: String, label: String) {
    shadowOf(appContext.packageManager)
        .addResolveInfoForIntent(launcherProbeIntent(), launcherResolveInfo(packageName, label))
}

internal fun launcherResolveInfo(packageName: String, label: String): ResolveInfo =
    ResolveInfo().apply {
        activityInfo = ActivityInfo().apply {
            this.packageName = packageName
            name = "$packageName.MainActivity"
            applicationInfo = ApplicationInfo().apply {
                this.packageName = packageName
                flags = ApplicationInfo.FLAG_INSTALLED
            }
        }
        nonLocalizedLabel = label
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
        packageName,
        System.currentTimeMillis() - millisAgo,
        UsageEvents.Event.ACTIVITY_RESUMED,
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
