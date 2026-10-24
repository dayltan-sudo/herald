package dev.hermeskotlin.ui.chat

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import dev.hermeskotlin.core.chat.PageSnapshot
import dev.hermeskotlin.core.chat.PreviewDestination
import dev.hermeskotlin.core.chat.resolvePreviewTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.coroutines.resume

/**
 * The agent's page in a WebView of its own: no gateway sign-in (the jar the plugin pages borrow is emptied
 * before and after), no bridge from the page to the app, and every address it moves to is checked the way the
 * first was, so a link to `localhost` reaches the gateway's machine and not the phone.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal actual fun PreviewWebView(url: String, version: Int, controller: PreviewController, host: String, modifier: Modifier) {
    val context = LocalContext.current
    val gatewayHost = rememberUpdatedState(host)
    // One view per controller: a new address is a new page with its own state.
    val webView = remember(controller) {
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            setAcceptCookie(true)
        }
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    val wanted = request.url.toString()
                    return when (val where = resolvePreviewTarget(wanted, gatewayHost.value)) {
                        is PreviewDestination.Web -> {
                            val same = where.url.equals(wanted, ignoreCase = true)
                            // `localhost` here is the phone; the page meant the gateway's machine.
                            if (!same) view.loadUrl(where.url)
                            !same
                        }
                        // Not for this page to show: the browser's to open, when it is a web address at all.
                        else -> {
                            if (request.url.scheme == "http" || request.url.scheme == "https") openExternally(context, request.url)
                            true
                        }
                    }
                }

                override fun onPageStarted(view: WebView, pageUrl: String?, favicon: android.graphics.Bitmap?) {
                    controller.loading = true
                    controller.failed = false
                    pageUrl?.let { controller.address = it }
                }

                override fun onPageFinished(view: WebView, pageUrl: String?) {
                    controller.loading = false
                    pageUrl?.let { controller.address = it }
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    // A missing picture or script still leaves a page; only the page itself failing is a failure.
                    if (request.isForMainFrame) {
                        controller.loading = false
                        controller.failed = true
                    }
                }

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    controller.loading = false
                    controller.failed = true
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView, title: String?) {
                    controller.title = title.orEmpty()
                }

                // The page gets no camera, microphone or location.
                override fun onPermissionRequest(request: PermissionRequest) = request.deny()
            }
        }
    }
    DisposableEffect(webView) {
        controller.reload = { webView.reload() }
        controller.goBack = { webView.canGoBack().also { if (it) webView.goBack() } }
        controller.read = { readPage(webView) }
        controller.drive = { params -> drivePage(webView, controller, params) }
        onDispose {
            controller.reload = null
            controller.goBack = null
            controller.read = null
            controller.drive = null
            webView.stopLoading()
            webView.destroy()
            // The view's jar is on disk and outlives the page: leave the dev app's sessions out of it.
            CookieManager.getInstance().removeAllCookies(null)
        }
    }
    // A new open of the same address reloads it: [version] moves, [url] may not.
    LaunchedEffect(url, version) { webView.loadUrl(url) }
    AndroidView(factory = { webView }, modifier = modifier)
}

private fun openExternally(context: Context, uri: Uri) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}

/**
 * Runs [script] in the page and gives back the string it returns (null when it returns nothing, or the page
 * doesn't answer in time). Must be called on the main thread.
 */
private suspend fun evalString(view: WebView, script: String): String? {
    val raw = withTimeoutOrNull(READ_TIMEOUT_MS) {
        suspendCancellableCoroutine<String?> { done -> view.evaluateJavascript(script) { done.resume(it) } }
    } ?: return null
    // A returned string comes back as a JSON string literal.
    return runCatching { Json.parseToJsonElement(raw).jsonPrimitive.content }.getOrNull()
}

/** The page's title and visible text, as the page itself reports them; null when it won't say in time. */
private suspend fun readPage(view: WebView): PageSnapshot? = withContext(Dispatchers.Main) {
    val reply = evalString(view, READ_SCRIPT) ?: return@withContext null
    val page = runCatching { Json.parseToJsonElement(reply).jsonObject }.getOrNull() ?: return@withContext null
    PageSnapshot(
        url = view.url.orEmpty(),
        title = page["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        text = page["text"]?.jsonPrimitive?.contentOrNull.orEmpty(),
    )
}

private fun failure(message: String): String = buildJsonObject {
    put("success", false)
    put("error", message)
}.toString()

/**
 * One `drive_preview` action on the page, answered as Desktop's tool does: an inventory of what can be clicked
 * or typed into, then only what changed after each action. History moves and reloads are the view's own; the rest
 * runs in the page ([PREVIEW_DRIVE_SCRIPT]), then waits for the page to settle before saying what changed.
 */
private suspend fun drivePage(view: WebView, controller: PreviewController, params: JsonObject): String = withContext(Dispatchers.Main) {
    val action = (params["action"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    when (action) {
        "back", "forward" -> {
            val can = if (action == "back") view.canGoBack() else view.canGoForward()
            if (!can) return@withContext failure("Nothing to go $action to.")
            if (action == "back") view.goBack() else view.goForward()
            return@withContext moved(view, controller)
        }
        "reload" -> {
            view.reload()
            return@withContext moved(view, controller)
        }
    }
    if (evalString(view, PREVIEW_DRIVE_SCRIPT) != "ready") return@withContext failure("The page isn't ready to be driven yet.")
    val literal = JsonPrimitive(JsonObject(params.filterKeys { it != "session_id" }).toString()).toString()
    val ran = evalString(view, "window.__hd.run($literal)") ?: return@withContext failure("The page didn't answer.")
    val error = runCatching { Json.parseToJsonElement(ran).jsonObject["error"]?.jsonPrimitive?.contentOrNull }.getOrNull()
    if (error != null) return@withContext failure(error)
    if (action != "elements") {
        // The page may react (a request, a re-render) or move to another page: give it a moment, then wait out a load.
        delay(SETTLE_MS)
        var waited = 0L
        while (controller.loading && waited < LOAD_WAIT_MS) {
            delay(POLL_MS)
            waited += POLL_MS
        }
    }
    val delta = evalString(view, "window.__hd ? window.__hd.delta($literal) : 'moved'")
    if (delta == null || delta == "moved") return@withContext moved(view, controller)
    delta
}

/** After the page itself moved: the refs of the old page are gone, so say so and where it is now. */
private suspend fun moved(view: WebView, controller: PreviewController): String {
    delay(SETTLE_MS)
    var waited = 0L
    while (controller.loading && waited < LOAD_WAIT_MS) {
        delay(POLL_MS)
        waited += POLL_MS
    }
    return buildJsonObject {
        put("success", true)
        put("navigated", true)
        put("url", view.url.orEmpty())
        put("title", view.title.orEmpty())
        put("note", "The page changed, so earlier refs are gone. Call elements again.")
    }.toString()
}

private const val READ_TIMEOUT_MS = 5_000L
private const val SETTLE_MS = 400L
private const val POLL_MS = 100L
private const val LOAD_WAIT_MS = 4_000L
private const val READ_SCRIPT = "JSON.stringify({title:document.title||'',text:(document.body?document.body.innerText:'')||''})"
