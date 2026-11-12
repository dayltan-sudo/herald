package dev.hermeskotlin.ui

import dev.hermeskotlin.core.settings.ThemeMode
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

    // Compares the choice, not the ColorScheme objects: those live in HermesTheme.kt, whose setup can't run in a host test.
    @Test
    fun theSchemeFollowsTheModeWhateverTheSystemIs() {
        for (systemDark in listOf(false, true)) {
            assertEquals(SchemeChoice.Light, schemeChoiceFor(ThemeMode.Light, pureBlack = false, systemDark = systemDark))
            assertEquals(SchemeChoice.Dark, schemeChoiceFor(ThemeMode.Dark, pureBlack = false, systemDark = systemDark))
            assertEquals(SchemeChoice.Olympus, schemeChoiceFor(ThemeMode.Olympus, pureBlack = false, systemDark = systemDark))
        }
        assertEquals(SchemeChoice.Dark, schemeChoiceFor(ThemeMode.System, pureBlack = false, systemDark = true))
        assertEquals(SchemeChoice.Light, schemeChoiceFor(ThemeMode.System, pureBlack = false, systemDark = false))
    }

    @Test
    fun pureBlackStaysBlackInOlympusAsInDark() {
        assertEquals(SchemeChoice.PureBlack, schemeChoiceFor(ThemeMode.Olympus, pureBlack = true, systemDark = false))
        assertEquals(SchemeChoice.PureBlack, schemeChoiceFor(ThemeMode.Dark, pureBlack = true, systemDark = false))
        assertEquals(SchemeChoice.PureBlack, schemeChoiceFor(ThemeMode.System, pureBlack = true, systemDark = true))
        // Light has no black variant.
        assertEquals(SchemeChoice.Light, schemeChoiceFor(ThemeMode.Light, pureBlack = true, systemDark = true))
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
