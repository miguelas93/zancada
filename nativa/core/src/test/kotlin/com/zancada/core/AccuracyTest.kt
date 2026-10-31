package com.zancada.core

import kotlinx.serialization.json.*
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Precisión frente a la realidad (no frente a la web): las carreras de prueba se generan con un
 * recorrido conocido (tests/tools/make-tracks.mjs), así que sabemos cuánto se corrió de verdad y
 * cuánto se subió. La app nativa corrige los fallos de la beta y tiene que acercarse más que ella.
 */
class AccuracyTest {
    private val dir = File(System.getProperty("zancada.tests") ?: "../../tests")
    private val parity = RunSessionParityTest()

    // metros reales recorridos (lo andado en la pausa manual no cuenta) y desnivel real
    private val truth = mapOf("parque-5k" to 5030.0, "arboles-ruido" to 3200.0, "pausa-manual" to 3600.0 - 42.0, "semaforo-autopausa" to 2500.0,
        "pantalla-bloqueada" to 4000.0, "cuestas" to 4000.0, "media-maraton" to 21150.0)
    private val gainTruth = mapOf("cuestas" to 60, "parque-5k" to 24, "media-maraton" to 50)

    private fun run(name: String, mode: RunSession.Mode, step: Double = 8.0): Run? {
        val track = Json.parseToJsonElement(File(dir, "fixtures/tracks/$name.json").readText()).jsonObject
        return (parity.replay(track, mode = mode, minStep = step).first as? RunSession.Result.Saved)?.run
    }

    @Test
    fun distanciaYDesnivelMasCercaDeLaRealidad() {
        val out = StringBuilder("pista                 real     beta    nativa   (error beta → nativa)\n")
        var worst = 0.0
        for ((name, real) in truth) {
            val web = run(name, RunSession.Mode.WEB)!!; val nat = run(name, RunSession.Mode.FIXED)!!
            val eW = (web.dist - real) / real * 100; val eN = (nat.dist - real) / real * 100
            worst = maxOf(worst, abs(eN))
            out.append("%-20s %7.0f %8.0f %8.0f   %+.1f %% → %+.1f %%  desnivel %s → %s (real %s)\n".format(name, real, web.dist, nat.dist, eW, eN, web.gain, nat.gain, gainTruth[name] ?: "?"))
            assertTrue(abs(eN) <= abs(eW) + 0.3, "$name: la nativa no debe medir peor que la beta ($eN % vs $eW %)")
            assertTrue(abs(eN) < 2.0, "$name: error de distancia ${"%.1f".format(eN)} %")
            gainTruth[name]?.let { g -> assertTrue(abs((nat.gain ?: 0) - g) <= maxOf(6.0, g * 0.25), "$name: desnivel ${nat.gain} m, real $g m") }
        }
        println(out)
        println("peor error de distancia en la nativa: %.2f %%".format(worst))
    }

    @Test
    fun terminarEnPausaNoDescuentaLaPausaDosVeces() {
        val s = RunSession(autopause = false)
        val t0 = 1_000_000L
        s.onFix(Fix(40.0, -3.0, 5.0, null, t0), t0); s.begin(t0)
        var lat = 40.0
        for (i in 1..120) { lat += 3.0 / 111320; s.onFix(Fix(lat, -3.0, 5.0, null, t0 + i * 1000L), t0 + i * 1000L) }
        s.pause(t0 + 120_000)
        val r = (s.finish(t0 + 180_000) as RunSession.Result.Saved).run
        assertTrue(abs(r.dur - 120.0) < 1.5, "duración ${r.dur} s, debería ser 120 s")
    }
}
