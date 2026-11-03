package dev.hermeskotlin.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.hermeskotlin.core.storage.KeyValueStore
import dev.hermeskotlin.designsystem.bodySmall
import dev.hermeskotlin.designsystem.colors
import dev.hermeskotlin.designsystem.components.Button
import dev.hermeskotlin.designsystem.components.ButtonVariant
import dev.hermeskotlin.designsystem.components.IconButton
import dev.hermeskotlin.designsystem.components.TextField
import dev.hermeskotlin.designsystem.components.plainTextClipEntry
import dev.hermeskotlin.designsystem.danger
import dev.hermeskotlin.designsystem.heading as headingStyle
import dev.hermeskotlin.designsystem.surface3
import dev.hermeskotlin.designsystem.text
import dev.hermeskotlin.designsystem.textSecondary
import dev.hermeskotlin.designsystem.typography
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.connectbot.terminal.ModifierManager
import org.connectbot.terminal.Terminal
import org.connectbot.terminal.TerminalDimensions
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.TerminalEmulatorFactory
import org.connectbot.terminal.VTermKey
import org.koin.compose.koinInject

private val TerminalBackground = Color(0xFF0B0B0D)
private val TerminalForeground = Color(0xFFE6E6E6)

private enum class Phase { Loading, Setup, Terminal }

private sealed interface Status {
    data object Connecting : Status
    data class Connected(val note: String) : Status
    data object Closed : Status
    data class Failed(val message: String, val hostKeyChanged: Boolean = false) : Status
}

/** What the emulator, the SSH shell and the keystrokes share; the emulator's callbacks arrive on the main thread. */
private class TerminalLink {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Keystrokes waiting to go to the shell, written off the main thread by the session. */
    val outbound = Channel<ByteArray>(Channel.UNLIMITED)

    @Volatile
    var shell: SshShell? = null

    @Volatile
    var dimensions: TerminalDimensions? = null

    fun send(bytes: ByteArray) {
        outbound.trySend(bytes)
    }

    fun resized(d: TerminalDimensions) {
        dimensions = d
        val s = shell ?: return
        scope.launch { runCatching { s.resize(d.columns, d.rows, d.widthPixels, d.heightPixels) } }
    }

    fun drain() {
        while (outbound.tryReceive().isSuccess) Unit
    }

    fun close() {
        shell?.close()
        shell = null
        outbound.close()
        scope.cancel()
    }
}

/** Ctrl and Alt as on-screen toggles that the terminal reads for the next key, then clears. */
private class StickyModifiers : ModifierManager {
    var ctrl by mutableStateOf(false)
    var alt by mutableStateOf(false)

    override fun isCtrlActive() = ctrl
    override fun isAltActive() = alt
    override fun isShiftActive() = false
    override fun clearTransients() {
        ctrl = false
        alt = false
    }

    fun bits(): Int = (if (ctrl) 4 else 0) or (if (alt) 2 else 0)
}

@Composable
actual fun TerminalPage(host: String, onBack: () -> Unit) {
    val store = koinInject<KeyValueStore>()
    var phase by remember { mutableStateOf(Phase.Loading) }
    var user by remember { mutableStateOf(DefaultTerminalUser) }
    var privatePem by remember { mutableStateOf<String?>(null) }
    var publicLine by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        user = store.get(TerminalUserStore) ?: DefaultTerminalUser
        privatePem = store.get(TerminalPrivateKeyStore)
        publicLine = store.get(TerminalPublicKeyStore)
        phase = if (privatePem != null) Phase.Terminal else Phase.Setup
    }

    when (phase) {
        Phase.Loading -> Box(Modifier.fillMaxSize())
        Phase.Setup -> SetupView(
            host = host,
            initialUser = user,
            publicLine = publicLine,
            store = store,
            onBack = onBack,
            onKeyCreated = { pem, line ->
                privatePem = pem
                publicLine = line
            },
            onConnect = { name ->
                user = name
                phase = Phase.Terminal
            },
        )
        Phase.Terminal -> ShellView(
            host = host,
            user = user,
            privatePem = privatePem.orEmpty(),
            store = store,
            onBack = onBack,
            onSetup = { phase = Phase.Setup },
        )
    }
}

@Composable
private fun SetupView(
    host: String,
    initialUser: String,
    publicLine: String?,
    store: KeyValueStore,
    onBack: () -> Unit,
    onKeyCreated: (privatePem: String, publicLine: String) -> Unit,
    onConnect: (user: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val userField = remember { TextFieldState(initialUser) }
    var copied by remember { mutableStateOf(false) }
    val name = userField.text.toString().trim()
    val nameOk = isValidTerminalUser(name)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(Lucide.ArrowLeft, contentDescription = "Back", onClick = onBack)
            Text("Terminal", style = Theme[typography][headingStyle], color = Theme[colors][text])
        }
        Text(
            "A shell on $host over SSH. The key stays on this phone; the host must be told to trust it once.",
            style = Theme[typography][bodySmall],
            color = Theme[colors][textSecondary],
        )
        TextField(
            state = userField,
            label = "Login name on $host",
            error = if (nameOk) null else "Lowercase letters, digits, - and _ only.",
        )
        if (publicLine == null) {
            Button(
                "Create key",
                onClick = {
                    scope.launch {
                        val pair = withContext(Dispatchers.Default) { newTerminalKeyPair() }
                        store.put(TerminalPrivateKeyStore, pair.privatePem)
                        store.put(TerminalPublicKeyStore, pair.publicLine)
                        onKeyCreated(pair.privatePem, pair.publicLine)
                    }
                },
            )
        } else {
            Text(
                "Run this once on $host, in a shell you already have, then connect:",
                style = Theme[typography][bodySmall],
                color = Theme[colors][textSecondary],
            )
            Text(
                authorizeCommand(publicLine),
                style = Theme[typography][bodySmall],
                color = Theme[colors][text],
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Theme[colors][surface3], RoundedCornerShape(10.dp))
                    .padding(12.dp),
            )
            Button(
                if (copied) "Copied" else "Copy command",
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(plainTextClipEntry(authorizeCommand(publicLine)))
                        copied = true
                    }
                },
                variant = ButtonVariant.Secondary,
            )
            Button(
                "Connect",
                enabled = nameOk,
                onClick = {
                    scope.launch {
                        store.put(TerminalUserStore, name)
                        onConnect(name)
                    }
                },
            )
        }
    }
}

@Composable
private fun ShellView(
    host: String,
    user: String,
    privatePem: String,
    store: KeyValueStore,
    onBack: () -> Unit,
    onSetup: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val link = remember { TerminalLink() }
    val modifiers = remember { StickyModifiers() }
    val emulator = remember {
        TerminalEmulatorFactory.create(
            initialRows = 24,
            initialCols = 80,
            defaultForeground = TerminalForeground,
            defaultBackground = TerminalBackground,
            onKeyboardInput = { link.send(it) },
            onResize = { link.resized(it) },
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            link.close()
            emulator.close()
        }
    }
    var status by remember { mutableStateOf<Status>(Status.Connecting) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        status = Status.Connecting
        link.drain()
        val remembered = store.get(hostKeyStoreKey(host))
        val shell = SshShell(host, user, privatePem, remembered)
        try {
            withContext(Dispatchers.IO) {
                val d = link.dimensions
                shell.open(d?.columns ?: 80, d?.rows ?: 24, d?.widthPixels ?: 0, d?.heightPixels ?: 0)
                link.shell = shell
                // A resize that arrived while connecting still has to reach the shell.
                link.dimensions?.let { runCatching { shell.resize(it.columns, it.rows, it.widthPixels, it.heightPixels) } }
                val shown = shell.presentedHostKey.orEmpty()
                if (shell.firstUse) store.put(hostKeyStoreKey(host), shown)
                status = Status.Connected(if (shell.firstUse) "new host key $shown" else "host key checked")
                coroutineScope {
                    val writer = launch {
                        for (bytes in link.outbound) shell.write(bytes)
                    }
                    shell.readInto { buffer, n -> runCatching { emulator.writeInput(buffer, 0, n) } }
                    writer.cancel()
                }
            }
            runCatching { emulator.writeInput("\r\n[connection closed]\r\n".toByteArray()) }
            status = Status.Closed
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            status = Status.Failed(describe(e), hostKeyChanged = hostKeyChanged(e))
        } finally {
            link.shell = null
            shell.close()
        }
    }

    Column(Modifier.fillMaxSize().background(TerminalBackground).imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(Lucide.ArrowLeft, contentDescription = "Back", onClick = onBack)
            Column(Modifier.weight(1f)) {
                Text("$user@$host", style = Theme[typography][bodySmall], color = TerminalForeground)
                val (line, bad) = when (val s = status) {
                    Status.Connecting -> "Connecting…" to false
                    is Status.Connected -> "Connected, ${s.note}" to false
                    Status.Closed -> "Disconnected" to false
                    is Status.Failed -> s.message to true
                }
                Text(
                    line,
                    style = Theme[typography][bodySmall].copy(fontSize = 12.sp),
                    color = if (bad) Theme[colors][danger] else Theme[colors][textSecondary],
                )
            }
            if (status is Status.Closed || status is Status.Failed) {
                IconButton(Lucide.RefreshCw, contentDescription = "Reconnect", onClick = { attempt++ })
            }
            val failed = status as? Status.Failed
            if (failed != null) {
                if (failed.hostKeyChanged) {
                    // Only after the owner has checked the new key is really the host's, e.g. after a reinstall.
                    Text(
                        "Trust new key",
                        style = Theme[typography][bodySmall],
                        color = Theme[colors][danger],
                        modifier = Modifier
                            .clickable {
                                scope.launch {
                                    store.remove(hostKeyStoreKey(host))
                                    attempt++
                                }
                            }
                            .padding(12.dp),
                    )
                }
                Text(
                    "Key",
                    style = Theme[typography][bodySmall],
                    color = Theme[colors][textSecondary],
                    modifier = Modifier.clickable(onClick = onSetup).padding(12.dp),
                )
            }
        }
        Terminal(
            terminalEmulator = emulator,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            initialFontSize = 13.sp,
            backgroundColor = TerminalBackground,
            foregroundColor = TerminalForeground,
            keyboardEnabled = true,
            showSoftKeyboard = true,
            modifierManager = modifiers,
        )
        ExtraKeys(emulator, modifiers)
    }
}

/** The keys a phone keyboard lacks, in a row that scrolls sideways; Ctrl and Alt apply to the next key. */
@Composable
private fun ExtraKeys(emulator: TerminalEmulator, modifiers: StickyModifiers) {
    fun special(key: Int) {
        emulator.dispatchKey(modifiers.bits(), key)
        modifiers.clearTransients()
    }

    fun character(ch: Char) {
        emulator.dispatchCharacter(modifiers.bits(), ch.code)
        modifiers.clearTransients()
    }

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        KeyCap("Esc") { special(VTermKey.ESCAPE) }
        KeyCap("Tab") { special(VTermKey.TAB) }
        KeyCap("Ctrl", active = modifiers.ctrl) { modifiers.ctrl = !modifiers.ctrl }
        KeyCap("Alt", active = modifiers.alt) { modifiers.alt = !modifiers.alt }
        KeyCap("←") { special(VTermKey.LEFT) }
        KeyCap("↑") { special(VTermKey.UP) }
        KeyCap("↓") { special(VTermKey.DOWN) }
        KeyCap("→") { special(VTermKey.RIGHT) }
        KeyCap("Home") { special(VTermKey.HOME) }
        KeyCap("End") { special(VTermKey.END) }
        KeyCap("PgUp") { special(VTermKey.PAGEUP) }
        KeyCap("PgDn") { special(VTermKey.PAGEDOWN) }
        KeyCap("|") { character('|') }
        KeyCap("~") { character('~') }
        KeyCap("-") { character('-') }
        KeyCap("/") { character('/') }
    }
}

@Composable
private fun KeyCap(label: String, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .widthIn(min = 40.dp)
            .background(if (active) Theme[colors][textSecondary] else Theme[colors][surface3], RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = Theme[typography][bodySmall],
            color = if (active) TerminalBackground else Theme[colors][text],
        )
    }
}

/** A short line for the header, without a stack trace; the cause chain is searched since the library wraps errors. */
private fun describe(error: Throwable): String {
    var e: Throwable? = error
    while (e != null) {
        when (e) {
            is HostKeyChangedException -> return "Host key changed (${e.presented}). Refusing to connect."
            is KeyRejectedException -> return "The host rejected this phone's key. Authorise it, then reconnect."
            is java.net.ConnectException, is java.net.SocketTimeoutException -> return "Can't reach the host on port 22."
        }
        e = e.cause
    }
    return error.message?.take(120) ?: "Connection failed."
}

private fun hostKeyChanged(error: Throwable): Boolean = generateSequence(error) { it.cause }.any { it is HostKeyChangedException }
