package com.zancada.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zancada.core.*
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point as GPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Vista 3D de una ruta o carrera: mapa inclinado con edificios en 3D (MapLibre + OpenFreeMap, sin clave ni cuenta)
 * o satélite con sombreado del relieve. «Sobrevolar» recorre el trazado con la cámara.
 * Solo se usa en esta pantalla; el resto de mapas sigue igual.
 */
object Map3d {
    /** Calles con edificios 3D (OpenFreeMap, estilo «liberty»: datos de OpenStreetMap). */
    const val STREETS = "https://tiles.openfreemap.org/styles/liberty"

    /** Satélite de Esri con el relieve sombreado (alturas: Terrain Tiles de Mapzen en AWS, datos abiertos). */
    val SAT = """
        {"version":8,
         "sources":{
          "sat":{"type":"raster","tiles":["https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],"tileSize":256,"maxzoom":19,"attribution":"Imágenes © Esri, Maxar, Earthstar Geographics"},
          "dem":{"type":"raster-dem","tiles":["https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png"],"encoding":"terrarium","tileSize":256,"maxzoom":15,"attribution":"Relieve: Mapzen Terrain Tiles (AWS)"}
         },
         "layers":[
          {"id":"sat","type":"raster","source":"sat"},
          {"id":"hill","type":"hillshade","source":"dem","paint":{"hillshade-exaggeration":0.45,"hillshade-shadow-color":"#000000","hillshade-highlight-color":"#ffffff"}}
         ]}
    """.trimIndent()

    fun bearing(a: LatLon, b: LatLon): Double {
        val f1 = Math.toRadians(a.lat); val f2 = Math.toRadians(b.lat); val dl = Math.toRadians(b.lon - a.lon)
        val y = sin(dl) * cos(f2); val x = cos(f1) * sin(f2) - sin(f1) * cos(f2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    /** Puntos repartidos a lo largo del trazado (n como mucho), para que el vuelo vaya a velocidad constante. */
    fun sample(ll: List<LatLon>, n: Int): List<LatLon> {
        if (ll.size <= 2) return ll
        val cum = DoubleArray(ll.size); for (i in 1 until ll.size) cum[i] = cum[i - 1] + hav(ll[i - 1], ll[i])
        val tot = cum.last(); if (tot <= 0) return ll
        val out = ArrayList<LatLon>(n); var j = 1
        for (k in 0 until n) {
            val d = tot * k / (n - 1)
            while (j < ll.size - 1 && cum[j] < d) j++
            val seg = cum[j] - cum[j - 1]; val t = if (seg > 0) ((d - cum[j - 1]) / seg).coerceIn(0.0, 1.0) else 0.0
            out.add(LatLon(ll[j - 1].lat + (ll[j].lat - ll[j - 1].lat) * t, ll[j - 1].lon + (ll[j].lon - ll[j - 1].lon) * t))
        }
        return out
    }

    private fun hav(a: LatLon, b: LatLon): Double {
        val dLat = Math.toRadians(b.lat - a.lat); val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 6371000 * 2 * atan2(Math.sqrt(h), Math.sqrt(1 - h))
    }
}

/** Pantalla «Vista 3D» de una ruta guardada/sugerida o de una carrera. */
@Composable
fun Map3dScreen(route: Route?, runId: String?) {
    val c = Z.c; val ctx = LocalContext.current
    val data by Repo.data.collectAsState()
    val ll: List<LatLon> = remember(route, runId) {
        route?.ll?.mapNotNull { p -> if (p.size >= 2) LatLon(p[0], p[1]) else null }
            ?: data.runs.firstOrNull { it.id == runId }?.points?.map { LatLon(it.lat, it.lon) } ?: emptyList()
    }
    val title = route?.name ?: data.runs.firstOrNull { it.id == runId }?.name ?: "Vista 3D"
    if (ll.size < 2) { LaunchedEffect(Unit) { toast("Esta ruta no tiene trazado"); Nav.back() }; return }

    var sat by remember { mutableStateOf(false) }
    var flying by remember { mutableStateOf(false) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val lineCol = c.volt.toArgb()
    val bounds = remember(ll) { LatLngBounds.Builder().apply { ll.forEach { include(LatLng(it.lat, it.lon)) } }.build() }
    val startBrg = remember(ll) { Map3d.bearing(ll.first(), ll[minOf(ll.size - 1, maxOf(1, ll.size / 6))]) }

    val lo = LocalLifecycleOwner.current
    val mv = remember { MapLibre.getInstance(ctx.applicationContext); MapView(ctx).apply { onCreate(null) } }
    DisposableEffect(lo) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mv.onStart(); Lifecycle.Event.ON_RESUME -> mv.onResume()
                Lifecycle.Event.ON_PAUSE -> mv.onPause(); Lifecycle.Event.ON_STOP -> mv.onStop()
                else -> {}
            }
        }
        lo.lifecycle.addObserver(obs)
        onDispose {
            lo.lifecycle.removeObserver(obs)
            val st = lo.lifecycle.currentState
            if (st.isAtLeast(Lifecycle.State.RESUMED)) mv.onPause()
            if (st.isAtLeast(Lifecycle.State.STARTED)) mv.onStop()
            mv.onDestroy()
        }
    }
    LaunchedEffect(mv) {
        mv.getMapAsync { m ->
            m.uiSettings.setCompassEnabled(true); m.uiSettings.setLogoEnabled(false)
            m.uiSettings.setAttributionMargins(0, 0, 24, 140)
            map = m
        }
    }
    // Estilo (calles 3D o satélite) y el trazado encima.
    LaunchedEffect(map, sat) {
        val m = map ?: return@LaunchedEffect
        style = null
        val b = if (sat) Style.Builder().fromJson(Map3d.SAT) else Style.Builder().fromUri(Map3d.STREETS)
        m.setStyle(b) { s ->
            val line = LineString.fromLngLats(ll.map { GPoint.fromLngLat(it.lon, it.lat) })
            s.addSource(GeoJsonSource("z-route", line))
            s.addLayer(LineLayer("z-route-case", "z-route").withProperties(
                PropertyFactory.lineColor(Color.Black.copy(alpha = 0.55f).toArgb()), PropertyFactory.lineWidth(9f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)))
            s.addLayer(LineLayer("z-route-line", "z-route").withProperties(
                PropertyFactory.lineColor(lineCol), PropertyFactory.lineWidth(5f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)))
            s.addSource(GeoJsonSource("z-dot", GPoint.fromLngLat(ll.first().lon, ll.first().lat)))
            s.addLayer(CircleLayer("z-dot", "z-dot").withProperties(
                PropertyFactory.circleRadius(7f), PropertyFactory.circleColor(lineCol),
                PropertyFactory.circleStrokeWidth(3f), PropertyFactory.circleStrokeColor(Color.White.toArgb())))
            style = s
        }
    }
    // Encuadre inicial: todo el trazado, inclinado.
    var framed by remember { mutableStateOf(false) }
    LaunchedEffect(style) {
        val m = map ?: return@LaunchedEffect
        if (style == null || framed) return@LaunchedEffect
        framed = true
        runCatching { m.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, startBrg, 0.0, 120)) }
        delay(350)
        runCatching { m.animateCamera(CameraUpdateFactory.tiltTo(60.0), 1400) }
    }
    // Sobrevolar el recorrido.
    LaunchedEffect(flying, style) {
        val m = map ?: return@LaunchedEffect
        val s = style ?: return@LaunchedEffect
        if (!flying) return@LaunchedEffect
        val pts = Map3d.sample(ll, 220); val step = 180L
        var brg = Map3d.bearing(pts.first(), pts[minOf(pts.size - 1, 10)])
        m.easeCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(LatLng(pts[0].lat, pts[0].lon)).zoom(16.3).tilt(65.0).bearing(brg).build()), 1500)
        delay(1600)
        for (i in pts.indices) {
            if (!flying) break
            val ahead = pts[minOf(pts.size - 1, i + 12)]
            if (ahead != pts[i]) {
                val want = Map3d.bearing(pts[i], ahead)
                val d = ((want - brg + 540) % 360) - 180
                brg = (brg + d * 0.25 + 360) % 360
            }
            runCatching { (s.getSource("z-dot") as? GeoJsonSource)?.setGeoJson(GPoint.fromLngLat(pts[i].lon, pts[i].lat)) }
            m.easeCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(LatLng(pts[i].lat, pts[i].lon)).zoom(16.3).tilt(65.0).bearing(brg).build()), step.toInt(), false)
            delay(step)
        }
        flying = false
    }

    Box(Modifier.fillMaxSize().background(c.bar)) {
        AndroidView(factory = { mv }, modifier = Modifier.fillMaxSize())
        val ins = WindowInsets.systemBars.asPaddingValues()
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = ins.calculateTopPadding() + 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ico.back2, { Nav.back() })
            ZCard(Modifier.weight(1f), dark = true, pad = PaddingValues(horizontal = 14.dp, vertical = 9.dp), radius = 22.dp, gap = 0.dp) {
                Lbl("Vista 3D"); Txt(title, size = 14.sp, weight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1)
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = ins.calculateBottomPadding() + 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipB(if (flying) "■ Parar" else "▶ Sobrevolar", false, { flying = !flying }, bg = c.bar, fg = c.onBar)
            ChipB("Calles 3D", !sat, { if (sat) { flying = false; sat = false } })
            ChipB("Satélite", sat, { if (!sat) { flying = false; sat = true } })
        }
        if (style == null) Txt("Cargando mapa 3D…", Modifier.align(Alignment.Center), size = 14.sp, color = c.onBar)
    }
}
