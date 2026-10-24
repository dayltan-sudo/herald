package dev.hermeskotlin.core.chat

import dev.hermeskotlin.core.wear.contextPercentOf
import dev.hermeskotlin.core.wear.modelOf

/** How full the context window is: the bar turns warm at 70% and hot at 90%, when compression is near. */
enum class ContextLevel { Unknown, Ok, Warm, Hot }

/**
 * What the bar above a chat says, all from what the chat already holds: the context window, the model and how it
 * is set, and what the agent is doing. A field the gateway hasn't sent yet is null, never a made-up zero.
 */
data class ContextBar(
    val model: String? = null,
    /** `none`, or a level such as `medium`; null when the chat uses the profile's default. */
    val effort: String? = null,
    val fast: Boolean = false,
    /** Tools run without asking for approval in this chat. */
    val yolo: Boolean = false,
    val used: Long? = null,
    val max: Long? = null,
    val percent: Int? = null,
    val cacheHitPercent: Int? = null,
    val calls: Long? = null,
    /** The agent's plan while it has steps left; both null otherwise. */
    val stepsDone: Int? = null,
    val stepsTotal: Int? = null,
    val agentsRunning: Int = 0,
) {
    val level: ContextLevel
        get() = when {
            percent == null -> ContextLevel.Unknown
            percent >= HOT_PERCENT -> ContextLevel.Hot
            percent >= WARM_PERCENT -> ContextLevel.Warm
            else -> ContextLevel.Ok
        }

    /** The context window as the bar's text: "142k / 200k · 71%", or just the percentage when the sizes are unknown. */
    val contextText: String?
        get() = when {
            percent == null -> null
            used != null && max != null && max > 0 -> "${compactCount(used)} / ${compactCount(max)} · $percent%"
            else -> "$percent%"
        }

    /** The small print under the bar, in the order it matters; empty when there is nothing to add. */
    val details: List<String>
        get() = listOfNotNull(
            model,
            effort?.takeIf { it != "none" },
            "fast".takeIf { fast },
            "YOLO".takeIf { yolo },
            cacheHitPercent?.let { "cache $it%" },
            calls?.takeIf { it > 0 }?.let { "$it calls" },
            if (stepsDone != null && stepsTotal != null) "steps $stepsDone/$stepsTotal" else null,
            agentsRunning.takeIf { it > 0 }?.let { if (it == 1) "1 agent" else "$it agents" },
        )

    companion object {
        const val WARM_PERCENT = 70
        const val HOT_PERCENT = 90
    }
}

fun contextBarOf(state: ChatState): ContextBar {
    val usage = state.usage
    val plan = state.todos?.takeIf { it.active }
    return ContextBar(
        model = modelOf(state),
        effort = state.reasoningEffort?.takeIf { it.isNotBlank() },
        fast = state.fast == true,
        yolo = state.yolo == true,
        used = usage?.contextUsed,
        max = usage?.contextMax,
        percent = contextPercentOf(state),
        cacheHitPercent = usage?.cacheHitPercent,
        calls = usage?.calls,
        stepsDone = plan?.done,
        stepsTotal = plan?.total,
        agentsRunning = state.subagents.count { it.status == SubagentStatus.Running },
    )
}
