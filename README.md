# MedicoMem — Grabar médico

App Android de **un solo botón** para que personas mayores graben sus consultas médicas.
Al parar, el audio llega solo a un **bot privado de Telegram**. Sin servidor, sin cuentas, coste 0 €.

- Un botón gigante (también como **widget**, de 1×1 a pantalla completa): toque para grabar (pantalla **roja**, vibración y pitido), toque para parar (**✓ GUARDADO** en verde).
- Graba con la pantalla apagada o el móvil en el bolso (servicio en primer plano + *wake lock*).
- Envío por datos móviles o Wi‑Fi en cuanto haya señal; si no hay cobertura se reintenta solo, incluso tras reiniciar el móvil.
- Android 7.0 o superior, APK de ~2 MB, pensado para móviles modestos.

## Cómo funciona

| Parte | Detalle |
| :--- | :--- |
| Audio | `MediaRecorder`, AAC mono 24 kHz a 48 kbps (~21 MB por hora). Micrófono «voz a distancia» (`VOICE_RECOGNITION`) o estándar (`MIC`). Si el móvil rechaza esa calidad, prueba otras más comunes. |
| Consultas largas | Cada archivo se corta a 42 MB (~1 h 50 min) y la grabación sigue en «Parte 2», por debajo del límite de 50 MB de los bots. |
| Llamadas | Si entra una llamada (u otra app toma el audio), la parte grabada se cierra y se envía; al terminar, se reanuda en una parte nueva. |
| Cortes automáticos | A las 3 h, o con la batería al 5 % sin cargar. El mensaje lo indica. |
| Envío | `WorkManager` + `OkHttp` en *streaming* desde disco. Llega como **nota de voz** (`sendVoice`): con Telegram Premium se transcribe con el botón «→A» y se oye a 1,5×/2×. Si Telegram rechaza la nota de voz, se envía como archivo de audio (`sendAudio`). Reintento con espera creciente (30 s, 1 min, 2 min…). |
| Espacio | Todo en el almacenamiento privado de la app; lo enviado se borra a los 15 días. |
| Si el móvil se apaga grabando | Al volver a abrirse, el trozo grabado se envía igualmente con un aviso de que puede estar dañado. |

Mensaje que llega a Telegram:

```text
🎙️ Cita médica — Mamá
📅 20/11/2026 10:35 · ⏱️ 22 min · 🔋 68%
⚠️ Batería baja — recordar cargar        (si < 20 %)
⚠️ Corte automático (límite de 3 h)       (si se cortó solo)
```

## Puesta en marcha (una vez, en el móvil de tus padres)

1. **Crea el bot**: en Telegram habla con `@BotFather`, envía `/newbot` y copia el *token*.
2. **Escribe cualquier mensaje a tu bot** (o añádelo a un grupo familiar y escribe allí).
3. Instala el APK y abre la app. Al primer toque pide **Micrófono** (y **Notificaciones** en Android 13+): acepta.
4. **Mantén pulsado 4 segundos** el texto gris de arriba («Configuración familiar»):
   - Nombre (`Mamá`, `Papá`…), *Bot Token* y pulsa **Buscar mi Chat ID**.
   - Pulsa **Probar envío ahora** y comprueba que llega a tu Telegram.
   - Pulsa **Desactivar ahorro de batería**.
5. En el escritorio, mantén pulsado un hueco → *Widgets* → **Grabar médico**, y colócalo bien grande.

### Ahorro de batería según la marca

| Marca | Qué tocar además |
| :--- | :--- |
| Xiaomi / Redmi / POCO (MIUI, HyperOS) | Ajustes de la app → **Inicio automático** activado; **Ahorro de batería → Sin restricciones**. |
| Samsung | Ajustes → Batería → **Límites de uso en segundo plano**: quitar la app de «inactivas» y añadirla a «nunca en reposo». |
| Huawei / Honor | Batería → **Inicio de aplicaciones**: gestionar manualmente y activar las tres opciones. |
| Oppo / Realme / OnePlus | Ajustes de la app → Batería → **Permitir actividad en segundo plano** e inicio automático. |

## Pruebas recomendadas

1. **A 2 metros**: móvil en la mesa con la pantalla apagada, habla normal a 2 m y escucha el audio en Telegram. Si se oye bajo, cambia el micrófono a «Estándar».
2. **Bolsillo**: graba, apaga la pantalla 5 minutos, para y comprueba que el audio dura 5 minutos.
3. **Sin cobertura**: modo avión, graba 20 s, para, quita el modo avión: debe llegar solo, sin abrir la app.
4. **Con ellos**: que empiecen y paren solos desde el widget.

## Compilar

Requiere JDK 17 y el SDK de Android (ruta en `local.properties`).

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew testDebugUnitTest assembleRelease
```

- `scripts/setup-signing.sh`: crea una sola vez la clave de firma en `~/.config/medicomem/` (fuera del repositorio; haz copia, sin ella no se puede actualizar la app instalada).
- `scripts/bump-version.sh patch|minor|major`: sube la versión.
- `scripts/send-apk-telegram.sh -m "resumen"`: envía el APK compilado a Telegram.

El token y el Chat ID nunca están en el código: se escriben en el menú familiar y se guardan solo en el móvil.
