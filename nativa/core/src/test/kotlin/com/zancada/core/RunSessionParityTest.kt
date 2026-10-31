package com.zancada.core

import kotlinx.serialization.json.*
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.fail

/**
 * Paridad con la web: corre las mismas carreras de ../tests/fixtures/tracks con el mismo calendario
 * que tests/run-tests.mjs (lecturas, pausas y el tic de 500 ms) y exige los mismos resultados que
 * ../tests/golden: km, tiempo, puntos, desnivel, parciales, mejores marcas y textos de la pantalla final.
 */
class RunSessionParityTest {
    private val dir = File(System.getProperty("zancada.tests") ?: "../../tests")
    private val T0 = java.time.Instant.parse("2026-10-20T08:00:00Z").toEpochMilli()

    /** Reproduce el guion de tests/run-tests.mjs con un reloj falso. */
    private fun replay(track: JsonObject): Pair<RunSession.Result, RunSession> {
        val fixes = track["fixes"]!!.jsonArray.map { it.jsonObject }
        val events = track["events"]!!.jsonArray.map { it.jsonObject }.toMutableList()
        val end = track["end"]!!.jsonPrimitive.long
        val s = RunSession(autopause = true)
        fun fixOf(o: JsonObject, ts: Long) = Fix(o["lat"]!!.jsonPrimitive.double, o["lon"]!!.jsonPrimitive.double, o["acc"]!!.jsonPrimitive.double, o["alt"]?.jsonPrimitive?.doubleOrNull, ts)
        // Preparación: la página lleva 1,5 s abierta; primera lectura con hora T0; 0,5 s después, «Empezar»; cuenta atrás de 3 s.
        var clock = T0 + 1500
        s.onFix(fixOf(fixes[0], T0), clock)
        clock += 500 + 3000
        s.begin(clock)
        val runStart = clock
        var nextTick = runStart + 500
        var now = 0L
        fun advance(to: Long) { if (to > now) { while (nextTick <= runStart + to) { s.tick(nextTick); nextTick += 500 }; now = to } }
        for (f in fixes) {
            val arrive = f["arrive"]!!.jsonPrimitive.long
            while (events.isNotEmpty() && events[0]["at"]!!.jsonPrimitive.long <= arrive) {
                val e = events.removeAt(0); val at = e["at"]!!.jsonPrimitive.long
                advance(at)
                if (e["ev"]!!.jsonPrimitive.content == "pause") s.pause(runStart + now) else s.resume(runStart + now)
            }
            advance(arrive)
            s.onFix(fixOf(f, runStart + f["t"]!!.jsonPrimitive.long), runStart + now)
        }
        advance(end)
        return s.finish(runStart + now) to s
    }

    private val errors = mutableListOf<String>()
    private fun near(name: String, got: Double?, want: JsonElement?) {
        val w = (want as? JsonPrimitive)?.doubleOrNull
        if (w == null && (got == null || !got.isFinite())) return
        if (w == null || got == null || abs(got - w) > 0.011) errors += "$name: esperaba $w, sale $got"
    }
    private fun same(name: String, got: Any?, want: Any?) { if (got != want) errors += "$name: esperaba $want, sale $got" }

    @Test
    fun lasMismasCifrasQueLaWeb() {
        val tracks = File(dir, "fixtures/tracks").listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }
        check(tracks.size >= 8) { "No encuentro las carreras de prueba en $dir" }
        for (file in tracks) {
            val name = file.nameWithoutExtension
            val before = errors.size
            val track = Json.parseToJsonElement(file.readText()).jsonObject
            val want = Json.parseToJsonElement(File(dir, "golden/$name.json").readText()).jsonObject["expected"]!!.jsonObject
            val (res, _) = replay(track)
            val saved = res is RunSession.Result.Saved
            same("$name.saved", saved, want["saved"]!!.jsonPrimitive.boolean)
            if (saved) {
                val r = (res as RunSession.Result.Saved).run
                near("$name.dist", r.dist, want["dist"]); near("$name.dur", r.dur, want["dur"])
                same("$name.nPoints", r.points.size, want["nPoints"]!!.jsonPrimitive.int)
                same("$name.gain", r.gain, want["gain"]?.jsonPrimitive?.intOrNull)
                val ws = want["splits"]!!.jsonArray
                same("$name.splits.size", r.splits.size, ws.size)
                r.splits.zip(ws).forEachIndexed { i, (g, w) ->
                    val wo = w.jsonObject
                    same("$name.splits.$i.km", g.km, wo["km"]!!.jsonPrimitive.int); near("$name.splits.$i.split", g.split, wo["split"])
                    same("$name.splits.$i.partial", g.partial, wo["partial"]?.jsonPrimitive?.boolean ?: false)
                }
                listOf("1000" to 1000.0, "5000" to 5000.0, "10000" to 10000.0, "21097.5" to 21097.5).forEach { (k, m) -> near("$name.best.$k", bestEffort(r, m), want["best"]!!.jsonObject[k]) }
                listOf(1000, 5000, 10000).forEach { m -> near("$name.bestFor.$m", bestFor(r, m), want["bestFor"]!!.jsonObject["$m"]) }
                val cele = want["cele"]!!.jsonObject
                same("$name.cele.dist", fmtKm(r.dist), cele["dist"]!!.jsonPrimitive.content)
                same("$name.cele.time", fmtTime(r.dur), cele["time"]!!.jsonPrimitive.content)
                same("$name.cele.pace", fmtPace(r.dur / (r.dist / 1000)), cele["pace"]!!.jsonPrimitive.content)
                println("${if (errors.size == before) "✓" else "✗"} ${name.padEnd(20)} ${fmtKm(r.dist)} km · ${fmtTime(r.dur)} · ${fmtPace(r.dur / (r.dist / 1000))}/km")
            } else println("${if (errors.size == before) "✓" else "✗"} ${name.padEnd(20)} no se guarda")
        }
        if (errors.isNotEmpty()) fail("La versión Kotlin no da lo mismo que la web:\n" + errors.joinToString("\n"))
    }
}
