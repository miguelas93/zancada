package com.zancada.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

const val COACH_URL = "https://text.pollinations.ai/openai"
const val COACH_URL2 = "https://text.pollinations.ai/"

val COACH_QS = listOf(
    "Semana que viene" to "¿Cómo afronto la semana que viene?",
    "¿Voy bien de carga?" to "¿Estoy entrenando demasiado o demasiado poco?",
    "Me duele algo" to "Me molesta algo al correr. ¿Qué hago?",
    "Series para el próximo día" to "Diseña mi próxima sesión de series con calentamiento y vuelta a la calma.",
    "Estrategia de carrera" to "¿Qué tiempo realista puedo hacer en mi carrera y cómo la corro?",
    "Comida" to "¿Qué como antes y después de correr?",
)

/** Lo que Zeta sabe del corredor. */
fun App.coachCtx(): String {
    val lines = mutableListOf<String>()
    lines.add("Nombre: ${profile.name.ifEmpty { "sin nombre" }}. Ritmo habitual: ${pace(userPace())}/km.")
    plan?.let { p ->
        val g = GOALS[p.goal]!!; val wi = curWeek(p); val st = planStatus(p)
        lines.add("Plan: ${g.name} en ${p.weeks} semanas, va por la semana ${wi + 1}, corre ${p.days.size} días por semana, nivel ${mapOf("inicio" to "principiante", "medio" to "intermedio", "avanzado" to "avanzado")[p.level] ?: p.level}.")
        // La web comparaba con 'done' (estado que no existe) y decía "pendiente" de lo ya hecho: arreglado.
        val doneState = if (webCompat) "done" else "ok"
        val week = (st.getOrNull(wi) ?: emptyList()).joinToString("; ") { x -> "${x.title} ${fmtKmS(x.m)} km (${if (x.state == doneState) "hecha" else if (x.state == "miss") "no hecha" else "pendiente"})" }
        if (week.isNotEmpty()) lines.add("Esta semana: $week.")
        p.lastReview?.let { lines.add("Última revisión del plan: " + it.text) }
    }
    val last = runs.take(6).joinToString("; ") { r -> "${es.numeric(r.start)}: ${fmtKm(r.dist)} km en ${fmtTime(r.dur)} (${pace(r.paceSec())}/km)${r.session?.let { ", " + it.title } ?: ""}${r.feel?.let { ", sensación $it/5" } ?: ""}${if (!webCompat && r.cad != null) ", cadencia ${r.cad} ppm" else ""}" }
    lines.add("Últimas carreras: " + last.ifEmpty { "ninguna todavía" } + ".")
    val recs = personalRecords().filter { it.run != null }.joinToString(", ") { "${it.label}: ${it.value}" }
    if (recs.isNotEmpty()) lines.add("Récords: $recs.")
    if (!webCompat) loadWarn()?.let { lines.add("Aviso de carga: " + it.text) }
    lines.add("Hoy es " + es.wdLongDayMonLong(now()) + ".")
    return lines.joinToString("\n")
}

fun App.coachSys() = "Eres Zeta, el entrenador de running de la app Zancada. Hablas en español de España, de tú, cercano y motivador pero honesto. Respuestas cortas (máximo 130 palabras), concretas y accionables: ritmos en min/km, distancias en km. Sin tablas ni títulos; como mucho una lista corta con guiones. Usa los datos del corredor. Si menciona dolor fuerte, punzante, que cojea o que no mejora, dile que pare y consulte a un profesional sanitario. No inventes datos que no tienes.\n\nDatos del corredor:\n" + coachCtx()

/** Quita los bloques de patrocinio que añade el servicio gratuito. */
fun coachClean(t: String?): String = (t ?: "")
    .replace(Regex("\\n+-{3,}[\\s\\S]*?(sponsor|pollinations|🌸)[\\s\\S]*$", RegexOption.IGNORE_CASE), "")
    .replace(Regex("\\n.*🌸.*$", RegexOption.DOT_MATCHES_ALL), "").trim()

/** Cuerpo de la petición (formato OpenAI). */
fun App.coachBody(history: List<CoachMsg>): String = buildJsonObject {
    put("model", "openai")
    put("messages", buildJsonArray {
        add(buildJsonObject { put("role", "system"); put("content", coachSys()) })
        history.filter { !it.err }.takeLast(12).forEach { m -> add(buildJsonObject { put("role", m.role); put("content", m.content) }) }
    })
    put("private", true); put("referrer", "zancada")
}.toString()

fun coachAnswer(text: String): String? = runCatching {
    val j = Json.parseToJsonElement(text) as JsonObject
    (((j["choices"] as JsonArray)[0] as JsonObject)["message"] as JsonObject)["content"].let { (it as JsonPrimitive).contentOrNull }
}.getOrNull()?.let { coachClean(it) }?.takeIf { it.isNotEmpty() }

/** Trozos de texto de Zeta: negritas con **…**, viñetas y párrafos. */
data class Rich(val text: String, val bold: Boolean)
fun coachRich(t: String): List<Rich> {
    val s = t.replace(Regex("^#{1,4}\\s*", RegexOption.MULTILINE), "").replace(Regex("^\\s*[-*•]\\s+", RegexOption.MULTILINE), "• ").replace(Regex("\\n{2,}"), "\n\n")
    val out = mutableListOf<Rich>(); var last = 0
    for (m in Regex("\\*\\*(.+?)\\*\\*").findAll(s)) { if (m.range.first > last) out.add(Rich(s.substring(last, m.range.first), false)); out.add(Rich(m.groupValues[1], true)); last = m.range.last + 1 }
    if (last < s.length) out.add(Rich(s.substring(last), false))
    return out
}
