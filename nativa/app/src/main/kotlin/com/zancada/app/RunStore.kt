package com.zancada.app

import android.content.Context
import com.zancada.core.BackupImport
import com.zancada.core.Point
import com.zancada.core.Run
import com.zancada.core.Split
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Carreras guardadas en el propio móvil (archivo privado de la app, nadie más puede leerlo).
 * Mismo formato que la web (zancada.runs.v1) para poder migrar y exportar sin conversiones.
 */
class RunStore(context: Context) {
    private val file = File(context.filesDir, "runs.v1.json")

    /** El archivo tal cual: se guardan los objetos completos para no perder campos que la nativa aún no usa (ruta, zapatilla…). */
    private fun raw(): List<JSONObject> = try {
        if (!file.exists()) emptyList() else JSONArray(file.readText()).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    } catch (e: Exception) { emptyList() }

    fun load(): List<Run> = raw().mapNotNull { runCatching { fromJson(it) }.getOrNull() }

    fun add(run: Run) = write(listOf(toJson(run)) + raw())

    /**
     * Importar la copia de la app actual. Devuelve cuántas se añaden y cuántas ya estaban.
     * El archivo entero se guarda aparte (backup-import.json) para traer zapatillas, plan y rutas en fases siguientes.
     */
    fun importBackup(text: String): Pair<Int, Int> {
        val (incoming, _) = BackupImport.parse(text)
        val fresh = BackupImport.pickNew(load(), incoming)
        val rawById = JSONObject(text).optJSONArray(BackupImport.RUNS)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.associateBy { it.optString("id") } } ?: emptyMap()
        val add = fresh.map { r -> (rawById[r.id] ?: toJson(r)).put("name", r.name) }   // el nombre ya limpio
        write((raw() + add).sortedByDescending { it.optLong("start") })
        File(file.parentFile, "backup-import.json").writeText(text)
        return fresh.size to (incoming.size - fresh.size)
    }

    private fun write(all: List<JSONObject>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(JSONArray(all).toString())
        tmp.renameTo(file)   // escritura atómica: si se corta a medias, no se pierde lo anterior
    }

    companion object {
        fun toJson(r: Run): JSONObject = JSONObject().apply {
            put("id", r.id); put("start", r.start); put("name", r.name); put("dist", r.dist); put("dur", r.dur)
            if (r.gain != null) put("gain", r.gain)
            if (r.manual) put("manual", true)
            put("splits", JSONArray(r.splits.map { s -> JSONObject().apply { put("km", s.km); put("split", s.split); if (s.partial) put("partial", true) } }))
            put("points", JSONArray(r.points.map { p -> JSONObject().apply { put("lat", p.lat); put("lon", p.lon); put("t", p.t); put("d", p.d); if (p.a != null) put("a", p.a); if (p.gap) put("gap", true) } }))
        }

        fun fromJson(o: JSONObject): Run {
            val pts = o.optJSONArray("points") ?: JSONArray()
            val sp = o.optJSONArray("splits") ?: JSONArray()
            return Run(
                id = o.getString("id"), start = o.getLong("start"), name = o.optString("name", "Carrera").replace(Regex("[<>]"), "").take(40),
                points = (0 until pts.length()).map { i -> pts.getJSONObject(i).let { p -> Point(p.getDouble("lat"), p.getDouble("lon"), p.getLong("t"), if (p.has("a")) p.getInt("a") else null, p.optBoolean("gap"), p.optDouble("d", 0.0)) } },
                dist = o.getDouble("dist"), dur = o.getDouble("dur"),
                splits = (0 until sp.length()).map { i -> sp.getJSONObject(i).let { s -> Split(s.getInt("km"), s.getDouble("split"), s.optBoolean("partial")) } },
                gain = if (o.has("gain") && !o.isNull("gain")) o.getInt("gain") else null,
                manual = o.optBoolean("manual"),
            )
        }
    }
}
