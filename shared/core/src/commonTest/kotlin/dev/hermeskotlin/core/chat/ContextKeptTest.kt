package dev.hermeskotlin.core.chat

import dev.hermeskotlin.core.network.HermesJson
import dev.hermeskotlin.core.rpc.GatewayEvent
import dev.hermeskotlin.core.storage.InMemoryKeyValueStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContextKeptTest {

    private fun event(type: String, payload: String) =
        GatewayEvent(type, HermesJson.parseToJsonElement(payload), sessionId = "rt", seq = null)

    private val known = SessionUsage(input = 900, calls = 4, contextUsed = 100_000, contextMax = 1_000_000, contextPercent = 10)

    @Test
    fun aSnapshotWithoutContextKeepsTheLastWindow() {
        val rebuilt = SessionUsage(input = 0, calls = 0)
        val merged = rebuilt.withContextOf(known)
        assertEquals(10, merged.contextPercent)
        assertEquals(100_000L, merged.contextUsed)
        // The counts are the new agent's own.
        assertEquals(0L, merged.calls)
    }

    @Test
    fun aSnapshotWithContextIsNotOverwritten() {
        val fresh = SessionUsage(contextUsed = 5, contextMax = 10, contextPercent = 50)
        assertEquals(50, fresh.withContextOf(known).contextPercent)
    }

    @Test
    fun aUsageEventFromARebuiltAgentLeavesTheBarFilled() {
        val state = ChatState(usage = known).reduce(event("session.usage", """{"usage":{"input":0,"output":0,"calls":0}}"""))
        assertEquals(10, state.usage?.contextPercent)
    }

    @Test
    fun sessionInfoFromARebuiltAgentLeavesTheBarFilled() {
        val state = ChatState(usage = known).withInfo(HermesJson.parseToJsonElement("""{"model":"m","usage":{"input":0,"calls":0}}""") as kotlinx.serialization.json.JsonObject)
        assertEquals(10, state.usage?.contextPercent)
        assertEquals("m", state.model)
    }

    @Test
    fun theStoreGivesBackTheLastWindowPerChat() = runTest {
        val disk = InMemoryKeyValueStore()
        val key = ContextStore.keyOf("https://g", null, "s1")
        ContextStore(disk).remember(key, known)

        val back = ContextStore(disk).recall(key)
        assertEquals(10, back?.contextPercent)
        assertEquals(1_000_000L, back?.contextMax)
        assertNull(ContextStore(disk).recall(ContextStore.keyOf("https://g", "other", "s1")))
    }

    @Test
    fun aUsageWithoutContextIsNotRemembered() = runTest {
        val store = ContextStore(InMemoryKeyValueStore())
        val key = ContextStore.keyOf("https://g", null, "s1")
        store.remember(key, known)
        store.remember(key, SessionUsage(calls = 1))
        assertEquals(10, store.recall(key)?.contextPercent)
    }
}
