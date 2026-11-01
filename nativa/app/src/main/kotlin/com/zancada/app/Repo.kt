package com.zancada.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.zancada.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalTime

/**
 * Todos los datos de la app, en un único archivo privado (zancada.v2.json) con las mismas claves que la web:
 * así la copia de seguridad de la web se importa tal cual y la de la nativa la entiende la web.
 * Lo usan la pantalla y el servicio de la carrera (mismo proceso).
 */
object Repo {
    private lateinit var appCtx: Context
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val data = MutableStateFlow(AppData())
    /** Rutas recomendadas (routes-data.js, el mismo archivo que la web). */
    var zr: List<Route> = emptyList(); private set
    /** Última posición conocida (sin pedir permisos: solo si ya están dados). */
    val here = MutableStateFlow<LatLon?>(null)
    /** Cambia cada minuto: para el modo noche automático. */
    val minute = MutableStateFlow(0L)
    private var saveJob: Job? = null
    private val file get() = File(appCtx.filesDir, "zancada.v2.json")

    fun init(ctx: Context) {
        if (::appCtx.isInitialized) return
        appCtx = ctx.applicationContext
        zr = runCatching { appCtx.assets.open("routes-data.js").bufferedReader().use { parseRecRoutes(it.readText()) } }.getOrDefault(emptyList())
        data.value = load()
        mutate { reviewPlan() }
        io.launch { while (true) { delay(60_000 - System.currentTimeMillis() % 60_000); minute.value = System.currentTimeMillis() } }
    }

    private fun load(): AppData {
        val f = file
        if (f.exists()) runCatching { return Store.fromKeys(Store.parseKeys(f.readText())) }
        // Migración desde la primera versión de la nativa (solo carreras) y la copia que se importó entonces.
        var d = AppData()
        runCatching { File(appCtx.filesDir, "backup-import.json").takeIf { it.exists() }?.let { d = Store.import(d, it.readText()).data } }
        runCatching {
            File(appCtx.filesDir, "runs.v1.json").takeIf { it.exists() }?.let { rf ->
                val runs = Store.decodeRuns(Store.json.parseToJsonElement(rf.readText()) as? kotlinx.serialization.json.JsonArray).first
                d = d.copy(runs = (d.runs + BackupImport.pickNew(d.runs, runs)).sortedByDescending { it.start })
            }
        }
        if (d.runs.isNotEmpty()) d = d.copy(profile = d.profile.copy(done = true))
        writeNow(d)
        return d
    }

    fun app(now: () -> Long = System::currentTimeMillis) = App(data.value, zr, now)

    /** Cambia los datos con la lógica de :core y guarda. */
    fun <T> mutate(block: App.() -> T): T { val a = app(); val r = a.block(); if (a.data !== data.value) { data.value = a.data; save() }; return r }
    fun update(f: (AppData) -> AppData) { data.value = f(data.value); save() }
    fun settings(f: (Settings) -> Settings) = update { it.copy(settings = f(it.settings)) }

    /** Guarda en segundo plano, agrupando cambios seguidos (como escribir una nota). */
    fun save() { saveJob?.cancel(); saveJob = io.launch { delay(250); writeNow(data.value) } }
    fun saveNow() { saveJob?.cancel(); writeNow(data.value) }
    @Synchronized private fun writeNow(d: AppData) {
        runCatching {
            val tmp = File(appCtx.filesDir, "zancada.v2.json.tmp")
            tmp.writeText(JsonObject(Store.toKeys(d)).toString())
            tmp.renameTo(file)   // atómico: o queda lo anterior o lo nuevo
        }
    }

    fun isNight(s: Settings = data.value.settings, systemDark: Boolean = false): Boolean {
        val h = LocalTime.now().hour
        return s.night == "on" || (s.night == "auto" && (h >= 20 || h < 7 || systemDark))
    }

    // ---------- ubicación (solo si ya hay permiso; nunca se pide al abrir Inicio) ----------
    fun hasLocation(ctx: Context) = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun locate(ctx: Context) {
        if (!hasLocation(ctx)) return
        runCatching {
            val c = LocationServices.getFusedLocationProviderClient(ctx)
            c.lastLocation.addOnSuccessListener { l -> if (l != null && here.value == null) here.value = LatLon(l.latitude, l.longitude) }
            c.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).addOnSuccessListener { l -> if (l != null) here.value = LatLon(l.latitude, l.longitude) }
        }
    }

    // ---------- clima (open-meteo, sin clave): 20 min de caché si no te has movido 3 km ----------
    private var wx: Wx? = null; private var wxAt = 0L; private var wxPos: LatLon? = null
    fun wxFresh(maxAge: Long = 4 * 3600_000L) = wx?.takeIf { System.currentTimeMillis() - wxAt < maxAge }
    suspend fun weather(p: LatLon): Wx? {
        val w0 = wx; val p0 = wxPos
        if (w0 != null && p0 != null && System.currentTimeMillis() - wxAt < 20 * 60_000 && hav(p0, p) < 3000) return w0
        val txt = Net.get(weatherUrl(p.lat, p.lon), 6000) ?: return null
        val w = parseWeather(txt, LocalTime.now().hour) ?: return null
        wx = w; wxAt = System.currentTimeMillis(); wxPos = p
        return w
    }

    // ---------- fotos (Wikimedia Commons) y fuentes/aseos/transporte (Overpass): con caché en los datos ----------
    suspend fun photos(r: Route): List<Photo> {
        val c = data.value.photos[r.id]
        if (c != null && c.v == 2 && System.currentTimeMillis() - c.t < 30 * DAY) return c.p
        val pages = photoUrls(r).flatMap { u -> Net.get(u, 10_000)?.let { photoPages(it) } ?: emptyList() }
        val p = pickPhotos(pages, photoPlace(r))
        update { it.copy(photos = it.photos + (r.id to PhotoCache(System.currentTimeMillis(), 2, p))) }
        return p
    }

    suspend fun pois(r: Route): PoiCache? {
        val c = data.value.pois[r.id]
        if (c != null && System.currentTimeMillis() - c.t < 14 * DAY) return c
        val u = poiUrl(r) ?: return null
        val txt = Net.get(u, 12_000) ?: return null
        val out = parsePois(txt, r, System.currentTimeMillis()) ?: return null
        update { it.copy(pois = it.pois + (r.id to out)) }
        return out
    }

    // ---------- «Bucle desde aquí» (Valhalla de OpenStreetMap) ----------
    private const val ROUTER = "https://valhalla1.openstreetmap.de"
    suspend fun loop(start: LatLon, km: Double, brg: Double): Route = withContext(Dispatchers.IO) {
        val res = makeLoop(start, km, brg) { pts -> val (code, body) = Net.postBlocking("$ROUTER/route", routeBody(pts)); if (code != 200 || body == null) error("router $code"); parseRouted(body) }
        val gain = runCatching { Net.postBlocking("$ROUTER/height", heightBody(res.ll)).let { (c, b) -> if (c == 200 && b != null) parseGain(b) else null } }.getOrNull()
        Route("", "Bucle hacia el ${compass(brg)}", res.ll, jsRound(res.km * 10) / 10.0, gain, created = System.currentTimeMillis(), start = LatLon(res.ll[0][0], res.ll[0][1]), src = "sug")
    }

    // ---------- sitios para correr (OpenStreetMap): 7 días de caché por zona de ~2 km ----------
    private var spotsMem: Pair<String, List<Spot>>? = null
    suspend fun spots(here: LatLon): List<Spot> {
        val key = "${toFixed(here.lat, 2)}_${toFixed(here.lon, 2)}"
        spotsMem?.takeIf { it.first == key }?.let { return it.second }
        val f = File(appCtx.cacheDir, "spots-v1-$key.json")
        val txt = withContext(Dispatchers.IO) { f.takeIf { it.exists() && System.currentTimeMillis() - it.lastModified() < 7 * DAY }?.readText() }
            ?: Net.get(spotsUrl(here), 30_000)?.also { t -> if (t.contains("\"elements\"")) withContext(Dispatchers.IO) { runCatching { f.writeText(t) } } }
            ?: return emptyList()
        val out = withContext(Dispatchers.Default) { parseSpots(txt, here) }
        if (out.isNotEmpty()) spotsMem = key to out
        return out
    }

    private fun routeWith(pts: List<LatLon>, spec: AskSpec): Routed {
        val opts = buildString {
            append("\"costing_options\":{\"pedestrian\":{\"walkway_factor\":0.8")
            if (spec.flat) append(",\"use_hills\":0.0"); if (spec.hills) append(",\"use_hills\":1.0")
            if ("verde" in spec.want) append(",\"sidewalk_factor\":1.4")
            append("}},")
        }
        val body = routeBody(pts).replace("\"costing\":\"pedestrian\",", "\"costing\":\"pedestrian\",$opts")
        val (code, txt) = Net.postBlocking("$ROUTER/route", body); if (code != 200 || txt == null) error("router $code")
        return parseRouted(txt)
    }

    data class AskOut(val routes: List<Route>, val allOk: Boolean, val miss: List<String>)

    /**
     * «Pídesela a Zeta»: la IA traduce la frase (si no contesta, reglas); se buscan los sitios y fuentes de alrededor;
     * cada plan pasa POR el sitio (dentro del parque, a lo largo del río) y se ajusta a los km; al final se comprueba
     * todo lo pedido y solo se enseñan las que cumplen (si ninguna, las más cercanas diciendo qué falla).
     */
    suspend fun askRoutes(here: LatLon, text: String, paceSec: Double, seed: Int, step: (String) -> Unit, onSpec: (AskSpec) -> Unit, emit: (List<Route>) -> Unit): AskOut = withContext(Dispatchers.IO) {
        step("Entendiendo lo que pides…")
        val rules = askRules(text, paceSec)
        val ai = runCatching { withTimeoutOrNull(14_000) { Net.post(COACH_URL, askBody(text, paceSec), 14_000) } }.getOrNull()
        var spec = ai?.takeIf { it.code == 200 }?.body?.let { coachAnswer(it) }?.let { parseAskSpec(it, rules) } ?: rules
        step("Mirando los sitios de alrededor…")
        val spots = runCatching { spots(here) }.getOrDefault(emptyList())
        val place = matchPlace(text, spots)
        if (place != null && place.kind != "pista") spec = spec.copy(want = spec.want + if (place.kind == "rio") "agua" else "verde")
        onSpec(spec)
        val feats = askOverpassUrl(here, spec.km, spec.want)?.let { u -> Net.get(u, 25_000)?.let { parseAskFeats(it) } } ?: emptyList()
        val plans = askPlans(here, spec, spots, feats, seed, place)
        val done = mutableListOf<Pair<AskCheck, Route>>()
        plans.forEachIndexed { i, p ->
            step("Probando recorridos (${i + 1} de ${plans.size})…")
            val r = runCatching { routePlan(p, spec.km, { pts -> routeWith(pts, spec) }) }.getOrNull() ?: return@forEachIndexed
            val gain = runCatching { Net.postBlocking("$ROUTER/height", heightBody(r.ll)).let { (c, b) -> if (c == 200 && b != null) parseGain(b) else null } }.getOrNull()
            val chk = checkAsk(r.ll, r.km, gain, spec, feats, spots)
            val name = p.via?.let { "Por $it" } ?: when {
                chk.why.contains("agua") -> "Junto al agua"; chk.why.contains("verdes") -> "Por zonas verdes"
                spec.hills -> "Bucle con cuestas"; spec.flat -> "Bucle llano"; else -> p.label
            }
            val route = Route("ask-${System.currentTimeMillis()}-$i", name, r.ll, jsRound(r.km * 10) / 10.0, gain, created = System.currentTimeMillis(),
                start = LatLon(r.ll[0][0], r.ll[0][1]), src = "ask", sub = if (chk.ok) "Cumple lo que pediste" else "No cumple: " + chk.miss.joinToString(", "), why = chk.why)
            done += chk to route
            val ok = done.filter { it.first.ok }
            emit((if (ok.isNotEmpty()) ok else done).sortedByDescending { it.first.score }.take(3).map { it.second })
        }
        val ok = done.filter { it.first.ok }
        AskOut((if (ok.isNotEmpty()) ok else done).sortedByDescending { it.first.score }.take(3).map { it.second }, ok.isNotEmpty(),
            if (ok.isEmpty()) done.maxByOrNull { it.first.score }?.first?.miss ?: emptyList() else emptyList())
    }
}

/** Peticiones sencillas (siempre https, con tiempo límite). */
object Net {
    private const val UA = "Zancada/1.0 (app Android para correr; https://github.com/miguelas93/zancada)"

    suspend fun get(url: String, timeout: Int = 8000): String? = withContext(Dispatchers.IO) { getBlocking(url, timeout) }

    fun getBlocking(url: String, timeout: Int = 8000): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeout; c.readTimeout = timeout; c.setRequestProperty("User-Agent", UA)
        try { if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null } finally { c.disconnect() }
    }.getOrNull()

    /** Para las fotos: bytes de una imagen (máx. 8 MB). */
    suspend fun bytes(url: String, timeout: Int = 10_000): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = timeout; c.readTimeout = timeout; c.setRequestProperty("User-Agent", UA)
            try { if (c.responseCode == 200) c.inputStream.use { s -> s.readBytes().takeIf { it.size < 8 * 1024 * 1024 } } else null } finally { c.disconnect() }
        }.getOrNull()
    }

    data class Res(val code: Int, val body: String?, val retryAfter: Int?)

    suspend fun post(url: String, body: String, timeout: Int = 60_000): Res = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000; c.readTimeout = timeout; c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("User-Agent", UA)
            try {
                c.outputStream.use { it.write(body.toByteArray()) }
                val code = c.responseCode
                Res(code, if (code in 200..299) c.inputStream.bufferedReader().use { it.readText() } else null, c.getHeaderField("Retry-After")?.toIntOrNull())
            } finally { c.disconnect() }
        }.getOrElse { Res(-1, null, null) }
    }

    fun postBlocking(url: String, body: String): Pair<Int, String?> = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 30_000; c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("User-Agent", UA)
        try { c.outputStream.use { it.write(body.toByteArray()) }; val code = c.responseCode; code to (if (code == 200) c.inputStream.bufferedReader().use { it.readText() } else null) } finally { c.disconnect() }
    }.getOrElse { -1 to null }
}
