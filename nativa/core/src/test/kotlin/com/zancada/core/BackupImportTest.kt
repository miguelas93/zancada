package com.zancada.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Importa la copia REAL que genera la web (tests/run-tests.mjs la guarda en tests/fixtures/backup)
 * y comprueba que no se pierde ni se cambia nada: mismas carreras, mismos km, tiempos, parciales y récords.
 */
class BackupImportTest {
    private val dir = File(System.getProperty("zancada.tests") ?: "../../tests")
    private val text = File(dir, "fixtures/backup/zancada-copia.json").readText()

    @Test
    fun importaTodasLasCarrerasSinCambiarNada() {
        val (runs, bad) = BackupImport.parse(text)
        assertEquals(0, bad)
        assertEquals(8, runs.size, "carreras en la copia")
        val raw = (MiniJson.parse(text) as Map<*, *>)[BackupImport.RUNS] as List<*>
        runs.zip(raw).forEach { (r, m) ->
            m as Map<*, *>
            assertEquals(m["id"], r.id); assertEquals((m["start"] as Number).toLong(), r.start)
            assertEquals((m["dist"] as Number).toDouble(), r.dist); assertEquals((m["dur"] as Number).toDouble(), r.dur)
            assertEquals((m["points"] as List<*>).size, r.points.size, "${r.id} puntos")
            assertEquals((m["splits"] as List<*>).size, r.splits.size, "${r.id} parciales")
            assertEquals((m["gain"] as Number?)?.toInt(), r.gain, "${r.id} desnivel")
        }
        val manual = runs.single { it.manual }
        assertEquals("Cinta del gimnasio", manual.name); assertTrue(manual.points.isEmpty())
        // Recalcular la carrera con los puntos importados da lo mismo que tenía guardado: los puntos llegan intactos.
        runs.filter { !it.manual }.forEach { r ->
            val again = finalizeRun(r.id, r.start, r.name, r.points.map { it.copy() }, r.dur)
            assertTrue(kotlin.math.abs(again.dist - r.dist) < 0.01, "${r.id}: ${again.dist} vs ${r.dist}")
            assertEquals(fmtKm(r.dist), fmtKm(again.dist))
        }
    }

    @Test
    fun noDuplicaAlImportarDosVeces() {
        val (runs, _) = BackupImport.parse(text)
        val first = BackupImport.pickNew(emptyList(), runs)
        assertEquals(8, first.size)
        assertEquals(0, BackupImport.pickNew(first, runs).size, "segunda importación")
        // Una carrera grabada en la nativa 30 s después de una de la copia es la misma.
        val dup = runs[0].copy(id = "otra", start = runs[0].start + 30_000)
        assertEquals(7, BackupImport.pickNew(listOf(dup), runs).size)
    }

    @Test
    fun rechazaArchivosQueNoSonCopias() {
        assertFailsWith<BackupImport.NotABackup> { BackupImport.parse("hola") }
        assertFailsWith<BackupImport.NotABackup> { BackupImport.parse("""{"app":"strava","zancada.runs.v1":[]}""") }
        // Una carrera rota no tumba el resto; nombres con código se limpian.
        val (runs, bad) = BackupImport.parse("""{"app":"zancada","zancada.runs.v1":[{"id":"x"},{"id":"r1","start":1,"name":"<img src=x onerror=1>Mi carrera","dist":1000,"dur":300,"points":[],"splits":[]}]}""")
        assertEquals(1, bad); assertEquals(1, runs.size)
        assertEquals("img src=x onerror=1Mi carrera", runs[0].name)
    }
}
