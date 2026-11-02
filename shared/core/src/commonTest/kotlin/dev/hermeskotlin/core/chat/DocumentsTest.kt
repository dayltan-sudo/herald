package dev.hermeskotlin.core.chat

import dev.hermeskotlin.core.media.GatewayFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DocumentsTest {

    private fun sent(vararg attachments: ShownAttachment) = ChatMessage.User("u${attachments.size}", "see attached", attachments = attachments.toList())

    private fun reply(text: String, tools: List<ToolActivity> = emptyList()) = ChatMessage.Assistant("a", text = text, tools = tools)

    @Test
    fun sentFilesAreFoundInTheChatsFolderAndImagesWhereTheGatewayKeepsThem() {
        val messages = listOf(
            sent(
                ShownAttachment("1", "photo.jpg", AttachmentKind.Image, gatewayPath = "/home/x/images/photo.jpg"),
                ShownAttachment("2", "CV.pdf", AttachmentKind.Pdf, refPath = "attachments/CV.pdf"),
            ),
        )
        assertEquals(
            listOf(
                SessionDocument("photo.jpg", DocumentOrigin.Sent, isImage = true, file = GatewayFile("/home/x/images/photo.jpg")),
                SessionDocument("CV.pdf", DocumentOrigin.Sent, isImage = false, file = GatewayFile("/work/attachments/CV.pdf")),
            ),
            documentsOf(messages, cwd = "/work/"),
        )
    }

    @Test
    fun aRelativeFileWithoutAKnownFolderIsListedButHasNothingToOpen() {
        val docs = documentsOf(listOf(sent(ShownAttachment("1", "CV.pdf", AttachmentKind.Pdf, refPath = "attachments/CV.pdf"))))
        assertEquals(listOf("CV.pdf"), docs.map { it.name })
        assertNull(docs.single().file)
    }

    @Test
    fun deliveredFilesAndGeneratedPicturesAreProducedButWebPicturesAreNot() {
        val generated = GeneratedImage("/cache/gen.png", listOf("/cache/gen.png"))
        val messages = listOf(
            reply(
                "Done: MEDIA:/out/report.xlsx and ![chart](https://example.com/c.png) and ![local](/out/chart.png)",
                tools = listOf(ToolActivity("t", "image_generate", generatedImage = generated)),
            ),
        )
        assertEquals(
            listOf(
                SessionDocument("gen.png", DocumentOrigin.Produced, isImage = true, file = GatewayFile("/cache/gen.png")),
                SessionDocument("chart.png", DocumentOrigin.Produced, isImage = true, file = GatewayFile("/out/chart.png")),
                SessionDocument("report.xlsx", DocumentOrigin.Produced, isImage = false, file = GatewayFile("/out/report.xlsx")),
            ),
            documentsOf(messages),
        )
    }

    @Test
    fun theSameFileTwiceIsListedOnce() {
        val messages = listOf(reply("MEDIA:/out/report.xlsx"), reply("Again: MEDIA:/out/report.xlsx"))
        assertEquals(1, documentsOf(messages).size)
    }

    @Test
    fun attachmentReferenceLinesKeepTheirPath() {
        val (shown, text) = splitAttachmentRefs("@file:attachments/CV.pdf\n@file:notes.txt\nSummarise", "k")
        assertEquals("Summarise", text)
        assertEquals(listOf("attachments/CV.pdf", "notes.txt"), shown.map { it.refPath })
        assertEquals(listOf(AttachmentKind.Pdf, AttachmentKind.File), shown.map { it.kind })
        assertNull(shown[0].gatewayPath)
    }
}
