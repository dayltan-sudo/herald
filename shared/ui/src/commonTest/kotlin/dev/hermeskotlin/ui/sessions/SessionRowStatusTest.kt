package dev.hermeskotlin.ui.sessions

import dev.hermeskotlin.core.chat.BackgroundWork
import dev.hermeskotlin.core.chat.Waiting
import dev.hermeskotlin.core.connection.ConnectionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionRowStatusTest {

    @Test
    fun waitingOnTheUserOutranksEverythingElse() {
        val status = RowStatus(waiting = Waiting.Approval, unread = true, running = true)
        assertEquals(RowTone.Approval, rowTone(status, draft = true))
        assertEquals(RowTone.Question, rowTone(RowStatus(waiting = Waiting.Question, running = true), draft = false))
        assertEquals(RowTone.Question, rowTone(RowStatus(waiting = Waiting.Input), draft = false))
    }

    @Test
    fun aRequestSeenElsewhereReadsAsApproval() {
        assertEquals(RowTone.Approval, rowTone(RowStatus(waiting = Waiting.Unknown), draft = false))
    }

    @Test
    fun runningThenReplyThenDraftThenIdle() {
        assertEquals(RowTone.Running, rowTone(RowStatus(running = true, unread = true), draft = true))
        assertEquals(RowTone.Reply, rowTone(RowStatus(unread = true), draft = true))
        assertEquals(RowTone.Draft, rowTone(null, draft = true))
        assertEquals(RowTone.Idle, rowTone(null, draft = false))
        assertEquals(RowTone.Idle, rowTone(RowStatus(), draft = false))
    }

    @Test
    fun backgroundWorkSitsBetweenRunningAndReply() {
        val bg = BackgroundWork(processes = 1)
        assertEquals(RowTone.Background, rowTone(RowStatus(background = bg, unread = true), draft = true))
        assertEquals(RowTone.Running, rowTone(RowStatus(background = bg, running = true), draft = false))
        assertEquals("Running · 1 process", rowStatusLine(RowStatus(running = true, background = bg), draft = false))
        assertEquals("2 subagents · New reply", rowStatusLine(RowStatus(unread = true, background = BackgroundWork(subagents = 2)), draft = false))
    }

    @Test
    fun theRunningFilterKeepsChatsWithBackgroundWork() {
        assertEquals(true, AttentionFilter.Running.keeps(RowStatus(background = BackgroundWork(processes = 1))))
        assertEquals(false, AttentionFilter.Running.keeps(RowStatus(unread = true)))
        // Background work alone is not something that needs the user.
        assertEquals(false, AttentionFilter.NeedsAttention.keeps(RowStatus(background = BackgroundWork(processes = 1))))
    }

    @Test
    fun theLineSaysEveryStatusInWords() {
        assertEquals(
            "Needs approval · Running · New reply · Draft",
            rowStatusLine(RowStatus(waiting = Waiting.Approval, unread = true, running = true), draft = true),
        )
        assertEquals("Draft", rowStatusLine(null, draft = true))
        assertNull(rowStatusLine(RowStatus(), draft = false))
    }

    @Test
    fun theConnectionDotIsSpoken() {
        assertEquals("Connected", connectionWord(null))
        assertEquals("Reconnecting", connectionWord(ConnectionState.Reconnecting(1, 0, "lost")))
        assertEquals("Signed out", connectionWord(ConnectionState.SessionExpired))
        assertEquals("Not connected", connectionWord(ConnectionState.Failed("guard")))
        assertEquals("Connecting", connectionWord(ConnectionState.Idle))
    }
}
