package com.zancada.app

import android.Manifest
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
}

class MainActivity : ComponentActivity() {
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
        setContent {
            val data by Repo.data.collectAsState()
            val minute by Repo.minute.collectAsState()
            val sysDark = isSystemInDarkTheme()
            val darkBars = Nav.cur.id in DARK_SCREENS || St.modal == "photo" || remember(minute, data.settings, sysDark) { Repo.isNight(data.settings, sysDark) }
            LaunchedEffect(darkBars) {
                WindowCompat.getInsetsController(window, window.decorView).apply { isAppearanceLightStatusBars = !darkBars; isAppearanceLightNavigationBars = !darkBars }
            }
            LaunchedEffect(Unit) {
                delay(700)
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
            ZRoot()
        }
    }

    override fun onDestroy() { if (Perm.act === this) Perm.act = null; super.onDestroy() }
}
