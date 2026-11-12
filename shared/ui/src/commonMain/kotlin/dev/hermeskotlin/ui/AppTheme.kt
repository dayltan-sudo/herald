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

/** Which scheme the app is drawn in; kept apart from the [ColorScheme] objects so it can be tested without building the theme. */
internal enum class SchemeChoice { Light, Dark, Olympus, PureBlack }

/** Pure black wins over Olympus, as it does over dark: the surfaces stay black. */
internal fun schemeChoiceFor(theme: ThemeMode, pureBlack: Boolean, systemDark: Boolean): SchemeChoice = when {
    !theme.isDark(systemDark) -> SchemeChoice.Light
    pureBlack -> SchemeChoice.PureBlack
    theme == ThemeMode.Olympus -> SchemeChoice.Olympus
    else -> SchemeChoice.Dark
}

/** The color scheme the app is drawn in. */
internal fun colorSchemeFor(theme: ThemeMode, pureBlack: Boolean, systemDark: Boolean): ColorScheme =
    when (schemeChoiceFor(theme, pureBlack, systemDark)) {
        SchemeChoice.Light -> ColorScheme.Light
        SchemeChoice.Dark -> ColorScheme.Dark
        SchemeChoice.Olympus -> OlympusMode
        SchemeChoice.PureBlack -> PureBlack
    }

/** Whether the window behind the app is dusk indigo: Olympus on its own surfaces, not with pure black. */
internal fun windowIsOlympus(theme: ThemeMode, pureBlack: Boolean): Boolean = theme == ThemeMode.Olympus && !pureBlack
