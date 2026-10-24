package dev.hermeskotlin.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.hermeskotlin.core.chat.ContextBar
import dev.hermeskotlin.core.chat.ContextLevel
import dev.hermeskotlin.designsystem.accent
import dev.hermeskotlin.designsystem.caption
import dev.hermeskotlin.designsystem.code
import dev.hermeskotlin.designsystem.colors
import dev.hermeskotlin.designsystem.danger
import dev.hermeskotlin.designsystem.surface3
import dev.hermeskotlin.designsystem.textSecondary
import dev.hermeskotlin.designsystem.textTertiary
import dev.hermeskotlin.designsystem.typography
import dev.hermeskotlin.designsystem.warning

/**
 * The strip above the conversation that answers "how full is this chat?" without asking: the context window as a
 * bar with its figures, and under it the model, how it is set, and what the agent is doing. A tap opens the usage
 * sheet for the cost and the split of what fills the window. Shows what the chat holds; nothing is fetched.
 */
@Composable
internal fun ContextBarRow(bar: ContextBar, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val spoken = buildString {
        append(bar.contextText?.let { "Context window $it. " } ?: "Context window not reported yet. ")
        append(bar.details.joinToString(", "))
    }
    Column(
        modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open usage", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(Theme[colors][surface3])) {
                val fraction = ((bar.percent ?: 0) / 100f).coerceIn(0f, 1f)
                if (bar.percent != null) Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(CircleShape).background(levelColor(bar.level)))
            }
            Text(
                bar.contextText ?: "context —",
                style = Theme[typography][code].copy(fontSize = 11.sp),
                color = if (bar.level == ContextLevel.Hot) Theme[colors][danger] else Theme[colors][textSecondary],
                maxLines = 1,
            )
        }
        if (bar.details.isNotEmpty()) {
            Text(
                bar.details.joinToString(" · "),
                style = Theme[typography][caption],
                color = Theme[colors][textTertiary],
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun levelColor(level: ContextLevel): Color = when (level) {
    ContextLevel.Hot -> Theme[colors][danger]
    ContextLevel.Warm -> Theme[colors][warning]
    else -> Theme[colors][accent]
}
