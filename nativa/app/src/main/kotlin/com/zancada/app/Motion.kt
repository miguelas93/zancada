package com.zancada.app

import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Movimiento y tacto de la app: esqueletos de carga, cifras que suben, vibraciones cortas.
 * Si el móvil tiene las animaciones desactivadas (accesibilidad), todo aparece quieto.
 */
object Motion {
    @Volatile var reduced = false
    fun init(ctx: android.content.Context) {
        reduced = runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    const val SCREEN_MS = 240
}

/** Tipos de vibración: toque normal, confirmación (guardar, logro) y paso (recorrer una gráfica). */
enum class Buzz { Tap, Confirm, Tick }

fun View.buzz(kind: Buzz = Buzz.Tap) {
    val k = when (kind) {
        Buzz.Tap -> HapticFeedbackConstants.VIRTUAL_KEY
        Buzz.Tick -> HapticFeedbackConstants.CLOCK_TICK
        Buzz.Confirm -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
    }
    runCatching { performHapticFeedback(k) }
}

/** Vibración corta para usar en un onClick: `val buzz = rememberBuzz(); … buzz()`. */
@Composable
fun rememberBuzz(kind: Buzz = Buzz.Tap): () -> Unit {
    val v = LocalView.current
    return remember(v, kind) { { v.buzz(kind) } }
}

/** Bloque gris que «respira» mientras algo carga (en vez de una pantalla quieta). */
@Composable
fun Skeleton(modifier: Modifier = Modifier, radius: Dp = 20.dp, color: Color? = null) {
    val base = color ?: if (Z.dark) Color(0x24F4F5F0) else Z.c.surface2
    val a = if (Motion.reduced) 0.8f else {
        val t = rememberInfiniteTransition(label = "sk")
        t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ska").value
    }
    Box(modifier.clip(RoundedCornerShape(radius)).alpha(a).background(base))
}

/** Tarjeta de ruta en carga: miniatura + dos líneas. */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Z.c.surface).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Skeleton(Modifier.size(72.dp), 18.dp)
        Column(Modifier.weight(1f).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Skeleton(Modifier.fillMaxWidth(0.7f).height(14.dp), 7.dp)
            Skeleton(Modifier.fillMaxWidth(0.45f).height(12.dp), 6.dp)
            Skeleton(Modifier.fillMaxWidth(0.3f).height(12.dp), 6.dp)
        }
    }
}

/** Tres puntos que laten: «Zeta está escribiendo». */
@Composable
fun TypingDots(color: Color = Z.muted) {
    val t = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        repeat(3) { i ->
            val a = if (Motion.reduced) 1f else t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(450, delayMillis = i * 150), RepeatMode.Reverse), label = "d$i").value
            Box(Modifier.size(7.dp).alpha(a).clip(CircleShape).background(color))
        }
    }
}

/** Un valor que sube desde 0 la primera vez que se ve (anillos, barras, cifras). */
@Composable
fun animatedOnce(target: Float, ms: Int = 700, delayMs: Int = 0): Float {
    if (Motion.reduced) return target
    val a = remember { Animatable(0f) }
    LaunchedEffect(target) { a.animateTo(target, tween(ms, delayMs, FastOutSlowInEasing)) }
    return a.value
}

/** Cifra que cuenta hacia arriba al entrar: `countUp(12.4) { fmtKm(it) }`. */
@Composable
fun countUp(target: Double, ms: Int = 650, fmt: (Double) -> String): String {
    if (Motion.reduced || !target.isFinite() || target <= 0) return fmt(target)
    val f = animatedOnce(1f, ms)
    return if (f >= 1f) fmt(target) else fmt(target * f)
}

