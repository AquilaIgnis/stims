package acidburn.stims

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * MainActivity's job outside of Compose: make sure the user is sent to grant Usage Access, since
 * without it the service can never see which app is in the foreground.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    private fun setUsageAccess(allowed: Boolean) {
        val appOps = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        shadowOf(appOps).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            appContext.packageName,
            if (allowed) AppOpsManager.MODE_ALLOWED else AppOpsManager.MODE_IGNORED,
        )
    }

    @Test
    fun `without usage access the user is sent to the usage access settings`() {
        setUsageAccess(allowed = false)

        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        val next = shadowOf(activity).nextStartedActivity
        assertThat(next).isNotNull()
        assertThat(next.action).isEqualTo(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    }

    @Test
    fun `without usage access the user is told why`() {
        setUsageAccess(allowed = false)

        Robolectric.buildActivity(MainActivity::class.java).setup()

        assertThat(ShadowToast.getTextOfLatestToast())
            .isEqualTo("Please enable Usage Stats permission")
    }

    @Test
    fun `with usage access granted the user is left alone`() {
        setUsageAccess(allowed = true)

        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        assertThat(shadowOf(activity).nextStartedActivity).isNull()
        assertThat(ShadowToast.getTextOfLatestToast()).isNull()
    }

    @Test
    fun `the permission check is repeated every time the activity resumes`() {
        setUsageAccess(allowed = true)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertThat(shadowOf(controller.get()).nextStartedActivity).isNull()

        // The user revokes the permission from Settings and comes back.
        setUsageAccess(allowed = false)
        controller.pause().resume()

        assertThat(shadowOf(controller.get()).nextStartedActivity.action)
            .isEqualTo(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    }

    @Test
    @Config(sdk = [24])
    fun `the pre-Q permission check path behaves the same`() {
        // unsafeCheckOpNoThrow() only exists from Q; minSdk 24 falls back to checkOpNoThrow().
        setUsageAccess(allowed = false)

        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        assertThat(shadowOf(activity).nextStartedActivity.action)
            .isEqualTo(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    }

    @Test
    fun `the activity survives a configuration change`() {
        setUsageAccess(allowed = true)

        val activity = Robolectric.buildActivity(MainActivity::class.java)
            .setup()
            .recreate()
            .get()

        assertThat(activity.isFinishing).isFalse()
    }
}
