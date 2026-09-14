# WebView APK Generator — V3

Plantilla Android para convertir páginas web en APK WebView usando GitHub Actions.
No necesitas Android Studio para compilar.

## La idea

Cada aplicación vive en `profiles/NOMBRE/` y tiene:

- `config.properties` → nombre, URL, ID, orientación, versión y permisos.
- `icon.png` → icono de esa aplicación.

Puedes tener tantos perfiles como quieras.

### Ejemplo

```text
profiles/
  control/
    config.properties
    icon.png
  inventario/
    config.properties
    icon.png
  miweb/
    config.properties
    icon.png
```

## Crear una nueva aplicación

1. Duplica una carpeta dentro de `profiles/`.
2. Cambia el nombre de la carpeta.
3. Edita `config.properties`.
4. Reemplaza `icon.png` por tu icono (recomendado: PNG cuadrado, 512x512 o 1024x1024).
5. En GitHub ve a Actions → Build APK → Run workflow.
6. En `profile` escribe el nombre de la carpeta, por ejemplo `control`.
7. Ejecuta el workflow.
8. Abre la ejecución terminada y descarga el Artifact de la APK.

## Configuración

```properties
NOMBRE=Control PRC
URL=https://ejemplo.com/control
ID=com.juan.controlprc
ORIENTACION=horizontal
VERSION=1.0
UBICACION=true
MICROFONO=true
CAMARA=true
NOTIFICACIONES=true
DESCARGAS=true
ZOOM=false
PANTALLA_COMPLETA=true
ENLACES_EXTERNOS=true
```

`ID` es el `applicationId` de Android. Debe ser único si quieres instalar dos aplicaciones a la vez.

- Misma ID → la nueva APK actualiza/reemplaza la anterior.
- ID diferente → pueden convivir instaladas.

`VERSION_CODE` no hace falta editarlo: GitHub Actions lo genera usando el número de ejecución. Esto permite instalar actualizaciones con una versión superior.

## Permisos

- `UBICACION`: permite geolocalización web cuando la página la solicita.
- `MICROFONO`: permite `getUserMedia()` para audio.
- `CAMARA`: permite cámara y subida/captura mediante WebView.
- `NOTIFICACIONES`: solicita el permiso Android 13+.
- `DESCARGAS`: activa el DownloadManager para enlaces descargables.
- `ZOOM`: activa controles de zoom.
- `PANTALLA_COMPLETA`: oculta barras del sistema.
- `ENLACES_EXTERNOS`: intenta abrir `tel:`, `mailto:`, `whatsapp:`, etc. fuera de WebView; los enlaces HTTP/HTTPS permanecen en WebView.

La web debe permitir técnicamente la función. Para cámara, micrófono y ubicación se recomienda HTTPS y permisos del propio navegador/web.

## Importante sobre el icono

GitHub Actions genera automáticamente iconos para mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi a partir de `icon.png`.

## Importante sobre la seguridad

Esta plantilla es para tus propias páginas o páginas que tienes permiso de envolver en una APK. Si una web requiere autenticación, cookies o políticas específicas, puede necesitar ajustes adicionales.

## Reemplazar tu proyecto actual en GitHub

La forma más sencilla es reemplazar el contenido del repositorio actual por el contenido de este ZIP, conservando el mismo repositorio si quieres seguir usando la misma dirección de GitHub.

### Opción recomendada: desde la web de GitHub

1. Abre tu repositorio.
2. Haz una copia de seguridad del proyecto actual descargándolo como ZIP si quieres conservarlo.
3. En GitHub elimina los archivos/carpetas actuales del proyecto o crea un repositorio nuevo.
4. Sube el contenido de este ZIP **sin crear una carpeta extra alrededor**. En la raíz deben verse `app`, `.github`, `profiles`, `build.gradle`, `settings.gradle`, etc.
5. Haz Commit changes.
6. Entra en **Actions → Build APK WebView → Run workflow**.
7. En `profile` escribe `control` y pulsa **Run workflow**.
8. Cuando termine en verde, entra a esa ejecución y en **Artifacts** descarga la APK.

### Para crear otra APK

Duplica, por ejemplo, `profiles/control` como `profiles/mipagina`. Dentro cambia `config.properties` y `icon.png`. Después vuelve a **Actions → Run workflow** y escribe `mipagina` en `profile`.

### No subas `active-config.properties` ni `app-icon-source.png`

Esos dos archivos los crea automáticamente GitHub Actions durante la compilación.
