package com.zancada.core

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RacesTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private fun at(d: String) = LocalDate.parse(d).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
    private fun app(today: String, runs: List<Run> = emptyList()) = App(AppData(runs = runs), now = { at(today) }, zone = zone)

    @Test
    fun distanciasYObjetivos() {
        assertEquals("maraton", RACES.first { it.id == "sevilla-mar-27" }.goal())
        assertEquals("media", RACES.first { it.id == "behobia-26" }.goal())
        assertEquals("10k", RACES.first { it.id == "vallecana-26" }.goal())
        assertTrue(RACES.all { runCatching { LocalDate.parse(it.date) }.isSuccess })
        assertEquals(RACES.size, RACES.map { it.id }.toSet().size)
    }

    @Test
    fun planHastaLaCarreraConLaCarreraEseDia() {
        // Lunes 2 nov 2026 → Sevilla, domingo 21 feb 2027 (DST no cambia, pero el cálculo redondea igual).
        val a = app("2026-11-02")
        val r = RACES.first { it.id == "sevilla-mar-27" }
        assertNull(a.raceReady(r))
        assertEquals(16, a.weeksTo(r))
        val p = a.newRacePlan(r, listOf(1, 3, 5), "medio", false)
        assertEquals(16, p.weeks); assertEquals("maraton", p.goal)
        val last = planSessions(p).last()
        val race = last.last()
        assertEquals("carrera", race.type)
        assertEquals(r.day(zone), race.date)
        assertEquals(r.name, race.title)
        assertTrue(last.all { it.date <= race.date })
    }

    @Test
    fun carreraEnSabadoQuitaLoQueVieneDespues() {
        val a = app("2027-02-01")
        val r = Race("x", "Prueba", "Madrid", "2027-03-27", 10000.0)   // sábado
        val p = a.newRacePlan(r, listOf(0, 2, 6), "medio", false)
        val last = planSessions(p).last()
        assertEquals(r.day(zone), last.last().date)
        assertEquals(5, last.last().dayIdx)
    }

    @Test
    fun muyJustoOMuyLejos() {
        val r = RACES.first { it.id == "valencia-mar-26" }
        assertNotNull(app("2026-11-20").raceReady(r))           // 3 semanas para un maratón
        assertNotNull(app("2026-05-01").raceReady(r))           // más de 24 semanas
        assertNull(app("2026-09-01").raceReady(r))
    }

    @Test
    fun resumenDelMes() {
        val runs = listOf(Run("a", at("2026-10-03"), dist = 8000.0, dur = 2800.0), Run("b", at("2026-10-10"), dist = 12000.0, dur = 4300.0), Run("c", at("2026-09-20"), dist = 10000.0, dur = 3600.0))
        val a = app("2026-11-02", runs.sortedByDescending { it.start })
        val w = assertNotNull(a.wrapToShow())
        assertEquals(20.0, w.km, 1e-9); assertEquals(2, w.runs); assertEquals("b", w.longest?.id)
        assertTrue(w.verdict().contains("100 %"))
        assertNull(app("2026-11-15", runs).wrapToShow())
    }

    @Test
    fun consejoDeComidaSiempreHay() {
        assertTrue(app("2026-11-02").fuelTip(30).title.contains("calor"))
        assertTrue(app("2026-11-02").fuelTip(null).text.isNotEmpty())
    }
}
