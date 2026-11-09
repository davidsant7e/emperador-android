<p align="center"><img src="docs/carta.svg" alt="IV · El Emperador" width="180"></p>

# IV · El Emperador

App Android para hacer el **inventario de la casa por habitación**: le tomas fotos a cada cuarto, **Gemini** reconoce los objetos (muebles, electrodomésticos, electrónica, decoración, puertas y ventanas…) y tú revisas la lista. El resultado se exporta para usarlo con Claude o en una hoja de cálculo.

El Emperador pone orden: qué hay, dónde y cuánto.

## Qué hace (versión 0.1, fase 1)

- **Casa → habitaciones**: crea las habitaciones (Sala, Cocina, Baño…), ordénalas y ponles notas.
- **Fotos**: tomar con la cámara o elegir varias de la galería. Se guardan dentro de la app, reducidas (1600 px), derechas y **sin ubicación GPS**.
- **Reconocer objetos con Gemini**: manda las fotos de esa habitación (hasta 8, las más recientes) y devuelve cada objeto con categoría, cantidad, dónde está y en qué foto se ve. No cuenta dos veces lo que aparece en varias fotos y agrupa los iguales («Silla ×4»).
- **Revisar**: lo que propone Gemini queda como **sugerido** hasta que lo aceptas (✓), lo corriges o lo descartas. Si vuelves a analizar (por ejemplo con más fotos), lo ya revisado se conserva y solo se agregan los objetos que falten.
- **A mano**: agregar, editar o borrar objetos sin Gemini.
- **Exportar** (menú *Exportar* en la pantalla de la casa):
  - **Enviar a Claude (texto)**: el inventario en Markdown, por habitación y categoría, para compartirlo con la app de Claude (o copiarlo).
  - **Hoja de cálculo (CSV)**: una fila por objeto (habitación, objeto, categoría, cantidad, ubicación, notas, revisado). Abre en Excel o Google Sheets.
  - **Datos completos (JSON)**: todo el inventario, para respaldos o para otros proyectos.

## Lo que sigue

- **Fase 2 — medidas con realidad aumentada (ARCore)**: apuntar el teléfono a las esquinas para medir cada habitación (el Motorola Edge 50 Fusion tiene *Servicios de Google Play para RA*).
- **Fase 3 — plano de la casa**: unir las habitaciones en un plano con medidas y objetos; exportar en PDF.

## Instalar

1. Abre la carpeta en **Android Studio** y deja que sincronice Gradle.
2. Conecta el celular con depuración USB y pulsa **Run** (o *Build → Build APK(s)*).
3. En la app: **⚙ → Clave de Gemini**. Se crea gratis en [aistudio.google.com/apikey](https://aistudio.google.com/apikey) y se guarda **cifrada** con el Android Keystore.

## Privacidad

- Las fotos de la habitación que **tú** mandas a analizar viajan a Google (API de Gemini) con tu clave. Nada más sale del teléfono.
- Fotos e inventario se guardan en la memoria privada de la app (`casa.json` y la carpeta `fotos`). Sin copia en la nube (`allowBackup=false`): exporta el JSON si quieres respaldo.
- No pide permisos de cámara ni de almacenamiento: usa la app de cámara del teléfono y el selector de fotos de Android.

## Cómo está hecho

- Kotlin + Jetpack Compose (Material 3), minSdk 26, compileSdk 37. Mismo esqueleto que La Templanza y El Carro (AGP 9.4.0, Kotlin 2.4.10, Gradle 9.6.0).
- `nucleo/` es Kotlin puro (sin Android) y tiene pruebas: modelos y JSON (`Modelos.kt`, `Json.kt`), la consulta a Gemini con respuesta estructurada (`Gemini.kt`, modelo `gemini-3.5-flash` por defecto) y las reglas de inventario y exportación (`Inventario.kt`).
- `datos/`: almacenamiento (`Almacen.kt`), fotos (`Fotos.kt`, con ExifInterface), clave cifrada (`Boveda.kt`), HTTP (`Red.kt`).
- `ui/`: `MainActivity` (cámara, selector de fotos, compartir con FileProvider), `EmperadorViewModel` y `Pantallas.kt`.

Pruebas del núcleo: `./gradlew test`.

Parte del mazo de [davidsant7e](https://github.com/davidsant7e).
