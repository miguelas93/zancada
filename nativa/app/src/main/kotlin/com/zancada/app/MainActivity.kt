package com.zancada.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.zancada.core.NEWS_VER
import kotlinx.coroutines.delay

/** Permisos en tiempo de ejecución, pedidos solo cuando hacen falta (ubicación al ir a correr, avisos al activarlos). */
object Perm {
    internal var act: MainActivity? = null
    fun location(cb: (Boolean) -> Unit) = act?.ask(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), cb) ?: cb(false)
    fun notifications(cb: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < 33) { cb(true); return }
        act?.ask(arrayOf(Manifest.permission.POST_NOTIFICATIONS), cb) ?: cb(false)
    }
    /** Antes de empezar: notificación, sensor de pasos (cadencia) y SMS si el modo seguridad está activo. Lo que se niegue, simplemente no se usa. */
    fun runExtras(cb: () -> Unit) {
        val want = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) want += Manifest.permission.POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT >= 29) want += Manifest.permission.ACTIVITY_RECOGNITION
        if (Repo.data.value.settings.sosOn == "1") want += Manifest.permission.SEND_SMS
        val a = act ?: return cb()
        a.askMissing(want.toTypedArray()) { cb() }
    }
    fun sms(cb: (Boolean) -> Unit) = act?.ask(arrayOf(Manifest.permission.SEND_SMS), cb) ?: cb(false)
    /** Buscar y conectar el pulsómetro Bluetooth. */
    fun bt(cb: (Boolean) -> Unit) { val a = act ?: return cb(false); a.askMissing(HrBle.perms()) { cb(HrBle.allowed(a)) } }
}

class MainActivity : ComponentActivity() {
    /** Animación del logo al abrir la app (no al volver a una carrera en marcha). */
    private var intro by mutableStateOf(false)
    private var pending: ((Boolean) -> Unit)? = null
    private val askPerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val cb = pending; pending = null
        cb?.invoke(res.values.any { it })
    }

    fun ask(perms: Array<String>, cb: (Boolean) -> Unit) {
        if (perms.any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) { cb(true); return }
        pending = cb
        askPerms.launch(perms)
    }

    /** Pide solo los permisos que falten; [cb] siempre se llama. */
    fun askMissing(perms: Array<String>, cb: (Boolean) -> Unit) {
        val miss = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (miss.isEmpty()) { cb(true); return }
        pending = cb
        askPerms.launch(miss.toTypedArray())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Perm.act = this
        Repo.init(this); Voice.init(this); mapsInit(this)
        val d = Repo.data.value
        if (!d.profile.done) Nav.reset(Scr(WELCOME))
        // ¿Quedó una carrera a medias (Android cerró la app)? Se retoma sola y se vuelve a la pantalla de carrera.
        if (savedInstanceState == null && RunBus.state.value.phase == Phase.IDLE && LiveStore(this).load() != null &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) { RunService.send(this, RunService.RECOVER); Nav.reset(Scr(HOME), Scr(RUN)) }
        else if (RunBus.state.value.phase == Phase.RUNNING && Nav.cur.id != RUN) Nav.reset(Scr(HOME), Scr(RUN))
        Reminders.sync(this)
        handle(intent)
        intro = savedInstanceState == null && Nav.cur.id != RUN
        setContent {
            val data by Repo.data.collectAsState()
            val minute by Repo.minute.collectAsState()
            val sysDark = isSystemInDarkTheme()
            val darkBars = Nav.cur.id in DARK_SCREENS || St.modal == "photo" || remember(minute, data.settings, sysDark) { Repo.isNight(data.settings, sysDark) }
            LaunchedEffect(darkBars) {
                WindowCompat.getInsetsController(window, window.decorView).apply { isAppearanceLightStatusBars = !darkBars; isAppearanceLightNavigationBars = !darkBars }
            }
            LaunchedEffect(Unit) {
                delay(if (intro) INTRO_MS + 700L else 700L)
                val s = Repo.data.value
                if (s.profile.done && s.settings.newsSeen != NEWS_VER && St.modal == null && Nav.cur.id == HOME) St.modal = "news"
            }
            BackHandler {
                when (St.modal) {
                    null -> if (!Nav.back()) finish()
                    "short", "news" -> {}
                    else -> closeModal()
                }
            }
            Box {
                ZRoot()
                if (intro) IntroSplash { intro = false }
            }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handle(intent) }

    /** «Terminar» desde la notificación: abre la app en la carrera y la termina (sale la celebración o la pregunta de carrera corta). */
    private fun handle(i: Intent?) {
        if (i?.action == RunService.FINISH && RunBus.state.value.phase == Phase.RUNNING) {
            i.action = null
            if (Nav.cur.id != RUN) Nav.reset(Scr(HOME), Scr(RUN))
            RunService.send(this, RunService.FINISH)
        }
    }

    override fun onDestroy() { if (Perm.act === this) Perm.act = null; super.onDestroy() }
}
