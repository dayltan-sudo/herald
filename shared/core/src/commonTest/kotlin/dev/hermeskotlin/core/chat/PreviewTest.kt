package dev.hermeskotlin.core.chat

import dev.hermeskotlin.core.rpc.GatewayEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreviewTest {
    private val host = "pi.tail1234.ts.net"

    private fun event(type: String, payload: String) = GatewayEvent(type, Json.parseToJsonElement(payload) as JsonObject, "s1", null)

    private fun web(raw: String, gateway: String = host) = assertIs<PreviewDestination.Web>(resolvePreviewTarget(raw, gateway)).url

    private fun refused(raw: String, gateway: String = host) = assertIs<PreviewDestination.Refused>(resolvePreviewTarget(raw, gateway)).reason

    @Test
    fun localhostIsTheGatewayMachineNotThePhone() {
        assertEquals("http://$host:3000/", web("http://localhost:3000/"))
        assertEquals("http://$host:5173/app?x=1#top", web("http://127.0.0.1:5173/app?x=1#top"))
        assertEquals("http://$host:8000", web("http://0.0.0.0:8000"))
        assertEquals("http://$host:3000", web("localhost:3000"))
        assertEquals("http://$host/", web("http://localhost/"))
        // An address the gateway is reached by as a number.
        assertEquals("http://100.78.230.91:3000/", web("http://localhost:3000/", gateway = "100.78.230.91"))
        assertEquals("http://[fd7a::1]:3000/", web("http://localhost:3000/", gateway = "fd7a::1"))
    }

    @Test
    fun unencryptedPagesLoadOnlyFromOwnNetworks() {
        assertEquals("http://100.78.230.91:3000/", web("http://100.78.230.91:3000/"))
        assertEquals("http://192.168.1.20:8080", web("http://192.168.1.20:8080"))
        assertEquals("http://10.0.0.5/", web("http://10.0.0.5/"))
        assertEquals("http://172.20.0.2/", web("http://172.20.0.2/"))
        assertEquals("http://pi.local:3000", web("pi.local:3000"))
        assertEquals("http://$host:3000/x", web("http://$host:3000/x"))
        assertTrue(refused("http://example.com/").contains("https"))
        assertTrue(refused("http://172.32.0.1/").contains("https"))
        assertTrue(refused("http://100.128.0.1/").contains("https"))
        assertTrue(refused("http://8.8.8.8/").contains("https"))
    }

    @Test
    fun httpsIsEnoughForAnywhere() {
        assertEquals("https://example.com/docs", web("https://example.com/docs"))
        assertEquals("https://example.com", web("example.com"))
        assertEquals("https://www.example.com/a?b=c", web("www.example.com/a?b=c"))
    }

    @Test
    fun filesGoToTheFileViewer() {
        assertEquals(PreviewDestination.File("/home/me/report.html"), resolvePreviewTarget("/home/me/report.html", host))
        assertEquals(PreviewDestination.File("~/site/index.html"), resolvePreviewTarget("~/site/index.html", host))
        assertEquals(PreviewDestination.File("/tmp/a.html"), resolvePreviewTarget("file:///tmp/a.html", host))
    }

    @Test
    fun otherSchemesAndSneakyAddressesAreRefused() {
        assertTrue(refused("javascript:alert(1)").isNotEmpty())
        assertTrue(refused("ftp://example.com/").contains("ftp"))
        assertTrue(refused("content://media/x").contains("content"))
        assertTrue(refused("https://user:pw@example.com/").contains("sign-in"))
        assertTrue(refused("https://example.com@evil.test/").contains("sign-in"))
        assertTrue(refused("   ").isNotEmpty())
    }

    @Test
    fun theAgentOpeningAndClosingAPage() {
        val start = ChatState()
        val opened = start.reduce(event("preview.open", """{"url":"http://localhost:3000","label":"App"}"""))
        assertEquals(PreviewTarget("http://localhost:3000", "App", 1), opened.preview)
        // The same address again is a new open, so the page reloads.
        val again = opened.reduce(event("preview.open", """{"url":"http://localhost:3000"}"""))
        assertEquals(2, again.preview?.version)
        // A blank address opens nothing.
        assertEquals(opened, opened.reduce(event("preview.open", """{"url":"  "}""")))
        // Closing another page leaves this one; the pane as a whole, or this page, closes it.
        assertEquals(opened, opened.reduce(event("preview.close", """{"url":"http://elsewhere"}""")))
        assertNull(opened.reduce(event("preview.close", """{"url":""}""")).preview)
        assertNull(opened.reduce(event("preview.close", """{}""")).preview)
        assertNull(opened.reduce(event("preview.close", """{"url":"http://localhost:3000"}""")).preview)
    }

    @Test
    fun readingAPageInSlices() {
        val snapshot = PageSnapshot("http://pi:3000/", "Todo", "x".repeat(50_000))
        val first = snapshot.slice(null, null)
        assertEquals(0, first.start)
        assertEquals(PreviewBridge.MAX_READ_CHARS, first.end)
        assertEquals(50_000, first.totalChars)
        val tail = snapshot.slice(49_990, 100)
        assertEquals(49_990, tail.start)
        assertEquals(50_000, tail.end)
        assertEquals(10, tail.text.length)
        // A start past the end gives nothing, not an error.
        val past = snapshot.slice(60_000, 10)
        assertEquals(50_000, past.start)
        assertEquals("", past.text)
        assertEquals(5, snapshot.slice(0, 5).text.length)
    }

    @Test
    fun theBridgeAnswersReadsAndDrives() {
        assertEquals(setOf("preview.read", "preview.act"), PreviewBridge.METHODS)
    }

    @Test
    fun theAnswerCarriesTheFieldsDesktopSends() {
        val json = PageSnapshot("http://pi:3000/", "Todo", "hello").slice(null, null).toJson()
        val parsed = Json.parseToJsonElement(json).jsonObject
        assertEquals("browser", parsed.getValue("kind").jsonPrimitive.content)
        assertEquals("http://pi:3000/", parsed.getValue("url").jsonPrimitive.content)
        assertEquals("Todo", parsed.getValue("title").jsonPrimitive.content)
        assertEquals("hello", parsed.getValue("text").jsonPrimitive.content)
        assertEquals("5", parsed.getValue("total_chars").jsonPrimitive.content)
    }
}
