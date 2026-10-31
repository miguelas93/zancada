package com.zancada.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Sistema "Volt" de la beta: tinta + un único acento lima. El lima nunca va como texto sobre fondo claro:
 * o es un relleno con texto tinta encima, o vive sobre superficies oscuras. Mismos valores que el CSS de la web.
 */
data class ZColors(
    val bg: Color, val surface: Color, val surface2: Color, val ink: Color, val muted: Color, val line: Color,
    val volt: Color = Volt, val onVolt: Color = Ink, val bar: Color, val onBar: Color = OnBar, val onBarMuted: Color = OnBarMuted,
    val mapLand: Color, val danger: Color, val scrim: Color, val night: Boolean,
)

val Ink = Color(0xFF0B0C0B)
val Volt = Color(0xFFD4FF3A)
val OnBar = Color(0xFFF4F5F0)
val OnBarMuted = Color(0xFF8A8E87)

val DayColors = ZColors(
    bg = Color(0xFFF3F3EE), surface = Color.White, surface2 = Color(0xFFE9E9E3), ink = Ink, muted = Color(0xFF5E625C), line = Color(0xFFDCDCD5),
    bar = Ink, mapLand = Color(0xFFE7E7E0), danger = Color(0xFFE5372A), scrim = Color(0x8C0B0C0B), night = false,
)
val NightColors = ZColors(
    bg = Color(0xFF0A0B0A), surface = Color(0xFF151715), surface2 = Color(0xFF1F221F), ink = OnBar, muted = Color(0xFF9BA097), line = Color(0xFF2A2D2A),
    bar = Color(0xFF1F221F), mapLand = Color(0xFF111311), danger = Color(0xFFFF5A47), scrim = Color(0xB3000000), night = true,
)

val LocalZ = staticCompositionLocalOf { DayColors }
/** Dentro de una tarjeta oscura (.dark): el texto es claro y las etiquetas, gris claro. */
val LocalDark = staticCompositionLocalOf { false }
/** Ajuste «Texto más grande». */
val LocalBig = staticCompositionLocalOf { false }

object Z {
    val c: ZColors @Composable @ReadOnlyComposable get() = LocalZ.current
    val dark: Boolean @Composable @ReadOnlyComposable get() = LocalDark.current
    val big: Boolean @Composable @ReadOnlyComposable get() = LocalBig.current
    /** Color del texto normal según dónde esté. */
    val text: Color @Composable @ReadOnlyComposable get() = if (LocalDark.current) LocalZ.current.onBar else LocalZ.current.ink
    /** Color de etiquetas, unidades y subtítulos según dónde esté. */
    val muted: Color @Composable @ReadOnlyComposable get() = if (LocalDark.current) LocalZ.current.onBarMuted else LocalZ.current.muted
}

val Display = FontFamily(Font(R.font.space_grotesk_500, FontWeight.Medium), Font(R.font.space_grotesk_700, FontWeight.Bold))
val Body = FontFamily(
    Font(R.font.inter_400, FontWeight.Normal), Font(R.font.inter_500, FontWeight.Medium),
    Font(R.font.inter_600, FontWeight.SemiBold), Font(R.font.inter_700, FontWeight.Bold),
)

/** Números grandes: Space Grotesk, cifras de ancho fijo, interletrado −0,03 em, altura de línea 1. */
fun numStyle(size: TextUnit, spacing: Double = -0.03) = TextStyle(
    fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = size, lineHeight = size, letterSpacing = spacing.em,
    fontFeatureSettings = "tnum",
)

fun bodyStyle(size: TextUnit = 15.sp, weight: FontWeight = FontWeight.Normal) = TextStyle(fontFamily = Body, fontWeight = weight, fontSize = size, lineHeight = (size.value * 1.45).sp)
