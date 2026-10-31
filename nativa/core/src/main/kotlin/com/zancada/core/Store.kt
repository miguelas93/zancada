package com.zancada.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Los datos en el mismo formato que la web (las mismas claves de localStorage), para importar
 * la copia de la web tal cual y que la copia de la nativa también la entienda la web.
 */
object Store {
    val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true; explicitNulls = false; encodeDefaults = false }

    const val RUNS = "zancada.runs.v1"; const val SET = "zancada.settings.v1"; const val PROF = "zancada.profile.v1"; const val PLAN = "zancada.plan.v1"
    const val ROUTES = "zancada.routes.v1"; const val BADGES = "zancada.badges.v1"; const val SHOES = "zancada.shoes.v1"; const val COACH = "zancada.coach.v1"
    const val PHOTOS = "zancada.photos.v1"; const val POIS = "zancada.pois.v1"; const val ROUTECACHE = "zancada.routecache.v1"
    val KEYS = listOf(RUNS, SET, PROF, PLAN, ROUTES, BADGES, SHOES, COACH, PHOTOS, POIS, ROUTECACHE)

    /** Carrera válida y con los nombres limpios (sin < ni >). */
    fun clean(r: Run): Run? {
        if (!(r.dist.isFinite() && r.dist > 0 && r.dur.isFinite() && r.dur > 0)) return null
        return r.copy(id = r.id.ifBlank { "r${r.start}" }, name = safeText(r.name, 40).ifEmpty { runName(r.start) }, route = r.route?.let { it.copy(name = safeText(it.name, 60)) }, note = r.note?.take(2000))
    }

    fun decodeRuns(a: JsonArray?): Pair<List<Run>, Int> {
        if (a == null) return emptyList<Run>() to 0
        var bad = 0
        val out = a.mapNotNull { e -> runCatching { clean(json.decodeFromJsonElement(Run.serializer(), e)) }.getOrNull() ?: run { bad++; null } }
        return out to bad
    }

    private fun <T> dec(m: Map<String, JsonElement>, k: String, s: KSerializer<T>, d: T): T = m[k]?.let { e -> if (e is JsonPrimitive && e.content == "null") d else runCatching { json.decodeFromJsonElement(s, e) }.getOrNull() } ?: d

    fun fromKeys(m: Map<String, JsonElement>): AppData {
        val prof = dec(m, PROF, Profile.serializer(), Profile())
        return AppData(
            runs = decodeRuns(m[RUNS] as? JsonArray).first,
            settings = dec(m, SET, Settings.serializer(), Settings()),
            profile = prof.copy(name = safeText(prof.name, 20)),
            plan = dec(m, PLAN, Plan.serializer().nullable, null).takeIf { it != null && GOALS.containsKey(it.goal) && LEVELS.containsKey(it.level) && it.days.isNotEmpty() && it.weeks > 0 },
            shoes = dec(m, SHOES, ListSerializer(Shoe.serializer()), emptyList()).map { it.copy(name = safeText(it.name, 40)) },
            savedRoutes = dec(m, ROUTES, ListSerializer(Route.serializer()), emptyList()).filter { it.ll.size >= 2 }.map { it.copy(name = safeText(it.name, 60)) },
            badgeSeen = dec(m, BADGES, ListSerializer(String.serializer()), emptyList()),
            coach = dec(m, COACH, ListSerializer(CoachMsg.serializer()), emptyList()),
            photos = dec(m, PHOTOS, MapSerializer(String.serializer(), PhotoCache.serializer()), emptyMap()),
            pois = dec(m, POIS, MapSerializer(String.serializer(), PoiCache.serializer()), emptyMap()),
            routeCache = dec(m, ROUTECACHE, MapSerializer(String.serializer(), ListSerializer(Route.serializer())), emptyMap()),
        )
    }

    fun toKeys(d: AppData): Map<String, JsonElement> = linkedMapOf(
        RUNS to json.encodeToJsonElement(ListSerializer(Run.serializer()), d.runs),
        SET to json.encodeToJsonElement(Settings.serializer(), d.settings),
        PROF to json.encodeToJsonElement(Profile.serializer(), d.profile),
        PLAN to (d.plan?.let { json.encodeToJsonElement(Plan.serializer(), it) } ?: JsonPrimitive(null as String?)),
        ROUTES to json.encodeToJsonElement(ListSerializer(Route.serializer()), d.savedRoutes),
        BADGES to json.encodeToJsonElement(ListSerializer(String.serializer()), d.badgeSeen),
        SHOES to json.encodeToJsonElement(ListSerializer(Shoe.serializer()), d.shoes),
        COACH to json.encodeToJsonElement(ListSerializer(CoachMsg.serializer()), d.coach),
        PHOTOS to json.encodeToJsonElement(MapSerializer(String.serializer(), PhotoCache.serializer()), d.photos),
        POIS to json.encodeToJsonElement(MapSerializer(String.serializer(), PoiCache.serializer()), d.pois),
        ROUTECACHE to json.encodeToJsonElement(MapSerializer(String.serializer(), ListSerializer(Route.serializer())), d.routeCache),
    )

    /** Copia de seguridad, igual que la de la web (sin la caché de bucles). */
    fun backup(d: AppData, now: Long): String = buildJsonObject {
        put("app", JsonPrimitive("zancada")); put("version", JsonPrimitive(1)); put("exported", JsonPrimitive(isoMs(now)))
        toKeys(d).forEach { (k, v) -> if (k != ROUTECACHE) put(k, v) }
    }.toString()

    data class Imported(val data: AppData, val added: Int, val had: Int, val parts: Int)

    /**
     * Importar una copia: las carreras se suman a las que ya hay (sin duplicar); el resto (ajustes, perfil, plan,
     * zapatillas, rutas…) se trae de la copia, como hacía «Importar copia» en la web.
     */
    fun import(cur: AppData, text: String): Imported {
        val root = BackupImport.root(text)
        val inc = fromKeys(root)
        val (incomingRuns, _) = decodeRuns(root[RUNS] as? JsonArray)
        val fresh = BackupImport.pickNew(cur.runs, incomingRuns)
        val has = { k: String -> root.containsKey(k) }
        val d = cur.copy(
            runs = (cur.runs + fresh).sortedByDescending { it.start },
            settings = if (has(SET)) inc.settings else cur.settings,
            profile = if (has(PROF)) inc.profile else cur.profile,
            plan = if (has(PLAN)) inc.plan else cur.plan,
            shoes = if (has(SHOES)) (inc.shoes + cur.shoes.filter { s -> inc.shoes.none { it.id == s.id } }) else cur.shoes,
            savedRoutes = if (has(ROUTES)) (inc.savedRoutes + cur.savedRoutes.filter { s -> inc.savedRoutes.none { it.id == s.id } }) else cur.savedRoutes,
            badgeSeen = (cur.badgeSeen + inc.badgeSeen).distinct(),
            coach = if (has(COACH) && cur.coach.isEmpty()) inc.coach else cur.coach,
            photos = cur.photos + inc.photos, pois = cur.pois + inc.pois,
        )
        return Imported(d, fresh.size, incomingRuns.size - fresh.size, root.keys.count { it.startsWith("zancada.") })
    }

    fun parseKeys(text: String): Map<String, JsonElement> = (json.parseToJsonElement(text) as? JsonObject) ?: emptyMap()
}
