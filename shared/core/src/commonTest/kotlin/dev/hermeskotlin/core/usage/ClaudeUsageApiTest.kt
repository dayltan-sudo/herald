package dev.hermeskotlin.core.usage

import dev.hermeskotlin.core.gateway.GatewayUrl
import dev.hermeskotlin.core.network.ApiResult
import dev.hermeskotlin.core.network.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClaudeUsageApiTest {

    private val url = GatewayUrl.parse("https://hermes.example.ts.net")
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun api(body: String, status: HttpStatusCode = HttpStatusCode.OK, onRequest: (String) -> Unit = {}) =
        ClaudeUsageApi(createHttpClient(MockEngine { request ->
            onRequest(request.url.encodedPath)
            respond(body, status, json)
        }))

    @Test
    fun parsesASuccessfulReading() = runTest {
        var path: String? = null
        val body = """{"ok":true,"plan":"pro","five_hour":{"percent":5.0,"resets_at":"2026-10-23T09:30:00.026100+00:00"},
            "seven_day":{"percent":14.0,"resets_at":"2026-10-26T12:59:00+00:00"},"fetched_at":1792738567.77,"stale":false,"extra":1}"""

        val usage = assertIs<ApiResult.Success<ClaudeUsage>>(api(body) { path = it }.usage(url)).value

        assertEquals("/api/plugins/claude-usage/usage", path)
        assertEquals("pro", usage.plan)
        assertEquals(5.0, usage.fiveHour?.percent)
        assertEquals(14.0, usage.sevenDay?.percent)
        assertEquals(false, usage.stale)
        assertNull(usage.error)
        // 2026-10-23T09:30:00.0261Z
        assertEquals(1792747800026L, usage.fiveHour?.resetsAtMillis)
        assertEquals(1793019540000L, usage.sevenDay?.resetsAtMillis)
    }

    @Test
    fun aStaleReadingKeepsItsNumbersAndTheError() = runTest {
        val body = """{"ok":true,"plan":"max","five_hour":{"percent":71.5,"resets_at":null},"seven_day":null,
            "fetched_at":1792738000.0,"stale":true,"error":"http_429"}"""

        val usage = assertIs<ApiResult.Success<ClaudeUsage>>(api(body).usage(url)).value

        assertTrue(usage.stale)
        assertEquals("http_429", usage.error)
        assertEquals(71.5, usage.fiveHour?.percent)
        assertNull(usage.fiveHour?.resetsAtMillis)
    }

    @Test
    fun eitherWindowMayBeNull() = runTest {
        val body = """{"ok":true,"plan":"pro","five_hour":null,"seven_day":{"percent":3.0,"resets_at":"2026-10-26T12:59:00+00:00"},
            "fetched_at":1.0,"stale":false}"""

        val usage = assertIs<ApiResult.Success<ClaudeUsage>>(api(body).usage(url)).value

        assertNull(usage.fiveHour)
        assertEquals(3.0, usage.sevenDay?.percent)
    }

    @Test
    fun aFailureBodyIsAFailedResultNamingTheCode() = runTest {
        for (code in listOf("token_expired", "no_credentials", "http_503", "unreachable", "unexpected_shape")) {
            val result = api("""{"ok":false,"error":"$code"}""").usage(url)
            assertEquals(ApiResult.Failed(0, code), result)
        }
    }

    @Test
    fun aMissingPluginIsA404() = runTest {
        val result = api("""{"detail":"Not Found"}""", HttpStatusCode.NotFound).usage(url)

        assertEquals(404, assertIs<ApiResult.Failed>(result).status)
    }

    @Test
    fun anUnreadableBodyFails() = runTest {
        assertIs<ApiResult.Failed>(api("""["not","an","object"]""").usage(url))
    }

    @Test
    fun anUnreadableResetTimeIsNull() {
        assertNull(UsageWindow(percent = 1.0, resetsAt = "tomorrow").resetsAtMillis)
    }
}
