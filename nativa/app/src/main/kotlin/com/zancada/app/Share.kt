package com.zancada.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.zancada.core.*
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** Compartir y guardar: el menú de compartir de Android (Drive, Telegram, WhatsApp, «Guardar en el dispositivo»…). */
object Share {
    fun file(ctx: Context, name: String, mime: String, bytes: ByteArray, text: String? = null) {
        runCatching {
            val dir = File(ctx.cacheDir, "share").apply { mkdirs(); listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 86_400_000) it.delete() } }
            val f = File(dir, name); f.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
            val i = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (text != null) i.putExtra(Intent.EXTRA_TEXT, text)
            i.clipData = ClipData.newRawUri(name, uri)
            ctx.startActivity(Intent.createChooser(i, "Guardar o enviar").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Toast.makeText(ctx, "No he podido compartir el archivo: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    fun text(ctx: Context, text: String, title: String? = null) {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        if (title != null) i.putExtra(Intent.EXTRA_SUBJECT, title)
        runCatching { ctx.startActivity(Intent.createChooser(i, title ?: "Compartir").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /**
     * Mensaje directo al contacto de seguridad, sin permisos: WhatsApp si está instalado, si no la app de SMS
     * con el texto ya escrito. El corredor solo pulsa «Enviar».
     */
    fun toContact(ctx: Context, phone: String, text: String) {
        val digits = phone.filter { it.isDigit() }.let { if (phone.startsWith("+") || it.length > 9) it else "34$it" }
        val wa = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits?text=" + Uri.encode(text))).setPackage("com.whatsapp").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { ctx.startActivity(wa) }.isSuccess) return
        val sms = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(phone))).putExtra("sms_body", text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { ctx.startActivity(sms) }.isFailure) text(ctx, text, "Avisar")
    }

    fun open(ctx: Context, url: String) { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }

    fun readText(ctx: Context, uri: Uri): String? = runCatching { ctx.contentResolver.openInputStream(uri)?.use { s -> s.readBytes().takeIf { it.size < 60 * 1024 * 1024 }?.toString(Charsets.UTF_8) } }.getOrNull()
}

/** Las imágenes para compartir (1080×1350), dibujadas igual que el canvas de la web. */
object Images {
    private const val W = 1080; private const val H = 1350
    private val INK = 0xFF0B0C0B.toInt(); private val VOLT = 0xFFD4FF3A.toInt(); private val FG = 0xFFF4F5F0.toInt()
    private fun mut(a: Double) = ((a * 255).toInt() shl 24) or 0xF4F5F0

    private class Fonts(ctx: Context) {
        val disp: Typeface = runCatching { ResourcesCompat.getFont(ctx, R.font.space_grotesk_700) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        val b500: Typeface = runCatching { ResourcesCompat.getFont(ctx, R.font.inter_500) }.getOrNull() ?: Typeface.DEFAULT
        val b600: Typeface = runCatching { ResourcesCompat.getFont(ctx, R.font.inter_600) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        val b700: Typeface = runCatching { ResourcesCompat.getFont(ctx, R.font.inter_700) }.getOrNull() ?: Typeface.DEFAULT_BOLD
    }
    private fun paint(tf: Typeface, size: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = size; this.color = color }
    private fun png(b: Bitmap) = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    fun run(ctx: Context, app: App, r: Run): ByteArray {
        val f = Fonts(ctx); val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888); val x = Canvas(bmp)
        x.drawColor(INK)
        val pts = r.points
        if (pts.size > 1) {
            val k = cos(Math.toRadians(pts[0].lat)); val xs = pts.map { it.lon * k }; val ys = pts.map { -it.lat }
            val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max(); val span = max(maxX - minX, maxY - minY).let { if (it == 0.0) 1e-6 else it }
            val bx = 90f; val by = 300f; val bw = 900f; val bh = 620f; val s = min(bw, bh) / span
            val ox = bx + (bw - (maxX - minX) * s) / 2; val oy = by + (bh - (maxY - minY) * s) / 2
            fun P(i: Int) = floatArrayOf(((xs[i] - minX) * s + ox).toFloat(), ((ys[i] - minY) * s + oy).toFloat())
            val path = android.graphics.Path(); pts.indices.forEach { i -> val p = P(i); if (i == 0) path.moveTo(p[0], p[1]) else path.lineTo(p[0], p[1]) }
            for ((col, w) in listOf(0x2ED4FF3A to 34f, VOLT to 12f)) x.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = col; strokeWidth = w })
            val a = P(0); val b = P(pts.size - 1); val fp = Paint(Paint.ANTI_ALIAS_FLAG)
            fp.color = 0xFFFFFFFF.toInt(); x.drawCircle(a[0], a[1], 14f, fp); fp.color = INK; x.drawCircle(a[0], a[1], 7f, fp)
            fp.color = VOLT; x.drawCircle(b[0], b[1], 14f, fp); fp.color = INK; x.drawCircle(b[0], b[1], 7f, fp)
        }
        lockup(ctx, x, 90f, 92f, 42f)
        x.drawText(app.es.fmtDate(r.start) + (r.route?.let { "  ·  " + it.name } ?: r.session?.let { "  ·  " + it.title.ifEmpty { "sesión del plan" } } ?: ""), 90f, 185f, paint(f.b500, 30f, mut(.6)))
        val big = paint(f.disp, 190f, FG); x.drawText(fmtKm(r.dist), 80f, 1095f, big)
        x.drawText("km", 90f + big.measureText(fmtKm(r.dist)) + 16f, 1095f, paint(f.b600, 56f, mut(.6)))
        val ok = r.splits.filter { !it.partial }; val best = if (ok.isNotEmpty()) app.pace(ok.minOf { it.split }) else null
        listOf("Tiempo" to fmtTime(r.dur), "Ritmo" to app.pace(r.paceSec()) + " /km", "Mejor km" to (best?.let { "$it /km" } ?: "—")).forEachIndexed { i, (l, v) ->
            val cx = 90f + i * 310f; x.drawText(l.uppercase(), cx, 1190f, paint(f.b600, 24f, mut(.5))); x.drawText(v, cx, 1260f, paint(f.disp, 62f, if (i == 1) VOLT else FG))
        }
        val recs = app.personalRecords().filter { it.run?.id == r.id }.map { it.label.uppercase() }
        if (recs.isNotEmpty()) {
            val label = "RÉCORD · " + recs.joinToString(" · "); val p = paint(f.b700, 26f, INK); val w = p.measureText(label) + 48f
            x.drawRoundRect(RectF(90f, 228f, 90f + w, 282f), 27f, 27f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = VOLT }); x.drawText(label, 114f, 265f, p)
        }
        app.compareRun(r)?.let { x.drawText(it.replace(Regex(" \\(.*\\)\\.?$"), "").replace(Regex("\\.$"), ""), 90f, 1320f, paint(f.b500, 26f, mut(.6))) }
        return png(bmp)
    }

    /** Logo (símbolo volt + «zancada») arriba a la izquierda de las imágenes para compartir. */
    private fun lockup(ctx: Context, x: Canvas, left: Float, top: Float, h: Float) {
        val mw = h * MARK_W / MARK_H
        ContextCompat.getDrawable(ctx, R.drawable.zancada_marca)?.mutate()?.apply { setBounds(left.toInt(), top.toInt(), (left + mw).toInt(), (top + h).toInt()); setTint(VOLT); draw(x) }
        val wh = h * 0.62f; val ww = wh * 5512f / 786f; val wx = left + mw + h * 0.42f; val wy = top + (h - wh) / 2
        ContextCompat.getDrawable(ctx, R.drawable.zancada_logotipo)?.mutate()?.apply { setBounds(wx.toInt(), wy.toInt(), (wx + ww).toInt(), (wy + wh).toInt()); setTint(FG); draw(x) }
    }

    fun week(ctx: Context, app: App): ByteArray? {
        val ws = app.weekStart(app.now()); val wr = app.runs.filter { it.start >= ws }
        if (wr.isEmpty()) return null
        val f = Fonts(ctx); val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888); val x = Canvas(bmp)
        val dist = wr.sumOf { it.dist }; val dur = wr.sumOf { it.dur }; val gain = wr.sumOf { (it.gain ?: 0).toDouble() }
        val day = DoubleArray(7); wr.forEach { day[app.es.dow(it.start)] += it.dist / 1000 }
        x.drawColor(INK)
        lockup(ctx, x, 90f, 92f, 42f)
        x.drawText("Mi semana · ${app.es.dayMon(ws)} – ${app.es.dayMon(ws + 6 * DAY)}", 90f, 180f, paint(f.b500, 30f, mut(.6)))
        val big = paint(f.disp, 230f, FG); x.drawText(fmtKm(dist), 80f, 480f, big)
        x.drawText("km", 96f + big.measureText(fmtKm(dist)) + 12f, 480f, paint(f.b600, 64f, mut(.6)))
        val mx = max(1.0, day.max()); val bw = 96f; val gap = (900f - 7 * bw) / 6; val base = 960f; val bh = 340f
        "LMXJVSD".forEachIndexed { i, l ->
            val bx = 90f + i * (bw + gap); val h = if (day[i] > 0) max(16.0, day[i] / mx * bh).toFloat() else 8f
            x.drawRoundRect(RectF(bx, base - h, bx + bw, base), 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (day[i] > 0) VOLT else 0x24F4F5F0 })
            if (day[i] > 0) { val p = paint(f.disp, 30f, FG); val t = toFixed(day[i], 1).replace('.', ','); x.drawText(t, bx + (bw - p.measureText(t)) / 2, base - h - 16f, p) }
            val p = paint(f.b600, 28f, mut(.6)); x.drawText(l.toString(), bx + (bw - p.measureText(l.toString())) / 2, base + 46f, p)
        }
        listOf("Salidas" to wr.size.toString(), "Tiempo" to fmtTime(dur), "Desnivel" to "${jsRound(gain)} m").forEachIndexed { i, (l, v) ->
            val cx = 90f + i * 310f; x.drawText(l.uppercase(), cx, 1140f, paint(f.b600, 24f, mut(.5))); x.drawText(v, cx, 1215f, paint(f.disp, 64f, if (i == 0) VOLT else FG))
        }
        x.drawText("Ritmo medio ${app.pace(dur / (dist / 1000))} /km", 90f, 1300f, paint(f.b500, 26f, mut(.6)))
        return png(bmp)
    }

    /** «Tu mes en Zancada»: km grandes, un punto por día y las cifras del mes. */
    fun month(ctx: Context, app: App, w: MonthWrap): ByteArray {
        val f = Fonts(ctx); val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888); val x = Canvas(bmp)
        x.drawColor(INK)
        lockup(ctx, x, 90f, 92f, 42f)
        x.drawText("Mi ${app.es.monYear(w.from)}", 90f, 180f, paint(f.b500, 30f, mut(.6)))
        val big = paint(f.disp, 230f, FG); val km = fmtKmS(w.km * 1000); x.drawText(km, 80f, 450f, big)
        x.drawText("km", 96f + big.measureText(km) + 12f, 450f, paint(f.b600, 64f, mut(.6)))
        val dot = 92f; val gap = (900f - 7 * dot) / 6
        w.dayKm.forEachIndexed { i, v ->
            val cx = 90f + (i % 7) * (dot + gap) + dot / 2; val cy = 560f + (i / 7) * (dot * 0.62f + 18f) + dot * 0.31f
            x.drawCircle(cx, cy, dot * 0.31f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (v > 0) VOLT else 0x24F4F5F0 })
        }
        listOf("Salidas" to w.runs.toString(), "Tiempo" to fmtTime(w.dur), "Días" to w.days.toString()).forEachIndexed { i, (l, v) ->
            val cx = 90f + i * 310f; x.drawText(l.uppercase(), cx, 1060f, paint(f.b600, 24f, mut(.5))); x.drawText(v, cx, 1135f, paint(f.disp, 64f, if (i == 0) VOLT else FG))
        }
        w.longest?.let { x.drawText("La más larga: ${fmtKm(it.dist)} km", 90f, 1220f, paint(f.b600, 32f, FG)) }
        x.drawText(w.verdict(), 90f, 1290f, paint(f.b500, 26f, mut(.6)))
        return png(bmp)
    }
}

/**
 * Avisos del plan: «Hoy toca correr» a la hora elegida los días de entreno, aunque la app esté cerrada.
 * Se reprograman los próximos 21 días cada vez que algo cambia (y al reiniciar el móvil).
 */
object Reminders {
    private const val CHANNEL = "plan"
    fun sync(ctx: Context) {
        Repo.init(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val prefs = ctx.getSharedPreferences("reminders", Context.MODE_PRIVATE)
        prefs.getStringSet("ids", emptySet())!!.forEach { id -> am.cancel(pi(ctx, id.toInt(), "", "")) }
        val list = Repo.app().reminders()
        list.forEach { r -> runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.at, pi(ctx, r.id, r.title, r.body)) } }
        prefs.edit().putStringSet("ids", list.map { it.id.toString() }.toSet()).apply()
    }

    private fun pi(ctx: Context, id: Int, title: String, body: String) = PendingIntent.getBroadcast(ctx, id,
        Intent(ctx, ReminderReceiver::class.java).putExtra("id", id).putExtra("title", title).putExtra("body", body), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun show(ctx: Context, id: Int, title: String, body: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Avisos del plan", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, id, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_zancada).setColor(0xFFD4FF3A.toInt())
            .setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(open).setAutoCancel(true).build()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        NotificationManagerCompat.from(ctx).notify(id, n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val title = i.getStringExtra("title") ?: return
        if (title.isEmpty()) return
        Reminders.show(ctx, i.getIntExtra("id", 1000), title, i.getStringExtra("body") ?: "")
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) { if (i.action == Intent.ACTION_BOOT_COMPLETED || i.action == Intent.ACTION_MY_PACKAGE_REPLACED) Reminders.sync(ctx) }
}
