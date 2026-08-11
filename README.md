# WebView App (Kotlin) — pantalla completa, horizontal, con descargas

Proyecto Android listo para compilar con GitHub Actions (sin necesitar Android Studio).

## Qué incluye
- WebView a pantalla completa horizontal, incluyendo el área del notch.
- Barra de estado/navegación ocultas automáticamente; aparecen un momento al deslizar desde el borde y se vuelven a ocultar.
- Descarga de archivos normales (enlaces `<a href="archivo.pdf">`, imágenes con URL directa) sin pedir permisos en Android 10+ (API 29+), que es justo lo que fallaba con Kodular en Android 13.
- Un puente JavaScript (`window.AndroidDownloader`) para guardar imágenes o PDF generados dentro del propio HTML (por ejemplo con `html2canvas`, `jsPDF`, `canvas.toDataURL`, o un `Blob`).

## Pasos para usarlo

1. **Sube este proyecto a un repositorio en GitHub** (crea un repo nuevo y sube todos estos archivos manteniendo la estructura de carpetas).
2. Entra a la pestaña **Actions** de tu repo. Al hacer push a `main`, se ejecutará automáticamente el workflow y compilará el APK.
3. Cuando termine (ícono verde ✅), entra al run y descarga el archivo `app-debug` en la sección **Artifacts**. Ahí está tu `.apk`.
4. Instala ese APK en tu celular (activa "orígenes desconocidos" si te lo pide).

## Antes de compilar, personaliza:

- **Tu página web:** abre `app/src/main/java/com/miapp/webviewapp/MainActivity.kt` y cambia la línea:
  ```kotlin
  private val PAGINA_A_CARGAR = "file:///android_asset/index.html"
  ```
  Si tu HTML está publicado en internet, pon la URL ahí. Si prefieres empaquetarlo dentro del APK, reemplaza el archivo `app/src/main/assets/index.html` por el tuyo (y todos sus recursos: css, js, imágenes) en esa misma carpeta `assets`.

- **Ícono/nombre de la app:** `android:label` y los íconos en `AndroidManifest.xml` / `res/mipmap`.

- **applicationId:** en `app/build.gradle`, cambia `com.miapp.webviewapp` por tu propio identificador si vas a publicarla.

## Cómo descargar imagen o PDF desde tu HTML

### Caso 1: es un enlace normal a un archivo
No necesitas hacer nada extra, el WebView ya lo intercepta y lo guarda en la carpeta Descargas.

### Caso 2: generas la imagen/PDF con JavaScript (html2canvas, jsPDF, canvas, blobs)
Llama al puente que ya está integrado, por ejemplo:

```javascript
// Imagen desde un <canvas>
const dataUrl = canvas.toDataURL("image/png");
const base64 = dataUrl.split(",")[1];
window.AndroidDownloader.saveBase64File(base64, "captura.png", "image/png");

// PDF con jsPDF
const pdfBase64 = doc.output('datauristring').split(",")[1];
window.AndroidDownloader.saveBase64File(pdfBase64, "reporte.pdf", "application/pdf");

// Si tienes un Blob (por ejemplo de html2canvas.toBlob)
blob.arrayBuffer().then(buf => {
  const base64 = btoa(new Uint8Array(buf).reduce((d, b) => d + String.fromCharCode(b), ''));
  window.AndroidDownloader.saveBase64File(base64, "imagen.png", "image/png");
});
```

El archivo se guarda directamente en la carpeta **Descargas** del teléfono, sin pedir ningún permiso en Android 10 en adelante (incluyendo Android 13), que es justo el problema que tenías con Kodular.

## Notas
- El workflow compila la versión **debug** (para pruebas). Para publicar en Play Store necesitarías firmarla en modo *release*, que es un paso aparte.
- Si tu HTML necesita hacer peticiones a Google Sheets/Apps Script, el `usesCleartextTraffic` y el permiso de `INTERNET` ya están habilitados en el Manifest.
