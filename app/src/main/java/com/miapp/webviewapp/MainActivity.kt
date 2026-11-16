package com.miapp.webviewapp

import android.Manifest
import android.app.DownloadManager
import android.content.Intent
import android.graphics.Bitmap
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.WindowManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var pendingWebPermissionRequest: PermissionRequest? = null
    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        pendingWebPermissionRequest?.let { request ->
            val audioOk = !request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) ||
                (!BuildConfig.ENABLE_MICROPHONE || hasPermission(Manifest.permission.RECORD_AUDIO))
            val videoOk = !request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) ||
                (!BuildConfig.ENABLE_CAMERA || hasPermission(Manifest.permission.CAMERA))
            if (audioOk && videoOk) request.grant(request.resources) else request.deny()
            pendingWebPermissionRequest = null
        }
        pendingGeoCallback?.let { callback ->
            val allowed = BuildConfig.ENABLE_LOCATION &&
                (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION))
            callback.invoke(pendingGeoOrigin ?: "", allowed, false)
            pendingGeoCallback = null
            pendingGeoOrigin = null
        }
    }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = filePathCallback ?: return@registerForActivityResult
        callback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        filePathCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = when (BuildConfig.APP_ORIENTATION.trim().lowercase()) {
            "portrait", "vertical" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            "auto", "libre", "sensor" -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE // horizontal / landscape
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (BuildConfig.FULLSCREEN) hideSystemBars()
        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webview)
        setupWebView()
        if (BuildConfig.ENABLE_NOTIFICATIONS && Build.VERSION.SDK_INT >= 33 &&
            !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && BuildConfig.FULLSCREEN) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private val desktopView: Boolean
        get() = BuildConfig.VIEW_MODE.trim().equals("pc", true)

    // Fuerza el viewport a un ancho de PC (ej. 1920) y fija la escala para que la pagina entera
    // quepa en la pantalla (sin barras de desplazamiento ni zoom automatico al tocar un campo).
    // Se aplica a TODAS las etiquetas <meta name="viewport">: la de la pagina llega despues de la
    // nuestra y, si no la tocamos tambien, es la que gana.
    private val forceViewportJs: String
        get() = """
            (function(){
              if (window.__forceViewport) { window.__forceViewport(); return; }
              var W = __WIDTH__;
              var LOCK = __LOCK__;
              function content(){
                var c = 'width=' + W;
                var sw = window.screen ? window.screen.width : 0;
                var sh = window.screen ? window.screen.height : 0;
                if (sw && sh) {
                  var land = window.innerWidth >= window.innerHeight;
                  var dw = land ? Math.max(sw, sh) : Math.min(sw, sh);
                  var s = Math.floor(dw / W * 10000) / 10000;
                  if (s > 0) {
                    c += ', initial-scale=' + s;
                    if (LOCK) c += ', minimum-scale=' + s + ', maximum-scale=' + s + ', user-scalable=no';
                  }
                }
                return c;
              }
              function apply(){
                var host = document.head || document.documentElement;
                if (!host) return;
                var C = content();
                var metas = document.querySelectorAll('meta[name="viewport"]');
                if (!metas.length) {
                  var m = document.createElement('meta');
                  m.setAttribute('name','viewport');
                  m.setAttribute('content', C);
                  host.appendChild(m);
                  return;
                }
                for (var i = 0; i < metas.length; i++) {
                  if (metas[i].getAttribute('content') !== C) metas[i].setAttribute('content', C);
                }
              }
              window.__forceViewport = apply;
              apply();
              new MutationObserver(apply).observe(document, {childList:true, subtree:true, attributes:true, attributeFilter:['content']});
              document.addEventListener('DOMContentLoaded', apply);
              window.addEventListener('load', apply);
              window.addEventListener('resize', function(){ setTimeout(apply, 100); });
              window.addEventListener('orientationchange', function(){ setTimeout(apply, 300); });
            })();
        """.trimIndent()
            .replace("__WIDTH__", BuildConfig.DESKTOP_WIDTH.toString())
            .replace("__LOCK__", (!BuildConfig.ENABLE_ZOOM).toString())

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(BuildConfig.ENABLE_ZOOM)
            builtInZoomControls = BuildConfig.ENABLE_ZOOM
            displayZoomControls = false
            setGeolocationDatabasePath(filesDir.path)
            if (desktopView && BuildConfig.DESKTOP_UA) {
                userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            }
        }
        if (desktopView && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, forceViewportJs, setOf("*"))
        }
        webView.addJavascriptInterface(DownloadBridge(this), "AndroidDownloader")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (desktopView) view.evaluateJavascript(forceViewportJs, null)
            }
            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                if (desktopView) view.evaluateJavascript(forceViewportJs, null)
                if (BuildConfig.DEBUG_VISTA) {
                    view.postDelayed({
                        view.evaluateJavascript(
                            "(function(){return 'ancho CSS='+innerWidth+' alto='+innerHeight+' dpr='+devicePixelRatio+' escala='+(window.visualViewport?visualViewport.scale.toFixed(2):'?')+' metas='+document.querySelectorAll('meta[name=viewport]').length+' contenido='+(document.querySelector('meta[name=viewport]')||{getAttribute:function(){return 'ninguno'}}).getAttribute('content')})()"
                        ) { r -> Toast.makeText(this@MainActivity, r, Toast.LENGTH_LONG).show() }
                    }, 1500)
                }
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!BuildConfig.EXTERNAL_LINKS) return false
                return openExternalIfNeeded(request.url.toString())
            }
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (!BuildConfig.EXTERNAL_LINKS) return false
                return openExternalIfNeeded(url)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val needed = mutableListOf<String>()
                    val audio = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                    val video = request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                    if (audio && BuildConfig.ENABLE_MICROPHONE && !hasPermission(Manifest.permission.RECORD_AUDIO)) needed += Manifest.permission.RECORD_AUDIO
                    if (video && BuildConfig.ENABLE_CAMERA && !hasPermission(Manifest.permission.CAMERA)) needed += Manifest.permission.CAMERA
                    if (audio && !BuildConfig.ENABLE_MICROPHONE || video && !BuildConfig.ENABLE_CAMERA) { request.deny(); return@runOnUiThread }
                    if (needed.isEmpty()) request.grant(request.resources)
                    else { pendingWebPermissionRequest = request; permissionLauncher.launch(needed.toTypedArray()) }
                }
            }

            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                if (!BuildConfig.ENABLE_LOCATION) { callback.invoke(origin, false, false); return }
                if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    callback.invoke(origin, true, false)
                } else {
                    pendingGeoOrigin = origin
                    pendingGeoCallback = callback
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            }

            override fun onShowFileChooser(webView: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                return try { fileChooserLauncher.launch(params.createIntent()); true } catch (_: Exception) { filePathCallback = null; false }
            }
        }

        if (BuildConfig.ENABLE_DOWNLOADS) {
            webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                try {
                    val request = DownloadManager.Request(Uri.parse(url))
                    request.setMimeType(mimeType)
                    request.addRequestHeader("User-Agent", userAgent)
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimeType))
                    (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                    Toast.makeText(this, "Descarga iniciada", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) { Toast.makeText(this, "No se pudo descargar: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
        webView.loadUrl(BuildConfig.WEB_URL)
    }

    private fun openExternalIfNeeded(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) return false
        return try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))); true } catch (_: Exception) { false }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
