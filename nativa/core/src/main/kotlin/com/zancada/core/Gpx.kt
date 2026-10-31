package com.zancada.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Lectura de GPX sin parser XML (así no hay ataques de entidades externas): basta con los trkpt/rtept,
 * sus atributos lat/lon y sus hijos time/ele, y el primer <name> del archivo.
 */
object GpxText {
    private val ENT = Regex("&(#x[0-9a-fA-F]+|#\\d+|amp|lt|gt|quot|apos);")
    fun decode(s: String): String = ENT.replace(s) { m ->
        when (val e = m.groupValues[1]) {
            "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"
            else -> runCatching { String(Character.toChars(if (e.startsWith("#x")) e.drop(2).toInt(16) else e.drop(1).toInt())) }.getOrDefault("")
        }
    }
    /** textContent de un trozo de XML: sin etiquetas, CDATA desenvuelto y entidades decodificadas. */
    fun text(xml: String): String {
        val cdata = Regex("<!\\[CDATA\\[([\\s\\S]*?)]]>")
        val parts = StringBuilder(); var last = 0
        for (m in cdata.findAll(xml)) { parts.append(decode(xml.substring(last, m.range.first).replace(Regex("<[^>]*>"), ""))); parts.append(m.groupValues[1]); last = m.range.last + 1 }
        parts.append(decode(xml.substring(last).replace(Regex("<[^>]*>"), "")))
        return parts.toString()
    }
    fun attr(attrs: String, name: String): String? = Regex("\\b$name\\s*=\\s*([\"'])(.*?)\\1").find(attrs)?.groupValues?.get(2)
    fun child(inner: String, tag: String): String? = Regex("<$tag\\b[^>]*>([\\s\\S]*?)</$tag>").find(inner)?.groupValues?.get(1)
    fun firstName(xml: String): String? = child(xml, "name")?.let { text(it) }
    data class El(val attrs: String, val inner: String)
    fun elements(xml: String, tag: String): List<El> = Regex("<$tag\\b([^>]*?)(?:/>|>([\\s\\S]*?)</$tag>)").findAll(xml).map { El(it.groupValues[1], it.groupValues[2]) }.toList()
    /** parseFloat de JavaScript: el número del principio del texto. */
    fun num(s: String?): Double? = s?.trim()?.let { Regex("^[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?").find(it)?.value?.toDoubleOrNull() }
    fun time(s: String, zone: ZoneId): Long? {
        val t = s.trim()
        return runCatching { Instant.parse(t).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(t).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(t).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
    }
    fun looksLikeXml(t: String) = t.trimStart().startsWith("<")
}

/** Carrera desde un GPX con horas (exportación de Strava, Garmin…). Null si no hay recorrido. */
fun App.parseGpx(text: String): Run? {
    if (!GpxText.looksLikeXml(text)) return null
    val tp = GpxText.elements(text, "trkpt"); if (tp.size < 2) return null
    val pts = mutableListOf<Point>(); var t0: Long? = null
    for (el in tp) {
        val ts = GpxText.child(el.inner, "time")?.let { GpxText.time(GpxText.text(it), zone) } ?: continue
        if (t0 == null) t0 = ts
        val lat = GpxText.num(GpxText.attr(el.attrs, "lat")) ?: Double.NaN; val lon = GpxText.num(GpxText.attr(el.attrs, "lon")) ?: Double.NaN
        val a = GpxText.child(el.inner, "ele")?.let { GpxText.num(GpxText.text(it)) }?.takeIf { it.isFinite() }?.let { jsRoundI(it) }
        pts.add(Point(lat, lon, ts - t0, a))
    }
    if (pts.size < 2 || t0 == null) return null
    val r = finalizeRun("g$t0", t0, runName(t0, zone), pts, 0.0, gainFn = if (webCompat) ::elevGain else ::elevGainSmooth)
    if (!(r.dist > 50)) return null
    val nm = GpxText.firstName(text)?.trim()
    return r.copy(name = if (!nm.isNullOrEmpty()) safeText(nm, 40) else r.name, imported = true)
}

/** Ruta desde un GPX (rte o trk, sin necesidad de horas) → Mis recorridos. */
fun App.parseRouteGpx(text: String): Route? {
    if (!GpxText.looksLikeXml(text)) return null
    val els = GpxText.elements(text, "rtept").let { if (it.size >= 2) it else GpxText.elements(text, "trkpt") }
    if (els.size < 2) return null
    val ll = mutableListOf<List<Double>>(); val ele = mutableListOf<Int?>()
    for (el in els) {
        val la = GpxText.num(GpxText.attr(el.attrs, "lat")); val lo = GpxText.num(GpxText.attr(el.attrs, "lon"))
        if (la == null || lo == null || !la.isFinite() || !lo.isFinite()) continue
        ll.add(listOf(la, lo))
        ele.add(GpxText.child(el.inner, "ele")?.let { GpxText.num(GpxText.text(it)) }?.let { jsRoundI(it) })
    }
    if (ll.size < 2) return null
    var m = 0.0; for (i in 1 until ll.size) m += haversine(ll[i - 1][0], ll[i - 1][1], ll[i][0], ll[i][1])
    val hasEle = ele.count { it != null } > ll.size * 0.8
    val gain = if (hasEle) (if (webCompat) elevGainOf(ele) else elevGainSmooth(ele.mapIndexed { i, a -> Point(0.0, 0.0, i * 5000L, a) })) else null
    val name = GpxText.firstName(text)?.let { safeText(it, 40) }?.takeIf { it.isNotEmpty() } ?: "Ruta importada"
    return Route("gpx-" + now(), name, ll, jsRound(m / 100) / 10.0, gain, if (hasEle) ele else null, now(), LatLon(ll[0][0], ll[0][1]), true)
}

/** GPX de una carrera (para Strava, Garmin…). */
fun runGpx(r: Run): String {
    val pts = r.points.joinToString("") { p -> "<trkpt lat=\"${toFixed(p.lat, 6)}\" lon=\"${toFixed(p.lon, 6)}\"><time>${isoMs(r.start + p.t)}</time></trkpt>" }
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"Zancada\" xmlns=\"http://www.topografix.com/GPX/1/1\"><metadata><name>${xmlEsc(r.name)}</name><time>${isoMs(r.start)}</time></metadata><trk><name>${xmlEsc(r.name)}</name><type>running</type><trkseg>$pts</trkseg></trk></gpx>"
}

/** Como toISOString() de JavaScript: siempre con milisegundos. */
fun isoMs(ts: Long): String = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(java.time.ZoneOffset.UTC).format(Instant.ofEpochMilli(ts))

/** CSV para hojas de cálculo (separador ;, con BOM para Excel). Fecha y hora locales. */
fun App.runsCsv(): String {
    fun q(v: Any?) = "\"" + (v?.toString() ?: "").replace("\"", "\"\"") + "\""
    val head = listOf("fecha", "hora", "nombre", "km", "tiempo", "ritmo_min_km", "desnivel_m", "sensacion", "ruta", "sesion", "notas")
    val rows = runs.reversed().map { r ->
        val d = es.z(r.start)
        listOf("%04d-%02d-%02d".format(d.year, d.monthValue, d.dayOfMonth), es.hm(r.start), r.name, toFixed(r.dist / 1000, 2), fmtTime(r.dur), pace(r.paceSec()),
            r.gain ?: "", r.feel?.let { FEEL_L.getOrNull(it) } ?: "", r.route?.name ?: "", r.session?.title ?: r.goal ?: "", r.note ?: "")
    }
    return "\uFEFF" + (listOf(head) + rows).joinToString("\r\n") { row -> row.joinToString(";") { q(it) } }
}

/** Carrera de ejemplo (un bucle por el Retiro). */
fun App.demoRun(): Run {
    val pts = mutableListOf<Point>(); val start = now() - 3600_000; val lat0 = 40.4152; val lon0 = -3.6845; var t = 0.0
    for (i in 0 until 400) {
        val a = i / 400.0 * Math.PI * 2
        val lat = lat0 + Math.sin(a) * 0.006 + Math.sin(a * 3) * 0.0012; val lon = lon0 + Math.cos(a) * 0.008 + Math.cos(a * 2) * 0.0015
        t += 4.3 + Math.sin(i / 20.0) * 0.6; pts.add(Point(lat, lon, jsRound(t * 1000)))
    }
    return finalizeRun("demo-$start", start, runName(start, zone), pts, 0.0)
}
