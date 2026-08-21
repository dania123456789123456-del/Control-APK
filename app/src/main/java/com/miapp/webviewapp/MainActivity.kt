package com.miapp.webviewapp

import android.app.DownloadManager
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.WindowManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    // ---------------------------------------------------------------
    // CAMBIA ESTA LÍNEA por la URL de tu página, o deja el archivo
    // local en app/src/main/assets/index.html y usa la ruta de abajo.
    // ---------------------------------------------------------------
    private val PAGINA_A_CARGAR = "https://dania123456789123456-del.github.io/Inventario-prc/"
    // private val PAGINA_A_CARGAR = "https://tu-dominio.com/index.html"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Forzar horizontal
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        // Permitir que el contenido dibuje detrás de las barras / notch
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        setupWebView()
        ocultarBarrasDelSistema()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) ocultarBarrasDelSistema()
    }

    /** Oculta status bar y navigation bar; el usuario puede deslizar para verlas un momento. */
    private fun ocultarBarrasDelSistema() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true

        // Puente para que el JS de tu HTML pueda guardar imágenes/PDF generados
        // con html2canvas, jsPDF, canvas.toDataURL, blobs convertidos a base64, etc.
        webView.addJavascriptInterface(DownloadBridge(this), "AndroidDownloader")

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()

        // Descargas "normales": enlaces <a href="archivo.pdf"> o data: URIs directas
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (url.startsWith("data:")) {
                val bridge = DownloadBridge(this)
                val base64 = url.substringAfter("base64,", url)
                val ext = when {
                    mimeType.contains("pdf") -> "pdf"
                    mimeType.contains("png") -> "png"
                    mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpg"
                    else -> "bin"
                }
                bridge.saveBase64File(base64, "descarga_${System.currentTimeMillis()}.$ext", mimeType)
            } else {
                try {
                    val request = DownloadManager.Request(Uri.parse(url))
                    request.setMimeType(mimeType)
                    request.addRequestHeader("User-Agent", userAgent)
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                    dm.enqueue(request)
                    Toast.makeText(this, "Descargando: $fileName", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "No se pudo descargar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        webView.loadUrl(PAGINA_A_CARGAR)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
