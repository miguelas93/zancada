package com.zancada.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.units.Length
import com.zancada.core.Run
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Health Connect (Google): cada carrera se guarda como ejercicio «correr» con su distancia,
 * y desde ahí llega a Google Fit, Samsung Health, Fitbit… Solo escribe; no lee nada de tu salud.
 */
object Hc {
    val PERMS = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
    )
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun available(ctx: Context) = runCatching { HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_AVAILABLE }.getOrDefault(false)

    suspend fun granted(ctx: Context): Boolean = runCatching {
        HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions().containsAll(PERMS)
    }.getOrDefault(false)

    /** Envía las carreras que aún no estén en Health Connect (se apunta cuáles se mandaron para no repetir). */
    fun push(ctx: Context, all: Boolean = false, done: ((Int) -> Unit)? = null) {
        val app = ctx.applicationContext
        if (Repo.data.value.settings.hc != "1" && !all) return
        io.launch {
            var n = 0
            runCatching {
                if (!available(app) || !granted(app)) return@runCatching
                val prefs = app.getSharedPreferences("hc", Context.MODE_PRIVATE)
                val sent = prefs.getStringSet("ids", emptySet())!!.toMutableSet()
                val since = System.currentTimeMillis() - 30L * 86_400_000   // al activar, solo el último mes (Health Connect limita lo antiguo)
                val todo = Repo.data.value.runs.filter { it.id !in sent && (all || it.start >= since) && it.dur > 0 }
                val client = HealthConnectClient.getOrCreate(app)
                todo.chunked(50).forEach { chunk ->
                    client.insertRecords(chunk.flatMap { records(it) })
                    chunk.forEach { sent += it.id }; n += chunk.size
                    prefs.edit().putStringSet("ids", sent.toSet()).apply()
                }
            }
            done?.invoke(n)
        }
    }

    private fun records(r: Run): List<androidx.health.connect.client.records.Record> {
        val s = Instant.ofEpochMilli(r.start); val e = Instant.ofEpochMilli(r.start + (r.dur * 1000).toLong().coerceAtLeast(1000))
        val zs = ZoneId.systemDefault().rules.getOffset(s); val ze = ZoneId.systemDefault().rules.getOffset(e)
        return listOf(
            ExerciseSessionRecord(startTime = s, startZoneOffset = zs, endTime = e, endZoneOffset = ze,
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, title = r.name, notes = r.note),
            DistanceRecord(startTime = s, startZoneOffset = zs, endTime = e, endZoneOffset = ze, distance = Length.meters(r.dist)),
        )
    }
}

/** Pantalla que Health Connect enseña cuando el usuario pregunta para qué usa Zancada sus permisos. */
class HcRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        setContent {
            Box(Modifier.fillMaxSize().background(DayColors.bg)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).systemBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    H1("Zancada y Health Connect")
                    Sub("Zancada solo ESCRIBE en Health Connect: guarda cada carrera como ejercicio de correr, con su hora, duración y distancia, para que la veas en Google Fit, Samsung Health u otras apps.")
                    Sub("No lee ningún dato de tu salud, no envía nada a internet y no comparte tus datos con nadie. Puedes desactivarlo cuando quieras en Perfil › Health Connect o en los ajustes de Health Connect.")
                }
            }
        }
    }
}
