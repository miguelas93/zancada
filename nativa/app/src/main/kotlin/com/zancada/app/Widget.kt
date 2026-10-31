package com.zancada.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.zancada.core.widgetInfo

/** Widget de la pantalla de inicio: km de la semana, próxima sesión y botón «Correr». */
class ZancadaWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) { Widget.render(ctx, mgr, ids) }
}

object Widget {
    const val RUN = "com.zancada.RUN"

    /** Se llama al guardar datos: actualiza los widgets que haya (si no hay ninguno, no hace nada). */
    fun update(ctx: Context) {
        runCatching {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, ZancadaWidget::class.java))
            if (ids.isNotEmpty()) render(ctx, mgr, ids)
        }
    }

    fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        Repo.init(ctx)
        val w = Repo.app().widgetInfo()
        val open = PendingIntent.getActivity(ctx, 20, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val run = PendingIntent.getActivity(ctx, 21, Intent(ctx, MainActivity::class.java).setAction(RUN).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val v = RemoteViews(ctx.packageName, R.layout.widget_zancada).apply {
            setTextViewText(R.id.w_km, "${w.km} km")
            setTextViewText(R.id.w_sub, w.sub)
            setTextViewText(R.id.w_next, w.next)
            setOnClickPendingIntent(R.id.w_root, open)
            setOnClickPendingIntent(R.id.w_run, run)
        }
        mgr.updateAppWidget(ids, v)
    }
}
