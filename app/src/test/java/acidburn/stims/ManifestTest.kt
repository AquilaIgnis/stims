package acidburn.stims

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app is nothing without its manifest declarations — losing one of these permissions or
 * intent filters breaks the app silently at runtime rather than at build time.
 */
@RunWith(AndroidJUnit4::class)
class ManifestTest {

    private val packageManager: PackageManager get() = appContext.packageManager

    @Suppress("DEPRECATION")
    private fun packageInfo(flags: Int) =
        packageManager.getPackageInfo(appContext.packageName, flags)

    @Test
    fun `every permission the app depends on is requested`() {
        val requested = packageInfo(PackageManager.GET_PERMISSIONS).requestedPermissions!!.toList()

        assertThat(requested).containsAtLeast(
            // enumerating installed apps for the picker
            android.Manifest.permission.QUERY_ALL_PACKAGES,
            // the two keep-awake strategies
            android.Manifest.permission.WAKE_LOCK,
            android.Manifest.permission.SYSTEM_ALERT_WINDOW,
            // the polling foreground service
            android.Manifest.permission.FOREGROUND_SERVICE,
            android.Manifest.permission.POST_NOTIFICATIONS,
            // reading which app is in the foreground
            "android.permission.PACKAGE_USAGE_STATS",
            // restarting monitoring after a reboot
            android.Manifest.permission.RECEIVE_BOOT_COMPLETED,
        )
    }

    @Test
    fun `the special-use foreground service permission is requested`() {
        val requested = packageInfo(PackageManager.GET_PERMISSIONS).requestedPermissions!!.toList()

        // Android 14+ refuses to start a specialUse foreground service without it.
        assertThat(requested).contains("android.permission.FOREGROUND_SERVICE_SPECIAL_USE")
    }

    @Test
    fun `the monitoring service is declared as a private special-use foreground service`() {
        val service = packageInfo(PackageManager.GET_SERVICES).services!!
            .single { it.name == StimsService::class.java.name }

        assertThat(service.exported).isFalse()
        assertThat(service.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            .isNotEqualTo(0)
    }

    @Test
    fun `the boot receiver is exported so the system can reach it`() {
        val receiver = packageInfo(PackageManager.GET_RECEIVERS).receivers!!
            .single { it.name == BootReceiver::class.java.name }

        assertThat(receiver.exported).isTrue()
        assertThat(receiver.enabled).isTrue()
    }

    @Test
    fun `the boot receiver is registered for every boot broadcast it handles`() {
        val actions = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
        )

        actions.forEach { action ->
            val receivers = packageManager.queryBroadcastReceivers(Intent(action), 0)
            assertThat(receivers.map { it.activityInfo.name })
                .contains(BootReceiver::class.java.name)
        }
    }

    @Test
    fun `MainActivity is the launcher entry point`() {
        val launchIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(appContext.packageName)

        val resolved = packageManager.queryIntentActivities(launchIntent, 0)

        assertThat(resolved.map { it.activityInfo.name })
            .contains(MainActivity::class.java.name)
    }
}
