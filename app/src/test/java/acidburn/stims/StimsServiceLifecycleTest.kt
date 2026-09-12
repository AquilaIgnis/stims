package acidburn.stims

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowBuild

/** onStartCommand contract: what the service does with the intent it is handed. */
@RunWith(AndroidJUnit4::class)
class StimsServiceLifecycleTest {

    @Before
    fun useWakeLockVendor() {
        // Keep these tests on the wake-lock strategy; the overlay path has its own suite.
        ShadowBuild.setManufacturer("Google")
    }

    private fun createService(intent: Intent?): StimsService =
        if (intent == null) {
            Robolectric.buildService(StimsService::class.java).create().get()
        } else {
            Robolectric.buildService(StimsService::class.java, intent).create().get()
        }

    @Test
    fun `stop action posts a notification then stops the service`() {
        val service = createService(serviceIntent(PKG_ZEBRA))
        val stopIntent = Intent(appContext, StimsService::class.java)
            .apply { action = StimsService.ACTION_STOP }

        val result = service.onStartCommand(stopIntent, 0, 1)

        assertThat(result).isEqualTo(Service.START_NOT_STICKY)
        assertThat(shadowOf(service).isStoppedBySelf).isTrue()
        // startForeground() must happen even on the stop path or the platform kills the
        // process with ForegroundServiceDidNotStartInTimeException.
        assertThat(shadowOf(service).lastForegroundNotification).isNotNull()
    }

    @Test
    fun `empty selection posts a notification then stops the service`() {
        val service = createService(serviceIntent())

        val result = service.onStartCommand(serviceIntent(), 0, 1)

        assertThat(result).isEqualTo(Service.START_NOT_STICKY)
        assertThat(shadowOf(service).isStoppedBySelf).isTrue()
        assertThat(shadowOf(service).lastForegroundNotification).isNotNull()
    }

    @Test
    fun `a non-empty selection keeps the service sticky`() {
        val intent = serviceIntent(PKG_ZEBRA, PKG_APPLE)
        val service = createService(intent)

        val result = service.onStartCommand(intent, 0, 1)

        assertThat(result).isEqualTo(Service.START_STICKY)
        assertThat(shadowOf(service).isStoppedBySelf).isFalse()
    }

    @Test
    fun `an intent without extras falls back to the persisted selection`() {
        persistStimmed(PKG_ZEBRA, PKG_APPLE)
        // This is what BootReceiver sends: a bare intent, no extras.
        val bareIntent = Intent(appContext, StimsService::class.java)
        val service = createService(bareIntent)

        val result = service.onStartCommand(bareIntent, 0, 1)

        assertThat(result).isEqualTo(Service.START_STICKY)
        assertThat(shadowOf(service).lastForegroundNotification.extras.getString("android.text"))
            .isEqualTo("Monitoring 2 apps")
    }

    @Test
    fun `a null intent falls back to the persisted selection`() {
        persistStimmed(PKG_ZEBRA)
        val service = createService(null)

        // START_STICKY restarts hand onStartCommand a null intent.
        val result = service.onStartCommand(null, 0, 1)

        assertThat(result).isEqualTo(Service.START_STICKY)
        assertThat(shadowOf(service).isStoppedBySelf).isFalse()
    }

    @Test
    fun `a null intent with nothing persisted stops the service`() {
        val service = createService(null)

        val result = service.onStartCommand(null, 0, 1)

        assertThat(result).isEqualTo(Service.START_NOT_STICKY)
        assertThat(shadowOf(service).isStoppedBySelf).isTrue()
    }

    @Test
    fun `intent extras win over the persisted selection`() {
        persistStimmed(PKG_ZEBRA, PKG_APPLE, PKG_MANGO)
        val intent = serviceIntent(PKG_MANGO)
        val service = createService(intent)

        service.onStartCommand(intent, 0, 1)

        assertThat(shadowOf(service).lastForegroundNotification.extras.getString("android.text"))
            .isEqualTo("Monitoring 1 apps")
    }

    @Test
    fun `foreground notification is ongoing and uses the documented id`() {
        val intent = serviceIntent(PKG_ZEBRA)
        val service = createService(intent)

        service.onStartCommand(intent, 0, 1)

        val shadow = shadowOf(service)
        assertThat(shadow.lastForegroundNotificationId).isEqualTo(StimsService.NOTIFICATION_ID)
        val notification = shadow.lastForegroundNotification
        assertThat(notification.flags and android.app.Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
        assertThat(notification.channelId).isEqualTo(StimsService.CHANNEL_ID)
        assertThat(notification.extras.getString("android.title")).isEqualTo("Stims Daemon Running")
    }

    @Test
    fun `notification channel is created with low importance`() {
        val intent = serviceIntent(PKG_ZEBRA)
        val service = createService(intent)

        service.onStartCommand(intent, 0, 1)

        val manager = appContext.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(StimsService.CHANNEL_ID)
        assertThat(channel).isNotNull()
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)
        assertThat(channel.name.toString()).isEqualTo("Stims Service")
    }

    @Test
    fun `an existing notification channel is left untouched`() {
        val manager = appContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                StimsService.CHANNEL_ID, "User renamed", NotificationManager.IMPORTANCE_HIGH,
            )
        )
        val intent = serviceIntent(PKG_ZEBRA)
        val service = createService(intent)

        service.onStartCommand(intent, 0, 1)

        // Recreating would silently reset user-customised channel settings.
        val channel = manager.getNotificationChannel(StimsService.CHANNEL_ID)
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_HIGH)
        assertThat(channel.name.toString()).isEqualTo("User renamed")
    }

    @Test
    fun `restarting with a different selection updates the notification`() {
        val service = createService(serviceIntent(PKG_ZEBRA))
        service.onStartCommand(serviceIntent(PKG_ZEBRA), 0, 1)

        service.onStartCommand(serviceIntent(PKG_ZEBRA, PKG_APPLE, PKG_MANGO), 0, 2)

        assertThat(shadowOf(service).lastForegroundNotification.extras.getString("android.text"))
            .isEqualTo("Monitoring 3 apps")
    }

    @Test
    fun `service is not bindable`() {
        val service = createService(serviceIntent(PKG_ZEBRA))

        assertThat(service.onBind(Intent())).isNull()
    }

    @Test
    fun `companion constants stay stable across upgrades`() {
        // These names are written to disk / sent across process boundaries; changing one
        // silently drops every user's saved selection on upgrade.
        assertThat(StimsService.PREFS_NAME).isEqualTo("stims_prefs")
        assertThat(StimsService.KEY_STIMMED_APPS).isEqualTo("stimmed_apps")
        assertThat(StimsService.KEY_FORCE_OVERLAY).isEqualTo("force_overlay")
        assertThat(StimsService.EXTRA_SELECTED_PACKAGES).isEqualTo("selected_packages")
        assertThat(StimsService.EXTRA_FORCE_OVERLAY).isEqualTo("force_overlay")
        assertThat(StimsService.ACTION_STOP).isEqualTo("acidburn.stims.STOP")
        assertThat(StimsService.CHANNEL_ID).isEqualTo("StimsServiceChannel")
    }

    @Test
    fun `overlay vendor list is matched case-insensitively`() {
        assertThat(StimsService.OVERLAY_VENDORS).containsExactly("samsung", "Realme")
    }

    @Test
    fun `service context can resolve every system service it uses`() {
        val service = createService(serviceIntent(PKG_ZEBRA))

        assertThat(service.getSystemService(Context.POWER_SERVICE)).isNotNull()
        assertThat(service.getSystemService(Context.USAGE_STATS_SERVICE)).isNotNull()
        assertThat(service.getSystemService(Context.WINDOW_SERVICE)).isNotNull()
        assertThat(service.getSystemService(NotificationManager::class.java)).isNotNull()
    }
}
