package com.zancada.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.net.URLEncoder

// Fotos del sitio desde Wikimedia Commons: solo paisaje (fuera fauna, arte, edificios, interiores y detalles).
private val PH_BAD = Regex("\\b(aves?|p[aá]jaros?|birds?|passer|columba|turdus|parus|cyanistes|pica|sturnus|anas|gaviota|gull|garza|heron|insect[oa]s?|insects?|mariposas?|butterfl|lepidoptera|abejas?|bees?|hormiga|ara[ñn]a|spider|ardillas?|squirrel|perros?|dogs?|gatos?|cats?|caballo|horse|animal|fauna|reptil|lagart|setas?|hongos?|fungi|mushroom|flor(es)?|flowers?|insecto|hoja|leaf|leaves|semilla|fruto|fruit|corteza|bark|planta|plant|species|especie|escultura|sculpture|estatua|statue|busto|monumento|monument|placa|plaque|cartel|sign(age)?|se[ñn]al|logo|escudo|mapa|maps?|plano|diagram|cuadro|painting|pintura|art(e|work)?|museo|museum|exposici[oó]n|exhibition|graffiti|grafiti|mural|interior|iglesia|church|ermita|capilla|chapel|edificio|building|casas|houses?|palacio|palace|fachada|facade|ventana|window|puerta|door|tejado|roof|torre|tower|hotel|hospital|colegio|school|universidad|facultad|estaci[oó]n|station|metro|autob[uú]s|bus|tren|train|coches?|cars?|tr[aá]fico|traffic|retrato|portrait|personas?|people|gente|selfie|evento|event|concierto|fiesta|manifestaci|deporte|f[uú]tbol|football|partido|match|noche|night|close-?up|macro|detalle|detail|bandera|flag|fuente ornamental|lamp|farola|banco|bench|papelera|grave|tumba|cementerio|cemetery)\\b", RegexOption.IGNORE_CASE)
private val PH_GOOD = Regex("\\b(parque|park|jard[ií]n|garden|pinar|pinos?|pine|bosque|forest|wood(s|land)?|arbol(es|ado)|[aá]rboles|trees?|camino|path|sendero|trail|paseo|promenade|avenida arbolada|pradera|meadow|c[eé]sped|lawn|vista|view|panor[aá]mic|panorama|paisaje|landscape|skyline|r[ií]o|river|arroyo|stream|canal|lago|lake|estanque|pond|embalse|playa|beach|costa|coast|mar|sea|bah[ií]a|bay|marisma|salinas?|marsh|dunas?|monte|monta[ñn]a|mountain|sierra|cerro|hill|colina|valle|valley|oto[ñn]o|autumn|primavera|spring|invierno|nieve|snow|atardecer|sunset|amanecer|sunrise|pista|track|circuito|dehesa|retiro|casa de campo)\\b", RegexOption.IGNORE_CASE)
private const val PH_PROPS = "&prop=imageinfo%7Ccategories&cllimit=max&clshow=!hidden&iiprop=url%7Cmime%7Csize%7Cextmetadata&iiurlwidth=640&iiextmetadatafilter=Artist%7CLicenseShortName%7CImageDescription"
private const val PH_BASE = "https://commons.wikimedia.org/w/api.php?action=query&format=json&origin=*"

fun stripHtml(h: String?) = (h ?: "").replace(Regex("<[^>]*>"), "").replace(Regex("\\s+"), " ").trim()
fun photoPlace(r: Route) = r.name.split(" · ")[0].replace(Regex("\\s*\\(.*?\\)\\s*"), "").trim()

/** Las cuatro búsquedas: por nombre del sitio y alrededor de la salida y de dos puntos del recorrido. */
fun photoUrls(r: Route): List<String> {
    val place = photoPlace(r); val out = mutableListOf<String>()
    if (place.length > 3) out.add(PH_BASE + "&generator=search&gsrnamespace=6&gsrlimit=40&gsrsearch=" + URLEncoder.encode("\"$place\" filetype:bitmap", "UTF-8").replace("+", "%20") + PH_PROPS)
    val n = r.ll.size
    for (p in listOf(r.ll[0], r.ll[n / 3], r.ll[n * 2 / 3])) out.add(PH_BASE + "&generator=geosearch&ggsnamespace=6&ggslimit=30&ggsradius=500&ggscoord=" + toFixed(p[0], 5) + "%7C" + toFixed(p[1], 5) + PH_PROPS)
    return out
}

private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.o(k: String) = this[k] as? JsonObject

fun photoPages(text: String): List<JsonObject> = runCatching { ((Json.parseToJsonElement(text) as JsonObject).o("query")?.o("pages"))?.values?.mapNotNull { it as? JsonObject } }.getOrNull() ?: emptyList()

fun phScore(pg: JsonObject, place: String): Int {
    val ii = (pg["imageinfo"] as? JsonArray)?.getOrNull(0) as? JsonObject ?: return -99
    if (ii.s("thumburl") == null || !(ii.s("mime") ?: "").contains("jpeg")) return -99
    val w = (ii["width"] as? JsonPrimitive)?.intOrNull ?: 0; val h = (ii["height"] as? JsonPrimitive)?.doubleOrNull ?: 0.0
    if (w == 0 || w < 900 || w < h * 1.15) return -99
    val cats = ((pg["categories"] as? JsonArray) ?: JsonArray(emptyList())).mapNotNull { (it as? JsonObject)?.s("title") }.joinToString(" · ")
    val desc = stripHtml(ii.o("extmetadata")?.o("ImageDescription")?.s("value")).take(300)
    val title = pg.s("title") ?: ""
    val txt = "$title · $cats · $desc"
    if (PH_BAD.containsMatchIn(title) || PH_BAD.containsMatchIn(cats)) return -99
    var sc = 0
    if (PH_GOOD.containsMatchIn(txt)) sc += 3
    if (PH_GOOD.containsMatchIn(title)) sc += 1
    if (Regex("Quality images|Featured pictures|Valued images", RegexOption.IGNORE_CASE).containsMatchIn(cats)) sc += 2
    if (place.isNotEmpty() && txt.lowercase().contains(place.lowercase())) sc += 2
    return sc
}

/** Las 8 mejores fotos de todas las respuestas. */
fun pickPhotos(pages: List<JsonObject>, place: String): List<Photo> {
    val seen = mutableSetOf<String>()
    val scored = pages.mapNotNull { pg -> val t = pg.s("title") ?: return@mapNotNull null; if (!seen.add(t)) return@mapNotNull null; val sc = phScore(pg, place); if (sc >= 3) pg to sc else null }.sortedByDescending { it.second }
    return scored.take(8).map { (pg, _) ->
        val ii = (pg["imageinfo"] as JsonArray)[0] as JsonObject; val thumb = ii.s("thumburl")!!
        Photo(thumb, thumb.replace(Regex("/640px-"), "/1280px-"), stripHtml(ii.o("extmetadata")?.o("Artist")?.s("value")).take(60), ii.o("extmetadata")?.o("LicenseShortName")?.s("value") ?: "", ii.s("descriptionurl") ?: "")
    }
}

// ---------- fuentes, aseos y transporte (Overpass, OpenStreetMap) ----------

fun poiUrl(r: Route): String? {
    if (r.ll.size < 2) return null
    val la = r.ll.map { it[0] }; val lo = r.ll.map { it[1] }; val pad = 0.0015
    val bb = listOf(la.min() - pad, lo.min() - pad, la.max() + pad, lo.max() + pad).joinToString(",") { toFixed(it, 5) }
    val st = r.startLL()
    val q = "[out:json][timeout:15];(node[\"amenity\"=\"drinking_water\"]($bb);node[\"amenity\"=\"toilets\"]($bb);node(around:1200,${toFixed(st.lat, 5)},${toFixed(st.lon, 5)})[\"railway\"~\"^(station|halt)$\"];);out;"
    return "https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(q, "UTF-8").replace("+", "%20")
}

fun parsePois(text: String, r: Route, now: Long): PoiCache? = runCatching {
    val els = ((Json.parseToJsonElement(text) as JsonObject)["elements"] as? JsonArray ?: JsonArray(emptyList())).mapNotNull { it as? JsonObject }.filter { (it["lat"] as? JsonPrimitive)?.doubleOrNull != null }
    val step = maxOf(1, r.ll.size / 400); val samp = r.ll.filterIndexed { i, _ -> i % step == 0 }
    fun lat(e: JsonObject) = (e["lat"] as JsonPrimitive).doubleOrNull!!
    fun lon(e: JsonObject) = (e["lon"] as JsonPrimitive).doubleOrNull!!
    fun tags(e: JsonObject) = e.o("tags")
    val ps = els.filter { tags(it)?.s("amenity") != null }.map { Poi(if (tags(it)!!.s("amenity") == "toilets") "t" else "w", lat(it), lon(it)) }
        .filter { e -> samp.any { (a, b) -> haversine(a, b, e.lat, e.lon) < 80 } }.take(40)
    fun kind(t: JsonObject): String {
        val net = t.s("network") ?: ""; val op = t.s("operator") ?: ""
        return if (t.s("station") == "subway" || (Regex("metro", RegexOption.IGNORE_CASE).containsMatchIn(net) && !Regex("ligero", RegexOption.IGNORE_CASE).containsMatchIn(net))) "Metro"
        else if (t.s("station") == "light_rail" || Regex("ligero", RegexOption.IGNORE_CASE).containsMatchIn(net)) "Metro Ligero"
        else if (Regex("cercan", RegexOption.IGNORE_CASE).containsMatchIn(net + op) || t.s("train") == "yes") "Cercanías" else "Estación"
    }
    val st = r.startLL(); val seen = mutableSetOf<String>()
    val ss = els.filter { tags(it)?.s("railway") != null && tags(it)?.s("name") != null }.map { Station(tags(it)!!.s("name")!!, kind(tags(it)!!), jsRoundI(haversine(st.lat, st.lon, lat(it), lon(it)))) }
        .sortedBy { it.d }.filter { seen.add(it.k + it.n) }.take(2)
    PoiCache(now, ps, ss)
}.getOrNull()

fun transitText(ss: List<Station>) = "Transporte cerca de la salida: " + ss.joinToString(" · ") { x -> "${x.k} ${x.n} (${if (x.d < 1000) "a ${jsRound(x.d / 10.0) * 10} m" else "a ${toFixed(x.d / 1000.0, 1).replace('.', ',')} km"})" } + "."

fun poiText(ps: List<Poi>): String {
    val w = ps.count { it.k == "w" }; val t = ps.size - w
    return if (w > 0 || t > 0) "${if (w > 0) "$w ${pl(w, "fuente")}" else "Sin fuentes"}${if (t > 0) " y $t ${pl(t, "aseo")}" else ""} junto al recorrido, marcad${if (w + t == 1) "o" else "os"} en el mapa. Datos de OpenStreetMap."
    else "No hay fuentes registradas junto al recorrido: lleva agua si hace calor. Datos de OpenStreetMap."
}

// ---------- bucles desde aquí (Valhalla) ----------

const val ROUTER = "https://valhalla1.openstreetmap.de"
data class Routed(val ll: List<List<Double>>, val km: Double)

fun routeBody(pts: List<LatLon>) = "{\"locations\":[" + pts.joinToString(",") { "{\"lat\":${it.lat},\"lon\":${it.lon},\"type\":\"break\"}" } + "],\"costing\":\"pedestrian\",\"directions_type\":\"none\",\"units\":\"kilometers\"}"

fun parseRouted(text: String): Routed {
    val trip = (Json.parseToJsonElement(text) as JsonObject).o("trip")!!
    val ll = (trip["legs"] as JsonArray).flatMap { decodePoly6(((it as JsonObject)["shape"] as JsonPrimitive).content) }
    return Routed(ll, (trip.o("summary")!!["length"] as JsonPrimitive).doubleOrNull!!)
}

/** Bucle de [km] desde [start] hacia [brg]: rodea cuatro puntos y corrige el radio una vez si sale largo o corto. */
fun makeLoop(start: LatLon, km: Double, brg: Double, route: (List<LatLon>) -> Routed): Routed {
    var r = km * 1000 / (2 * Math.PI) / 1.25
    var best: Routed? = null
    for (i in 0 until 2) {
        val c = dest(start, brg, r)
        val res = route(listOf(start, dest(c, brg + 90, r), dest(c, brg, r), dest(c, brg - 90, r), start))
        if (best == null || Math.abs(res.km - km) < Math.abs(best.km - km)) best = res
        if (Math.abs(res.km - km) / km <= 0.12) break
        r *= km / res.km
    }
    return best!!
}

fun heightBody(ll: List<List<Double>>): String { val step = maxOf(1, ll.size / 120); return "{\"shape\":[" + ll.filterIndexed { i, _ -> i % step == 0 }.joinToString(",") { "{\"lat\":${it[0]},\"lon\":${it[1]}}" } + "]}" }
fun parseGain(text: String): Int? = runCatching {
    val h = ((Json.parseToJsonElement(text) as JsonObject)["height"] as? JsonArray ?: return null).map { (it as? JsonPrimitive)?.doubleOrNull }
    var gain = 0.0; var ref = h.firstOrNull() ?: return null
    h.forEach { x -> if (x == null) return@forEach; if (x - ref >= 3) { gain += x - ref; ref = x } else if (x < ref) ref = x }
    jsRoundI(gain)
}.getOrNull()
