package com.medicomem.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import com.medicomem.R
import com.medicomem.recording.RecordingState
import com.medicomem.ui.MainActivity

/**
 * Widget del escritorio, redimensionable desde 1×1. Según el tamaño muestra:
 *   pequeño          solo el icono (micrófono / cuadrado de parar)
 *   ancho y bajo     icono + texto en fila
 *   2×2 o mayor      icono arriba + texto debajo
 * Un toque abre la pantalla de la app y empieza o para: así el micrófono se activa
 * siempre con la app en primer plano (Android lo exige desde la versión 11).
 */
class RecordWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        update(context, manager, id)
    }

    companion object {
        private enum class Shape(val layout: Int) {
            SMALL(R.layout.widget_small), WIDE(R.layout.widget_wide), LARGE(R.layout.widget_large)
        }

        /** Umbrales en dp: una celda del escritorio mide unos 60–100 dp según el móvil. */
        private const val SMALL_MAX = 100f
        private const val WIDE_MIN_RATIO = 1.8f

        private fun pick(w: Float, h: Float): Shape = when {
            w <= 0f || h <= 0f -> Shape.LARGE
            w < SMALL_MAX && h < SMALL_MAX -> Shape.SMALL
            w / h >= WIDE_MIN_RATIO -> Shape.WIDE
            h / w >= WIDE_MIN_RATIO && w < SMALL_MAX -> Shape.SMALL
            else -> Shape.LARGE
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            manager.getAppWidgetIds(ComponentName(context, RecordWidgetProvider::class.java))
                .forEach { update(context, manager, it) }
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            // El diseño se elige con el tamaño real; al redimensionar llega onAppWidgetOptionsChanged.
            val o = manager.getAppWidgetOptions(id)
            val sizes = if (Build.VERSION.SDK_INT >= 31) {
                o.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            } else null
            val (w, h) = sizes?.firstOrNull()?.let { it.width to it.height }
                ?: (o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).toFloat() to
                    o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).toFloat())
            val views = build(context, pick(w, h))
            manager.updateAppWidget(id, views)
        }

        private fun build(context: Context, shape: Shape): RemoteViews {
            val recording = RecordingState.isRecording
            val tap = PendingIntent.getActivity(
                context, 0,
                Intent().setClassName(context, MainActivity.WIDGET_ALIAS)
                    .setAction(MainActivity.ACTION_TOGGLE)
                    // Lo que veía la persona al tocar: si era «PARAR» y ya no se graba, no se empieza otra.
                    .putExtra(MainActivity.EXTRA_WIDGET_WAS_RECORDING, recording)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return RemoteViews(context.packageName, shape.layout).apply {
                setInt(R.id.widgetRoot, "setBackgroundResource",
                    if (recording) R.drawable.widget_bg_recording else R.drawable.widget_bg_idle)
                setImageViewResource(R.id.widgetIcon, if (recording) R.drawable.ic_stop else R.drawable.ic_mic)
                if (shape != Shape.SMALL) {
                    setTextViewText(R.id.widgetText,
                        context.getString(if (recording) R.string.widget_recording else R.string.widget_idle))
                }
                setContentDescription(R.id.widgetRoot,
                    context.getString(if (recording) R.string.widget_desc_recording else R.string.widget_desc_idle))
                setOnClickPendingIntent(R.id.widgetRoot, tap)
            }
        }
    }
}
