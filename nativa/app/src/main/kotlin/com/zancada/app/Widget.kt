package com.zancada.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    /** ¿Hay ya algún widget puesto en el escritorio? */
    fun placed(ctx: Context): Boolean = runCatching {
        AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, ZancadaWidget::class.java)).isNotEmpty()
    }.getOrDefault(false)

    /** Pide al escritorio que coloque el widget (Android muestra «Añadir a la pantalla de inicio»). false si el escritorio no lo permite. */
    fun pin(ctx: Context): Boolean = runCatching {
        val mgr = AppWidgetManager.getInstance(ctx)
        mgr.isRequestPinAppWidgetSupported && mgr.requestPinAppWidget(ComponentName(ctx, ZancadaWidget::class.java), null, null)
    }.getOrDefault(false)

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

/** Tarjeta con la vista previa del widget y el botón para ponerlo en el escritorio (Perfil, y en Inicio hasta que se ponga o se oculte). */
@Composable
fun WidgetCard(home: Boolean = false) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("ui", Context.MODE_PRIVATE) }
    var hidden by remember { mutableStateOf(prefs.getBoolean("widgetCardHidden", false)) }
    var placed by remember { mutableStateOf(Widget.placed(ctx)) }
    var help by remember { mutableStateOf(false) }
    // Al volver del escritorio, comprobar si ya se ha puesto.
    val data by Repo.data.collectAsState()
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1500); placed = Widget.placed(ctx) } }
    if (home && (hidden || placed)) return
    val w = remember(data) { Repo.app().widgetInfo() }
    ZCard(dark = home, pad = PaddingValues(horizontal = 20.dp, vertical = 18.dp), gap = 12.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (home) Chip("Nuevo") else H2("Widget")
            if (placed) Chip("Puesto")
        }
        if (home) Num("Zancada en tu escritorio", 24.sp)
        Sub("Un recuadro en la pantalla de inicio del móvil con tus km de la semana, tu próxima sesión y un botón para salir a correr sin abrir la app. Así se ve:")
        // Vista previa (los mismos colores que el widget real)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF151614)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Txt("ESTA SEMANA", size = 10.sp, color = Color(0xFF8A8E87))
                Txt("${w.km} km", size = 26.sp, weight = FontWeight.Bold, color = Color(0xFFF4F5F0))
                if (w.sub.isNotBlank()) Txt(w.sub, size = 11.sp, color = Color(0xFF8A8E87), maxLines = 1)
                if (w.next.isNotBlank()) Txt(w.next, size = 12.sp, color = Color(0xFFD4FF3A), maxLines = 2)
            }
            Box(Modifier.padding(start = 10.dp).clip(RoundedCornerShape(50)).background(Color(0xFFD4FF3A)).padding(horizontal = 16.dp, vertical = 10.dp)) {
                Txt("Correr", size = 14.sp, weight = FontWeight.Bold, color = Color(0xFF0B0C0B))
            }
        }
        if (placed) Sub("Ya lo tienes en el escritorio. Puedes poner otro o moverlo manteniéndolo pulsado.", size = 12.sp)
        Btn(if (placed) "Añadir otro" else "Añadir a la pantalla de inicio", {
            if (!Widget.pin(ctx)) help = true
        }, kind = if (placed) BtnKind.Sec else BtnKind.Volt, height = 48.dp, size = 15.sp)
        if (help) Sub(
            "Tu móvil no deja que la app lo ponga sola. Hazlo a mano: mantén pulsado un hueco vacío del escritorio, toca «Widgets», busca «Zancada N» y arrástralo. " +
                "En Xiaomi, si no aparece: Ajustes > Aplicaciones > Zancada N > Otros permisos > activa «Accesos directos en pantalla de inicio» y vuelve a pulsar el botón.",
            size = 12.sp,
        )
        if (home) Underline("Ahora no", { prefs.edit().putBoolean("widgetCardHidden", true).apply(); hidden = true }, size = 12.sp)
    }
}
