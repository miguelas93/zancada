package com.zancada.app

import android.content.Context
import com.zancada.core.RunSession
import com.zancada.core.SnapshotJson
import java.io.File

/**
 * La carrera EN CURSO, guardada en disco cada pocos segundos. Si Android (o la batería de Xiaomi)
 * cierra la app a mitad de carrera, al volver se recupera desde aquí y sigue grabando.
 * Archivo privado de la app; se borra al guardar o descartar la carrera.
 */
class LiveStore(context: Context) {
    private val file = File(context.filesDir, "live.v1.json")

    fun exists() = file.exists()

    fun load(): RunSession.Snapshot? = try {
        if (file.exists()) SnapshotJson.decode(file.readText()).takeIf { it.active } else null
    } catch (e: Exception) { null }

    fun save(s: RunSession.Snapshot) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(SnapshotJson.encode(s))
        tmp.renameTo(file)   // atómico: o queda la foto anterior o la nueva, nunca media
    }

    fun clear() { file.delete() }
}
