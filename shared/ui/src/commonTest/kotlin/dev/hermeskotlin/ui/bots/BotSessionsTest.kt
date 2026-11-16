package dev.hermeskotlin.ui.bots

import dev.hermeskotlin.core.sessions.SessionSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BotSessionsTest {

    private fun chat(id: String, title: String? = null, active: Double? = null, started: Double? = null) =
        SessionSummary(id = id, title = title, lastActive = active, startedAt = started)

    @Test
    fun theBotChatIsNeverOneOfTheBotsOtherChats() {
        assertTrue(chat("a", title = "Bot Chat").isBotChat())
        // The gateway writes the title exactly; a space around it is the same title.
        assertTrue(chat("b", title = " Bot Chat ").isBotChat())
        assertFalse(chat("c", title = "Bot Chat (2)").isBotChat())
        assertFalse(chat("d", title = null).isBotChat())
        assertFalse(chat("e", title = "").isBotChat())
    }

    @Test
    fun theBotChatIsDroppedAndTheRestAreNewestFirst() {
        val rows = listOf(
            chat("chat", title = "Bot Chat", active = 300.0),
            chat("routine", title = "Routine: Brief", active = 100.0),
            // Nothing recorded since it started: its start is its latest activity.
            chat("fresh", title = "New chat", started = 200.0),
            chat("bare", title = null),
        )

        assertEquals(listOf("fresh", "routine", "bare"), botChats(rows).map { it.id })
    }

    @Test
    fun aRetriedLoadInAnotherOrderComesOutTheSame() {
        val one = listOf(chat("a", active = 10.0), chat("b", active = 30.0), chat("c", active = 20.0))
        val other = listOf(chat("c", active = 20.0), chat("b", active = 30.0), chat("a", active = 10.0))

        assertEquals(listOf("b", "c", "a"), botChats(one).map { it.id })
        assertEquals(botChats(one).map { it.id }, botChats(other).map { it.id })
    }
}
