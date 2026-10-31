package com.zancada.core

import kotlin.math.abs

/**
 * Leer la copia que hace la app actual (Perfil → «Exportar copia», archivo zancada-copia-AAAA-MM-DD.json)
 * y decidir qué carreras faltan en la nativa. Mismas reglas que «Importar copia» de la web:
 * una carrera ya existe si tiene el mismo id o empezó a menos de 1 minuto de otra.
 */
object BackupImport {
    const val RUNS = "zancada.runs.v1"

    class NotABackup(msg: String) : IllegalArgumentException(msg)

    /** Las carreras de la copia. Las que vengan rotas se saltan (no tumban la importación). */
    fun parse(text: String): Pair<List<Run>, Int> {
        val o = try { MiniJson.parse(text) as? Map<*, *> } catch (e: Exception) { null }
            ?: throw NotABackup("El archivo no es una copia de Zancada")
        if (o["app"] != "zancada") throw NotABackup("El archivo no es una copia de Zancada")
        val list = o[RUNS] as? List<*> ?: return emptyList<Run>() to 0
        var bad = 0
        val runs = list.mapNotNull { m -> (m as? Map<*, *>)?.let { runCatching { runOf(it) }.getOrNull() } ?: run { bad++; null } }
        return runs to bad
    }

    fun runOf(m: Map<*, *>): Run {
        fun num(k: String, mm: Map<*, *> = m) = (mm[k] as Number).toDouble()
        val start = (m["start"] as Number).toLong()
        val dist = num("dist"); val dur = num("dur")
        require(dist.isFinite() && dist > 0 && dur.isFinite() && dur > 0) { "carrera sin distancia o tiempo" }
        val pts = (m["points"] as? List<*> ?: emptyList<Any>()).map { it as Map<*, *> }.map { p ->
            Point(num("lat", p), num("lon", p), (p["t"] as Number).toLong(), (p["a"] as? Number)?.let { jsRound(it.toDouble()).toInt() }, p["gap"] == true, (p["d"] as? Number)?.toDouble() ?: 0.0)
        }
        val splits = (m["splits"] as? List<*> ?: emptyList<Any>()).map { it as Map<*, *> }.map { s -> Split((s["km"] as Number).toInt(), num("split", s), s["partial"] == true) }
        return Run(
            id = (m["id"] as? String)?.takeIf { it.isNotBlank() } ?: "r$start", start = start,
            name = safeText(m["name"] as? String ?: runName(start)).ifEmpty { runName(start) },
            points = pts, dist = dist, dur = dur, splits = splits, gain = (m["gain"] as? Number)?.toInt(), manual = m["manual"] == true,
        )
    }

    /** De [incoming], las que no están ya en [existing] (ni repetidas dentro de la propia copia). */
    fun pickNew(existing: List<Run>, incoming: List<Run>): List<Run> {
        val have = existing.toMutableList()
        val out = mutableListOf<Run>()
        for (r in incoming) {
            if (have.any { it.id == r.id || abs(it.start - r.start) < 60_000 }) continue
            have += r; out += r
        }
        return out
    }
}

/** Igual que safeText de la web: quita < y >, recorta espacios y largo. */
fun safeText(t: String?, n: Int = 60) = (t ?: "").replace(Regex("[<>]"), "").trim().take(n)
