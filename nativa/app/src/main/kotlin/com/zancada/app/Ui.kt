package com.zancada.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.zancada.core.LatLon
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

// ---------- texto ----------

@Composable
fun Txt(
    text: String, modifier: Modifier = Modifier, size: TextUnit = 15.sp, weight: FontWeight = FontWeight.Normal, color: Color = Z.text,
    align: TextAlign? = null, maxLines: Int = Int.MAX_VALUE, lineHeight: Double = 1.45,
) {
    val s = if (Z.big && size.value <= 16f) (size.value + 2).sp else size
    Text(text, modifier, style = bodyStyle(s, weight).copy(color = color, lineHeight = (s.value * lineHeight).sp), textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** .lbl: etiqueta en mayúsculas. */
@Composable
fun Lbl(text: String, modifier: Modifier = Modifier, color: Color = Z.muted, size: TextUnit = 11.sp) {
    val s = if (Z.big) (size.value + 1).sp else size
    Text(text.uppercase(), modifier, style = bodyStyle(s, FontWeight.SemiBold).copy(color = color, letterSpacing = 0.08.em, lineHeight = (s.value * 1.3).sp), maxLines = 2)
}

@Composable
fun Sub(text: String, modifier: Modifier = Modifier, size: TextUnit = 13.sp, align: TextAlign? = null, maxLines: Int = Int.MAX_VALUE, color: Color = Z.muted) =
    Txt(text, modifier, size, FontWeight.Medium, color, align, maxLines)

@Composable
fun H1(text: String, modifier: Modifier = Modifier, size: TextUnit = 30.sp, color: Color = Z.text) =
    Text(text, modifier, style = numStyle(size, -0.02).copy(color = color, lineHeight = (size.value * 1.05).sp, fontFeatureSettings = null))

@Composable
fun H2(text: String, modifier: Modifier = Modifier) = Txt(text, modifier, 17.sp, FontWeight.SemiBold, lineHeight = 1.2)

/** Número grande con su unidad pegada (.num + .unit). */
@Composable
fun Num(value: String, size: TextUnit, modifier: Modifier = Modifier, unit: String? = null, unitSize: TextUnit = 13.sp, unitWeight: FontWeight = FontWeight.Medium, color: Color = Z.text, spacing: Double = -0.03) {
    val us = if (Z.big) (unitSize.value + 2).sp else unitSize
    val mutedC = Z.muted
    Text(buildAnnotatedString {
        append(value)
        if (!unit.isNullOrEmpty()) withStyle(SpanStyle(fontFamily = Body, fontWeight = unitWeight, fontSize = us, color = mutedC, letterSpacing = 0.sp)) { append(" $unit") }
    }, modifier, style = numStyle(size, spacing).copy(color = color), maxLines = 1, softWrap = false)
}

/** Columna de dato: etiqueta arriba, número abajo (.stats > div). */
@Composable
fun RowScope.Stat(lbl: String, value: String, size: TextUnit = 26.sp, unit: String? = null, end: Boolean = false, color: Color = Z.text, unitSize: TextUnit = 13.sp) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = if (end) Alignment.End else Alignment.Start) {
        Lbl(lbl); Num(value, size, unit = unit, color = color, unitSize = unitSize)
    }
}

// ---------- superficies ----------

/** .card: blanca (u oscura con [dark]), radio 28, relleno 20. */
@Composable
fun ZCard(
    modifier: Modifier = Modifier, dark: Boolean = false, pad: PaddingValues = PaddingValues(20.dp), gap: Dp = 14.dp, radius: Dp = 28.dp,
    color: Color? = null, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit,
) {
    val c = Z.c
    val bg = color ?: if (dark) c.bar else c.surface
    CompositionLocalProvider(LocalDark provides (dark || LocalDark.current && color == null)) {
        Column(
            modifier.fillMaxWidth().clip(RoundedCornerShape(radius)).background(bg).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(pad),
            verticalArrangement = Arrangement.spacedBy(gap), content = content,
        )
    }
}

enum class BtnKind { Volt, Sec, Inv, Ghost }

/** .btn: píldora de 58 de alto. */
@Composable
fun Btn(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, kind: BtnKind = BtnKind.Volt, height: Dp = 58.dp, size: TextUnit = 16.sp,
    enabled: Boolean = true, color: Color? = null, bg: Color? = null, icon: ImageVector? = null, fill: Boolean = true,
) {
    val c = Z.c
    val back = bg ?: when (kind) { BtnKind.Volt -> c.volt; BtnKind.Sec -> c.surface2; BtnKind.Inv -> c.bar; BtnKind.Ghost -> Color.Transparent }
    val fg = color ?: when (kind) { BtnKind.Volt -> c.onVolt; BtnKind.Sec -> c.ink; BtnKind.Inv -> c.onBar; BtnKind.Ghost -> Z.muted }
    val h = if (kind == BtnKind.Ghost && height == 58.dp) 44.dp else height
    val fs = if (kind == BtnKind.Ghost && size == 16.sp) 14.sp else size
    Row(
        (if (fill) modifier.fillMaxWidth() else modifier).height(h).clip(RoundedCornerShape(50)).background(back)
            .clickable(enabled = enabled, onClick = onClick).alpha(if (enabled) 1f else 0.4f).padding(horizontal = if (fill) 20.dp else 16.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp), tint = fg); Spacer(Modifier.width(8.dp)) }
        Text(text, style = bodyStyle(if (Z.big && fs.value <= 16f) (fs.value + 1).sp else fs, FontWeight.SemiBold).copy(color = fg, lineHeight = fs), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** .chip: etiqueta lima en mayúsculas (mid = oscura, hard = roja). */
@Composable
fun Chip(text: String, kind: String = "", size: TextUnit = 11.sp, modifier: Modifier = Modifier) {
    val c = Z.c
    val (bg, fg) = when (kind) { "mid" -> c.bar to c.onBar; "hard" -> c.danger to Color.White; else -> c.volt to c.onVolt }
    Text(text.uppercase(), modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = if (size.value < 11) 7.dp else 10.dp, vertical = if (size.value < 11) 2.dp else 4.dp),
        style = bodyStyle(size, FontWeight.Bold).copy(color = fg, letterSpacing = 0.06.em, lineHeight = (size.value * 1.3).sp), maxLines = 1)
}

/** .chipb: botón píldora de 38 (filtros). */
@Composable
fun ChipB(text: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, bg: Color? = null, fg: Color? = null, shadow: Boolean = true) {
    val c = Z.c
    Box(modifier.height(38.dp).then(if (shadow) Modifier.shadow(4.dp, RoundedCornerShape(50), ambientColor = Color(0x40000000), spotColor = Color(0x40000000)) else Modifier)
        .clip(RoundedCornerShape(50)).background(bg ?: if (on) c.bar else c.surface).clickable(onClick = onClick).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        Text(text, style = bodyStyle(if (Z.big) 14.sp else 13.sp, FontWeight.SemiBold).copy(color = fg ?: if (on) c.onBar else c.ink), maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
/** Fila de chips con desplazamiento horizontal (margen negativo como en la web). */
@Composable
fun Chips(modifier: Modifier = Modifier, wrap: Boolean = false, content: @Composable () -> Unit) {
    if (wrap) FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    else Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/** .seg: control segmentado. */
@Composable
fun Seg(options: List<Pair<String, String>>, sel: String, onSel: (String) -> Unit, modifier: Modifier = Modifier, dark: Boolean = Z.dark, bg: Color? = null, h: Dp = 36.dp, pad: Dp = 4.dp, fill: Boolean = true) {
    val c = Z.c
    Row(modifier.then(if (fill) Modifier.fillMaxWidth() else Modifier).clip(RoundedCornerShape(50)).background(bg ?: if (dark) Color(0x14F4F5F0) else c.surface2).padding(pad)) {
        options.forEach { (k, label) ->
            val on = k == sel
            Box((if (fill) Modifier.weight(1f) else Modifier).height(h).clip(RoundedCornerShape(50))
                .background(if (on) (if (dark) c.volt else c.bar) else Color.Transparent).clickable { onSel(k) }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text(label, style = bodyStyle(13.sp, if (on) FontWeight.SemiBold else FontWeight.Medium).copy(color = if (on) (if (dark) c.onVolt else c.onBar) else if (dark) c.onBarMuted else c.muted), maxLines = 1)
            }
        }
    }
}

/** .track: barra de progreso de 6. */
@Composable
fun Track(frac: Double, modifier: Modifier = Modifier, h: Dp = 6.dp, bg: Color? = null, color: Color = Z.c.volt) {
    Box(modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(h / 2)).background(bg ?: if (Z.dark) Color(0x24F4F5F0) else Z.c.line)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(frac.coerceIn(0.0, 1.0).toFloat()).clip(RoundedCornerShape(h / 2)).background(color))
    }
}

@Composable
fun Divider(color: Color = if (Z.dark) Color(0x1FF4F5F0) else Z.c.line) = Box(Modifier.fillMaxWidth().height(1.dp).background(color))

/** .icon: botón redondo de 44 con icono. */
@Composable
fun IconBtn(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp, bg: Color = Z.c.surface, tint: Color = Z.c.ink, iconSize: Dp = 22.dp) {
    Box(modifier.size(size).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(iconSize), tint = tint) }
}

/** .opt: opción grande de los asistentes (oscuros). */
@Composable
fun Opt(n: String, title: String, sub: String, on: Boolean, onClick: () -> Unit) {
    val c = Z.c
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (on) Color(0x14D4FF3A) else Color(0x14F4F5F0))
        .border(1.5.dp, if (on) c.volt else Color.Transparent, RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(n, Modifier.width(34.dp), style = numStyle(28.sp, 0.0).copy(color = c.volt))
        Column { Txt(title, size = 16.sp, weight = FontWeight.SemiBold, color = c.onBar, lineHeight = 1.25); Txt(sub, size = 13.sp, color = c.onBarMuted, lineHeight = 1.3) }
    }
}

/** .dots: pasos del asistente. */
@Composable
fun Dots(n: Int, i: Int) = Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    repeat(n) { k -> Box(Modifier.size(18.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(if (k == i) Z.c.volt else Color(0x33F4F5F0))) }
}

/** .setrow con un desplegable (.sel). */
@Composable
fun SelRow(label: String, options: List<Pair<String, String>>, value: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val c = Z.c
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Txt(label, Modifier.weight(1f), size = 15.sp, weight = FontWeight.Medium)
        Box {
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(c.surface2).clickable { open = true }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Txt((options.firstOrNull { it.first == value }?.second ?: options.first().second) + "  ▾", size = 14.sp, weight = FontWeight.SemiBold)
            }
            DropdownMenu(open, { open = false }, Modifier.background(c.surface)) {
                options.forEach { (k, l) -> DropdownMenuItem(text = { Txt(l, size = 15.sp, weight = if (k == value) FontWeight.SemiBold else FontWeight.Normal) }, onClick = { open = false; onChange(k) }) }
            }
        }
    }
}

/** Campo de texto con el estilo de la web (fondo surface2, borde fino). */
@Composable
fun Field(
    value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, h: Dp = 44.dp, size: TextUnit = 15.sp,
    numeric: Boolean = false, decimal: Boolean = false, maxLen: Int = 60, dark: Boolean = false, singleLine: Boolean = true, onDone: (() -> Unit)? = null,
    align: TextAlign = TextAlign.Start, radius: Dp = 12.dp,
) {
    val c = Z.c
    val fg = if (dark) c.onBar else c.ink
    BasicTextField(
        value, { onChange(it.take(maxLen)) }, modifier.then(if (singleLine) Modifier.height(h) else Modifier.heightIn(min = h)),
        textStyle = bodyStyle(size, if (dark) FontWeight.SemiBold else FontWeight.Normal).copy(color = fg, textAlign = align), singleLine = singleLine,
        cursorBrush = SolidColor(if (dark) c.volt else c.ink),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else if (numeric) KeyboardType.Number else KeyboardType.Text, imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().then(if (singleLine) Modifier.fillMaxHeight() else Modifier.heightIn(min = h)).clip(RoundedCornerShape(radius))
                .background(if (dark) Color.Transparent else c.surface2).border(if (dark) 1.5.dp else 1.dp, if (dark) Color(0x29F4F5F0) else c.line, RoundedCornerShape(radius))
                .padding(horizontal = if (dark) 18.dp else 12.dp, vertical = if (singleLine) 0.dp else 10.dp), contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart) {
                if (value.isEmpty()) Text(placeholder, Modifier.fillMaxWidth(), style = bodyStyle(size, if (dark) FontWeight.SemiBold else FontWeight.Normal).copy(color = if (dark) c.onBarMuted else c.muted, textAlign = align), maxLines = 1)
                inner()
            }
        },
    )
}

@Composable
fun Underline(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: TextUnit = 13.sp, color: Color = Z.muted) =
    Text(text, modifier.clickable(onClick = onClick).padding(vertical = 4.dp), style = bodyStyle(size, FontWeight.Medium).copy(color = color, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline))

/** .rmini: miniatura cuadrada con el trazado. */
@Composable
fun RouteThumb(ll: List<List<Double>>, modifier: Modifier = Modifier, w: Dp = 84.dp, h: Dp = 84.dp, bg: Color = Z.c.surface2, stroke: Color = Z.text) {
    Box(modifier.size(w, h).clip(RoundedCornerShape(18.dp)).background(bg), contentAlignment = Alignment.Center) { RouteSvg(ll, Modifier.size(w, h), stroke) }
}

/** routeSvg de la web: trazado sin mapa, con la salida en lima. */
@Composable
fun RouteSvg(ll: List<List<Double>>, modifier: Modifier, stroke: Color) {
    if (ll.size < 2) return
    val pts = remember(ll) { val step = max(1, ll.size / 160); ll.filterIndexed { i, _ -> i % step == 0 || i == ll.size - 1 } }
    Canvas(modifier) {
        val k = cos(Math.toRadians(ll[0][0]))
        val xs = pts.map { it[1] * k }; val ys = pts.map { -it[0] }
        val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
        val span = max(maxX - minX, maxY - minY).let { if (it == 0.0) 1e-6 else it }
        val pad = 8.dp.toPx(); val s = (min(size.width, size.height) - pad * 2) / span
        val ox = (size.width - (maxX - minX) * s) / 2; val oy = (size.height - (maxY - minY) * s) / 2
        fun P(i: Int) = Offset(((xs[i] - minX) * s + ox).toFloat(), ((ys[i] - minY) * s + oy).toFloat())
        val path = Path().apply { pts.indices.forEach { i -> val p = P(i); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
        drawPath(path, stroke, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(Volt, 3.5.dp.toPx(), P(0)); drawCircle(stroke, 3.5.dp.toPx(), P(0), style = Stroke(1.5.dp.toPx()))
    }
}

/** thumbSvg de la web: trazado de una carrera (tinta con alma lima), para listas y la última carrera. */
@Composable
fun RunThumb(pts: List<LatLon>, modifier: Modifier, strokeDp: Float = 5f, crop: Boolean = true) {
    if (pts.size < 2) return
    Canvas(modifier) {
        val lats = pts.map { it.lat }; val lons = pts.map { it.lon }
        val minLa = lats.min(); val maxLa = lats.max(); val minLo = lons.min(); val maxLo = lons.max()
        val cosLat = cos(Math.toRadians((minLa + maxLa) / 2))
        val w = ((maxLo - minLo) * cosLat).let { if (it == 0.0) 1e-6 else it }; val h = (maxLa - minLa).let { if (it == 0.0) 1e-6 else it }
        // viewBox 100×60 como la web; "slice" (recorta) o "none" (estira) según [crop]
        val vbW = 100.0; val vbH = 60.0
        val sc = min(84 / w, 48 / h); val ox = (vbW - w * sc) / 2; val oy = (vbH - h * sc) / 2
        val sx: Double; val sy: Double; val tx: Double; val ty: Double
        if (crop) { val s = max(size.width / vbW, size.height / vbH); sx = s; sy = s; tx = (size.width - vbW * s) / 2; ty = (size.height - vbH * s) / 2 }
        else { sx = size.width / vbW; sy = size.height / vbH; tx = 0.0; ty = 0.0 }
        fun P(p: LatLon) = Offset((tx + (ox + (p.lon - minLo) * cosLat * sc) * sx).toFloat(), (ty + (vbH - (oy + (p.lat - minLa) * sc)) * sy).toFloat())
        val path = Path().apply { pts.forEachIndexed { i, p -> val o = P(p); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
        val sw = strokeDp.dp.toPx() / 2.2f * 1.0f
        drawPath(path, Ink, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, Volt, style = Stroke(sw * 0.42f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val a = P(pts.first()); val b = P(pts.last())
        drawCircle(Color.White, 3.dp.toPx(), a); drawCircle(Ink, 3.dp.toPx(), a, style = Stroke(2.dp.toPx()))
        drawCircle(Volt, 3.6.dp.toPx(), b); drawCircle(Ink, 3.6.dp.toPx(), b, style = Stroke(2.dp.toPx()))
    }
}

/** Perfil de desnivel relleno (profileSvg de la web). */
@Composable
fun ElevProfile(ele: List<Int>, modifier: Modifier = Modifier.fillMaxWidth().height(90.dp), line: Color = Z.text) {
    if (ele.size < 4) return
    val e = remember(ele) { val step = max(1, ele.size / 200); ele.filterIndexed { i, _ -> i % step == 0 } }
    Canvas(modifier) {
        val mn = e.min(); val mx = e.max(); val span = max(mx - mn, 12).toFloat()
        val w = size.width; val h = size.height
        fun pt(i: Int) = Offset(i.toFloat() / (e.size - 1) * w, h - 6.dp.toPx() * (h / 90.dp.toPx()) - (e[i] - mn) / span * (h - 14.dp.toPx() * (h / 90.dp.toPx())))
        val fill = Path().apply { moveTo(0f, h); e.indices.forEach { val p = pt(it); lineTo(p.x, p.y) }; lineTo(w, h); close() }
        drawPath(fill, Volt.copy(alpha = 0.55f))
        val l = Path().apply { e.indices.forEach { val p = pt(it); if (it == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
        drawPath(l, line, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
    }
}

// ---------- iconos (los mismos trazos SVG de la web) ----------

private fun icon(vararg d: String, fill: Boolean = false, stroke: Float = 2f): ImageVector {
    val b = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    d.forEach { p ->
        if (fill) b.addPath(addPathNodes(p), fill = SolidColor(Color.Black))
        else b.addPath(addPathNodes(p), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = stroke, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }
    return b.build()
}
private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

object Ico {
    val home = icon("M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8", "M3 10a2 2 0 0 1 .7-1.5l7-6a2 2 0 0 1 2.6 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z")
    val history = icon("M3 3v18h18", "M18 17V9", "M13 17V5", "M8 17v-3")
    val routes = icon("M4 19c4-1 5-6 8-7s5 2 8-5", circle(4f, 19f, 2f), circle(20f, 7f, 2f))
    val profile = icon("M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2", circle(12f, 7f, 4f))
    val play = icon("M7 4.5v15a1 1 0 0 0 1.5.86l12-7.5a1 1 0 0 0 0-1.72l-12-7.5A1 1 0 0 0 7 4.5Z", fill = true)
    val pause = icon("M7 4h2a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1Z", "M15 4h2a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1h-2a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1Z", fill = true)
    val restart = icon("M6 5h2v14H6zM19 6.5v11a1 1 0 0 1-1.55.83l-8-5.5a1 1 0 0 1 0-1.66l8-5.5A1 1 0 0 1 19 6.5Z", fill = true)
    val close = icon("M6 6l12 12M18 6 6 18")
    val back = icon("M15 5l-7 7 7 7")
    val back2 = icon("M15 18l-6-6 6-6")
    val next = icon("M9 5l7 7-7 7")
    val chevron = icon("m9 18 6-6-6-6")
    val lock = icon("M7 11h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2Z", "M8 11V8a4 4 0 0 1 8 0v3")
    val share = icon("M12 15V4M7 9l5-5 5 5M5 14v5a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-5")
    val save = icon("M6 3h12v18l-6-4-6 4z")
    val saved = icon("M6 3h12v18l-6-4-6 4z", fill = true)
    val pencil = icon("M4 20h4l10.5-10.5a2 2 0 0 0 0-2.8l-1.2-1.2a2 2 0 0 0-2.8 0L4 16v4Z")
    val arrow = icon("M5 12h14M13 6l6 6-6 6", stroke = 2.6f)
    val check = icon("M5 12l5 5L20 7", stroke = 3f)
    val navArrow = icon("M12 3l7 18-7-4-7 4z", fill = true)
    val plan = icon("M4 19h16M6 15l4-5 4 3 4-7")
    val layers = icon("M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83Z", "m22 17.65-9.17 4.16a2 2 0 0 1-1.66 0L2 17.65", "m22 12.65-9.17 4.16a2 2 0 0 1-1.66 0L2 12.65")
    val expand = icon("M15 3h6v6", "M9 21H3v-6", "M21 3l-7 7", "M3 21l7-7")
    val shrink = icon("M4 14h6v6", "M20 10h-6V4", "M14 10l7-7", "M3 21l7-7")
    val sliders = icon("M4 21v-7", "M4 10V3", "M12 21v-9", "M12 8V3", "M20 21v-5", "M20 12V3", "M2 14h4", "M10 8h4", "M18 16h4")
    val heart = icon("M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z")
    val pin = icon("M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0", circle(12f, 10f, 3f))
    val sound = icon("M11 5 6 9H2v6h4l5 4z", "M15.54 8.46a5 5 0 0 1 0 7.07", "M19.07 4.93a10 10 0 0 1 0 14.14")
    val mic = icon("M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z", "M19 10v2a7 7 0 0 1-14 0v-2", "M12 19v3")
}
