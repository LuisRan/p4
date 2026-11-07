# Circuito Guinda (Kotlin Multiplatform + Compose Multiplatform)

Práctica Parte 2 - IPN ESCOM, Desarrollo de aplicaciones móviles. Versión **KMP** del puzzle
"Circuito Guinda" (también hecho en Flutter). UI 100 % compartida con Compose Multiplatform,
100 % offline, con guardado de partidas en **TXT, XML y JSON**.
Android: `minSdk 24`, `compileSdk 36`. iOS: 15.0+. Paquete: `mx.ipn.escom.circuito`.

## Cómo ejecutar

**Android:** abrir esta carpeta en Android Studio (cualquier versión reciente; probado con el JDK
y Gradle que trae el propio IDE), esperar el *Gradle Sync* y pulsar **Run** (configuración
`composeApp`). No hay claves, servicios ni pasos manuales. `local.properties` lo genera Android Studio.
Por terminal: `./gradlew :composeApp:assembleDebug` (APK en `composeApp/build/outputs/apk/debug/`).

**iOS:** abrir `iosApp/iosApp.xcodeproj` en Xcode, elegir un simulador iOS 15+ y pulsar **Run**.
Una fase de compilación ejecuta `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`, que
compila el framework compartido `ComposeApp` (sin CocoaPods). La primera vez Gradle descarga el
compilador Kotlin/Native (requiere internet). El script usa automáticamente el JDK de Android Studio
si `JAVA_HOME` no está definido. El simulador requiere un Mac con Apple Silicon (el target `iosX64` no se incluye). Para un iPhone real, escribe tu `TEAM_ID` en `iosApp/Configuration/Config.xcconfig`.

## El juego y sus reglas

Tablero de losetas con tubos (recta, codo, T, cruz, extremo). La loseta con núcleo blanco es la
**fuente de energía** (centro).
- Toca una loseta para girarla 90° en sentido horario.
- Una loseta se **energiza** si está unida a la fuente por una cadena de tubos que se "miran" mutuamente.
- Ganas cuando **todas** están energizadas. **Deshacer** revierte el último giro (el historial se acorta).
- **Clásico**: sin límite de movimientos. **Reto**: límite = 1.5 x mínimo + 3; si se agota, pierdes.
- **Niveles** infinitos: el tablero crece una casilla cada 3 niveles (tope 8x8). La dificultad
  (Fácil 4x4, Normal 5x5, Difícil 6x6) fija el tamaño inicial. Cada nivel sale de una semilla
  (árbol de expansión aleatorio + mezcla de giros) y siempre es resoluble.
- **Puntuación**: 100 por loseta - 8 por movimiento - 1 por segundo (mínimo 10 % de la base);
  Reto x1.5; al ganar se suma 50 x nivel. Progreso persistente (puntos, mejor puntuación, victorias/derrotas, mejor nivel por modo).
- **Continuar**: autoguardado (`auto/autosave.json`) tras cada movimiento y al salir de la pantalla.
- Efectos: giro animado con rebote, brillo pulsante en tubos energizados, partículas en Canvas al
  conectar y al ganar; sonidos WAV **sintetizados en memoria** (sin assets) y vibración/haptics.
- Temas **Guinda (#6A1B3A)** y **Azul (#0B3C8C)**, selector de **Apariencia** en Ajustes: Sistema (por defecto, sigue al SO automáticamente), Claro u Oscuro, persistido en settings.json. Tablero y losetas usan colores del tema (legibles en oscuro). UI responsiva (vertical/horizontal, tablet).

## Arquitectura

```
composeApp/src/
  commonMain/kotlin/mx/ipn/escom/circuito/
    game/      Pipes (máscaras de bits), GameState, Generator, Scoring, GameController
    saves/     SaveGame (modelo + validación única), Serializers (JSON/XML/TXT + MiniXml),
               SaveRepository, SaveExceptions, SaveFormat, IsoTime
    core/      Storage (AppSettings, PlayerProgress), WavSynth (+SoundBank), GameFeedback, Time
    platform/  Platform.kt: interfaces PlatformServices / FileStore + expect (sin lógica)
    ui/        App (navegación), Home, Game, Saves, Settings, RawViewer, BoardView (Canvas), Dialogs, Theme
  androidMain/ MainActivity, Platform.android.kt (actual), AndroidManifest, FileProvider
  iosMain/     MainViewController.kt, Platform.ios.kt (actual)
  commonTest/  pruebas de serializadores, repositorio y lógica del juego
iosApp/        proyecto Xcode (SwiftUI) que embebe el framework ComposeApp
```

Decisiones clave:
- `SaveGame` es neutral al formato. Cada serializador convierte texto <-> mapa neutral y **un único
  validador** (`SaveGame.fromMap`) comprueba tipos, rangos, longitud del tablero, historial y versión.
- Excepciones propias (`SaveException`): `EmptySaveException`, `CorruptSaveException`,
  `IncompleteSaveException`, `UnsupportedVersionException`, `SaveIoException`, con mensajes en español.
  `SaveRepository.list()` valida cada archivo por separado: uno dañado aparece en rojo con su error
  (se puede ver crudo o eliminar) y **no afecta a las demás partidas**.
- Escritura atómica (temporal + renombrado). Importar valida **antes** de copiar a `saves/`.
- `schemaVersion = 1`; una versión mayor se rechaza con un mensaje claro.
- `expect/actual` mínimo: `rememberPlatformServices()`, `createFileStore()`, `PlatformBackHandler()`.

## Formatos (contenido: estado, puntuación, tiempo, configuración, historial, nombre, fecha, etiquetas)

Máscara de loseta: N=1, E=2, S=4, O=8. `moves` = índices de losetas giradas, en orden.

### JSON
```json
{
  "schemaVersion": 1,
  "name": "Nivel 1 Clásico",
  "savedAt": "2026-11-05T10:30:15.000Z",
  "tags": ["practica", "favorita"],
  "game": {
    "mode": "clasico", "difficulty": "facil", "level": 1, "size": 4, "seed": 12345,
    "source": 10, "moveLimit": 0, "score": 1580, "elapsedSeconds": 12, "won": false,
    "tiles": [6, 14, 12, 4, 5, 9, 3, 1, 10, 2, 15, 8, 2, 4, 1, 5],
    "moves": [0, 5, 5]
  },
  "settings": {"sound": true, "vibration": true, "difficulty": "facil", "palette": "guinda"}
}
```

### XML (parser propio `MiniXml`, sin dependencias)
```xml
<?xml version="1.0" encoding="UTF-8"?>
<save schemaVersion="1">
  <name>Nivel 1 Clásico</name>
  <savedAt>2026-11-05T10:30:15.000Z</savedAt>
  <tags>
    <tag>practica</tag>
    <tag>favorita</tag>
  </tags>
  <game>
    <mode>clasico</mode>
    <difficulty>facil</difficulty>
    <level>1</level>
    <size>4</size>
    <seed>12345</seed>
    <source>10</source>
    <moveLimit>0</moveLimit>
    <score>1580</score>
    <elapsedSeconds>12</elapsedSeconds>
    <won>false</won>
    <tiles>6,14,12,4,5,9,3,1,10,2,15,8,2,4,1,5</tiles>
    <moves>0,5,5</moves>
  </game>
  <settings>
    <sound>true</sound>
    <vibration>true</vibration>
    <difficulty>facil</difficulty>
    <palette>guinda</palette>
  </settings>
</save>
```

### TXT (clave=valor)
```
# Circuito Guinda - partida guardada (formato TXT)
schemaVersion=1
name=Nivel 1 Clásico
savedAt=2026-11-05T10:30:15.000Z
tags=practica;favorita

# Estado de la partida
game.mode=clasico
game.difficulty=facil
game.level=1
game.size=4
game.seed=12345
game.source=10
game.moveLimit=0
game.score=1580
game.elapsedSeconds=12
game.won=false
game.tiles=6,14,12,4,5,9,3,1,10,2,15,8,2,4,1,5
game.moves=0,5,5

# Configuración personalizada
settings.sound=true
settings.vibration=true
settings.difficulty=facil
settings.palette=guinda
```
Se ignoran líneas vacías y las que empiezan con `#`; en textos se escapan `\`, saltos de línea y `;`.

## APIs de archivos y plataforma

| Función | Android | iOS |
|---|---|---|
| Guardado por defecto | `filesDir/saves` (privado, sin permisos) | `Documents/saves` (NSFileManager; visible en Archivos por `UIFileSharingEnabled`) |
| Exportar a ubicación elegida | Storage Access Framework: `ActivityResultContracts.CreateDocument` | `UIDocumentPickerViewController(forExportingURLs:)` |
| Importar | SAF: `ActivityResultContracts.OpenDocument` | `UIDocumentPickerViewController(forOpeningContentTypes:)` |
| Compartir | `Intent.ACTION_SEND` + `createChooser` + `FileProvider` | `UIActivityViewController` |
| Sonido | `AudioTrack` (MODE_STATIC, PCM del WAV sintetizado) | `AVAudioPlayer(data:)` |
| Vibración | `Vibrator` / `VibrationEffect` | `UISelectionFeedbackGenerator`, `UINotificationFeedbackGenerator` |

No se piden permisos de almacenamiento (solo `VIBRATE`, que es automático). En la pantalla "Partidas
guardadas": lista con metadatos (modo, nivel, puntos, tiempo, formato, fecha), búsqueda, filtro por
etiquetas, cargar, ver crudo, exportar/compartir (eligiendo formato), editar etiquetas, eliminar e importar.

## Dependencias y versiones (noviembre 2026)

| Componente | Versión |
|---|---|
| Kotlin / Kotlin Multiplatform plugin | 2.4.21 |
| Compose Multiplatform | 1.11.1 (Material 3) |
| Android Gradle Plugin | 8.13.2 |
| Gradle (wrapper) | 9.3.1 |
| kotlinx-serialization-json | 1.12.0 (solo la API `JsonElement`; sin plugin de compilador) |
| androidx.activity-compose / core-ktx | 1.11.0 / 1.16.0 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 24 |
| iOS deployment target | 15.0 (targets `iosArm64`, `iosSimulatorArm64`) |

Versiones centralizadas en `gradle/libs.versions.toml`. Se fijó AGP 8.13.2 y Compose 1.11.1 porque las
librerías androidx más nuevas exigen AGP 9.1+ y compileSdk 37.

## Pruebas

`./gradlew :composeApp:testDebugUnitTest` (también `:composeApp:iosSimulatorArm64Test` en macOS).
Cubren: ida y vuelta de los 3 formatos, archivos vacíos/truncados/corruptos/versión futura, campos
faltantes, aislamiento de archivos dañados en el repositorio, importación validada, etiquetas,
generador determinista y resoluble, reglas de puntuación, modo Reto, deshacer, restauración exacta y WAV válido.
