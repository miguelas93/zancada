package com.zancada.app

import android.content.Context
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

    fun load(): List<Run> = try {
        if (!file.exists()) emptyList() else JSONArray(file.readText()).let { a -> (0 until a.length()).map { fromJson(a.getJSONObject(it)) } }
    } catch (e: Exception) { emptyList() }

    fun add(run: Run) {
        val all = listOf(run) + load()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(JSONArray(all.map { toJson(it) }).toString())
        tmp.renameTo(file)   // escritura atómica: si se corta a medias, no se pierde lo anterior
    }

    companion object {
        fun toJson(r: Run): JSONObject = JSONObject().apply {
            put("id", r.id); put("start", r.start); put("name", r.name); put("dist", r.dist); put("dur", r.dur)
            if (r.gain != null) put("gain", r.gain)
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
            )
        }
    }
}
