# Gestor de archivos KMP (IPN ESCOM) - Parte 1

Gestor de archivos para Android (minSdk 24) e iOS (15.0+) escrito en **Kotlin Multiplatform + Compose Multiplatform** con la UI y la lógica compartidas. Funciona sin conexión, sin permisos de almacenamiento globales, sin claves ni servicios externos.

## Cómo ejecutarlo
- **Android:** abrir esta carpeta en Android Studio (2026.2) y pulsar **Run** (módulo `androidApp`). Android Studio genera `local.properties` y descarga lo necesario (Gradle 9.6.1 vía wrapper; JDK incluido en Android Studio). Requiere el SDK Platform 37 (Compose 1.12 lo exige) (Android Studio ofrece instalarlo si falta).
- **iOS:** abrir `iosApp/iosApp.xcodeproj` en Xcode y pulsar **Run** sobre un simulador iOS 15+. Un script de la fase "Compile Kotlin Framework" ejecuta `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` y enlaza el framework `ComposeApp` (modo directo, sin CocoaPods). Para un iPhone real, escribir su `TEAM_ID` en `iosApp/Configuration/Config.xcconfig`.
- Terminal: `./gradlew :androidApp:assembleDebug`, `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`, pruebas: `./gradlew :composeApp:testAndroidHostTest`.

## Funciones
- Explorar el espacio propio (Documentos y datos internos) y carpetas elegidas con el selector del sistema; sin acceso global.
- Breadcrumbs pulsables, atrás del sistema sube un nivel.
- Visores: txt/log/csv/yml..., Markdown (renderizado propio o fuente), JSON y XML con *pretty print* y resaltado propio (`AnnotatedString`), imágenes con zoom/arrastre (gestos), doble toque y rotación de 90 grados.
- Formatos no soportados: diálogo con **Abrir con…**, **Compartir** y **Exportar…** (selector del sistema).
- Metadatos: nombre, tamaño, modificación, creación (si existe), MIME y UTType; "No disponible" cuando falta.
- Temas **Guinda** (#6A1B3A, IPN) y **Azul** (#0B3C8C, ESCOM); claro/oscuro automático (`isSystemInDarkTheme`) y selector en Ajustes.
- UI adaptativa: ancho >= 720 dp -> `NavigationRail` + cuadrícula; estrecho -> `NavigationBar` + lista (portrait y landscape).
- Recientes (máx. 20) y favoritos persistentes. Caché de miniaturas. Copiar/mover/renombrar/eliminar/crear carpeta/importar con confirmación, validaciones y barra de progreso.
- Errores tipificados (`FailureKind`: ruta no disponible, permiso revocado, inexistente, lectura fallida, sin espacio, nombre inválido, no permitido) mostrados con Snackbar o pantalla con reintento; nunca cierran la app.
- Antes del selector de carpetas se muestra un diálogo con la finalidad del acceso.
- Datos de ejemplo en el primer arranque: `Documentos de la app/Ejemplos/` (txt, md, json, xml, png generada en código, bin).

## Arquitectura: compartido vs específico
```
composeApp/                      (módulo KMP; plugin com.android.kotlin.multiplatform.library)
  src/commonMain/                ~90 % del código
    core/    Errors, Formatters (bytes, pretty JSON/XML), FileTypes (MIME/UTType), SyntaxHighlighter,
             XmlLexer, Markdown, PngEncoder   -> lógica pura, probada en commonTest
    data/    FileStore + PlatformServices (interfaces), FileOps (copiar/mover/renombrar con validación),
             Prefs (JSON), ThumbnailCache, SeedData, PlatformHelpers (expect)
    ui/      Compose Material 3: App, AppState, Theme, Explorer, Viewer, Library, Settings, Dialogs
  src/androidMain/               AndroidFileStore, AndroidPlatform, actuals (BitmapFactory, SimpleDateFormat)
  src/iosMain/                   IosFileStore, IosPlatform, MainViewController, actuals (Skia, NSDateFormatter)
  src/commonTest/                pruebas unitarias
androidApp/                      MainActivity, AndroidManifest (sin permisos), FileProvider
iosApp/                          proyecto Xcode (SwiftUI mínimo que aloja MainViewController)
```
**Específico por plataforma** (expect/actual y las interfaces `FileStore`/`PlatformServices` que cada plataforma implementa): acceso a archivos, selectores del sistema, compartir/abrir con, decodificación de imágenes y formato de fecha. Todo lo demás (navegación, estado, temas, visores, operaciones, persistencia) es común.

> Nota: con AGP 9 una aplicación Android no puede vivir en el mismo módulo que el plugin multiplatform, por eso la plantilla oficial separa `androidApp` (aplicación) del módulo compartido `composeApp`.

## APIs de archivos
- **Android**: espacio propio con `java.io.File` (`filesDir`, `cacheDir`; sin permisos). Carpetas externas con **Storage Access Framework**: `ACTION_OPEN_DOCUMENT_TREE` + `takePersistableUriPermission` (el URI de árbol se guarda y se valida con `persistedUriPermissions`); listado/creación/renombrado/borrado con `ContentResolver` + `DocumentsContract` (y `DocumentFile` para nombre/existencia de la raíz). Importar: `ACTION_OPEN_DOCUMENT` (múltiple). Exportar: `ACTION_CREATE_DOCUMENT`. Compartir y abrir con: `ACTION_SEND` / `ACTION_VIEW` dentro de `Intent.createChooser`, con `FileProvider` para archivos propios. Manifiesto sin permisos de almacenamiento.
- **iOS**: sandbox (Documents, Application Support, Caches) con `NSFileManager`. `UIDocumentPickerViewController` para carpetas (`UTTypeFolder`), importar (`asCopy`) y exportar; las carpetas se conservan como **bookmarks** (`NSURL.bookmarkData`, base64 en prefs) y se reabren con `startAccessingSecurityScopedResource`. Compartir: `UIActivityViewController`; abrir con: `UIDocumentInteractionController`. `UIFileSharingEnabled` y `LSSupportsOpeningDocumentsInPlace` hacen visible Documents en la app Archivos.

## Persistencia
Favoritos, recientes, tema, carpetas autorizadas y bandera de datos sembrados se guardan en **un único `prefs.json`** en el directorio privado de la app (`filesDir/internal` en Android, `Application Support/internal` en iOS), serializado con `kotlinx.serialization`. Se eligió un archivo JSON (en lugar de multiplatform-settings) para no añadir dependencias; son listas pequeñas. Un archivo corrupto se ignora y se usan valores por defecto. Las miniaturas (PNG 128 px, máx. 20 MB con expulsión de las más antiguas) viven en `cacheDir/thumbs`, más una caché LRU en memoria.

## Dependencias y versiones
| Componente | Versión |
|---|---|
| Kotlin | 2.4.21 |
| Compose Multiplatform | 1.12.2 (Material 3, ui-backhandler) |
| Android Gradle Plugin | 9.5.1 |
| Gradle (wrapper) | 9.6.1 |
| kotlinx-coroutines | 1.11.0 |
| kotlinx-serialization-json | 1.12.0 |
| androidx.activity-compose | 1.13.0 |
| androidx.documentfile | 1.1.0 |
| androidx.core-ktx | 1.17.0 |
| compileSdk / targetSdk / minSdk | 37 / 36 / 24 |
| iOS deployment target | 15.0 (targets iosArm64, iosSimulatorArm64) |

El catálogo está en `gradle/libs.versions.toml`. No hay claves, servicios ni CocoaPods.

## Pruebas
`./gradlew :composeApp:testAndroidHostTest` ejecuta las pruebas de `commonTest` en la JVM: resaltado JSON/XML (reconstrucción exacta del texto), formato de bytes y pretty print, MIME/UTType, Markdown, serialización de preferencias (ida y vuelta, archivo corrupto, límite de 20 recientes, favoritos) y `FileOps` con un almacén en memoria (nombres únicos, copia recursiva, carpeta dentro de sí misma, mover, validación de destino/espacio).

## Limitaciones conocidas
- El simulador iOS soportado es el de Apple Silicon (arm64): Compose Multiplatform 1.12 ya no publica iosX64 (Mac Intel no soportado en simulador).
- Iconos con emoji (sin paquete de iconos extendidos).
- Mover = copiar + borrar; en iOS la copia no informa progreso intermedio (NSFileManager copia de una vez).
- Favoritos/recientes guardan la ruta/URI; si el archivo desaparece se informa al abrirlo. En iOS la ruta del contenedor puede cambiar tras reinstalar la app.
- Orden fijo (carpetas primero, nombre). El visor de texto limita a 300 KB; las imágenes a 30 MB.
- El selector de carpetas de Android no admite la raíz del almacenamiento ni Descargas (restricción del sistema).
- Las pruebas de UI y la ejecución en simulador/dispositivo no se automatizaron aquí; sólo se verificó la compilación.
