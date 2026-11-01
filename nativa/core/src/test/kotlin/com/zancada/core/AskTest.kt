package com.zancada.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AskTest {
    @Test
    fun entiendeLaFraseSinIa() {
        val s = askRules("Quiero 8 km llanos, con sombra, pasando por el río y acabando cerca de una fuente")
        assertEquals(8.0, s.km); assertTrue(s.flat); assertTrue(!s.hills)
        assertEquals(setOf("verde", "agua", "fuente"), s.want)
        val t = askRules("45 minutos con cuestas hacia el norte", paceSec = 300.0)
        assertEquals(9.0, t.km, 0.01); assertTrue(t.hills); assertEquals(0.0, t.dir)
        assertEquals(5.0, askRules("algo tranquilo").km)
    }

    @Test
    fun leeLaRespuestaDeLaIaYSiNoUsaLasReglas() {
        val fb = askRules("6 km por el río")
        val s = parseAskSpec("Aquí tienes: {\"km\": 6.5, \"llano\": true, \"cuestas\": false, \"verde\": true, \"agua\": false, \"fuente\": false, \"direccion\": \"Sur\"}", fb)
        assertNotNull(s); assertEquals(6.5, s.km); assertTrue(s.flat)
        assertEquals(setOf("verde", "agua"), s.want)          // el río de las reglas se mantiene
        assertEquals(180.0, s.dir)
        assertNull(parseAskSpec("no sé", fb))
    }

    @Test
    fun puntuaMejorElBucleQuePasaPorElParqueYLaFuente() {
        val park = AskFeat("verde", box = doubleArrayOf(40.410, -3.690, 40.420, -3.680))
        val fountain = AskFeat("fuente", listOf(LatLon(40.415, -3.685)))
        val spec = AskSpec(km = 3.0, want = setOf("verde", "fuente"))
        val inPark = (0..60).map { i -> listOf(40.411 + i * 0.00013, -3.685) }
        val street = (0..60).map { i -> listOf(40.430 + i * 0.00013, -3.700) }
        val a = scoreAsk(inPark, 3.0, 10, spec, listOf(park, fountain))
        val b = scoreAsk(street, 3.0, 10, spec, listOf(park, fountain))
        assertTrue(a.score > b.score + 2, "${a.score} vs ${b.score}")
        assertTrue(a.why.contains("zonas verdes") && a.why.contains("fuente en el km"), a.why)
    }

    @Test
    fun rumbosHaciaLosSitiosYRepartidos() {
        val start = LatLon(40.4, -3.7)
        val east = AskFeat("verde", box = doubleArrayOf(40.399, -3.688, 40.401, -3.684))   // ~1,2 km al este
        val b = askBearings(start, AskSpec(km = 8.0, want = setOf("verde")), listOf(east), n = 5)
        assertEquals(5, b.size); assertTrue(b.first() in 80.0..100.0, b.toString())
        assertTrue(b.zipWithNext().all { (x, y) -> x != y })
    }
}
