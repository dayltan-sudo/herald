package dev.hermeskotlin.ui.terminal

import androidx.compose.runtime.Composable

/**
 * The Terminal page: a shell on the gateway host, over SSH with a key kept on this phone. Only the host
 * the gateway runs on is reachable; there is no host field. Drawn on Android only.
 */
@Composable
expect fun TerminalPage(host: String, onBack: () -> Unit)

/** What to do with the host key the server presented, against the one remembered from the first connect. */
internal enum class HostKeyVerdict { TrustFirstUse, Match, Mismatch }

/** Trust on first use: the first key seen is remembered, and any other one later is refused. */
internal fun hostKeyVerdict(remembered: String?, presented: String): HostKeyVerdict = when {
    remembered == null -> HostKeyVerdict.TrustFirstUse
    remembered == presented -> HostKeyVerdict.Match
    else -> HostKeyVerdict.Mismatch
}

internal const val DefaultTerminalUser = "dayltan"
internal const val TerminalPrivateKeyStore = "terminal.key"
internal const val TerminalPublicKeyStore = "terminal.pub"
internal const val TerminalUserStore = "terminal.user"

internal fun hostKeyStoreKey(host: String) = "terminal.hostkey.$host"

private val LoginName = Regex("[a-z_][a-z0-9_-]{0,31}")

/** A Linux login name, which also keeps shell metacharacters out of the command shown for the owner to run. */
internal fun isValidTerminalUser(name: String): Boolean = LoginName.matches(name)

/** The one command to run on the host to let this phone's key in; appends, never overwrites. */
internal fun authorizeCommand(publicKeyLine: String): String =
    "mkdir -p ~/.ssh && chmod 700 ~/.ssh && echo '$publicKeyLine' >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
