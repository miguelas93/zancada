package com.zancada.app

import com.zancada.core.Run
import kotlinx.coroutines.flow.MutableStateFlow

enum class Phase { IDLE, PREP, RUNNING, DONE }

/** Lo que la pantalla necesita saber de la carrera. Lo publica [RunService]. */
data class RunUi(
    val phase: Phase = Phase.IDLE,
    val acc: Double? = null,
    val gpsError: String? = null,
    val dist: Double = 0.0,
    val elapsed: Double = 0.0,
    val pace: Double = Double.NaN,
    val avg: Double = Double.NaN,
    val bestKm: Double = Double.POSITIVE_INFINITY,
    val paused: Boolean = false,
    val autoPaused: Boolean = false,
    val tooShort: Double? = null,
    val result: Run? = null,
)

object RunBus {
    val state = MutableStateFlow(RunUi())
}
