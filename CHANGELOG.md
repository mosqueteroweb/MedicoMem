# Changelog

## 1.2.2 — 2026-11-20
- ⏱️ Las grabaciones de una sola parte de menos de 10 s se descartan y no se envían (suelen ser toques por error). La pantalla lo avisa en gris: «Muy corta. No se ha enviado.». Las partes cortas de una consulta partida (por una llamada) sí se envían.

## 1.2.1 — 2026-11-20
- 📱 «Probar envío ahora» incluye el modelo del móvil, la versión de Android y si habrá transcripción →A. También se ve al final del menú familiar.

## 1.2.0 — 2026-11-20
- 🎤 En Android 10 o superior se graba en **Opus dentro de OGG** (48 kHz, 48 kbps, ~21 MB por hora), el formato de las notas de voz de Telegram: se ve con onda y permite transcribir con «→A» de Telegram Premium. El M4A enviado como nota de voz llegaba con onda plana y sin «→A».
- En Android 7–9 sigue en AAC/M4A a 64 kbps. El almacén admite `.ogg` y `.m4a`.

## 1.1.2 — 2026-11-20
- 🔎 «Buscar chat o grupo» (antes «Buscar mi Chat ID») comprueba cada chat con `getChat`, descarta los grupos de los que se echó al bot y deja elegir por nombre (👤 / 👥).
- 👥 Si un grupo pasa a supergrupo y cambia de número (`migrate_to_chat_id`), la app guarda el nuevo y reenvía sola.

## 1.1.1 — 2026-11-20
- 🎨 Icono nuevo, igual que la imagen del bot: micrófono blanco con cruz y punto rojo sobre degradado turquesa-azul. Vectorial y adaptativo (con versión monocroma); circular en Android 7.
- 📦 Releases de GitHub con el APK y enlace fijo a la última versión (`releases/latest/download/MedicoMem.apk`).

## 1.1.0 — 2026-11-20
- 🔊 **Tratamiento de voz**: motor nuevo `AudioRecord` → `VoiceProcessor` → `MediaCodec` + `MediaMuxer`, con el `MediaRecorder` de Android como respaldo si el móvil no lo admite.
  - Filtro paso alto de ~90 Hz (golpes en la mesa, aire acondicionado, zumbido).
  - Volumen automático que solo sube cuando detecta voz por encima del ruido medido (máx. +18 dB), sube despacio y baja deprisa.
  - Limitador suave para que la voz cercana no sature.
  - Con una voz lejana simulada (−45 dB, ruido de sala y zumbido de 50 Hz): voz +16 dB, zumbido −11 dB, diferencia voz/ruido de 5 a 11 dB.
- AAC a 32 kHz y 64 kbps; las partes pasan a durar ~1 h 30 min (límite de 42 MB).
- 🔄 Al grabar, la pantalla gira 180° con «▲ HACIA EL MÉDICO ▲» en el borde del micrófono, y muestra un medidor de nivel.
- 🎛️ Menú familiar: interruptor «Mejorar voces», micrófono sin procesar (`VOICE_RECOGNITION`, por defecto), estándar (`MIC`) o modo vídeo (`CAMCORDER`), y consejos de colocación.
- Corrección: `VOICE_RECOGNITION` no activa el control de volumen del fabricante (al contrario de lo que decía el plan inicial); por eso el volumen lo controla ahora la app.

## 1.0.2 — 2026-11-20
- 🎤 Las grabaciones se envían como nota de voz (`sendVoice`); si Telegram la rechaza, como archivo de audio (`sendAudio`).

## 1.0.1 — 2026-11-20
- 🧩 Widget adaptable desde 1×1: solo icono si es pequeño, icono y texto en fila si es ancho y bajo, icono arriba y texto debajo en 2×2 o mayor. El diseño se elige con el tamaño real del widget en todas las versiones de Android.

## 1.0.0 — 2026-11-20
Primera versión, a partir del plan de la grabadora «un solo botón».
- Kotlin + Views XML, `minSdk` 24 (Android 7.0), `targetSdk` 34, APK de ~1 MB.
- Pantalla única con botón gigante verde/rojo, contador, vibración y pitido, anti-doble-toque de 1,5 s y menú familiar oculto (pulsación de 4 s).
- `ForegroundService` de micrófono con `PARTIAL_WAKE_LOCK`; graba con la pantalla apagada.
- Partes de 42 MB por debajo del límite de 50 MB de los bots; cierre limpio ante llamadas (foco de audio), a las 3 h o con la batería al 5 %.
- Cola de envío con `WorkManager` (datos o Wi‑Fi, o solo Wi‑Fi) y `OkHttp` en *streaming* desde disco, con reintentos crecientes que sobreviven a reinicios.
- Almacenamiento privado (`pending/`, `sent/`), borrado de lo enviado a los 15 días y recuperación de grabaciones a medias si el móvil se apaga grabando.
- Widget que abre la app y empieza o para (Android 11+ no deja activar el micrófono desde segundo plano).
