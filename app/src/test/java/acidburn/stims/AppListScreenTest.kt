package acidburn.stims

import acidburn.stims.ui.theme.StimsTheme
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * The main screen is where the selection is made, persisted and handed to the service, so these
 * tests follow that whole path rather than just checking that labels render.
 */
@RunWith(AndroidJUnit4::class)
// A phone-sized viewport; Robolectric's default screen is too short to lay the whole list out.
@Config(qualifiers = "w411dp-h891dp")
class AppListScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var overlaySettingsOpened = 0

    @Before
    fun installApps() {
        installLauncherApp(PKG_ZEBRA, "Zebra")
        installLauncherApp(PKG_APPLE, "Apple")
        installLauncherApp(PKG_MANGO, "Mango")
    }

    private fun showAppList(showOverlayWarning: Boolean = false) {
        composeRule.setContent {
            StimsTheme {
                AppListScreen(
                    prefs = stimsPrefs(),
                    showOverlayWarning = showOverlayWarning,
                    onOpenOverlaySettings = { overlaySettingsOpened++ },
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("ALL APPS").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun search(query: String) =
        composeRule.onNodeWithText("Search apps to stim...").performTextInput(query)

    private fun replaceSearch(query: String) =
        composeRule.onNode(
            androidx.compose.ui.test.hasSetTextAction()
        ).performTextReplacement(query)

    private fun verticalPositionOf(text: String): Float =
        composeRule.onNodeWithText(text).fetchSemanticsNode().positionInRoot.y

    private fun serviceStarts() = shadowOf(appContext as Application).allStartedServices

    private fun clearServiceHistory() =
        shadowOf(appContext as Application).clearStartedServices()

    // ---- listing ----

    @Test
    fun `every launchable app is listed`() {
        showAppList()

        composeRule.onNodeWithText("Zebra").assertIsDisplayed()
        composeRule.onNodeWithText("Apple").assertIsDisplayed()
        composeRule.onNodeWithText("Mango").assertIsDisplayed()
    }

    @Test
    fun `apps are listed alphabetically by label, not by package`() {
        showAppList()

        assertThat(verticalPositionOf("Apple")).isLessThan(verticalPositionOf("Mango"))
        assertThat(verticalPositionOf("Mango")).isLessThan(verticalPositionOf("Zebra"))
    }

    @Test
    fun `the package name is shown so lookalike apps can be told apart`() {
        showAppList()

        composeRule.onNodeWithText(PKG_ZEBRA).assertIsDisplayed()
    }

    @Test
    fun `an app exposing several launcher activities is listed once`() {
        // Some apps register more than one LAUNCHER activity; the list must collapse them.
        shadowOf(appContext.packageManager).addResolveInfoForIntent(
            launcherProbeIntent(),
            launcherResolveInfo(PKG_ZEBRA, "Zebra"),
        )

        showAppList()

        assertThat(composeRule.onAllNodesWithText("Zebra").fetchSemanticsNodes()).hasSize(1)
    }

    // ---- search ----

    @Test
    fun `search filters by app name`() {
        showAppList()

        search("zeb")

        composeRule.onNodeWithText("Zebra").assertIsDisplayed()
        assertThat(composeRule.onAllNodesWithText("Apple").fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun `search filters by package name`() {
        showAppList()

        search("example.mango")

        composeRule.onNodeWithText("Mango").assertIsDisplayed()
        assertThat(composeRule.onAllNodesWithText("Zebra").fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun `search ignores case`() {
        showAppList()

        search("APPLE")

        composeRule.onNodeWithText("Apple").assertIsDisplayed()
    }

    @Test
    fun `clearing the search restores the full list`() {
        showAppList()
        search("zeb")
        assertThat(composeRule.onAllNodesWithText("Apple").fetchSemanticsNodes()).isEmpty()

        replaceSearch("")

        composeRule.onNodeWithText("Apple").assertIsDisplayed()
        composeRule.onNodeWithText("Mango").assertIsDisplayed()
    }

    @Test
    fun `a search that matches nothing leaves the list empty`() {
        showAppList()

        search("no such app")

        assertThat(composeRule.onAllNodesWithText("Zebra").fetchSemanticsNodes()).isEmpty()
        assertThat(composeRule.onAllNodesWithText("Apple").fetchSemanticsNodes()).isEmpty()
        assertThat(composeRule.onAllNodesWithText("Mango").fetchSemanticsNodes()).isEmpty()
    }

    // ---- selecting and stimming ----

    @Test
    fun `the stim button is disabled until something is selected`() {
        showAppList()

        composeRule.onNodeWithText("STIM SELECTED APPS").assertIsNotEnabled()

        composeRule.onNodeWithText("Zebra").performClick()

        composeRule.onNodeWithText("STIM SELECTED APPS").assertIsEnabled()
    }

    @Test
    fun `tapping a selected app deselects it again`() {
        showAppList()
        composeRule.onNodeWithText("Zebra").performClick()

        composeRule.onNodeWithText("Zebra").performClick()

        composeRule.onNodeWithText("STIM SELECTED APPS").assertIsNotEnabled()
    }

    @Test
    fun `stimming moves the app into the stay awake section`() {
        showAppList()
        composeRule.onNodeWithText("Zebra").performClick()

        composeRule.onNodeWithText("STIM SELECTED APPS").performClick()

        composeRule.onNodeWithText("STAY AWAKE (STIMMED)").assertIsDisplayed()
        composeRule.onNodeWithText("STIMMED").assertIsDisplayed()
    }

    @Test
    fun `a stimmed app is no longer offered in the all apps list`() {
        showAppList()
        composeRule.onNodeWithText("Zebra").performClick()
        composeRule.onNodeWithText("STIM SELECTED APPS").performClick()

        // Once stimmed it appears exactly once — in the stimmed section.
        assertThat(composeRule.onAllNodesWithText("Zebra").fetchSemanticsNodes()).hasSize(1)
        assertThat(composeRule.onAllNodesWithText(PKG_ZEBRA).fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun `stimming confirms to the user`() {
        showAppList()
        composeRule.onNodeWithText("Zebra").performClick()

        composeRule.onNodeWithText("STIM SELECTED APPS").performClick()

        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Apps Stimmed!")
    }

    @Test
    fun `several apps can be stimmed at once`() {
        showAppList()
        composeRule.onNodeWithText("Zebra").performClick()
        composeRule.onNodeWithText("Apple").performClick()

        composeRule.onNodeWithText("STIM SELECTED APPS").performClick()
        composeRule.waitForIdle()

        assertThat(persistedStimmed()).containsExactly(PKG_ZEBRA, PKG_APPLE)
    }

    @Test
    fun `the selection is persisted so it survives the app being killed`() {
        showAppList()
        composeRule.onNodeWithText("Mango").performClick()

        composeRule.onNodeWithText("STIM SELECTED APPS").performClick()
        composeRule.waitForIdle()

        assertThat(persistedStimmed()).containsExactly(PKG_MANGO)
    }

    @Test
    fun `a previously stimmed app is restored on launch`() {
        persistStimmed(PKG_APPLE)

        showAppList()

        composeRule.onNodeWithText("STAY AWAKE (STIMMED)").assertIsDisplayed()
        composeRule.onNodeWithText("STIMMED").assertIsDisplayed()
        assertThat(composeRule.onAllNodesWithText("Apple").fetchSemanticsNodes()).hasSize(1)
    }

    @Test
    fun `removing a stimmed app takes it off the list and out of storage`() {
        persistStimmed(PKG_APPLE)
        showAppList()

        composeRule.onNodeWithContentDescription("Remove").performClick()
        composeRule.waitForIdle()

        assertThat(persistedStimmed()).isEmpty()
        assertThat(composeRule.onAllNodesWithText("STAY AWAKE (STIMMED)").fetchSemanticsNodes())
            .isEmpty()
        composeRule.onNodeWithText(PKG_APPLE).assertIsDisplayed()
    }

    // ---- talking to the service ----

    @Test
    fun `stimming starts the monitoring service with the selection`() {
        showAppList()
        clearServiceHistory()
        composeRule.onNodeWithText("Zebra").performClick()

        composeRule.onNodeWithText("STIM SELECTED APPS").performClick()
        composeRule.waitForIdle()

        val started = serviceStarts().last()
        assertThat(started.component!!.className).isEqualTo(StimsService::class.java.name)
        assertThat(started.getStringArrayListExtra(StimsService.EXTRA_SELECTED_PACKAGES))
            .containsExactly(PKG_ZEBRA)
    }

    @Test
    fun `the service is told whether the overlay strategy was forced`() {
        persistForceOverlay(true)
        showAppList()
        clearServiceHistory()
        composeRule.onNodeWithText("Zebra").performClick()

        composeRule.onNodeWithText("STIM SELECTED APPS").performClick()
        composeRule.waitForIdle()

        assertThat(serviceStarts().last().getBooleanExtra(StimsService.EXTRA_FORCE_OVERLAY, false))
            .isTrue()
    }

    @Test
    fun `emptying the selection stops the service instead of starting it`() {
        persistStimmed(PKG_APPLE)
        showAppList()

        composeRule.onNodeWithContentDescription("Remove").performClick()
        composeRule.waitForIdle()

        val stopped = shadowOf(appContext as Application).nextStoppedService
        assertThat(stopped).isNotNull()
        assertThat(stopped.component!!.className).isEqualTo(StimsService::class.java.name)
    }

    @Test
    fun `an empty selection on launch does not start the service`() {
        showAppList()

        assertThat(serviceStarts()).isEmpty()
    }

    // ---- overlay warning banner ----

    @Test
    fun `the overlay warning is hidden when the permission is not needed`() {
        showAppList(showOverlayWarning = false)

        assertThat(
            composeRule.onAllNodesWithText("Overlay permission required (Samsung)")
                .fetchSemanticsNodes()
        ).isEmpty()
    }

    @Test
    fun `the overlay warning explains what to do`() {
        showAppList(showOverlayWarning = true)

        composeRule.onNodeWithText("Overlay permission required (Samsung)").assertIsDisplayed()
        composeRule.onNodeWithText("Tap → Allow display over other apps").assertIsDisplayed()
    }

    @Test
    fun `tapping the overlay warning opens the system permission screen`() {
        showAppList(showOverlayWarning = true)

        composeRule.onNodeWithText("Overlay permission required (Samsung)").performClick()

        assertThat(overlaySettingsOpened).isEqualTo(1)
    }

    // ---- navigation ----

    @Test
    fun `the settings button opens the settings screen`() {
        showAppList()

        composeRule.onNodeWithContentDescription("Settings").performClick()

        composeRule.onNodeWithText("Force awake overlay").assertIsDisplayed()
        composeRule.onNodeWithText("Version").assertIsDisplayed()
    }

    @Test
    fun `the back arrow returns from settings to the app list`() {
        showAppList()
        composeRule.onNodeWithContentDescription("Settings").performClick()

        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithText("Stims Manager").assertIsDisplayed()
        composeRule.onNodeWithText("ALL APPS").assertIsDisplayed()
    }

    @Test
    fun `changing the overlay setting is persisted`() {
        showAppList()
        composeRule.onNodeWithContentDescription("Settings").performClick()

        composeRule.onNode(androidx.compose.ui.test.isToggleable()).performClick()
        composeRule.waitForIdle()

        assertThat(stimsPrefs().getBoolean(StimsService.KEY_FORCE_OVERLAY, false)).isTrue()
    }
}
