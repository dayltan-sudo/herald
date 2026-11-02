package dev.hermeskotlin.core.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackgroundWorkTest {
    private fun process(id: String, running: Boolean) =
        BackgroundProcess(id, "sleep 9", cwd = null, pid = 1, running = running, uptimeSeconds = 1, exitCode = null, output = "")

    @Test
    fun nothingKnownIsNothingGoingOn() {
        val work = backgroundWorkOf(null, emptyList())
        assertFalse(work.any)
        assertEquals("", work.label)
    }

    @Test
    fun onlyRunningProcessesCount() {
        val work = backgroundWorkOf(listOf(process("a", true), process("b", false), process("c", true)), emptyList())
        assertEquals(2, work.processes)
        assertTrue(work.any)
    }

    @Test
    fun onlyLiveSubagentsCount() {
        val subagents = listOf(
            Subagent("1", status = SubagentStatus.Running),
            Subagent("2", status = SubagentStatus.Queued),
            Subagent("3", status = SubagentStatus.Done),
            Subagent("4", status = SubagentStatus.Unwatched),
        )
        assertEquals(2, backgroundWorkOf(null, subagents).subagents)
    }

    @Test
    fun theLabelNamesWhatIsThereAndPluralises() {
        assertEquals("1 process", BackgroundWork(processes = 1).label)
        assertEquals("2 subagents", BackgroundWork(subagents = 2).label)
        assertEquals("1 process, 3 subagents", BackgroundWork(processes = 1, subagents = 3).label)
    }
}
