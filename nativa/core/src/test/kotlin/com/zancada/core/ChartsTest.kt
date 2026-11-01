package com.zancada.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChartsTest {
    // 3 km hacia el norte a 3:20/km, un punto cada 10 m (2 s), con altitud que sube 1 m cada 100 m.
    private val pts = (0..300).map { i -> Point(40.0 + i * 10 / 111_195.0, -3.7, i * 2000L, a = 600 + i / 10) }

    @Test
    fun ritmoConstanteDaLaMismaLinea() {
        val s = paceSeries(pts)
        assertTrue(s.size in 13..16, "ventanas de 200 m: ${s.size}")
        s.y.forEach { assertEquals(200.0, it, 3.0) }
        assertEquals(3.0, s.x.last(), 0.25)
    }

    @Test
    fun pausasNoCuentan() {
        val withGap = pts.mapIndexed { i, p -> if (i == 150) p.copy(gap = true, t = p.t + 600_000) else if (i > 150) p.copy(t = p.t + 600_000) else p }
        paceSeries(withGap).y.forEach { assertEquals(200.0, it, 3.0) }
    }

    @Test
    fun desnivelYPulso() {
        val e = elevSeries(pts); assertEquals(e.x.size, e.y.size); assertTrue(e.y.last() > e.y.first())
        val h = hrSeries((0..20).map { listOf(it * 5, 150) } + listOf(listOf(105, 10)))
        assertEquals(21, h.size); assertEquals(150.0, h.avg(), 0.01)
        assertEquals(0, hrSeries(listOf(listOf(0, 150))).size)
        assertEquals(10, h.nearest(0.8))
    }
}
