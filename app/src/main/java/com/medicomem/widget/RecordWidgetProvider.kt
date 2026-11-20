package com.medicomem.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.medicomem.R
import com.medicomem.recording.RecordingState
import com.medicomem.ui.MainActivity

/**
 * Widget gigante del escritorio. Un toque abre la pantalla de la app y empieza o para:
 * así el micrófono se activa siempre con la app en primer plano (Android lo exige desde la versión 11).
 */
class RecordWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, RecordWidgetProvider::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context))
        }

        private fun views(context: Context): RemoteViews {
            val recording = RecordingState.isRecording
            val tap = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_TOGGLE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return RemoteViews(context.packageName, R.layout.widget_record).apply {
                setInt(R.id.widgetRoot, "setBackgroundResource",
                    if (recording) R.drawable.widget_bg_recording else R.drawable.widget_bg_idle)
                setTextViewText(R.id.widgetText,
                    context.getString(if (recording) R.string.widget_recording else R.string.widget_idle))
                setOnClickPendingIntent(R.id.widgetRoot, tap)
            }
        }
    }
}
