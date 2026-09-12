package acidburn.stims

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Looper
import android.os.PowerManager
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBuild
import org.robolectric.shadows.ShadowPowerManager
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl

/**
 * Samsung/Realme ROMs quietly ignore SCREEN_BRIGHT_WAKE_LOCK, so on those devices the service
 * keeps the screen on with a 1x1 FLAG_KEEP_SCREEN_ON overlay window instead. These tests pin
 * down when that strategy is chosen and that the window is added and removed symmetrically.
 */
@RunWith(AndroidJUnit4::class)
class StimsServiceOverlayTest {

    private lateinit var controller: ServiceController<StimsService>

    @Before
    fun grantOverlayPermission() {
        ShadowSettings.setCanDrawOverlays(true)
    }

    private fun startMonitoring(intent: Intent): StimsService {
        controller = Robolectric.buildService(StimsService::class.java, intent).create()
        controller.get().onStartCommand(intent, 0, 1)
        return controller.get()
    }

    private fun pollNow() = shadowOf(Looper.getMainLooper()).idle()

    private fun advance(seconds: Long) =
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))

    private fun windowManagerViews(): List<android.view.View> {
        val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return Shadow.extract<ShadowWindowManagerImpl>(windowManager).views
    }

    private fun setScreenInteractive(interactive: Boolean) {
        shadowOf(appContext.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .setIsInteractive(interactive)
    }

    @Test
    fun `samsung devices keep the screen on with an overlay instead of a wake lock`() {
        ShadowBuild.setManufacturer("samsung")
        appContext.recordForegroundApp(PKG_ZEBRA, millisAgo = 2_000)
        val service = startMonitoring(serviceIntent(PKG_ZEBRA))

        pollNow()

        assertThat(service.overlayViewForTest()).isNotNull()
        assertThat(windowManagerViews()).contains(service.overlayViewForTest())
        assertThat(ShadowPowerManager.getLatestWakeLock()).isNull()
    }

    @Test
    fun `vendor matching ignores case`() {
        ShadowBuild.setManufacturer("SAMSUNG")
        appContext.recordForegroundApp(PKG_ZEBRA, millisAgo = 2_000)
        val service = startMonitoring(serviceIntent(PKG_ZEBRA))

        pollNow()

        assertThat(service.overlayViewForTest()).isNotNull()
    }

    @Test
    fun `realme is also an overlay vendor`() {
        ShadowBuild.setManufacturer("realme")
        appContext.recordForegroundApp(PKG_ZEBRA, millisAgo = 2_000)
        val service = startMonitoring(serviceIntent(PKG_ZEBRA))

        pollNow()

        assertThat(service.overlayViewForTest()).isNotNull()
    }

    @Test
    fun `stock devices use the wake lock and add no window`() {
        ShadowBuild.setManufacturer("Google")
        appContext.recordForegroundApp(PKG_ZEBRA, millisAgo = 2_000)
        val service = startMonitoring(serviceIntent(PKG_ZEBRA))

        pollNow()

        assertThat(service.overlayViewForTest()).isNull()
        assertThat(ShadowPowerManager.getLatestWakeLock()!!.isHeld).isTrue()
    }

    @Test
    fun `the force-overlay extra switches a stock device onto the overlay`() {
        ShadowBuild.setManufacturer("Google")
        appContext.recordForegroundApp(PKG_ZEBRA, millisAgo = 2_000)
        val service = startMonitoring(serviceIntent(PKG_ZEBRA, forceOverlay = true))

        pollNow()

        assertThat(service.overlayViewForTest()).isNotNull()
        assertThat(ShadowPowerManager.getLatestWakeLock()).isNull()
    }

    @Test
    fun `force overlay falls back to the saved preference when the intent omits it`() {
        // This is the boot-restart path: BootReceiver sends a bare intent.
        ShadowBuild.setManufacturer("Google")
        persistStimmed(PKG_ZEBRA)
        persistForceOverlay(true)
        appContext.recordForegroundApp(PKG_ZEBRA, millisAgo = 2_000)
        val service = startMonitoring(Intent(appContext, StimsService::class.java))

        pollNow()

        assertThat(service.overlayViewForTest()).isNotNull()
    }

    @Test
    fun `a vendor device stays on the overlay even when force overlay is off`() {
        ShadowBuild.setManufacturer("samsung")
        appContext.recordForegroundApp(PKG_ZEBRA, millisAgo = 2_000)
        val service = startMonitoring(serviceIntent(PKG_ZEBRA, forceOverlay = false))

        pollNow()

        assertThat(service.overlayViewForTest()).isNotNull()
    }

    @Test
    fun `without the overlay permission nothing is added and nothing crashes`() {
        ShadowSettings.setCanDrawOverlays(false)
        ShadowBuild.setManufacturer("samsung")
        appContext.recordForegroundApp(PKG_ZEBRA, millisAgo = 2_000)
        val service = startMonitoring(serviceIntent(PKG_ZEBRA))

        pollNow()

        assertThat(service.overlayViewForTest()).isNull()
        assertThat(windowManagerViews()).isEmpty()
    }

    @Test
    fun `the overlay is an invisible one-by-one keep-screen-on window`() {
        ShadowBuild.setManufacturer("samsung")
        appContext.recordForegroundApp(PKG_ZEBRA, millisAgo = 2_000)
        val service = startMonitoring(serviceIntent(PKG_ZEBRA))

        pollNow()

        val params = service.overlayViewForTest()!!.layoutParams as WindowManager.LayoutParams
        assertThat(params.width).isEqualTo(1)
        assertThat(params.height).isEqualTo(1)
        assertThat(params.type).isEqualTo(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        assertThat(params.format).isEqualTo(PixelFormat.TRANSLUCENT)
        // The whole point of the window: it must keep the screen on...
        assertThat(params.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON).isNotEqualTo(0)
        // ...while staying completely out of the user's way.
        assertThat(params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE).isNotEqualTo(0)
        assertThat(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE).isNotEqualTo(0)
    }

    @Test
    fun `the overlay is added once across repeated polls`() {
        ShadowBuild.setManufacturer("samsung")
        appContext.recordForegroundApp(PKG_ZEBRA, millisAgo = 2_000)
        val service = startMonitoring(serviceIntent(PKG_ZEBRA))

        pollNow()
        val firstView = service.overlayViewForTest()
        advance(10)
        advance(10)

        assertThat(service.overlayViewForTest()).isSameInstanceAs(firstView)
        assertThat(windowManagerViews()).hasSize(1)
    }

    @Test
    fun `leaving the stimmed app removes the overlay`() {
        ShadowBuild.setManufacturer("samsung")
        appContext.recordForegroundApp(PKG_ZEBRA, millisAgo = 8_000)
        val service = startMonitoring(serviceIntent(PKG_ZEBRA))
        pollNow()
        assertThat(service.overlayViewForTest()).isNotNull()

        appContext.recordForegroundApp(PKG_APPLE, millisAgo = 1_000)
        advance(10)

        assertThat(service.overlayViewForTest()).isNull()
        assertThat(windowManagerViews()).isEmpty()
    }

    @Test
    fun `turning the screen off removes the overlay`() {
        ShadowBuild.setManufacturer("samsung")
        appContext.recordForegroundApp(PKG_ZEBRA, millisAgo = 8_000)
        val service = startMonitoring(serviceIntent(PKG_ZEBRA))
        pollNow()
        assertThat(service.overlayViewForTest()).isNotNull()

        setScreenInteractive(false)
        advance(10)

        assertThat(service.overlayViewForTest()).isNull()
    }

    @Test
    fun `destroying the service removes the overlay`() {
        ShadowBuild.setManufacturer("samsung")
        appContext.recordForegroundApp(PKG_ZEBRA, millisAgo = 2_000)
        val service = startMonitoring(serviceIntent(PKG_ZEBRA))
        pollNow()
        assertThat(service.overlayViewForTest()).isNotNull()

        controller.destroy()

        assertThat(service.overlayViewForTest()).isNull()
        assertThat(windowManagerViews()).isEmpty()
    }
}
