package dev.hermeskotlin.core.chat

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * What a chat has going on beside its turn: background processes the agent started and subagents still at
 * work. Kept apart from [ChatState.running], which says only that a reply is being written: a dev server can
 * outlive any number of turns without the agent being busy.
 */
data class BackgroundWork(val processes: Int = 0, val subagents: Int = 0) {
    val any: Boolean get() = processes > 0 || subagents > 0

    /** "1 process, 2 subagents"; empty when there is none. */
    val label: String
        get() = listOfNotNull(
            plural(processes, "process", "processes"),
            plural(subagents, "subagent", "subagents"),
        ).joinToString(", ")

    private fun plural(n: Int, one: String, many: String): String? = when {
        n <= 0 -> null
        n == 1 -> "1 $one"
        else -> "$n $many"
    }
}

/** The chat's [BackgroundWork] as it changes: `process.list` asked every [pollMillis], its subagents as they are seen. */
fun ChatSession.backgroundWorkFlow(pollMillis: Long = 5_000L): Flow<BackgroundWork> {
    val polled = flow<List<BackgroundProcess>?> {
        emit(null)
        while (true) {
            // A failed read keeps the last answer rather than blinking off.
            this@backgroundWorkFlow.processes()?.let { emit(it) }
            delay(pollMillis)
        }
    }
    return combine(polled, state.map { it.subagents }.distinctUntilChanged()) { list, agents -> backgroundWorkOf(list, agents) }
}

/**
 * The background work of a chat, from its latest `process.list` ([processes], null when not known yet) and
 * the [subagents] it has seen. Only subagents that are queued or running count: one sent off unwatched has no
 * known end here, and counting it would leave a badge on forever.
 */
fun backgroundWorkOf(processes: List<BackgroundProcess>?, subagents: List<Subagent>): BackgroundWork =
    BackgroundWork(
        processes = processes.orEmpty().count { it.running },
        subagents = subagents.count { it.status.live },
    )
