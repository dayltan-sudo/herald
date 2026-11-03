package dev.hermeskotlin.ui.terminal

import com.trilead.ssh2.Connection
import com.trilead.ssh2.ServerHostKeyVerifier
import com.trilead.ssh2.Session
import com.trilead.ssh2.crypto.OpenSSHKeyEncoder
import com.trilead.ssh2.crypto.PublicKeyUtils
import com.trilead.ssh2.crypto.fingerprint.KeyFingerprint
import com.trilead.ssh2.crypto.keys.Ed25519KeyPairGenerator
import com.trilead.ssh2.crypto.keys.Ed25519PrivateKey
import com.trilead.ssh2.crypto.keys.Ed25519Provider
import com.trilead.ssh2.crypto.keys.Ed25519PublicKey
import java.io.IOException

/** The server's host key is not the one remembered from the first connect. */
internal class HostKeyChangedException(val presented: String) : IOException("The host key changed.")

/** The server did not accept this phone's key: it is not in authorized_keys for that user. */
internal class KeyRejectedException : IOException("The host did not accept this phone's key.")

/** A new key pair: the private half in OpenSSH format to keep, and the public half as an authorized_keys line. */
internal class TerminalKeyPair(val privatePem: String, val publicLine: String)

internal fun newTerminalKeyPair(comment: String = "herald"): TerminalKeyPair {
    Ed25519Provider.insertIfNeeded()
    val pair = Ed25519KeyPairGenerator().generateKeyPair()
    val priv = pair.private as Ed25519PrivateKey
    val pub = pair.public as Ed25519PublicKey
    return TerminalKeyPair(
        privatePem = OpenSSHKeyEncoder.exportOpenSSHEd25519(priv, pub, comment),
        publicLine = PublicKeyUtils.toAuthorizedKeysFormat(pub, comment).trim(),
    )
}

/**
 * One SSH shell on [host]. Every method blocks, so call them off the main thread; [close] from any
 * thread ends a blocked [readInto]. [rememberedHostKey] is the fingerprint kept from an earlier connect.
 */
internal class SshShell(
    private val host: String,
    private val user: String,
    private val privatePem: String,
    private val rememberedHostKey: String?,
    private val port: Int = 22,
) : AutoCloseable {
    private var connection: Connection? = null
    private var session: Session? = null

    /** The host key fingerprint the server showed, once connected. */
    @Volatile
    var presentedHostKey: String? = null
        private set

    /** True when the connect remembered nothing before, so the key shown is new to this phone. */
    val firstUse: Boolean get() = rememberedHostKey == null

    fun open(columns: Int, rows: Int, widthPixels: Int, heightPixels: Int) {
        Ed25519Provider.insertIfNeeded()
        val c = Connection(host, port)
        connection = c
        c.connect(
            ServerHostKeyVerifier { _, _, _, blob ->
                val fingerprint = KeyFingerprint.createSHA256Fingerprint(blob)
                presentedHostKey = fingerprint
                if (hostKeyVerdict(rememberedHostKey, fingerprint) == HostKeyVerdict.Mismatch) {
                    throw HostKeyChangedException(fingerprint)
                }
                true
            },
            CONNECT_TIMEOUT_MS,
            CONNECT_TIMEOUT_MS,
        )
        if (!c.authenticateWithPublicKey(user, privatePem.toCharArray(), null)) throw KeyRejectedException()
        val s = c.openSession()
        session = s
        s.requestPTY("xterm-256color", columns, rows, widthPixels, heightPixels, null)
        s.startShell()
    }

    /** Hands each chunk of the shell's output to [onChunk] until the shell ends. */
    fun readInto(onChunk: (ByteArray, Int) -> Unit) {
        val input = session?.stdout ?: return
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) return
            onChunk(buffer, n)
        }
    }

    fun write(bytes: ByteArray) {
        val out = session?.stdin ?: return
        out.write(bytes)
        out.flush()
    }

    fun resize(columns: Int, rows: Int, widthPixels: Int, heightPixels: Int) {
        session?.resizePTY(columns, rows, widthPixels, heightPixels)
    }

    override fun close() {
        runCatching { session?.close() }
        runCatching { connection?.close() }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
    }
}
