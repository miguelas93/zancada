package com.zancada.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.min

/*
 * Logo B5 de Zancada: una Z de trazo redondo que es una ruta, con la pastilla A (salida, hueca)
 * arriba a la izquierda y la B (meta, maciza) abajo a la derecha. Medidas en el espacio del dibujo
 * (765 × 670, trazo 150), las mismas que los SVG de 02_Marca/aplicaciones.
 */
const val MARK_W = 765f
const val MARK_H = 670f
private val ZPTS = listOf(Offset(420f, 75f), Offset(690f, 75f), Offset(75f, 595f), Offset(345f, 595f))
private const val SW = 150f
private val INK = Color(0xFF0B0C0B)
private val VOLT = Color(0xFFD4FF3A)
private val PAPER = Color(0xFFF3F3EE)

/** Dibuja el logo centrado. [z] = cuánto de la Z está trazado (0..1); [a] y [b] = tamaño de las pastillas (0..1). */
fun DrawScope.drawZMark(color: Color, z: Float = 1f, a: Float = 1f, b: Float = 1f) {
    val s = min(size.width / MARK_W, size.height / MARK_H)
    val ox = (size.width - MARK_W * s) / 2; val oy = (size.height - MARK_H * s) / 2
    withTransform({ translate(ox, oy); scale(s, s, Offset.Zero) }) {
        if (a > 0f) scale(a, Offset(150f, 75f)) {
            // Pastilla hueca: aro de 42 de grosor (radio exterior 75, interior 33).
            drawRoundRect(color, Offset(21f, 21f), Size(258f, 108f), CornerRadius(54f), style = Stroke(42f))
        }
        if (z > 0f) {
            val segs = ZPTS.zipWithNext(); val lens = segs.map { (p, q) -> hypot(q.x - p.x, q.y - p.y) }
            var rem = lens.sum() * z.coerceAtMost(1f)
            val path = Path().apply { moveTo(ZPTS[0].x, ZPTS[0].y) }
            for ((i, sg) in segs.withIndex()) {
                val (p, q) = sg
                if (rem >= lens[i]) { path.lineTo(q.x, q.y); rem -= lens[i] }
                else { val t = rem / lens[i]; path.lineTo(p.x + (q.x - p.x) * t + 0.01f, p.y + (q.y - p.y) * t); break }
            }
            drawPath(path, color, style = Stroke(SW, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (b > 0f) scale(b, Offset(615f, 595f)) { drawRoundRect(color, Offset(465f, 520f), Size(300f, 150f), CornerRadius(75f)) }
    }
}

/** El símbolo a [height] de alto. */
@Composable
fun ZMark(height: Dp, color: Color, modifier: Modifier = Modifier) =
    Canvas(modifier.size(height * (MARK_W / MARK_H), height).semantics { contentDescription = "Zancada" }) { drawZMark(color) }

/** Símbolo + «zancada» (la firma horizontal). */
@Composable
fun ZLockup(height: Dp, mark: Color, text: Color, modifier: Modifier = Modifier) =
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(height * 0.42f), verticalAlignment = Alignment.CenterVertically) {
        ZMark(height, mark)
        Image(painterResource(R.drawable.zancada_logotipo), null, Modifier.height(height * 0.62f).width(height * 0.62f * (5512f / 786f)), colorFilter = ColorFilter.tint(text))
    }

private fun backOut(x: Float): Float { val c1 = 1.70158f; val c3 = c1 + 1; val u = x - 1; return 1 + c3 * u * u * u + c1 * u * u }

const val INTRO_MS = 1500

/** Pantalla de arranque: aparece la A, se dibuja la ruta, aterriza la B y sale «zancada». Un toque la salta. */
@Composable
fun IntroSplash(onDone: () -> Unit) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(INTRO_MS, easing = LinearEasing)); onDone() }
    val ms = t.value * INTRO_MS
    fun seg(from: Float, to: Float, e: Easing = FastOutSlowInEasing) = e.transform(((ms - from) / (to - from)).coerceIn(0f, 1f))
    val a = backOut(seg(0f, 260f, LinearEasing)); val z = seg(160f, 800f); val b = backOut(seg(760f, 1020f, LinearEasing))
    val wm = seg(880f, 1150f); val out = seg(1280f, 1500f)
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = 1f - out }.background(INK).pointerInput(Unit) { detectTapGestures { onDone() } },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(96.dp * (MARK_W / MARK_H), 96.dp).semantics { contentDescription = "Zancada" }) { drawZMark(VOLT, z, a, b) }
        Image(
            painterResource(R.drawable.zancada_logotipo), null,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 64.dp).height(20.dp).width(20.dp * (5512f / 786f)).alpha(wm),
            colorFilter = ColorFilter.tint(PAPER)
        )
    }
}
