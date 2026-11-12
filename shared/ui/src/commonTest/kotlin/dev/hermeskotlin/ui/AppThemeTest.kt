package dev.hermeskotlin.ui

import com.composeunstyled.theme.ColorScheme
import dev.hermeskotlin.core.settings.ThemeMode
import dev.hermeskotlin.designsystem.OlympusMode
import dev.hermeskotlin.designsystem.PureBlack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppThemeTest {

    @Test
    fun onlySystemFollowsTheSystemAndOlympusIsDark() {
        for (systemDark in listOf(false, true)) {
            assertEquals(systemDark, ThemeMode.System.isDark(systemDark))
            assertFalse(ThemeMode.Light.isDark(systemDark))
            assertTrue(ThemeMode.Dark.isDark(systemDark))
            assertTrue(ThemeMode.Olympus.isDark(systemDark))
        }
    }

    @Test
    fun theSchemeFollowsTheModeWhateverTheSystemIs() {
        for (systemDark in listOf(false, true)) {
            assertEquals(ColorScheme.Light, colorSchemeFor(ThemeMode.Light, pureBlack = false, systemDark = systemDark))
            assertEquals(ColorScheme.Dark, colorSchemeFor(ThemeMode.Dark, pureBlack = false, systemDark = systemDark))
            assertEquals(OlympusMode, colorSchemeFor(ThemeMode.Olympus, pureBlack = false, systemDark = systemDark))
        }
        assertEquals(ColorScheme.Dark, colorSchemeFor(ThemeMode.System, pureBlack = false, systemDark = true))
        assertEquals(ColorScheme.Light, colorSchemeFor(ThemeMode.System, pureBlack = false, systemDark = false))
    }

    @Test
    fun pureBlackStaysBlackInOlympusAsInDark() {
        assertEquals(PureBlack, colorSchemeFor(ThemeMode.Olympus, pureBlack = true, systemDark = false))
        assertEquals(PureBlack, colorSchemeFor(ThemeMode.Dark, pureBlack = true, systemDark = false))
        assertEquals(PureBlack, colorSchemeFor(ThemeMode.System, pureBlack = true, systemDark = true))
        // Light has no black variant.
        assertEquals(ColorScheme.Light, colorSchemeFor(ThemeMode.Light, pureBlack = true, systemDark = true))
    }

    @Test
    fun theWindowIsIndigoOnlyForOlympusWithoutPureBlack() {
        assertTrue(windowIsOlympus(ThemeMode.Olympus, pureBlack = false))
        assertFalse(windowIsOlympus(ThemeMode.Olympus, pureBlack = true))
        for (mode in listOf(ThemeMode.System, ThemeMode.Light, ThemeMode.Dark)) {
            assertFalse(windowIsOlympus(mode, pureBlack = false), mode.name)
        }
    }
}
