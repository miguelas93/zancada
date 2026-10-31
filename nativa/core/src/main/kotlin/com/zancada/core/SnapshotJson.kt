package com.zancada.core

/**
 * Guardar y leer la foto de la carrera en curso como texto JSON, sin librerías
 * (formato propio y pequeño; solo números, booleanos y listas).
 */
object SnapshotJson {
    fun encode(s: RunSession.Snapshot): String = buildString {
        fun num(d: Double) = if (d.isFinite()) d.toString() else "null"
        fun fix(f: Fix?) = if (f == null) "null" else "[${f.lat},${f.lon},${f.acc},${f.alt ?: "null"},${f.fixTime},${f.arrival}]"
        append("{\"v\":1,\"active\":${s.active},\"paused\":${s.paused},\"auto\":${s.autoPaused},\"start\":${s.start},\"pausedMs\":${s.pausedMs},\"pauseAt\":${s.pauseAt}")
        append(",\"dist\":${num(s.dist)},\"lastKm\":${s.lastKm},\"bestKm\":${num(s.bestKm)},\"gap\":${s.gap},\"clk\":${s.clk ?: "null"}")
        append(",\"lastFix\":${fix(s.lastFix)},\"pausePos\":${fix(s.pausePos)}")
        append(",\"splits\":[" + s.liveSplits.indices.joinToString(",") { i -> "[${s.liveSplits[i].km},${s.liveSplits[i].split},${s.liveSplitTimes[i]}]" } + "]")
        append(",\"points\":[" + s.points.joinToString(",") { p -> "[${p.lat},${p.lon},${p.t},${p.a ?: "null"},${if (p.gap) 1 else 0},${p.d}]" } + "]}")
    }

    fun decode(text: String): RunSession.Snapshot {
        val o = MiniJson.parse(text) as Map<*, *>
        fun d(k: String) = (o[k] as Number?)?.toDouble()
        fun l(k: String) = (o[k] as Number?)?.toLong()
        fun b(k: String) = o[k] as Boolean
        fun fix(v: Any?): Fix? = (v as List<*>?)?.let { a -> Fix(n(a[0]), n(a[1]), n(a[2]), (a[3] as Number?)?.toDouble(), (a[4] as Number).toLong(), (a[5] as Number).toLong()) }
        val sp = (o["splits"] as List<*>).map { it as List<*> }
        return RunSession.Snapshot(
            b("active"), b("paused"), b("auto"), l("start")!!, l("pausedMs")!!, l("pauseAt")!!,
            d("dist") ?: 0.0, l("lastKm")!!.toInt(),
            (o["points"] as List<*>).map { it as List<*> }.map { a -> Point(n(a[0]), n(a[1]), (a[2] as Number).toLong(), (a[3] as Number?)?.toInt(), (a[4] as Number).toInt() == 1, n(a[5])) },
            sp.map { Split((it[0] as Number).toInt(), n(it[1])) }, sp.map { n(it[2]) },
            d("bestKm") ?: Double.POSITIVE_INFINITY, b("gap"), l("clk"), fix(o["lastFix"]), fix(o["pausePos"]),
        )
    }

    private fun n(v: Any?) = (v as Number).toDouble()
}

/** Lector JSON mínimo (objetos, listas, números, textos, true/false/null). */
object MiniJson {
    fun parse(s: String): Any? { val p = P(s); val v = p.value(); p.ws(); require(p.i == s.length) { "JSON con basura al final" }; return v }
    private class P(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            ws()
            return when (val c = s[i]) {
                '{' -> { i++; val m = LinkedHashMap<String, Any?>(); ws(); if (s[i] == '}') { i++; return m }
                    while (true) { ws(); val k = str(); ws(); require(s[i] == ':'); i++; m[k] = value(); ws(); if (s[i] == ',') { i++; continue }; require(s[i] == '}'); i++; return m } ; @Suppress("UNREACHABLE_CODE") m }
                '[' -> { i++; val a = ArrayList<Any?>(); ws(); if (s[i] == ']') { i++; return a }
                    while (true) { a.add(value()); ws(); if (s[i] == ',') { i++; continue }; require(s[i] == ']'); i++; return a } ; @Suppress("UNREACHABLE_CODE") a }
                '"' -> str()
                't' -> { require(s.startsWith("true", i)); i += 4; true }
                'f' -> { require(s.startsWith("false", i)); i += 5; false }
                'n' -> { require(s.startsWith("null", i)); i += 4; null }
                else -> { require(c == '-' || c.isDigit()) { "carácter inesperado '$c'" }; val st = i; i++; while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++; s.substring(st, i).toDouble() }
            }
        }
        fun str(): String {
            require(s[i] == '"'); i++; val b = StringBuilder()
            while (s[i] != '"') { if (s[i] == '\\') { i++; when (s[i]) { 'n' -> b.append('\n'); 't' -> b.append('\t'); 'r' -> b.append('\r'); 'b' -> b.append('\b'); 'f' -> b.append('\u000C'); 'u' -> { b.append(s.substring(i + 1, i + 5).toInt(16).toChar()); i += 4 }; else -> b.append(s[i]) } } else b.append(s[i]); i++ }
            i++; return b.toString()
        }
    }
}
