package dev.hermeskotlin.designsystem

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeColorsTest {

    // Every color token in Tokens.kt. A lookup of a token a scheme lacks crashes at runtime, so all must be in each one.
    private val allTokens = setOf(
        background, surface, surface2, surface3, well, thumb, surfaceElevated, input, sidebar,
        userBubble, userBubbleStroke, onUserBubble,
        text, textSecondary, textTertiary, textMuted,
        accent, onAccent, accentText, accentSoft, inverse, onInverse,
        stroke, strokeStrong,
        danger, success, warning, dangerSoft, successSoft, warningSoft,
        headingText, toolAccent, thinkingText,
    )

    private fun everyScheme(palette: AccentPalette) = listOf(
        lightColors(palette.light),
        darkColors(palette.dark),
        pureBlackColors(palette.black),
        olympusColors(palette.dark),
    )

    @Test
    fun everySchemeDefinesEveryColorToken() {
        for (palette in AccentPalette.all) {
            assertEquals(allTokens, lightColors(palette.light).keys, "${palette.name} light")
            assertEquals(allTokens, darkColors(palette.dark).keys, "${palette.name} dark")
            assertEquals(allTokens, pureBlackColors(palette.black).keys, "${palette.name} pure black")
            assertEquals(allTokens, olympusColors(palette.dark).keys, "${palette.name} olympus")
        }
    }

    @Test
    fun theUsersBubbleCarriesItsOwnTextColor() {
        for (palette in AccentPalette.all) {
            for (scheme in everyScheme(palette)) {
                assertEquals(scheme.getValue(accent), scheme.getValue(userBubble), palette.name)
                assertEquals(scheme.getValue(onAccent), scheme.getValue(onUserBubble), palette.name)
            }
        }
    }

    @Test
    fun olympusHasTheMockupsSurfacesAndNeutrals() {
        val scheme = olympusColors(AccentPalette.Blue.dark)
        assertEquals(Color(0xFF1E1B3A), scheme.getValue(background))
        assertEquals(Color(0xFF15122B), scheme.getValue(surface))
        assertEquals(Color(0xFF3D3770), scheme.getValue(stroke))
        assertEquals(Color(0xFFECE8FF), scheme.getValue(text))
        assertEquals(Color(0xFF8F86BD), scheme.getValue(textTertiary))
        assertEquals(Color(0xFFFFB38A), scheme.getValue(headingText))
        assertEquals(Color(0xFF5EEAD4), scheme.getValue(toolAccent))
        assertEquals(Color(0xFFA89BD6), scheme.getValue(thinkingText))
    }

    @Test
    fun olympusLeavesTheAccentToTheUsersPreset() {
        for (palette in AccentPalette.all) {
            val scheme = olympusColors(palette.dark)
            assertEquals(palette.dark.accent, scheme.getValue(accent), palette.name)
            assertEquals(palette.dark.text, scheme.getValue(accentText), palette.name)
        }
    }

    @Test
    fun pureBlackStaysBlackWhateverTheMode() {
        // Olympus with pure black is the pure black scheme itself (see colorSchemeFor), not indigo.
        for (palette in AccentPalette.all) {
            assertEquals(Color.Black, pureBlackColors(palette.black).getValue(background), palette.name)
        }
    }

    @Test
    fun theOtherSchemesDrawTheNewRolesAsTheyAlwaysDid() {
        for (palette in AccentPalette.all) {
            for (scheme in listOf(lightColors(palette.light), darkColors(palette.dark), pureBlackColors(palette.black))) {
                assertEquals(scheme.getValue(text), scheme.getValue(headingText), palette.name)
                assertEquals(scheme.getValue(textTertiary), scheme.getValue(toolAccent), palette.name)
                assertEquals(scheme.getValue(textSecondary), scheme.getValue(thinkingText), palette.name)
            }
        }
    }
}
