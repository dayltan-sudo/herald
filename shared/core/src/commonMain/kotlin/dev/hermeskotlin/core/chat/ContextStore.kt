package dev.hermeskotlin.core.chat

import dev.hermeskotlin.core.network.HermesJson
import dev.hermeskotlin.core.storage.KeyValueStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** How full a chat's context window was when this device last heard, by chat. */
@Serializable
data class SavedContext(val used: Long? = null, val max: Long? = null, val percent: Int? = null)

/**
 * The context window of each chat as last seen. The gateway only knows it while it holds a built agent and
 * that agent has made a call, so a chat left alone for a while reads blank when opened again; this device
 * keeps the last figure to show until the gateway has a fresh one.
 */
class ContextStore(private val store: KeyValueStore) {

    private val lock = Mutex()
    private var cache: LinkedHashMap<String, SavedContext>? = null

    suspend fun remember(key: String, usage: SessionUsage) = lock.withLock {
        if (!usage.hasContext) return@withLock
        val saved = SavedContext(usage.contextUsed, usage.contextMax, usage.contextPercent)
        val all = load()
        if (all[key] == saved) return@withLock
        all.remove(key)
        all[key] = saved
        // Oldest first: past the cap, the chats least likely to be opened again go.
        while (all.size > MAX_ENTRIES) all.remove(all.keys.first())
        store.put(KEY, HermesJson.encodeToString(SERIALIZER, all))
    }

    /** The last context window seen for [key], as usage with no token counts; null when none was kept. */
    suspend fun recall(key: String): SessionUsage? = lock.withLock {
        load()[key]?.let { SessionUsage(contextUsed = it.used, contextMax = it.max, contextPercent = it.percent) }
    }

    private suspend fun load(): LinkedHashMap<String, SavedContext> = cache ?: LinkedHashMap(
        store.get(KEY)?.let { runCatching { HermesJson.decodeFromString(SERIALIZER, it) }.getOrNull() }.orEmpty(),
    ).also { cache = it }

    companion object {
        private const val KEY = "chat.context.v1"
        private const val MAX_ENTRIES = 300
        private val SERIALIZER = MapSerializer(String.serializer(), SavedContext.serializer())

        /** The chat's key: the same stored id on another gateway or profile is another chat. */
        fun keyOf(gateway: String, profile: String?, storedId: String) = "$gateway|${profile.orEmpty()}|$storedId"
    }
}
