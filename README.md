<p align="center"><img src="docs/icono.png" width="120" alt="Icono de MedicoMem"></p>

# MedicoMem — Grabar médico

App Android de **un solo botón** para que las personas mayores graben sus consultas médicas.
Al parar, el audio llega solo al **Telegram de la familia**. Sin servidor, sin cuentas y gratis.

## ✨ Qué hace

- 🎙️ **Un botón gigante**: un toque para grabar (pantalla **roja**, vibración y pitido) y otro para parar (**✓ GUARDADO** en verde).
- 🔓 **Se para sin desbloquear el móvil**: con la pantalla apagada, basta pulsar el botón de encendido y aparece la pantalla roja, sin PIN ni patrón. También desde la notificación (flecha ⌄ → **Parar y enviar**).
- 🧩 **Widget** en el escritorio, de 1×1 a pantalla completa: verde para grabar, rojo para parar.
- 🔄 **Ayuda a colocar el móvil**: al grabar, la pantalla se pone del revés con «▲ HACIA EL MÉDICO ▲», así el micrófono queda apuntando al médico.
- 🔊 **Mejora las voces**: sube la voz de quien habla lejos, quita golpes y zumbidos y no deja que sature la voz cercana.
- 📨 **Envío automático a Telegram** (a ti o a un grupo familiar) en cuanto hay señal, por datos o Wi‑Fi. Si no hay cobertura, lo reintenta solo, incluso tras reiniciar el móvil.
- 🎤 **Llega como nota de voz**: se oye a 1,5× o 2× y, con Telegram Premium, se transcribe con el botón **→A** (Android 10 o superior).
- 📞 **No pierde nada**: si entra una llamada, guarda y envía lo grabado y sigue en una parte nueva al colgar. Las consultas muy largas se parten solas.
- 🛟 **Cortes de seguridad**: para sola a las 3 horas o con la batería al 5 %, y lo avisa en el mensaje.
- ⏱️ **Ignora toques por error**: las grabaciones de menos de 10 segundos no se envían.
- 🔒 **Ajustes escondidos**: solo se abren manteniendo pulsado 4 segundos un texto discreto, para que nadie los cambie sin querer.
- 🧹 **No llena el móvil**: lo enviado se borra solo a los 15 días.

Mensaje que llega a Telegram:

```text
🎙️ Cita médica — Mamá
📅 20/11/2026 10:35 · ⏱️ 22 min · 🔋 68%
⚠️ Batería baja — recordar cargar        (si queda menos del 20 %)
⚠️ Corte automático (límite de 3 h)       (si se paró sola)
```

## 📋 Requisitos

| | |
| :--- | :--- |
| 📱 Móvil | Android 7.0 o superior. Para el botón →A de transcripción, Android 10 o superior. |
| 💾 Espacio | Unos 21–28 MB por hora de grabación (se borra solo a los 15 días de enviarse). |
| ✈️ Telegram | Una cuenta tuya y un bot propio (se crea gratis en 2 minutos). Telegram Premium solo si quieres transcripción. |
| 📶 Conexión | Datos móviles o Wi‑Fi en algún momento después de la consulta. |

## ⬇️ Descargar

Última versión: **[MedicoMem.apk](https://github.com/mosqueteroweb/MedicoMem/releases/latest/download/MedicoMem.apk)** · [todas las versiones](https://github.com/mosqueteroweb/MedicoMem/releases) · [novedades](CHANGELOG.md)

## 🔐 Privacidad

- Las grabaciones y el token solo se guardan en el móvil, en la zona privada de la app: no van a copias en la nube ni pasan a un móvil nuevo al cambiar de teléfono.
- Telegram guarda los audios en sus servidores: los chats con bots y los grupos **no** tienen cifrado de extremo a extremo. Tenlo en cuenta al elegir quién está en el grupo.
- Es buena idea **avisar al médico** de que se graba («es para que mis hijos se enteren bien»). Suele agradecerlo y evita malentendidos.

## ⚙️ Configuración (una sola vez)

**En tu Telegram**

1. 🤖 Habla con **@BotFather**, envía `/newbot`, ponle un nombre y copia el **token** que te da (algo como `85xxxxxxx:AAexxxxx`). Es como una contraseña: no lo compartas.
2. 💬 Abre tu bot y pulsa **Iniciar**. Si quieres que los audios lleguen a varios, crea un **grupo familiar**, añade el bot y escribe **`/start`** en el grupo.

**En el móvil de la persona mayor**

3. ⬇️ Abre el enlace de descarga, instala el APK (Android pedirá permitir instalar apps de esa fuente) y abre la app.
4. 🎙️ Toca el botón verde una vez y acepta **Micrófono** (y **Notificaciones** en Android 13 o superior). Vuelve a tocar para parar.
5. 🔒 **Mantén pulsado 4 segundos** el texto gris de arriba («Configuración familiar»):
   - ✍️ Escribe el **nombre** (`Mamá`, `Papá`…) y pega el **token** en «Bot Token» (se ve con puntos; 👁 para comprobarlo).
   - 🔎 Pulsa **Buscar chat o grupo** y elige a dónde se envían (👤 tú o 👥 el grupo).
   - ✅ Pulsa **Probar envío ahora**: te llegará un mensaje con el modelo del móvil, su versión de Android y si habrá transcripción.
   - 🔋 Pulsa **Desactivar ahorro de batería**. En **Xiaomi, Redmi, POCO, Huawei y Honor** haz además los pasos de la tabla de abajo: sin ellos la grabación puede cortarse.
6. 🧩 En el escritorio, mantén pulsado un hueco → **Widgets** → **Grabar médico**, y ponlo bien grande en un sitio despejado.
7. 🧪 Haz una prueba de más de 10 segundos y comprueba que llega. Después, la prueba de la pantalla apagada (abajo).

### 🔋 Ahorro de batería

La app no funciona todo el rato: solo trabaja sin estar en pantalla **mientras graba con la pantalla apagada** y **cuando envía un audio que se quedó pendiente** por falta de cobertura. Quitarle el ahorro de batería no hace que gaste más; solo evita que Android la congele en esos dos momentos (la grabación se cortaría o el envío se retrasaría horas).

| Marca | ¿Hace falta? | Qué tocar además del botón **Desactivar ahorro de batería** |
| :--- | :--- | :--- |
| Xiaomi / Redmi / POCO | ⚠️ **Imprescindible** | Botón **Ajustes de la app** → **Inicio automático** activado; **Ahorro de batería → Sin restricciones**. Si al tocar el widget no se abre la app: **Otros permisos → Mostrar ventanas emergentes mientras se ejecuta en segundo plano**. |
| Huawei / Honor | ⚠️ **Imprescindible** | Batería → **Inicio de aplicaciones**: gestionar manualmente y activar las tres opciones. |
| Samsung | 👍 Recomendable | Ajustes → Batería → **Límites de uso en segundo plano**: quitar la app de «inactivas» y añadirla a «nunca en reposo». |
| Oppo / Realme / OnePlus | 👍 Recomendable | Ajustes de la app → Batería → **Permitir actividad en segundo plano** e inicio automático. |
| LG, Motorola, Google Pixel y otros | 👍 Recomendable | Basta con el botón de la app. |

🧪 **Prueba para saber si está bien**: graba, apaga la pantalla 5 minutos y para. Si el audio llega y dura 5 minutos, todo correcto.

## 👂 Para que se oiga bien

- 📱 Móvil **sobre la mesa**, pantalla arriba, con «▲ HACIA EL MÉDICO» apuntando al médico.
- 👜 **Nunca en el bolsillo ni en el bolso**: la tela tapa las voces aunque la grabación siga.
- 📕 Si tiene funda de libro, **abierta y plana**; ninguna funda debe tapar el agujero del micrófono (borde de abajo).
- 🎧 Mejor aún: un **micrófono de solapa con cable** (USB‑C o minijack) encima de la mesa. Los Bluetooth graban peor.
- 🎛️ En el menú familiar se puede elegir el micrófono. Graba a 2 metros con cada opción y quédate con la que mejor se oiga en ese móvil.

## 🛠️ Compilar

Requiere JDK 17 y el SDK de Android (ruta en `local.properties`).

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew testDebugUnitTest assembleRelease
```

- `scripts/setup-signing.sh`: crea una sola vez la clave de firma en `~/.config/medicomem/`, fuera del repositorio. Haz copia: sin ella no se puede actualizar la app instalada.
- `scripts/bump-version.sh patch|minor|major`: sube la versión.
- `scripts/publish-release.sh "resumen"`: crea la release de GitHub con el APK adjunto.
- `scripts/send-apk-telegram.sh -m "resumen"`: envía el APK compilado a Telegram.

El token y el Chat ID nunca están en el código: se escriben en el menú familiar y se guardan solo en el móvil. Detalles técnicos y cambios de cada versión en el [CHANGELOG](CHANGELOG.md).
