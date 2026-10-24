package dev.hermeskotlin.core.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContextBarTest {
    @Test
    fun anUntouchedChatSaysNothingItDoesNotKnow() {
        val bar = contextBarOf(ChatState())
        assertEquals(ContextLevel.Unknown, bar.level)
        assertNull(bar.contextText)
        assertEquals(emptyList(), bar.details)
    }

    @Test
    fun theWindowShowsUsedOverMaxAndTurnsWarmThenHot() {
        fun bar(percent: Int) = ContextBar(used = 142_000, max = 200_000, percent = percent)
        assertEquals("142k / 200k · 71%", bar(71).contextText)
        assertEquals(ContextLevel.Ok, bar(69).level)
        assertEquals(ContextLevel.Warm, bar(70).level)
        assertEquals(ContextLevel.Warm, bar(89).level)
        assertEquals(ContextLevel.Hot, bar(90).level)
        assertEquals(ContextLevel.Hot, bar(100).level)
    }

    @Test
    fun withoutSizesTheBarStillHasThePercentage() {
        assertEquals("42%", ContextBar(percent = 42).contextText)
        assertEquals("42%", ContextBar(used = 10, max = 0, percent = 42).contextText)
    }

    @Test
    fun theSmallPrintKeepsItsOrderAndSkipsWhatIsOff() {
        val bar = ContextBar(
            model = "Sonnet 5.5",
            effort = "high",
            fast = true,
            yolo = true,
            cacheHitPercent = 92,
            calls = 14,
            stepsDone = 3,
            stepsTotal = 5,
            agentsRunning = 2,
        )
        assertEquals(listOf("Sonnet 5.5", "high", "fast", "YOLO", "cache 92%", "14 calls", "steps 3/5", "2 agents"), bar.details)
        // Reasoning off, no calls yet, one agent: nothing to say about the first two.
        assertEquals(listOf("1 agent"), ContextBar(effort = "none", calls = 0, agentsRunning = 1).details)
    }

    @Test
    fun theBarReadsTheLiveChat() {
        val state = ChatState(
            model = "claude-sonnet-5-5",
            reasoningEffort = "medium",
            yolo = true,
            usage = SessionUsage(calls = 3, contextUsed = 50_000, contextMax = 200_000, contextPercent = 25, cacheHitPercent = 80),
        )
        val bar = contextBarOf(state)
        assertEquals(25, bar.percent)
        assertEquals("50k / 200k · 25%", bar.contextText)
        assertEquals(ContextLevel.Ok, bar.level)
        assertEquals(true, bar.yolo)
        assertEquals("medium", bar.effort)
        assertEquals(80, bar.cacheHitPercent)
        assertEquals(3L, bar.calls)
    }
}
