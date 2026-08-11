package com.miapp.webviewapp

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

/**
 * Se expone a tu HTML como: window.AndroidDownloader
 *
 * Desde tu JavaScript, cuando generes una imagen o PDF (canvas.toDataURL,
 * html2canvas, jsPDF.output('datauristring'), un Blob convertido a base64, etc.)
 * llama:
 *
 *   window.AndroidDownloader.saveBase64File(base64SinPrefijo, "reporte.pdf", "application/pdf");
 *
 * Ejemplo para convertir un Blob a base64 antes de llamarlo:
 *
 *   blob.arrayBuffer().then(buf => {
 *     const base64 = btoa(new Uint8Array(buf).reduce((d, b) => d + String.fromCharCode(b), ''));
 *     window.AndroidDownloader.saveBase64File(base64, "imagen.png", "image/png");
 *   });
 */
class DownloadBridge(private val context: Context) {

    @JavascriptInterface
    fun saveBase64File(base64Data: String, fileName: String, mimeType: String) {
        try {
            val clean = if (base64Data.contains(",")) base64Data.substringAfter(",") else base64Data
            val bytes = Base64.decode(clean, Base64.DEFAULT)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ : MediaStore, sin necesidad de ningún permiso
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    avisar("Guardado en Descargas: $fileName")
                } else {
                    avisar("No se pudo crear el archivo")
                }
            } else {
                // Android 9 o menor (requiere el permiso declarado en el Manifest)
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)
                FileOutputStream(file).use { it.write(bytes) }
                avisar("Guardado en Descargas: $fileName")
            }
        } catch (e: Exception) {
            avisar("Error al guardar: ${e.message}")
        }
    }

    private fun avisar(msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }
}
