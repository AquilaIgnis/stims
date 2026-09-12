package acidburn.stims

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The receiver's whole purpose is to bring the service back after a reboot, so on-device the
 * thing worth checking is that the service really comes up from a bare intent.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BootReceiverInstrumentedTest {

    @Before
    fun setUp() {
        grantUsageAccess()
        clearStimsPrefs()
        requireServiceStopped()
    }

    @After
    fun tearDown() {
        stopStimsServiceAndWait()
        clearStimsPrefs()
    }

    private fun deliverBoot(action: String) {
        BootReceiver().onReceive(targetContext, Intent(action))
    }

    @Test
    fun bootBroadcastBringsTheServiceBackUp() {
        stimsPrefs().edit()
            .putStringSet(StimsService.KEY_STIMMED_APPS, setOf(targetContext.packageName))
            .commit()

        deliverBoot(Intent.ACTION_BOOT_COMPLETED)

        assertThat(waitForServiceForeground()).isTrue()
        assertThat(isStimsServiceRunning()).isTrue()
    }

    @Test
    fun anAppUpdateBringsTheServiceBackUp() {
        stimsPrefs().edit()
            .putStringSet(StimsService.KEY_STIMMED_APPS, setOf(targetContext.packageName))
            .commit()

        deliverBoot(Intent.ACTION_MY_PACKAGE_REPLACED)

        assertThat(waitForServiceForeground()).isTrue()
        assertThat(isStimsServiceRunning()).isTrue()
    }

    @Test
    fun bootWithNothingStimmedLeavesTheServiceDown() {
        deliverBoot(Intent.ACTION_BOOT_COMPLETED)

        assertThat(waitFor(timeoutMillis = 2_000) { isStimsServiceRunning() }).isFalse()
    }

    @Test
    fun anUnrelatedBroadcastLeavesTheServiceDown() {
        stimsPrefs().edit()
            .putStringSet(StimsService.KEY_STIMMED_APPS, setOf(targetContext.packageName))
            .commit()

        deliverBoot(Intent.ACTION_POWER_CONNECTED)

        assertThat(waitFor(timeoutMillis = 2_000) { isStimsServiceRunning() }).isFalse()
    }
}
