package com.zancada.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zancada.core.Series
import com.zancada.core.fmtKmS
import com.zancada.core.nearest

/**
 * Gráfica de línea que se recorre con el dedo (como las de Strava): al tocar o arrastrar
 * aparece una línea vertical y arriba se lee el dato de ese punto. Sin tocar, se lee la media.
 *
 * [invert] pone los valores pequeños arriba (para el ritmo: más rápido = más arriba).
 */
@Composable
fun ScrubChart(
    title: String, icon: ImageVector, s: Series, fmtY: (Double) -> String, unitY: String, xUnit: String,
    modifier: Modifier = Modifier, invert: Boolean = false, color: Color = Z.c.volt, line: Color = Z.text, key: String = title,
    fmtX: (Double) -> String = { x -> if (xUnit == "km") fmtKmS(Math.round(x * 10) * 100.0) else "${x.toInt()}" },
) {
    if (s.size < 3) return
    val view = LocalView.current
    var sel by remember(s) { mutableStateOf(-1) }
    val mn = remember(s) { s.y.min() }; val mx = remember(s) { s.y.max() }; val avg = remember(s) { s.avg() }
    val grow = animatedOnce(1f, 800)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(18.dp), tint = Z.text)
            Txt(title, Modifier.weight(1f), size = 15.sp, weight = FontWeight.SemiBold, maxLines = 1)
            if (sel >= 0) { Num(fmtY(s.y[sel]), 18.sp, unit = unitY, unitSize = 11.sp); Sub("· ${fmtX(s.x[sel])} $xUnit", size = 12.sp) }
            else { Sub("media", size = 12.sp); Num(fmtY(avg), 18.sp, unit = unitY, unitSize = 11.sp) }
        }
        val sz = Z.c; val muted = Z.muted; val lineC = if (Z.dark) Color(0x24F4F5F0) else sz.line
        Box(Modifier.fillMaxWidth().height(120.dp).noSwipeNav("chart-$key").semantics { contentDescription = "$title: media ${fmtY(avg)} $unitY, de ${fmtY(if (invert) mx else mn)} a ${fmtY(if (invert) mn else mx)}" }
            .pointerInput(s) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun pick(px: Float) {
                        val x0 = s.x.first() + (px / size.width).coerceIn(0f, 1f) * (s.x.last() - s.x.first())
                        val i = s.nearest(x0); if (i != sel) { sel = i; view.buzz(Buzz.Tick) }
                    }
                    pick(down.position.x); down.consume()
                    while (true) {
                        val ev = awaitPointerEvent(); val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        pick(ch.position.x); ch.consume()
                    }
                    sel = -1
                }
            }) {
            Canvas(Modifier.matchParentSize()) {
                val w = size.width; val h = size.height; val top = 6.dp.toPx(); val bot = h - 4.dp.toPx()
                val x0 = s.x.first(); val span = (s.x.last() - x0).let { if (it <= 0) 1.0 else it }
                val pad = (mx - mn) * 0.12 + 1e-6; val lo = mn - pad; val hi = mx + pad
                fun X(i: Int) = ((s.x[i] - x0) / span * w).toFloat()
                fun Y(v: Double): Float { val f = ((v - lo) / (hi - lo)).toFloat(); return if (invert) top + f * (bot - top) else bot - f * (bot - top) }
                // media, discontinua
                val ya = Y(avg); drawLine(lineC, Offset(0f, ya), Offset(w, ya), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
                val pl = Path().apply { s.y.indices.forEach { i -> if (i == 0) moveTo(X(i), Y(s.y[i])) else lineTo(X(i), Y(s.y[i])) } }
                val fill = Path().apply { addPath(pl); lineTo(w, h); lineTo(0f, h); close() }
                clipRect(right = w * grow) {
                    drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.55f), color.copy(alpha = 0.05f))))
                    drawPath(pl, line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                if (sel >= 0) {
                    val x = X(sel); val y = Y(s.y[sel])
                    drawLine(line, Offset(x, 0f), Offset(x, h), 1.5.dp.toPx())
                    drawCircle(color, 7.dp.toPx(), Offset(x, y)); drawCircle(line, 7.dp.toPx(), Offset(x, y), style = Stroke(2.dp.toPx()))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Sub("${fmtX(s.x.first())} $xUnit", size = 11.sp, color = muted)
            Sub(if (invert) "más rápido ↑" else "máx ${fmtY(mx)} · mín ${fmtY(mn)}", size = 11.sp, color = muted)
            Sub("${fmtX(s.x.last())} $xUnit", size = 11.sp, color = muted)
        }
    }
}
