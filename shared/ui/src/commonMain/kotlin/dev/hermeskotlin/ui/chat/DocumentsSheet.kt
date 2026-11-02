package dev.hermeskotlin.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.File
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Image as ImageIcon
import com.composables.icons.lucide.Lucide
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.hermeskotlin.core.chat.ChatMessage
import dev.hermeskotlin.core.chat.DocumentOrigin
import dev.hermeskotlin.core.chat.SessionDocument
import dev.hermeskotlin.core.chat.documentsOf
import dev.hermeskotlin.core.media.GatewayFile
import dev.hermeskotlin.designsystem.bodySmall
import dev.hermeskotlin.designsystem.colors
import dev.hermeskotlin.designsystem.components.BottomSheet
import dev.hermeskotlin.designsystem.components.SheetAction
import dev.hermeskotlin.designsystem.components.SheetHeader
import dev.hermeskotlin.designsystem.textTertiary
import dev.hermeskotlin.designsystem.typography

/**
 * Every file and picture this chat has carried, from the chat menu: what you sent, then what the agent
 * delivered, newest first. The list comes from the open transcript and is stored nowhere; a file is fetched
 * from the gateway only when opened, in the same viewer a delivered file opens in.
 */
@Composable
internal fun DocumentsSheet(
    visible: Boolean,
    messages: List<ChatMessage>,
    /** The chat's folder, where a file the prompt named relatively is looked for. */
    cwd: String?,
    onOpen: (GatewayFile) -> Unit,
    onDismiss: () -> Unit,
) {
    BottomSheet(visible = visible, onDismiss = onDismiss) {
        val documents = remember(messages, cwd) { documentsOf(messages, cwd) }
        val produced = documents.filter { it.origin == DocumentOrigin.Produced }.asReversed()
        val sent = documents.filter { it.origin == DocumentOrigin.Sent }.asReversed()
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
            if (documents.isEmpty()) {
                SheetHeader("Documents", "Nothing has been attached or delivered in this chat yet.")
            }
            if (produced.isNotEmpty()) {
                SheetHeader("From the agent", "${produced.size} delivered")
                produced.forEach { DocumentRow(it, onOpen, onDismiss) }
            }
            if (sent.isNotEmpty()) {
                SheetHeader("From you", "${sent.size} attached")
                sent.forEach { DocumentRow(it, onOpen, onDismiss) }
            }
            if (documents.any { it.file == null }) {
                Text(
                    "Greyed files can't be opened from here yet: one you just sent opens after the chat is reloaded.",
                    style = Theme[typography][bodySmall],
                    color = Theme[colors][textTertiary],
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun DocumentRow(document: SessionDocument, onOpen: (GatewayFile) -> Unit, onDismiss: () -> Unit) {
    val icon = when {
        document.isImage -> Lucide.ImageIcon
        document.name.substringAfterLast('.', "").lowercase() in DOCUMENT_EXTENSIONS -> Lucide.FileText
        else -> Lucide.File
    }
    val file = document.file
    // A file with nothing to fetch yet has no action to offer, so it is shown as a label, not a button.
    if (file == null) {
        Text(
            document.name,
            style = Theme[typography][bodySmall],
            color = Theme[colors][textTertiary],
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
        )
    } else {
        SheetAction(document.name, icon, onClick = { onDismiss(); onOpen(file) })
    }
}

private val DOCUMENT_EXTENSIONS = setOf("pdf", "doc", "docx", "txt", "md", "rtf", "odt", "csv", "xls", "xlsx", "json", "html", "htm")
