package com.zancada.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class BestHour(val h: Int, val t: Long, val rain: Int)
data class Wx(val t: Long, val app: Long, val wind: Long, val rain: Int, val now: Boolean, val code: Int?, val sunrise: String?, val sunset: String?, val best: BestHour?)

private fun JsonElement?.arr(): List<Double?> = (this as? JsonArray)?.map { (it as? JsonPrimitive)?.doubleOrNull } ?: emptyList()
private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull

fun weatherUrl(lat: Double, lon: Double) = "https://api.open-meteo.com/v1/forecast?latitude=${toFixed(lat, 3)}&longitude=${toFixed(lon, 3)}&current=temperature_2m,apparent_temperature,precipitation,wind_speed_10m,weather_code&hourly=precipitation_probability,temperature_2m&daily=sunrise,sunset&forecast_days=1&timezone=auto"

/** Mejor hora para correr lo que queda de día (con luz): poca lluvia y temperatura cerca de 12 °C. */
fun bestHour(j: JsonObject, h: Int): BestHour? {
    val pp = j["hourly"].obj()?.get("precipitation_probability").arr(); val tt = j["hourly"].obj()?.get("temperature_2m").arr()
    fun hr(s: String?) = s?.let { if (it.length >= 13) it.substring(11, 13).toIntOrNull() else null }
    val sr = hr(j["daily"].obj()?.get("sunrise")?.let { (it as? JsonArray)?.getOrNull(0) }.str()) ?: 7
    val ss = hr(j["daily"].obj()?.get("sunset")?.let { (it as? JsonArray)?.getOrNull(0) }.str()) ?: 19
    fun sc(i: Int) = (pp.getOrNull(i) ?: 0.0) + abs((tt.getOrNull(i) ?: 12.0) - 12) * 3
    var best: Int? = null
    for (i in max(h + 1, sr)..min(22, ss - 1)) if (tt.getOrNull(i) != null && (best == null || sc(i) < sc(best))) best = i
    if (best == null || sc(best) + 8 >= sc(min(h, 23))) return null   // si ahora ya es buen momento, no lo decimos
    return BestHour(best, jsRound(tt[best]!!), (pp.getOrNull(best) ?: 0.0).toInt())
}

/** Respuesta de open-meteo → lo que enseña la app. */
fun parseWeather(text: String, hourNow: Int): Wx? = runCatching {
    val j = Json.parseToJsonElement(text).jsonObject
    val c = j["current"]!!.jsonObject
    fun n(k: String) = (c[k] as? JsonPrimitive)?.doubleOrNull ?: 0.0
    val pp = j["hourly"].obj()?.get("precipitation_probability").arr()
    Wx(jsRound(n("temperature_2m")), jsRound(n("apparent_temperature")), jsRound(n("wind_speed_10m")),
        max(pp.getOrNull(hourNow) ?: 0.0, pp.getOrNull(hourNow + 1) ?: 0.0).toInt(), n("precipitation") > 0, (c["weather_code"] as? JsonPrimitive)?.doubleOrNull?.toInt(),
        j["daily"].obj()?.get("sunrise")?.jsonArray?.getOrNull(0).str()?.takeIf { it.length >= 16 }?.substring(11, 16),
        j["daily"].obj()?.get("sunset")?.jsonArray?.getOrNull(0).str()?.takeIf { it.length >= 16 }?.substring(11, 16),
        bestHour(j, hourNow))
}.getOrNull()

/** "15 °C · viento 8 km/h · lluvia 10 %\nCamiseta técnica, perfecto para correr." */
fun wxText(w: Wx?): String {
    if (w == null) return ""
    val a = w.app
    val ropa = if (a < 5) "manga larga, mallas y guantes" else if (a < 12) "manga larga fina o camiseta con manguitos" else if (a < 19) "camiseta técnica, perfecto para correr" else if (a < 25) "camiseta ligera y lleva agua" else "calor: lo más fresco posible, agua y ritmo tranquilo"
    val extra = listOf(if (w.now) "está lloviendo: chubasquero" else if (w.rain >= 50) "lluvia probable (${w.rain} %): cortavientos" else "", if (w.wind >= 25) "viento fuerte (${w.wind} km/h)" else "").filter { it.isNotEmpty() }.joinToString(" · ")
    return "${w.t} °C${if (w.app != w.t) " (sensación ${w.app})" else ""} · viento ${w.wind} km/h · lluvia ${w.rain} %\n${cap(ropa)}${if (extra.isNotEmpty()) " · $extra" else ""}."
}
