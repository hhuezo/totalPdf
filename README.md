# PdfKit Pro

Aplicación Android para leer, convertir, firmar, unir, escanear y editar archivos PDF. Todo el procesamiento ocurre **en tu dispositivo**: sin publicidad, sin cuentas y sin enviar documentos a servidores.

## Características

| Herramienta | Descripción |
|---|---|
| **Leer PDF** | Visor con zoom, búsqueda de texto, ir a página y recuerdo de la última página leída |
| **PDF a imagen** | Convierte páginas a JPG o PNG, con rango personalizado |
| **Firmar PDF** | Firma dibujada, iniciales, texto y fecha sobre el documento |
| **Eliminar páginas** | Marca páginas y genera un PDF nuevo sin modificar el original |
| **Unir PDFs** | Combina varios archivos en uno solo, con orden configurable |
| **Escanear a PDF** | Escaneo con detección de bordes (ML Kit) o imágenes desde galería |

### Otras funciones

- Archivos recientes con nombre, tamaño y fecha
- Abrir PDFs desde el administrador de archivos o con **Abrir con / Compartir**
- Guardar resultados en `Descargas/PdfKit Pro` e imágenes en `Imágenes/PdfKit Pro`
- Interfaz en español con Jetpack Compose y Material 3

## Privacidad

- No se requiere permiso de Internet
- Los PDFs e imágenes no se suben a ningún servidor
- Los metadatos de recientes se guardan solo en el dispositivo (DataStore)

Consulta la [política de privacidad](PRIVACY_POLICY.md) para más detalle.

## Requisitos

- Android 7.0 (API 24) o superior
- Android Studio con soporte para Compose

## Compilar el proyecto

```bash
git clone https://github.com/hhuezo/totalPdf.git
cd totalPdf
./gradlew assembleDebug
```

El APK de debug se genera en `app/build/outputs/apk/debug/`.

### Firma de release (opcional)

Añade estas propiedades en `local.properties`:

```properties
RELEASE_STORE_FILE=/ruta/a/tu/keystore.jks
RELEASE_STORE_PASSWORD=tu_contraseña
RELEASE_KEY_ALIAS=tu_alias
RELEASE_KEY_PASSWORD=tu_contraseña
```

Luego:

```bash
./gradlew assembleRelease
```

## Stack tecnológico

- **Kotlin** + **Jetpack Compose** (Material 3)
- **[PdfBox Android](https://github.com/TomRoush/PdfBox-Android)** — manipulación de PDF
- **ML Kit Document Scanner** — escaneo de documentos
- **DataStore Preferences** — archivos recientes
- **AndroidX Activity, Lifecycle, ExifInterface**

## Estructura del proyecto

```
app/src/main/java/com/hhuezo/pdfconverter/
├── MainActivity.kt          # Navegación principal
├── data/                    # Repositorio de recientes
├── pdf/                     # Lógica PDF (unir, firmar, convertir…)
├── ui/
│   ├── home/                # Inicio y recientes
│   ├── reader/              # Lector PDF
│   ├── tools/               # Pantalla de herramientas
│   ├── sign/ merge/ scan/ …  # Flujos por herramienta
│   └── theme/               # Colores y tipografía
└── util/                    # Guardado, permisos, utilidades
```

## Permisos

| Permiso | Uso |
|---|---|
| Cámara | Escanear documentos |
| Almacenamiento (API ≤ 28) | Guardar archivos en dispositivos antiguos |

El acceso a PDFs e imágenes se realiza mediante el selector de archivos del sistema (SAF), sin permisos amplios de almacenamiento en versiones recientes de Android.

## Contacto

- **Issues:** [github.com/hhuezo/totalPdf/issues](https://github.com/hhuezo/totalPdf/issues)
- **Autor:** [hhuezo](https://github.com/hhuezo)

---

**Application ID:** `com.hhuezo.pdfconverter`
