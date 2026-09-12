package acidburn.stims

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real foreground service against the real platform: the thing Robolectric cannot
 * confirm is that Android actually lets this service start and stay up.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class StimsServiceInstrumentedTest {

    private val notificationManager: NotificationManager
        get() = targetContext.getSystemService(NotificationManager::class.java)

    private fun serviceIntent(vararg packages: String) =
        Intent(targetContext, StimsService::class.java).apply {
            putStringArrayListExtra(
                StimsService.EXTRA_SELECTED_PACKAGES, ArrayList(packages.toList()),
            )
        }

    private fun startService(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            targetContext.startForegroundService(intent)
        } else {
            targetContext.startService(intent)
        }
    }

    private fun ownNotification() =
        notificationManager.activeNotifications
            .firstOrNull { it.id == StimsService.NOTIFICATION_ID }

    @Before
    fun setUp() {
        grantUsageAccess()
        clearStimsPrefs()
    }

    @After
    fun tearDown() {
        stopStimsServiceAndWait()
        clearStimsPrefs()
    }

    @Test
    fun theServiceStartsInTheForegroundWithASelection() {
        startService(serviceIntent(targetContext.packageName))

        assertThat(waitForServiceForeground()).isTrue()
        assertThat(isStimsServiceRunning()).isTrue()
    }

    @Test
    fun theNotificationChannelIsRegisteredWithTheSystem() {
        startService(serviceIntent(targetContext.packageName))
        waitForServiceForeground()

        val channel = notificationManager.getNotificationChannel(StimsService.CHANNEL_ID)
        assertThat(channel).isNotNull()
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)
    }

    @Test
    fun theNotificationSaysHowManyAppsAreMonitored() {
        startService(serviceIntent(targetContext.packageName, "com.android.settings"))
        waitForServiceForeground()

        val extras = ownNotification()!!.notification.extras
        assertThat(extras.getString("android.title")).isEqualTo("Stims Daemon Running")
        assertThat(extras.getString("android.text")).isEqualTo("Monitoring 2 apps")
    }

    @Test
    fun anEmptySelectionShutsTheServiceDownAgain() {
        startService(serviceIntent(targetContext.packageName))
        assertThat(waitForServiceForeground()).isTrue()

        startService(serviceIntent())

        // The service must call startForeground() before stopping itself, or the platform
        // kills the process with ForegroundServiceDidNotStartInTimeException.
        assertThat(waitFor { !isStimsServiceRunning() }).isTrue()
    }

    @Test
    fun theStopActionShutsTheServiceDown() {
        startService(serviceIntent(targetContext.packageName))
        assertThat(waitForServiceForeground()).isTrue()

        startService(
            Intent(targetContext, StimsService::class.java)
                .apply { action = StimsService.ACTION_STOP }
        )

        assertThat(waitFor { !isStimsServiceRunning() }).isTrue()
    }

    @Test
    fun aRestartWithoutExtrasPicksUpTheSavedSelection() {
        // What BootReceiver and a START_STICKY restart both look like.
        stimsPrefs().edit()
            .putStringSet(StimsService.KEY_STIMMED_APPS, setOf(targetContext.packageName))
            .commit()

        startService(Intent(targetContext, StimsService::class.java))

        assertThat(waitForServiceForeground()).isTrue()
        assertThat(ownNotification()!!.notification.extras.getString("android.text"))
            .isEqualTo("Monitoring 1 apps")
    }

    @Test
    fun aRestartWithNothingSavedDoesNotKeepTheServiceAlive() {
        startService(Intent(targetContext, StimsService::class.java))

        assertThat(waitFor { !isStimsServiceRunning() }).isTrue()
    }

    @Test
    fun theServiceSurvivesTheFirstPollingCycle() {
        startService(serviceIntent(targetContext.packageName))
        assertThat(waitForServiceForeground()).isTrue()

        // The loop polls every 10s; make sure a real poll against real UsageStats does not
        // take the service down (missing permissions, SecurityException, and so on).
        assertThat(waitFor(timeoutMillis = 15_000) { !isStimsServiceRunning() }).isFalse()
    }

    @Test
    fun usageAccessIsActuallyGrantedForTheseTests() {
        // Guards the rest of the suite: without this op the service is a no-op.
        assertThat(hasUsageAccess()).isTrue()
    }
}
