package dev.hermeskotlin.core.chat

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What the agent asked Herald to show beside the chat (`preview.open`): a web address or a file on the gateway,
 * as the agent wrote it. [version] moves on every open, so the same address opened again reloads.
 */
data class PreviewTarget(val target: String, val label: String = "", val version: Int = 0)

/** The questions the agent can put to the page Herald shows (tui_gateway/contracts/server_requests.py). */
object PreviewBridge {
    const val READ = "preview.read"
    val METHODS = setOf(READ)

    /** The most text one read returns; the agent pages through the rest with `start`. */
    const val MAX_READ_CHARS = 20_000
}

/** The page on screen, whole: its address, title and visible text. */
class PageSnapshot(val url: String, val title: String, val text: String) {
    /** The part `preview.read` asked for, [start] characters in, at most [count] (and [PreviewBridge.MAX_READ_CHARS]) long. */
    fun slice(start: Int?, count: Int?): PreviewPage {
        val from = (start ?: 0).coerceIn(0, text.length)
        val to = (from + (count ?: PreviewBridge.MAX_READ_CHARS).coerceIn(0, PreviewBridge.MAX_READ_CHARS)).coerceAtMost(text.length)
        return PreviewPage(url, title, text.substring(from, to), from, to, text.length)
    }
}

/** Reads the page the preview shows now; null when it can't (still loading, closed). */
fun interface PreviewReader {
    suspend fun snapshot(): PageSnapshot?
}

/** The answer to `preview.read`, shaped like Desktop's so the agent's tool reads it the same way. */
data class PreviewPage(val url: String, val title: String, val text: String, val start: Int, val end: Int, val totalChars: Int) {
    fun toJson(): String = buildJsonObject {
        put("kind", "browser")
        put("url", url)
        put("title", title)
        put("text", text)
        put("start", start)
        put("end", end)
        put("total_chars", totalChars)
    }.toString()
}

/** Where a [PreviewTarget] goes: a page in the preview, a file in the file viewer, or nowhere. */
sealed interface PreviewDestination {
    data class Web(val url: String) : PreviewDestination

    data class File(val path: String) : PreviewDestination

    data class Refused(val reason: String) : PreviewDestination
}

/**
 * Reads [raw], as the agent wrote it, for the phone. `localhost` and its kin are the gateway's machine, not the
 * phone, so they are swapped for [gatewayHost] (a dev server there must listen on that address). Unencrypted
 * pages load only from the gateway, a private or tailnet address or a local name; anything else must be
 * https. Other schemes and addresses with a sign-in in them are refused.
 */
fun resolvePreviewTarget(raw: String, gatewayHost: String): PreviewDestination {
    val text = raw.trim()
    if (text.isEmpty()) return PreviewDestination.Refused("The agent didn't say what to show.")
    if (text.startsWith("/") || text.startsWith("~/") || text.startsWith("file://", ignoreCase = true)) {
        return PreviewDestination.File(text.replace(FILE_SCHEME, ""))
    }
    // `javascript:`, `mailto:`, `data:`: a scheme with no `//`. `localhost:3000` is a host with a port, not one.
    NO_SLASHES_SCHEME.find(text)?.let { return PreviewDestination.Refused("Herald shows web pages (http or https), not ${it.groupValues[1]}: addresses.") }
    val gateway = gatewayHost.lowercase()
    val withScheme = if ("://" in text) {
        text
    } else {
        // A bare `localhost:3000` or `app.example.com`: plain http only where http is allowed anyway.
        val bareHost = hostOf(text.substringBefore('/').substringBefore('?').substringBefore('#'))
        if (isLoopback(bareHost) || isPrivateHost(bareHost, gateway)) "http://$text" else "https://$text"
    }
    val scheme = withScheme.substringBefore("://").lowercase()
    if (scheme != "http" && scheme != "https") return PreviewDestination.Refused("Herald shows web pages (http or https), not $scheme: addresses.")
    val rest = withScheme.substringAfter("://")
    val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
    val tail = rest.removePrefix(authority)
    if ('@' in authority) return PreviewDestination.Refused("Herald won't open an address with a sign-in in it.")
    val host = hostOf(authority)
    if (host.isEmpty()) return PreviewDestination.Refused("That address has no host.")
    val port = authority.substringAfterLast(':', "").takeIf { it.isNotEmpty() && it.all(Char::isDigit) && !authority.endsWith("]") }
    val swapped = isLoopback(host)
    val finalHost = if (swapped) gateway else host
    if (scheme == "http" && !swapped && !isPrivateHost(finalHost, gateway)) {
        return PreviewDestination.Refused("Herald loads unencrypted pages only from your own network or tailnet. Use https for $host.")
    }
    val shownHost = if (':' in finalHost) "[$finalHost]" else finalHost
    return PreviewDestination.Web("$scheme://$shownHost${port?.let { ":$it" }.orEmpty()}$tail")
}

/** Whether [url], already a web address, may load in the preview: the same rule [resolvePreviewTarget] applies. */
fun previewMayLoad(url: String, gatewayHost: String): Boolean = resolvePreviewTarget(url, gatewayHost) is PreviewDestination.Web

private val NO_SLASHES_SCHEME = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):(?!//)(?!\\d+(?:[/?#]|$))")

private val FILE_SCHEME = Regex("^file://", RegexOption.IGNORE_CASE)

private fun hostOf(authority: String): String {
    if (authority.startsWith("[")) return authority.substringAfter('[').substringBefore(']').lowercase()
    val port = authority.substringAfterLast(':', "")
    val hasPort = authority.contains(':') && port.isNotEmpty() && port.all(Char::isDigit)
    return (if (hasPort) authority.substringBeforeLast(':') else authority).lowercase()
}

private fun isLoopback(host: String) = host == "localhost" || host == "0.0.0.0" || host == "::1" || host == "::" || host.startsWith("127.")

private fun isPrivateHost(host: String, gateway: String): Boolean {
    if (host == gateway) return true
    if (host.endsWith(".ts.net") || host.endsWith(".local") || host.endsWith(".lan")) return true
    if (':' in host) return host.startsWith("fd") || host.startsWith("fc") || host.startsWith("fe80")
    val octets = host.split('.').map { it.toIntOrNull() }
    if (octets.size == 4 && octets.all { it != null && it in 0..255 }) {
        val (a, b) = octets[0]!! to octets[1]!!
        return a == 10 || a == 192 && b == 168 || a == 172 && b in 16..31 || a == 100 && b in 64..127
    }
    // A name without a dot is on the local network (`piserver`), not the public internet.
    return '.' !in host
}
