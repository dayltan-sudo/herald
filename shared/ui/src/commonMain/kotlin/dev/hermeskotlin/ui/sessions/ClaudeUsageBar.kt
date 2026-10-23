package dev.hermeskotlin.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.hermeskotlin.core.usage.ClaudeUsage
import dev.hermeskotlin.core.usage.UsageWindow
import dev.hermeskotlin.designsystem.accent
import dev.hermeskotlin.designsystem.caption
import dev.hermeskotlin.designsystem.colors
import dev.hermeskotlin.designsystem.danger
import dev.hermeskotlin.designsystem.surface3
import dev.hermeskotlin.designsystem.text
import dev.hermeskotlin.designsystem.textMuted
import dev.hermeskotlin.designsystem.textSecondary
import dev.hermeskotlin.designsystem.typography
import dev.hermeskotlin.designsystem.warning
import dev.hermeskotlin.ui.components.LocalMoment
import dev.hermeskotlin.ui.components.clockTime
import dev.hermeskotlin.ui.components.localMoment
import dev.hermeskotlin.ui.components.uses24HourClock

/**
 * The Claude subscription's two limit windows as thin bars, under the sidebar's page tiles. Draws nothing
 * until there is a reading; [ClaudeUsageUiState.muted] dims it when the numbers are not fresh.
 */
@Composable
internal fun ClaudeUsageBar(state: ClaudeUsageUiState, modifier: Modifier = Modifier) {
    val usage = state.usage?.takeIf { state.visible } ?: return
    val use24Hour = uses24HourClock()
    Column(
        modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        usage.fiveHour?.let { UsageRow("5h", it, weekday = false, muted = state.muted, use24Hour = use24Hour) }
        usage.sevenDay?.let { UsageRow("Week", it, weekday = true, muted = state.muted, use24Hour = use24Hour) }
    }
}

@Composable
private fun UsageRow(name: String, window: UsageWindow, weekday: Boolean, muted: Boolean, use24Hour: Boolean) {
    val severity = usageSeverity(window.percent)
    val fill = when (severity) {
        UsageSeverity.Normal -> Theme[colors][accent]
        UsageSeverity.Warning -> Theme[colors][warning]
        UsageSeverity.Error -> Theme[colors][danger]
    }
    val label = Theme[typography][caption].copy(fontSize = 11.sp)
    val reset = window.resetsAtMillis?.let { usageResetLabel(localMoment(it), weekday, use24Hour) }
    Row(
        Modifier.fillMaxWidth().alpha(if (muted) 0.5f else 1f).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = label, color = if (muted) Theme[colors][textMuted] else Theme[colors][textSecondary], maxLines = 1, modifier = Modifier.width(32.dp))
        Box(
            Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Theme[colors][surface3]),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(usageFraction(window.percent)).background(fill))
        }
        Text("${percentShown(window.percent)}%", style = label, color = Theme[colors][text], maxLines = 1)
        if (reset != null) {
            Text(reset, style = label, color = Theme[colors][textMuted], maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** "resets 17:30", or with [weekday] "resets Mon 20:59", in the device's own zone and clock style. */
internal fun usageResetLabel(at: LocalMoment, weekday: Boolean, use24Hour: Boolean): String =
    "resets ${if (weekday) WEEKDAYS[at.dayOfWeek - 1] + " " else ""}${clockTime(at, use24Hour)}"

/** A mid-way reading for previews. */
internal val ClaudeUsageSample = ClaudeUsageUiState(
    ClaudeUsage(
        ok = true,
        plan = "pro",
        fiveHour = UsageWindow(percent = 42.0, resetsAt = "2026-10-23T09:30:00+00:00"),
        sevenDay = UsageWindow(percent = 78.0, resetsAt = "2026-10-26T12:59:00+00:00"),
    ),
)
