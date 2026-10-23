package dev.hermeskotlin.core.usage

import dev.hermeskotlin.core.gateway.GatewayUrl
import dev.hermeskotlin.core.network.ApiResult
import dev.hermeskotlin.core.network.apiCall
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** One rate-limit window of the Claude subscription. */
@Serializable
data class UsageWindow(
    /** How much of the window is used, 0–100 (the gateway may report more). */
    val percent: Double = 0.0,
    /** When the window resets, ISO 8601 with an offset. */
    @SerialName("resets_at") val resetsAt: String? = null,
) {
    /** [resetsAt] as epoch milliseconds; null when absent or unreadable. */
    @OptIn(ExperimentalTime::class)
    val resetsAtMillis: Long? get() = resetsAt?.let { runCatching { Instant.parse(it) }.getOrNull()?.toEpochMilliseconds() }
}

/**
 * The claude-usage plugin's answer. Either window can be null. [stale] means the numbers are the last good
 * ones and [error] says why they could not be refreshed.
 */
@Serializable
data class ClaudeUsage(
    val ok: Boolean = false,
    val plan: String? = null,
    @SerialName("five_hour") val fiveHour: UsageWindow? = null,
    @SerialName("seven_day") val sevenDay: UsageWindow? = null,
    /** When the gateway read the numbers, epoch seconds. */
    @SerialName("fetched_at") val fetchedAt: Double? = null,
    val stale: Boolean = false,
    val error: String? = null,
)

/** `GET /api/plugins/claude-usage/usage`, from the claude-usage dashboard plugin. */
class ClaudeUsageApi(private val client: HttpClient) {

    /**
     * The current readings. The plugin reports its own failures in a 200 body with `ok` false
     * (`token_expired`, `no_credentials`, `http_NNN`, ...); those come back as [ApiResult.Failed] with the
     * code as the message. A 404 means the plugin isn't installed on this gateway.
     */
    suspend fun usage(url: GatewayUrl): ApiResult<ClaudeUsage> = when (val result = apiCall { client.get(url.resolve("api/plugins/claude-usage/usage")) }) {
        is ApiResult.Success -> try {
            val usage = result.value.body<ClaudeUsage>()
            if (usage.ok) ApiResult.Success(usage) else ApiResult.Failed(0, usage.error ?: "unknown")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResult.Failed(0, "Unexpected response: ${e.message}")
        }
        is ApiResult.Failed -> result
        is ApiResult.Unavailable -> result
        is ApiResult.InvalidCredentials -> result
        is ApiResult.SessionExpired -> result
        is ApiResult.RateLimited -> result
    }
}
