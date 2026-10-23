package dev.hermeskotlin.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hermeskotlin.core.gateway.GatewayUrl
import dev.hermeskotlin.core.network.ApiResult
import dev.hermeskotlin.core.usage.ClaudeUsage
import dev.hermeskotlin.core.usage.ClaudeUsageApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** How full a usage window is: the bar's colour. */
enum class UsageSeverity { Normal, Warning, Error }

/** Under 70 is normal, 70 to 90 a warning, over 90 an error, by the whole number the bar shows. */
fun usageSeverity(percent: Double): UsageSeverity = when {
    percentShown(percent) > 90 -> UsageSeverity.Error
    percentShown(percent) >= 70 -> UsageSeverity.Warning
    else -> UsageSeverity.Normal
}

/** [percent] as the whole number to print; a reading that isn't a number counts as 0. */
fun percentShown(percent: Double): Int = if (percent.isFinite()) percent.roundToInt() else 0

/** [percent] as the bar's fill, 0 to 1. */
fun usageFraction(percent: Double): Float = if (percent.isFinite()) (percent / 100).toFloat().coerceIn(0f, 1f) else 0f

/**
 * The bar is hidden while [usage] is null (plugin missing, or nothing read yet). [muted] draws the last good
 * numbers dimmed, because the latest refresh failed or the gateway itself served stale numbers.
 */
data class ClaudeUsageUiState(
    val usage: ClaudeUsage? = null,
    val muted: Boolean = false,
) {
    val visible: Boolean get() = usage != null && (usage.fiveHour != null || usage.sevenDay != null)
}

/**
 * The Claude subscription's usage for the sidebar's bar. It reads when the sidebar shows and every
 * [POLL_MILLIS] while it stays shown; a closed drawer does not poll.
 */
class ClaudeUsageViewModel(private val api: ClaudeUsageApi) : ViewModel() {

    private val _state = MutableStateFlow(ClaudeUsageUiState())
    val state: StateFlow<ClaudeUsageUiState> = _state.asStateFlow()

    private var gateway: GatewayUrl? = null
    private var visible = false
    private var pollJob: Job? = null

    fun bind(gateway: GatewayUrl) {
        if (this.gateway == gateway) return
        this.gateway = gateway
        // Another gateway's numbers are not this one's.
        _state.value = ClaudeUsageUiState()
        restart()
    }

    fun setVisible(visible: Boolean) {
        if (this.visible == visible) return
        this.visible = visible
        restart()
    }

    private fun restart() {
        pollJob?.cancel()
        pollJob = null
        val url = gateway?.takeIf { visible } ?: return
        pollJob = viewModelScope.launch {
            while (true) {
                refresh(url)
                delay(POLL_MILLIS)
            }
        }
    }

    private suspend fun refresh(url: GatewayUrl) {
        val result = api.usage(url)
        _state.value = when (result) {
            is ApiResult.Success -> ClaudeUsageUiState(result.value, muted = result.value.stale)
            // Keep an earlier good reading, dimmed; with none there is nothing to show, and no error chrome.
            else -> _state.value.copy(muted = true)
        }
    }

    companion object {
        const val POLL_MILLIS = 2 * 60 * 1000L
    }
}
