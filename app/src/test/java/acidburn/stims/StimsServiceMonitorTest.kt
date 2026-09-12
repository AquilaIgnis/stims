package acidburn.stims

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.ShadowBuild
import org.robolectric.shadows.ShadowPowerManager

/**
 * The polling loop on the wake-lock strategy: which foreground app leads to the screen being
 * held awake, and when the lock is handed back.
 */
@RunWith(AndroidJUnit4::class)
class StimsServiceMonitorTest {

    private lateinit var controller: ServiceController<StimsService>

    @Before
    fun useWakeLockVendor() {
        ShadowBuild.setManufacturer("Google")
    }

    private fun powerManager(): PowerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    private fun setScreenInteractive(interactive: Boolean) {
        shadowOf(powerManager()).setIsInteractive(interactive)
    }

    private fun startMonitoring(vararg packages: String): StimsService {
        val intent: Intent = serviceIntent(*packages)
        controller = Robolectric.buildService(StimsService::class.java, intent).create()
        controller.get().onStartCommand(intent, 0, 1)
        return controller.get()
    }

    /** Runs whatever the monitor loop has queued for right now. */
    private fun pollNow() = shadowOf(Looper.getMainLooper()).idle()

    /** Advances past [seconds] of the 10s polling interval. */
    private fun advance(seconds: Long) =
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))

    private fun wakeLockHeld(): Boolean = ShadowPowerManager.getLatestWakeLock()?.isHeld == true

    @Test
    fun `a stimmed app in the foreground holds the screen awake`() {
        appContext.recordForegroundApp(PKG_ZEBRA, millisAgo = 2_000)
        startMonitoring(PKG_ZEBRA)

        pollNow()

        assertThat(wakeLockHeld()).isTrue()
    }

    @Test
    fun `an app that is not stimmed leaves the screen alone`() {
        appContext.recordForegroundApp(PKG_APPLE, millisAgo = 2_000)
        startMonitoring(PKG_ZEBRA)

        pollNow()

        assertThat(wakeLockHeld()).isFalse()
    }

    @Test
    fun `no usage data means no wake lock`() {
        startMonitoring(PKG_ZEBRA)

        pollNow()

        assertThat(ShadowPowerManager.getLatestWakeLock()).isNull()
    }

    @Test
    fun `the wake lock is tagged for attribution`() {
        appContext.recordForegroundApp(PKG_ZEBRA, millisAgo = 2_000)
        startMonitoring(PKG_ZEBRA)

        pollNow()

        assertThat(shadowOf(ShadowPowerManager.getLatestWakeLock()).tag).isEqualTo("Stims::WakeLock")
    }

    @Test
    fun `switching away from a stimmed app releases the screen`() {
        appContext.recordForegroundApp(PKG_ZEBRA, millisAgo = 8_000)
        startMonitoring(PKG_ZEBRA)
        pollNow()
        assertThat(wakeLockHeld()).isTrue()

        appContext.recordForegroundApp(PKG_APPLE, millisAgo = 1_000)
        advance(10)

        assertThat(wakeLockHeld()).isFalse()
    }

    @Test
    fun `returning to a stimmed app re-acquires the screen`() {
        appContext.recordForegroundApp(PKG_ZEBRA, millisAgo = 12_000)
        appContext.recordForegroundApp(PKG_APPLE, millisAgo = 8_000)
        startMonitoring(PKG_ZEBRA)
        pollNow()
        assertThat(wakeLockHeld()).isFalse()

        appContext.recordForegroundApp(PKG_ZEBRA, millisAgo = 1_000)
        advance(10)

        assertThat(wakeLockHeld()).isTrue()
    }

    @Test
    fun `a screen that is already off releases the wake lock without consulting usage stats`() {
        appContext.recordForegroundApp(PKG_ZEBRA, millisAgo = 8_000)
        startMonitoring(PKG_ZEBRA)
        pollNow()
        assertThat(wakeLockHeld()).isTrue()

        setScreenInteractive(false)
        advance(10)

        assertThat(wakeLockHeld()).isFalse()
    }

    @Test
    fun `nothing is acquired while the screen is off even for a stimmed app`() {
        setScreenInteractive(false)
        appContext.recordForegroundApp(PKG_ZEBRA, millisAgo = 2_000)
        startMonitoring(PKG_ZEBRA)

        pollNow()

        assertThat(ShadowPowerManager.getLatestWakeLock()).isNull()
    }

    @Test
    fun `repeated polls do not stack wake locks`() {
        appContext.recordForegroundApp(PKG_ZEBRA, millisAgo = 2_000)
        startMonitoring(PKG_ZEBRA)

        pollNow()
        advance(10)
        advance(10)
        advance(10)

        val wakeLock = ShadowPowerManager.getLatestWakeLock()
        assertThat(wakeLock.isHeld).isTrue()
        assertThat(shadowOf(wakeLock).timesHeld).isEqualTo(1)
    }

    @Test
    fun `the loop keeps polling on the ten second interval`() {
        appContext.recordForegroundApp(PKG_APPLE, millisAgo = 15_000)
        startMonitoring(PKG_ZEBRA)
        pollNow()
        assertThat(wakeLockHeld()).isFalse()

        // Not yet due: the next tick is 10s after the first.
        appContext.recordForegroundApp(PKG_ZEBRA, millisAgo = 1_000)
        advance(9)
        assertThat(wakeLockHeld()).isFalse()

        advance(1)
        assertThat(wakeLockHeld()).isTrue()
    }

    @Test
    fun `any stimmed app in the selection counts`() {
        appContext.recordForegroundApp(PKG_MANGO, millisAgo = 2_000)
        startMonitoring(PKG_ZEBRA, PKG_APPLE, PKG_MANGO)

        pollNow()

        assertThat(wakeLockHeld()).isTrue()
    }

    @Test
    fun `destroying the service releases the screen and stops polling`() {
        appContext.recordForegroundApp(PKG_ZEBRA, millisAgo = 2_000)
        startMonitoring(PKG_ZEBRA)
        pollNow()
        assertThat(wakeLockHeld()).isTrue()

        controller.destroy()

        assertThat(wakeLockHeld()).isFalse()
        // The loop must not resurrect itself after teardown.
        advance(60)
        assertThat(wakeLockHeld()).isFalse()
    }
}
