package studio.virel.office

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewAssetLoader
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var tokens: TokenStore
    private lateinit var notion: NotionClient
    private val io = Executors.newFixedThreadPool(3)

    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var pendingSave: Pair<String, String>? = null

    private val pickFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        fileChooserCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        fileChooserCallback = null
    }

    private val createDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val pending = pendingSave
        pendingSave = null
        if (uri == null || pending == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(pending.second.toByteArray(Charsets.UTF_8)) }
            toast("저장했어요")
        } catch (e: Exception) {
            toast("저장하지 못했어요: ${e.message}")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokens = TokenStore(applicationContext)
        notion = NotionClient({ tokens.get() })

        // targetSdk 35부터는 화면이 상태바·내비게이션바 뒤까지 그려진다(edge-to-edge).
        // WebView에 시스템바·키보드 높이만큼 여백을 줘서 헤더와 입력창이 가려지지 않게 한다.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        val root = FrameLayout(this).apply { setBackgroundColor(PAPER) }
        webView = WebView(this).apply { setBackgroundColor(PAPER) }
        root.addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(false)
            textZoom = 100
        }
        webView.addJavascriptInterface(Bridge(), "VirelNative")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.host == APP_HOST) return false
                // 노션·GitHub 등 바깥 링크는 앱 밖(노션 앱/브라우저)으로 연다.
                openExternal(url.toString())
                return true
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams,
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = callback
                return try {
                    pickFile.launch("*/*")
                    true
                } catch (e: ActivityNotFoundException) {
                    fileChooserCallback = null
                    false
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript("(window.__virelBack && window.__virelBack()) ? '1' : '0'") { handled ->
                    if (handled?.contains("1") != true) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(START_URL)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        webView.pauseTimers() // 백그라운드에서는 노션 폴링을 멈춘다
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
        webView.evaluateJavascript("window.__virelNative && window.__virelNative.resume && window.__virelNative.resume()", null)
    }

    override fun onDestroy() {
        io.shutdownNow()
        webView.removeJavascriptInterface("VirelNative")
        webView.destroy()
        super.onDestroy()
    }

    private fun toast(msg: String) = runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

    private fun openExternal(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (e: ActivityNotFoundException) {
            toast("열 수 있는 앱이 없어요")
        }
    }

    private fun js(script: String) = runOnUiThread {
        if (!isDestroyed) webView.evaluateJavascript(script, null)
    }

    private fun showTokenDialog() {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        val help = TextView(this).apply {
            text = "노션 개발자 페이지의 VIREL Office 통합에서 '내부 통합 시크릿'(ntn_…)을 복사해 붙여넣으세요.\n" +
                "토큰은 이 기기 Keystore로 암호화돼 저장되고, 노션 서버 말고는 어디로도 보내지 않아요."
            setTextColor(Color.parseColor("#6b5a4a"))
            textSize = 13f
        }
        val input = EditText(this).apply {
            hint = if (tokens.has()) "저장된 토큰이 있어요 (새로 입력하면 바뀜)" else "ntn_…"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }
        box.addView(help)
        box.addView(input)
        AlertDialog.Builder(this)
            .setTitle("노션 연동 토큰")
            .setView(box)
            .setPositiveButton("저장") { _, _ ->
                val t = input.text.toString().trim()
                if (t.isNotEmpty()) {
                    tokens.set(t)
                    js("window.__virelNative && window.__virelNative.onToken && window.__virelNative.onToken(true)")
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /** WebView(index.html)에서 window.VirelNative 로 부르는 다리. 토큰 값은 JS로 넘기지 않는다. */
    inner class Bridge {
        @JavascriptInterface
        fun version(): String = BuildConfigInfo.VERSION

        @JavascriptInterface
        fun hasToken(): Boolean = tokens.has()

        @JavascriptInterface
        fun openTokenDialog() = runOnUiThread { showTokenDialog() }

        @JavascriptInterface
        fun clearToken() {
            tokens.clear()
        }

        /** 비동기 노션 호출. 결과는 window.__virelNative.resolve(id, status, body) 로 돌려준다. */
        @JavascriptInterface
        fun notion(callId: String, method: String, path: String, body: String?) {
            io.execute {
                val r = notion.call(method, path, body?.takeIf { it.isNotEmpty() })
                js(
                    "window.__virelNative && window.__virelNative.resolve(" +
                        NotionClient.quote(callId) + "," + r.status + "," + NotionClient.quote(r.body) + ")"
                )
            }
        }

        @JavascriptInterface
        fun copy(text: String): Boolean {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("VIREL Office", text))
            return true
        }

        @JavascriptInterface
        fun saveFile(name: String, text: String) = runOnUiThread {
            pendingSave = name to text
            createDoc.launch(name)
        }

        @JavascriptInterface
        fun openExternal(url: String) = runOnUiThread { this@MainActivity.openExternal(url) }
    }

    private object BuildConfigInfo {
        const val VERSION = "1.0.0"
    }

    companion object {
        private const val APP_HOST = "appassets.androidplatform.net"
        private const val START_URL = "https://$APP_HOST/assets/www/index.html#office"
        private val PAPER = Color.parseColor("#F7F1E8")
    }
}
