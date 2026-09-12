package acidburn.stims

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end on a real device: pick an app in the UI and check that the selection is persisted
 * and that the monitoring service actually comes up.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivityInstrumentedTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun grantPermissionsBeforeTheActivityLaunches() {
            // The rule launches MainActivity before @Before runs, and onResume() bounces the
            // user to system settings unless Usage Access is already granted.
            grantUsageAccess()
            clearStimsPrefs()
        }
    }

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun tearDown() {
        stopStimsServiceAndWait()
        clearStimsPrefs()
    }

    private fun awaitLoaded() {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText("ALL APPS").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun theAppListLoadsOnARealDevice() {
        awaitLoaded()

        composeRule.onNodeWithText("Stims Manager").assertIsDisplayed()
        composeRule.onNodeWithText("Search apps to stim...").assertIsDisplayed()
        // Every device has at least a settings app in the launcher.
        assertThat(composeRule.onAllNodesWithText("ALL APPS").fetchSemanticsNodes()).isNotEmpty()
    }

    @Test
    fun theStimButtonStartsOutDisabled() {
        awaitLoaded()

        composeRule.onNodeWithText("STIM SELECTED APPS").assertIsNotEnabled()
    }

    @Test
    fun searchingNarrowsTheListDownToThisApp() {
        awaitLoaded()

        composeRule.onNodeWithText("Search apps to stim...").performTextInput("acidburn")

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(targetContext.packageName).fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText(targetContext.packageName).assertIsDisplayed()
    }

    @Test
    fun stimmingAnAppPersistsItAndStartsTheService() {
        awaitLoaded()
        composeRule.onNodeWithText("Search apps to stim...").performTextInput("acidburn")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(targetContext.packageName).fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithText(targetContext.packageName).performClick()
        composeRule.onNodeWithText("STIM SELECTED APPS").performClick()
        composeRule.waitForIdle()

        assertThat(
            stimsPrefs().getStringSet(StimsService.KEY_STIMMED_APPS, emptySet())
        ).contains(targetContext.packageName)
        assertThat(waitForServiceForeground()).isTrue()
    }

    @Test
    fun removingTheLastStimmedAppStopsTheService() {
        awaitLoaded()
        composeRule.onNodeWithText("Search apps to stim...").performTextInput("acidburn")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(targetContext.packageName).fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText(targetContext.packageName).performClick()
        composeRule.onNodeWithText("STIM SELECTED APPS").performClick()
        // Compose's test clock only ticks while the framework is waiting, so the LaunchedEffect
        // that starts the service does not run until we idle here.
        composeRule.waitForIdle()
        assertThat(waitForServiceForeground()).isTrue()

        composeRule.onNodeWithContentDescription("Remove").performClick()
        composeRule.waitForIdle()

        assertThat(
            stimsPrefs().getStringSet(StimsService.KEY_STIMMED_APPS, emptySet())
        ).isEmpty()
        assertThat(waitFor { !isStimsServiceRunning() }).isTrue()
    }

    @Test
    fun theSettingsScreenIsReachableAndShowsTheBuild() {
        awaitLoaded()

        composeRule.onNodeWithContentDescription("Settings").performClick()

        composeRule.onNodeWithText("Force awake overlay").assertIsDisplayed()
        composeRule.onNodeWithText(BuildConfig.VERSION_NAME).assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Stims Manager").assertIsDisplayed()
    }
}
