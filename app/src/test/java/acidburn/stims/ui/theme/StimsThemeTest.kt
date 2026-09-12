package acidburn.stims.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Which Material 3 colour scheme the app ends up with, per OS version and system theme. */
@RunWith(AndroidJUnit4::class)
class StimsThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun appliedScheme(): ColorScheme {
        lateinit var scheme: ColorScheme
        composeRule.setContent {
            StimsTheme { scheme = MaterialTheme.colorScheme }
        }
        composeRule.waitForIdle()
        return scheme
    }

    /**
     * Captures the scheme the theme applied alongside the wallpaper-derived one, in a single
     * composition — the test rule only allows one setContent per test.
     */
    private fun appliedVsDynamic(dark: Boolean): Pair<ColorScheme, ColorScheme> {
        lateinit var applied: ColorScheme
        lateinit var dynamic: ColorScheme
        composeRule.setContent {
            val context = LocalContext.current
            dynamic = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            StimsTheme { applied = MaterialTheme.colorScheme }
        }
        composeRule.waitForIdle()
        return applied to dynamic
    }

    @Test
    @Config(sdk = [30])
    fun `before Android 12 the static light scheme is used`() {
        assertThat(appliedScheme().primary).isEqualTo(lightColorScheme().primary)
    }

    @Test
    @Config(sdk = [30], qualifiers = "night")
    fun `before Android 12 the static dark scheme is used in dark mode`() {
        assertThat(appliedScheme().primary).isEqualTo(darkColorScheme().primary)
    }

    @Test
    @Config(sdk = [30])
    fun `the light and dark schemes actually differ`() {
        assertThat(lightColorScheme().background).isNotEqualTo(darkColorScheme().background)
    }

    @Test
    fun `from Android 12 the wallpaper-derived light scheme is used`() {
        val (applied, dynamic) = appliedVsDynamic(dark = false)
        assertThat(applied.primary).isEqualTo(dynamic.primary)
        assertThat(applied.background).isEqualTo(dynamic.background)
    }

    @Test
    @Config(qualifiers = "night")
    fun `from Android 12 the wallpaper-derived dark scheme is used`() {
        val (applied, dynamic) = appliedVsDynamic(dark = true)
        assertThat(applied.primary).isEqualTo(dynamic.primary)
        assertThat(applied.background).isEqualTo(dynamic.background)
    }

    @Test
    @Config(qualifiers = "night")
    fun `dark mode produces a dark background`() {
        val scheme = appliedScheme()
        val luminance = 0.299f * scheme.background.red +
            0.587f * scheme.background.green +
            0.114f * scheme.background.blue
        assertThat(luminance).isLessThan(0.5f)
    }
}
