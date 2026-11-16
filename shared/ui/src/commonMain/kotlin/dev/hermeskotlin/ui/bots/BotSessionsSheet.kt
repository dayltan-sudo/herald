package dev.hermeskotlin.ui.bots

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.hermeskotlin.core.bots.Bot
import dev.hermeskotlin.core.bots.BotsApi
import dev.hermeskotlin.core.gateway.GatewayUrl
import dev.hermeskotlin.core.network.ApiResult
import dev.hermeskotlin.core.network.errorMessage
import dev.hermeskotlin.core.sessions.SessionListFilter
import dev.hermeskotlin.core.sessions.SessionSummary
import dev.hermeskotlin.core.sessions.SessionsApi
import dev.hermeskotlin.designsystem.components.BottomSheet
import dev.hermeskotlin.designsystem.components.SheetHeader
import dev.hermeskotlin.ui.sessions.ListNotice
import dev.hermeskotlin.ui.sessions.ListSpinner
import dev.hermeskotlin.ui.sessions.SessionRow
import org.koin.compose.koinInject

/**
 * The ordinary chats a bot has had beyond its permanent one: a throwaway "New chat with <bot>" that the Chats
 * side can't reach, since that side lists one profile's chats and these live in the bot's.
 * Newest first; tapping one opens it in the bot's profile. The Bot Chat is never listed here, and nothing on this
 * sheet creates, renames, archives or opens it: it is the bot's card's own door.
 */
@Composable
fun BotSessionsSheet(
    /** The bot whose chats are listed; null keeps the sheet closed, with its rows gone. */
    bot: Bot?,
    onDismiss: () -> Unit,
    /** Opens the picked chat: the bot, the stored id, and what to call it. */
    onOpen: (Bot, String, String) -> Unit,
    /** The gateway they're read from; a bot's chats live in the bot's own profile. */
    url: GatewayUrl,
    /** Reads a bot's chats; the gateway's own REST list unless a preview hands over another. */
    api: SessionsApi = koinInject(),
) {
    // Keep the last bot while the sheet animates out, so its chats stay put on the way down.
    var shown by remember { mutableStateOf(bot) }
    if (bot != null) shown = bot
    // Keyed on the bot shown, so opening the sheet for another bot never shows the one before's rows.
    var chats by remember(shown) { mutableStateOf<List<SessionSummary>?>(null) }
    var error by remember(shown) { mutableStateOf<String?>(null) }
    // Counts retries: a new number is a new read.
    var attempt by remember(shown) { mutableStateOf(0) }
    LaunchedEffect(bot, url, attempt) {
        val target = bot ?: return@LaunchedEffect
        error = null
        val result = api.list(url, profile = target.name, limit = CHAT_LIMIT, filter = SessionListFilter.Recent)
        when (result) {
            is ApiResult.Success -> chats = botChats(result.value.sessions)
            else -> error = result.errorMessage
        }
    }
    BottomSheet(visible = bot != null, onDismiss = onDismiss) {
        val target = shown ?: return@BottomSheet
        SheetHeader(title = target.label, subtitle = "Chats with ${target.label}, newest first.")
        val rows = chats
        when {
            error != null -> ListNotice(error.orEmpty(), action = "Try again") { attempt++ }
            rows == null -> ListSpinner()
            rows.isEmpty() -> ListNotice("No other chats with ${target.label} yet.")
            else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                items(rows, key = { it.id }) { chat ->
                    SessionRow(
                        session = chat,
                        selected = false,
                        showSnippet = false,
                        onClick = { onOpen(target, chat.id, chat.displayTitle) },
                        onActions = { onOpen(target, chat.id, chat.displayTitle) },
                    )
                }
            }
        }
    }
}

/**
 * A bot's chats as the sheet shows them: its Bot Chat dropped and the newest activity first. The gateway lists them
 * recent-first already; sorting here keeps that the sheet's own rule whatever order arrives.
 */
internal fun botChats(rows: List<SessionSummary>): List<SessionSummary> =
    rows.filterNot { it.isBotChat() }.sortedByDescending { it.activityAt ?: 0.0 }

/**
 * Whether a stored row is a bot's Bot Chat: the one session a profile titles exactly "Bot Chat". It has its own door
 * on the bot's card, so it is never one of "the bot's other chats".
 */
internal fun SessionSummary.isBotChat(): Boolean = title?.trim() == BotsApi.BOT_CHAT_TITLE

/** The newest chats one sheet shows; no paging in v1. */
private const val CHAT_LIMIT = 50
