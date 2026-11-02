package dev.hermeskotlin.core.chat

import dev.hermeskotlin.core.media.GatewayFile

enum class DocumentOrigin { Sent, Produced }

/**
 * A file or picture the chat has carried, for the Documents sheet. [file] is where the gateway keeps it; null
 * when there is nothing to fetch, e.g. an attachment this device just sent (the chat names its path only once
 * reloaded) or a file the prompt named relatively in a chat whose folder isn't known.
 */
data class SessionDocument(
    val name: String,
    val origin: DocumentOrigin,
    val isImage: Boolean,
    val file: GatewayFile?,
)

/**
 * Every file in [messages]: what the person attached ([DocumentOrigin.Sent]) and what replies delivered
 * ([DocumentOrigin.Produced]: `MEDIA:` files, pictures on the gateway's disk, generated images), oldest first.
 * Derived from the transcript each time and kept nowhere; the files stay on the gateway until one is opened.
 * Web pictures are links, not files, so they are left out. A file named relatively is looked for in [cwd],
 * the chat's folder.
 */
fun documentsOf(messages: List<ChatMessage>, cwd: String? = null): List<SessionDocument> {
    val found = mutableMapOf<String, SessionDocument>()
    fun add(document: SessionDocument) {
        found.putIfAbsent(document.file?.path ?: "${document.origin}:${document.name}", document)
    }
    for (message in messages) {
        when (message) {
            is ChatMessage.User -> message.attachments.forEach { attachment ->
                val path = attachment.gatewayPath ?: attachment.refPath?.let { inFolder(it, cwd) }
                add(SessionDocument(attachment.name, DocumentOrigin.Sent, attachment.kind == AttachmentKind.Image, path?.let { GatewayFile(it) }))
            }
            is ChatMessage.Assistant -> {
                val generated = message.tools.mapNotNull { it.generatedImage }
                val media = generated.map { it.asMedia() } + extractReplyMedia(message.text, generated).second
                media.filter { it.generated || it.onGateway }.forEach { item ->
                    add(SessionDocument(item.name, DocumentOrigin.Produced, item.isImage, GatewayFile.fromLink(item.source)))
                }
            }
            else -> {}
        }
    }
    return found.values.toList()
}

/** [path] as the gateway can read it: absolute and `~/` paths as they are, others joined to [cwd]; null without one. */
private fun inFolder(path: String, cwd: String?): String? {
    val clean = path.trim().trim('`', '"')
    if (clean.isEmpty()) return null
    if (clean.startsWith("/") || clean.startsWith("~/")) return clean
    val folder = cwd?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return null
    return "$folder/${clean.removePrefix("./")}"
}
