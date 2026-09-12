package acidburn.stims

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import acidburn.stims.ui.theme.StimsTheme
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowBuild
import org.robolectric.shadows.ShadowSettings

/**
 * The settings screen is where a user on a non-Samsung device can opt into the overlay strategy,
 * so the status line has to tell the truth about whether the screen is actually being held awake.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var forceOverlayChanges = mutableListOf<Boolean>()
    private var overlaySettingsOpened = 0
    private var backPresses = 0

    private fun showSettings(forceOverlay: Boolean = false) {
        composeRule.setContent {
            StimsTheme {
                SettingsScreen(
                    forceOverlay = forceOverlay,
                    onForceOverlayChange = { forceOverlayChanges += it },
                    onOpenOverlaySettings = { overlaySettingsOpened++ },
                    onBack = { backPresses++ },
                )
            }
        }
    }

    @Test
    fun `shows the version the app was built with`() {
        showSettings()

        composeRule.onNodeWithText("Version").assertIsDisplayed()
        composeRule.onNodeWithText(BuildConfig.VERSION_NAME).assertIsDisplayed()
    }

    @Test
    fun `credits the author and links to the project`() {
        showSettings()

        composeRule.onNodeWithText("acidburnmonkey").assertIsDisplayed()
        composeRule.onNodeWithText("GitHub").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Open GitHub").assertIsDisplayed()
    }

    @Test
    fun `the GitHub row opens the project page`() {
        showSettings()

        composeRule.onNodeWithText("GitHub").performClick()

        val started = shadowOf(composeRule.activity).nextStartedActivity
        assertThat(started.action).isEqualTo(android.content.Intent.ACTION_VIEW)
        assertThat(started.dataString).isEqualTo("https://github.com/acidburnmonkey/stims")
    }

    @Test
    fun `the overlay is inactive on a stock device with the setting off`() {
        ShadowBuild.setManufacturer("Google")
        ShadowSettings.setCanDrawOverlays(false)

        showSettings(forceOverlay = false)

        composeRule.onNodeWithText("Inactive").assertIsDisplayed()
        composeRule.onNode(isToggleable()).assertIsOff().assertIsEnabled()
    }

    @Test
    fun `turning the overlay on without permission warns that it is not working yet`() {
        ShadowBuild.setManufacturer("Google")
        ShadowSettings.setCanDrawOverlays(false)

        showSettings(forceOverlay = true)

        composeRule.onNodeWithText("Enabled — overlay permission required").assertIsDisplayed()
        composeRule.onNode(isToggleable()).assertIsOn()
    }

    @Test
    fun `the overlay reports as active once it is on and permitted`() {
        ShadowBuild.setManufacturer("Google")
        ShadowSettings.setCanDrawOverlays(true)

        showSettings(forceOverlay = true)

        composeRule.onNodeWithText("Active — screen kept on via overlay").assertIsDisplayed()
    }

    @Test
    fun `a samsung device reports the overlay as required and locks the switch`() {
        ShadowBuild.setManufacturer("samsung")
        ShadowSettings.setCanDrawOverlays(true)

        showSettings(forceOverlay = false)

        composeRule.onNodeWithText("Active — required on this device").assertIsDisplayed()
        // The strategy is not optional here, so the user must not be able to switch it off.
        composeRule.onNode(isToggleable()).assertIsOn().assertIsNotEnabled()
    }

    @Test
    fun `a samsung device without permission still shows the switch as on but not working`() {
        ShadowBuild.setManufacturer("samsung")
        ShadowSettings.setCanDrawOverlays(false)

        showSettings(forceOverlay = false)

        composeRule.onNodeWithText("Enabled — overlay permission required").assertIsDisplayed()
        composeRule.onNode(isToggleable()).assertIsOn().assertIsNotEnabled()
    }

    @Test
    fun `switching the overlay on reports the change`() {
        ShadowBuild.setManufacturer("Google")
        ShadowSettings.setCanDrawOverlays(true)
        showSettings(forceOverlay = false)

        composeRule.onNode(isToggleable()).performClick()

        assertThat(forceOverlayChanges).containsExactly(true)
    }

    @Test
    fun `switching the overlay off reports the change`() {
        ShadowBuild.setManufacturer("Google")
        ShadowSettings.setCanDrawOverlays(true)
        showSettings(forceOverlay = true)

        composeRule.onNode(isToggleable()).performClick()

        assertThat(forceOverlayChanges).containsExactly(false)
    }

    @Test
    fun `switching the overlay on without permission jumps straight to the system settings`() {
        ShadowBuild.setManufacturer("Google")
        ShadowSettings.setCanDrawOverlays(false)
        showSettings(forceOverlay = false)

        composeRule.onNode(isToggleable()).performClick()

        assertThat(forceOverlayChanges).containsExactly(true)
        assertThat(overlaySettingsOpened).isEqualTo(1)
    }

    @Test
    fun `switching the overlay on with permission already granted does not nag`() {
        ShadowBuild.setManufacturer("Google")
        ShadowSettings.setCanDrawOverlays(true)
        showSettings(forceOverlay = false)

        composeRule.onNode(isToggleable()).performClick()

        assertThat(overlaySettingsOpened).isEqualTo(0)
    }

    @Test
    fun `the back arrow navigates back`() {
        showSettings()

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(backPresses).isEqualTo(1)
    }
}
