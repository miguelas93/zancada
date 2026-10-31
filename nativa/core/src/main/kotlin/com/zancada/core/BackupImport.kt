package com.zancada.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.math.abs

/**
 * Leer la copia que hace la web (Perfil → «Exportar copia», archivo zancada-copia-AAAA-MM-DD.json)
 * y decidir qué carreras faltan. Mismas reglas que «Importar copia» de la web:
 * una carrera ya existe si tiene el mismo id o empezó a menos de 1 minuto de otra.
 */
object BackupImport {
    const val RUNS = "zancada.runs.v1"

    class NotABackup(msg: String) : IllegalArgumentException(msg)

    fun root(text: String): JsonObject {
        val o = try { Store.json.parseToJsonElement(text) as? JsonObject } catch (e: Exception) { null }
            ?: throw NotABackup("El archivo no es una copia de Zancada")
        if ((o["app"] as? JsonPrimitive)?.contentOrNull != "zancada") throw NotABackup("El archivo no es una copia de Zancada")
        return o
    }

    /** Las carreras de la copia. Las que vengan rotas se saltan (no tumban la importación). */
    fun parse(text: String): Pair<List<Run>, Int> = Store.decodeRuns(root(text)[RUNS] as? JsonArray)

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
