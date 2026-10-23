package dev.hermeskotlin.ui.sessions

import dev.hermeskotlin.core.gateway.SavedGateway
import dev.hermeskotlin.core.network.createHttpClient
import dev.hermeskotlin.core.usage.ClaudeUsageApi
import dev.hermeskotlin.ui.components.LocalMoment
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ClaudeUsageViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val gateway = SavedGateway("https://hermes.example.ts.net")
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val good = """{"ok":true,"plan":"pro","five_hour":{"percent":5.0,"resets_at":"2026-10-23T09:30:00+00:00"},
        "seven_day":{"percent":14.0,"resets_at":"2026-10-26T12:59:00+00:00"},"fetched_at":1.0,"stale":false}"""

    /** What the gateway answers next, and how many times it was asked. */
    private var status = HttpStatusCode.OK
    private var body = good
    private var requests = 0

    private fun viewModel(): ClaudeUsageViewModel {
        val engine = MockEngineConfig()
        engine.dispatcher = dispatcher
        engine.addHandler {
            requests++
            respond(body, status, json)
        }
        return ClaudeUsageViewModel(ClaudeUsageApi(createHttpClient(MockEngine(engine)))).also { it.bind(gateway.gatewayUrl) }
    }

    @Test
    fun aReadingShowsOnceTheSidebarIsVisible() = runTest(dispatcher) {
        val vm = viewModel()
        assertFalse(vm.state.value.visible)
        assertEquals(0, requests)

        vm.setVisible(true)

        assertTrue(vm.state.value.visible)
        assertFalse(vm.state.value.muted)
        assertEquals(5.0, vm.state.value.usage?.fiveHour?.percent)
        assertEquals(14.0, vm.state.value.usage?.sevenDay?.percent)
        vm.setVisible(false)
    }

    @Test
    fun aMissingPluginHidesTheBar() = runTest(dispatcher) {
        status = HttpStatusCode.NotFound
        body = """{"detail":"Not Found"}"""
        val vm = viewModel()

        vm.setVisible(true)

        assertEquals(1, requests)
        assertFalse(vm.state.value.visible)
        assertNull(vm.state.value.usage)
        vm.setVisible(false)
    }

    @Test
    fun aFailureWithNoEarlierReadingHidesTheBar() = runTest(dispatcher) {
        body = """{"ok":false,"error":"token_expired"}"""
        val vm = viewModel()

        vm.setVisible(true)

        assertFalse(vm.state.value.visible)
        vm.setVisible(false)
    }

    @Test
    fun aFailureAfterAGoodReadingKeepsTheNumbersMuted() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setVisible(true)
        assertFalse(vm.state.value.muted)

        body = """{"ok":false,"error":"unreachable"}"""
        advanceTimeBy(ClaudeUsageViewModel.POLL_MILLIS + 1)

        assertEquals(2, requests)
        assertTrue(vm.state.value.visible)
        assertTrue(vm.state.value.muted)
        assertEquals(5.0, vm.state.value.usage?.fiveHour?.percent)

        // The next good reading brings it back to full strength.
        body = good
        advanceTimeBy(ClaudeUsageViewModel.POLL_MILLIS)
        assertFalse(vm.state.value.muted)
        vm.setVisible(false)
    }

    @Test
    fun aStaleReadingIsMuted() = runTest(dispatcher) {
        body = """{"ok":true,"plan":"pro","five_hour":{"percent":50.0,"resets_at":null},"seven_day":null,
            "fetched_at":1.0,"stale":true,"error":"http_429"}"""
        val vm = viewModel()

        vm.setVisible(true)

        assertTrue(vm.state.value.visible)
        assertTrue(vm.state.value.muted)
        vm.setVisible(false)
    }

    @Test
    fun aReadingWithNoWindowsShowsNothing() = runTest(dispatcher) {
        body = """{"ok":true,"plan":"pro","five_hour":null,"seven_day":null,"fetched_at":1.0,"stale":false}"""
        val vm = viewModel()

        vm.setVisible(true)

        assertNotNull(vm.state.value.usage)
        assertFalse(vm.state.value.visible)
        vm.setVisible(false)
    }

    @Test
    fun itRefreshesEveryTwoMinutesWhileVisibleAndNeverWhileClosed() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setVisible(true)
        assertEquals(1, requests)

        advanceTimeBy(ClaudeUsageViewModel.POLL_MILLIS - 1)
        assertEquals(1, requests)
        advanceTimeBy(2)
        assertEquals(2, requests)

        vm.setVisible(false)
        advanceTimeBy(10 * ClaudeUsageViewModel.POLL_MILLIS)
        assertEquals(2, requests)

        // Opening again reads at once.
        vm.setVisible(true)
        assertEquals(3, requests)
        vm.setVisible(false)
    }

    @Test
    fun severityFollowsTheThresholds() {
        assertEquals(UsageSeverity.Normal, usageSeverity(0.0))
        assertEquals(UsageSeverity.Normal, usageSeverity(69.4))
        assertEquals(UsageSeverity.Warning, usageSeverity(69.5))
        assertEquals(UsageSeverity.Warning, usageSeverity(70.0))
        assertEquals(UsageSeverity.Warning, usageSeverity(90.0))
        assertEquals(UsageSeverity.Warning, usageSeverity(90.4))
        assertEquals(UsageSeverity.Error, usageSeverity(90.5))
        assertEquals(UsageSeverity.Error, usageSeverity(100.0))
        assertEquals(UsageSeverity.Error, usageSeverity(140.0))
    }

    @Test
    fun percentIsRoundedAndTheBarClamped() {
        assertEquals(6, percentShown(5.6))
        assertEquals(5, percentShown(5.4))
        assertEquals(0, percentShown(Double.NaN))
        assertEquals(0.5f, usageFraction(50.0))
        assertEquals(1f, usageFraction(140.0))
        assertEquals(0f, usageFraction(-3.0))
        assertEquals(0f, usageFraction(Double.NaN))
    }

    @Test
    fun resetLabelsUseTheLocalClockAndForTheWeekTheWeekday() {
        val monday = LocalMoment(epochDay = 0, year = 2026, month = 10, day = 26, dayOfWeek = 1, hour = 20, minute = 59)
        val evening = monday.copy(hour = 17, minute = 30)

        assertEquals("resets 17:30", usageResetLabel(evening, weekday = false, use24Hour = true))
        assertEquals("resets Mon 20:59", usageResetLabel(monday, weekday = true, use24Hour = true))
        assertEquals("resets Mon 8:59 PM", usageResetLabel(monday, weekday = true, use24Hour = false))
    }
}
