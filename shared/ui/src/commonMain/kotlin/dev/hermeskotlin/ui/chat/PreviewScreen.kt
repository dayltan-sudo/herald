package dev.hermeskotlin.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.X
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.hermeskotlin.core.chat.PageSnapshot
import dev.hermeskotlin.core.chat.PreviewDestination
import dev.hermeskotlin.core.chat.PreviewReader
import dev.hermeskotlin.core.chat.PreviewTarget
import dev.hermeskotlin.core.chat.resolvePreviewTarget
import dev.hermeskotlin.core.media.GatewayFile
import dev.hermeskotlin.designsystem.background
import dev.hermeskotlin.designsystem.body
import dev.hermeskotlin.designsystem.bodySmall
import dev.hermeskotlin.designsystem.caption
import dev.hermeskotlin.designsystem.colors
import dev.hermeskotlin.designsystem.components.Button
import dev.hermeskotlin.designsystem.components.ButtonSize
import dev.hermeskotlin.designsystem.components.Spinner
import dev.hermeskotlin.designsystem.text as textColor
import dev.hermeskotlin.designsystem.textTertiary
import dev.hermeskotlin.designsystem.typography

/**
 * What the page view shows and can do, shared between the platform view that owns the page and the screen
 * around it. Also the [PreviewReader] the agent's `preview.read` is answered from.
 */
class PreviewController : PreviewReader {
    var title by mutableStateOf("")
    var address by mutableStateOf("")
    var loading by mutableStateOf(true)
    var failed by mutableStateOf(false)

    /** Set by the platform view while it is alive. */
    var read: (suspend () -> PageSnapshot?)? = null
    var reload: (() -> Unit)? = null

    /** Steps the page back; false when it has nowhere to go back to. */
    var goBack: (() -> Boolean)? = null

    override suspend fun snapshot(): PageSnapshot? = read?.invoke()
}

/**
 * The page at [url] in a view with no gateway sign-in and no way to reach the gateway's pages. It reloads when
 * [version] moves, and keeps [controller] up to date. [host] is the gateway's, for what a link in the page points at.
 */
@Composable
internal expect fun PreviewWebView(url: String, version: Int, controller: PreviewController, host: String, modifier: Modifier)

/**
 * The page or file the agent opened with `preview.open`, in the chat's own column above the conversation, so the
 * chat stays usable beside it. A web address (the gateway's own `localhost` read as the gateway) opens in the page
 * view; a file on the gateway goes to the file viewer; anything else says why it won't open. [minimised] folds it
 * to its title bar, with the page still loaded and still readable by the agent (`preview.read`).
 */
@Composable
internal fun ColumnScope.PreviewPane(
    target: PreviewTarget,
    host: String,
    minimised: Boolean,
    onToggle: () -> Unit,
    attach: (PreviewReader?) -> Unit,
    onOpenFile: (GatewayFile) -> Unit,
    onClose: () -> Unit,
) {
    val destination = remember(target.target, host) { resolvePreviewTarget(target.target, host) }
    val close by rememberUpdatedState(onClose)
    when (destination) {
        is PreviewDestination.File -> LaunchedEffect(destination, target.version) {
            onOpenFile(GatewayFile(destination.path))
            close()
        }
        is PreviewDestination.Refused -> Refusal(target.target, destination.reason, onClose)
        is PreviewDestination.Web -> {
            val controller = remember(destination.url) { PreviewController() }
            DisposableEffect(controller) {
                attach(controller)
                onDispose { attach(null) }
            }
            val uriHandler = LocalUriHandler.current
            Column((if (minimised) Modifier else Modifier.weight(1f)).fillMaxWidth().background(Theme[colors][background])) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    BarButton(Lucide.X, "Close the preview", onClick = onClose)
                    Column(Modifier.weight(1f)) {
                        Text(
                            controller.title.ifBlank { target.label.ifBlank { "Preview" } },
                            style = Theme[typography][body].copy(fontWeight = FontWeight.SemiBold),
                            color = Theme[colors][textColor],
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            controller.address.ifBlank { destination.url },
                            style = Theme[typography][caption],
                            color = Theme[colors][textTertiary],
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis,
                        )
                    }
                    if (controller.loading) Spinner(Modifier.size(16.dp))
                    BarButton(Lucide.RefreshCw, "Reload the page", onClick = { controller.reload?.invoke() })
                    BarButton(Lucide.ExternalLink, "Open in the browser", onClick = {
                        runCatching { uriHandler.openUri(controller.address.ifBlank { destination.url }) }
                    })
                    BarButton(
                        if (minimised) Lucide.ChevronDown else Lucide.ChevronUp,
                        if (minimised) "Show the preview" else "Minimise the preview",
                        onClick = onToggle,
                    )
                }
                // Folded, the page stays in the layout one dp high: still loaded, still readable.
                Box((if (minimised) Modifier.height(1.dp) else Modifier.weight(1f)).fillMaxWidth()) {
                    PreviewWebView(destination.url, target.version, controller, host, Modifier.fillMaxSize())
                    if (controller.failed && !minimised) {
                        Column(
                            Modifier.fillMaxSize().background(Theme[colors][background]).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                        ) {
                            Text("Couldn't load the page.", style = Theme[typography][body], color = Theme[colors][textColor])
                            Text(
                                "A dev server on the gateway's machine has to listen on its network address, not only on localhost.",
                                style = Theme[typography][bodySmall],
                                color = Theme[colors][textTertiary],
                                textAlign = TextAlign.Center,
                            )
                            Button("Try again", onClick = {
                                controller.failed = false
                                controller.reload?.invoke()
                            }, size = ButtonSize.Small)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Refusal(target: String, reason: String, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Theme[colors][background]).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Herald won't open this preview", style = Theme[typography][body].copy(fontWeight = FontWeight.SemiBold), color = Theme[colors][textColor])
        Text(reason, style = Theme[typography][bodySmall], color = Theme[colors][textTertiary], textAlign = TextAlign.Center)
        Text(target, style = Theme[typography][caption], color = Theme[colors][textTertiary], maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
        Button("Close", onClick = onClose, size = ButtonSize.Small)
    }
}
