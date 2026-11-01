package com.zancada.app

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import com.zancada.core.Badge
import com.zancada.core.LatLon
import com.zancada.core.LiveRun
import com.zancada.core.Route
import com.zancada.core.Run
import com.zancada.core.Sess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import java.util.Locale

enum class Phase { IDLE, PREP, RUNNING, DONE }

/** Lo que la pantalla necesita saber de la carrera. Lo publica [RunService]. */
data class RunUi(
    val phase: Phase = Phase.IDLE,
    val acc: Double? = null,
    val fix: LatLon? = null,
    val gpsError: String? = null,
    val dist: Double = 0.0,
    val elapsed: Double = 0.0,
    val pace: Double = Double.NaN,
    val avg: Double = Double.NaN,
    val bestKm: Double = Double.POSITIVE_INFINITY,
    val paused: Boolean = false,
    val autoPaused: Boolean = false,
    val tooShort: Double? = null,
    val live: LiveRun.State = LiveRun.State(),
    val lockLine: String = "",
    /** Pasos por minuto en los últimos 30 s (sensor de pasos); null si no hay sensor o permiso. */
    val cad: Int? = null,
    /** Pulso del pulsómetro Bluetooth; null si no hay o no llega señal. */
    val hr: Int? = null,
    /** Parciales cerrados (segundos por km) y ritmo del km en curso. */
    val splits: List<Double> = emptyList(),
    val kmPace: Double = Double.NaN,
)

/** Lo que se enseña al terminar (pantalla «Carrera terminada»). */
data class Cele(val run: Run, val badges: List<Badge>, val text: String, val week: String)

/** Con qué se sale a correr: sesión (plan, tramos u objetivo) y ruta. Se guarda para recuperar la carrera si Android cierra la app. */
@Serializable
data class RunConfig(val sess: Sess? = null, val route: Route? = null)


object RunBus {
    /** Ruta elegida en mitad de la carrera (la recoge el servicio con LOAD_ROUTE). */
    @Volatile var loadRoute: Route? = null
    val state = MutableStateFlow(RunUi())
    /** Trazado en directo para el mapa. */
    val track = MutableStateFlow<List<LatLon>>(emptyList())
    val cele = MutableStateFlow<Cele?>(null)
    /** Se fija antes de START. */
    @Volatile var config = RunConfig()
    /** Avisos de la carrera para la pantalla (salir de la ruta, ruta completada…). */
    val toast = MutableStateFlow<Pair<Long, String>?>(null)
}

/** Voz (TextToSpeech en español) y vibración, compartidas por la carrera, los estiramientos y la cinta. */
object Voice : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false
    private lateinit var ctx: Context
    private var pending: String? = null

    fun init(c: Context) { if (tts != null) return; ctx = c.applicationContext; tts = TextToSpeech(ctx, this) }
    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) { tts?.language = Locale.forLanguageTag("es-ES"); pending?.let { pending = null; say(it, false) } }
    }

    /** Como speak() de la web: sin voz si está desactivada; vibra si está activado y [buzz]. */
    fun say(text: String, buzz: Boolean = false) {
        if (text.isBlank()) return
        val s = Repo.data.value.settings
        if (buzz && (s.vibrate ?: "1") != "0") buzz()
        if (s.voice == "0") return
        if (!ready) { pending = text; return }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "z")
    }

    fun buzz(pattern: LongArray = longArrayOf(0, 80, 60, 80)) {
        if (!::ctx.isInitialized) return
        runCatching {
            val v = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(VibratorManager::class.java).defaultVibrator else @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
            v?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }
}
