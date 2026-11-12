package dev.hermeskotlin.ui

import com.composeunstyled.theme.ColorScheme
import dev.hermeskotlin.core.settings.ThemeMode
import dev.hermeskotlin.designsystem.OlympusMode
import dev.hermeskotlin.designsystem.PureBlack

/** Whether this mode is drawn dark; [systemDark] only matters for [ThemeMode.System]. */
internal fun ThemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.System -> systemDark
    ThemeMode.Light -> false
    ThemeMode.Dark, ThemeMode.Olympus -> true
}

/** The color scheme the app is drawn in. Pure black wins over Olympus, as it does over dark: the surfaces stay black. */
internal fun colorSchemeFor(theme: ThemeMode, pureBlack: Boolean, systemDark: Boolean): ColorScheme = when {
    !theme.isDark(systemDark) -> ColorScheme.Light
    pureBlack -> PureBlack
    theme == ThemeMode.Olympus -> OlympusMode
    else -> ColorScheme.Dark
}

/** Whether the window behind the app is dusk indigo: Olympus on its own surfaces, not with pure black. */
internal fun windowIsOlympus(theme: ThemeMode, pureBlack: Boolean): Boolean = theme == ThemeMode.Olympus && !pureBlack
