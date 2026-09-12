package acidburn.stims

import android.app.Application
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Without the boot receiver the service only exists once MainActivity has been opened since the
 * last reboot, so "does a boot broadcast restart monitoring" is the behaviour worth pinning.
 */
@RunWith(AndroidJUnit4::class)
class BootReceiverTest {

    private fun deliver(action: String?) {
        BootReceiver().onReceive(appContext, Intent().apply { this.action = action })
    }

    private fun startedService(): Intent? =
        shadowOf(appContext as Application).nextStartedService

    @Test
    fun `BOOT_COMPLETED restarts the service when apps are stimmed`() {
        persistStimmed(PKG_ZEBRA)

        deliver(Intent.ACTION_BOOT_COMPLETED)

        val started = startedService()
        assertThat(started).isNotNull()
        assertThat(started!!.component!!.className).isEqualTo(StimsService::class.java.name)
    }

    @Test
    fun `an app update restarts the service`() {
        persistStimmed(PKG_ZEBRA)

        deliver(Intent.ACTION_MY_PACKAGE_REPLACED)

        assertThat(startedService()).isNotNull()
    }

    @Test
    fun `OEM quick boot broadcasts restart the service`() {
        persistStimmed(PKG_ZEBRA)

        deliver("android.intent.action.QUICKBOOT_POWERON")

        assertThat(startedService()).isNotNull()
    }

    @Test
    fun `the HTC quick boot broadcast restarts the service`() {
        persistStimmed(PKG_ZEBRA)

        deliver("com.htc.intent.action.QUICKBOOT_POWERON")

        assertThat(startedService()).isNotNull()
    }

    @Test
    fun `unrelated broadcasts are ignored`() {
        persistStimmed(PKG_ZEBRA)

        deliver(Intent.ACTION_POWER_CONNECTED)

        assertThat(startedService()).isNull()
    }

    @Test
    fun `an actionless broadcast is ignored`() {
        persistStimmed(PKG_ZEBRA)

        deliver(null)

        assertThat(startedService()).isNull()
    }

    @Test
    fun `booting with nothing stimmed does not start the service`() {
        deliver(Intent.ACTION_BOOT_COMPLETED)

        assertThat(startedService()).isNull()
    }

    @Test
    fun `clearing the selection stops the receiver from starting anything`() {
        persistStimmed(PKG_ZEBRA)
        persistStimmed()

        deliver(Intent.ACTION_BOOT_COMPLETED)

        assertThat(startedService()).isNull()
    }

    @Test
    fun `the boot intent carries no extras so the service reads the saved selection`() {
        persistStimmed(PKG_ZEBRA, PKG_APPLE)

        deliver(Intent.ACTION_BOOT_COMPLETED)

        val started = startedService()!!
        assertThat(started.extras).isNull()
        assertThat(started.action).isNull()
    }

    @Test
    fun `only one service start is requested per boot broadcast`() {
        persistStimmed(PKG_ZEBRA)

        deliver(Intent.ACTION_BOOT_COMPLETED)

        assertThat(shadowOf(appContext as Application).allStartedServices).hasSize(1)
    }

    @Test
    @Config(sdk = [24])
    fun `pre-Oreo devices still get the service started`() {
        // minSdk is 24, where startForegroundService() does not exist yet.
        persistStimmed(PKG_ZEBRA)

        deliver(Intent.ACTION_BOOT_COMPLETED)

        assertThat(startedService()).isNotNull()
    }
}
