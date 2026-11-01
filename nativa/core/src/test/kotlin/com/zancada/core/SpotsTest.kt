package com.zancada.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpotsTest {
    private fun way(id: Long, name: String, pts: List<Pair<Double, Double>>, tags: String = "\"leisure\":\"park\"") =
        "{\"type\":\"way\",\"id\":$id,\"tags\":{$tags,\"name\":\"$name\"},\"geometry\":[" + pts.joinToString(",") { "{\"lat\":${it.first},\"lon\":${it.second}}" } + "]}"
    private fun rect(la: Double, lo: Double, dla: Double, dlo: Double) = listOf(la to lo, la to lo + dlo, la + dla to lo + dlo, la + dla to lo, la to lo)

    @Test
    fun juntaTrozosConElMismoNombreYDetectaLaRibera() {
        // Tres trozos alargados de «Madrid Río» seguidos (≈ 0,9 km cada uno, 60 m de ancho) y un parque cuadrado.
        val parts = (0..2).map { k -> way(10L + k, if (k == 1) "Parque de Madrid Río" else "Madrid Río", rect(40.400, -3.720 + k * 0.0106, 0.00055, 0.0105)) }
        val retiro = way(99, "Parque del Retiro", rect(40.410, -3.690, 0.012, 0.014))
        val json = "{\"elements\":[" + (parts + retiro).joinToString(",") + "]}"
        val s = parseSpots(json, LatLon(40.405, -3.705))
        assertEquals(2, s.size)
        val rio = s.first { it.name.contains("Madrid Río") }
        assertEquals(3, rio.rings.size); assertEquals("rio", rio.kind); assertTrue(rio.lengthM in 2500.0..3000.0, "${rio.lengthM}")
        val ret = s.first { it.name == "Parque del Retiro" }
        assertEquals("parque", ret.kind); assertTrue(ret.areaM2 in 1_400_000.0..1_700_000.0, "${ret.areaM2}")
        assertTrue(ret.contains(LatLon(40.416, -3.683)))
        assertTrue(spotWorkouts(rio, 330.0).any { it.title.contains("ida y vuelta") })
        assertTrue(spotWorkouts(ret, 330.0).any { it.title.contains("vueltas") })
    }

    @Test
    fun uneLosTrozosDeUnaRelacion() {
        val a = listOf(LatLon(0.0, 0.0), LatLon(0.0, 1.0)); val b = listOf(LatLon(1.0, 1.0), LatLon(0.0, 1.0)); val c = listOf(LatLon(1.0, 1.0), LatLon(1.0, 0.0), LatLon(0.0, 0.0))
        val r = joinRings(listOf(a, b, c))
        assertEquals(1, r.size); assertEquals(4, r[0].size)
    }

    @Test
    fun laRutaPasaPorDentroDelParqueYSeComprueba() {
        val park = Spot("1", "Parque", "parque", listOf(rect(40.410, -3.690, 0.012, 0.014).map { LatLon(it.first, it.second) }.dropLast(1)), 1_500_000.0)
        val here = LatLon(40.405, -3.683)
        val spec = AskSpec(km = 6.0, want = setOf("verde"))
        val plans = askPlans(here, spec, listOf(park), emptyList())
        assertEquals("Por Parque", plans.first().label)
        // Enrutador de mentira: une los puntos en línea recta.
        val fake: (List<LatLon>) -> Routed = { pts -> Routed(pts.map { listOf(it.lat, it.lon) }, pts.zipWithNext().sumOf { (x, y) -> hav(x, y) } / 1000) }
        val r = routePlan(plans.first(), spec.km, fake)
        assertTrue(kotlin.math.abs(r.km - 6.0) / 6.0 < 0.12, "${r.km}")
        val dense = r.ll.zipWithNext().flatMap { (x, y) -> (0 until 20).map { t -> listOf(x[0] + (y[0] - x[0]) * t / 20, x[1] + (y[1] - x[1]) * t / 20) } }
        val ok = checkAsk(dense, r.km, 8, spec, emptyList(), listOf(park))
        assertTrue(ok.ok, ok.miss.toString()); assertTrue(ok.why.startsWith("Por Parque"), ok.why)
        val bad = checkAsk(dense, 9.5, 120, spec.copy(flat = true), emptyList(), emptyList())
        assertTrue(!bad.ok && bad.miss.size == 3, bad.miss.toString())
    }

    @Test
    fun reconoceElSitioNombrado() {
        val rio = Spot("1", "Madrid Río", "rio", listOf(listOf(LatLon(40.4, -3.72), LatLon(40.4, -3.69), LatLon(40.4006, -3.69), LatLon(40.4006, -3.72))), 180_000.0)
        val ret = Spot("2", "Parque del Retiro", "parque", listOf(listOf(LatLon(40.41, -3.69), LatLon(40.41, -3.676), LatLon(40.422, -3.676), LatLon(40.422, -3.69))), 1_500_000.0)
        assertEquals("Madrid Río", matchPlace("10 km por madrid rio", listOf(rio, ret))?.name)
        assertEquals("Parque del Retiro", matchPlace("5 km en el Retiro", listOf(rio, ret))?.name)
        val p = askPlans(LatLon(40.405, -3.70), AskSpec(km = 8.0), listOf(rio, ret), emptyList(), place = rio)
        assertEquals("Por Madrid Río", p.first().label)
    }
}
