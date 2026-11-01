package com.zancada.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import com.zancada.core.LatLon
import com.zancada.core.pointInRing
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import java.io.File
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sinh
import kotlin.math.tan

// Mapas: OpenStreetMap (calles, en gris para que la ruta sea lo único con color) y Esri (satélite). Sin clave ni cuenta.

/** Satélite de Esri (el mismo que la web). Esri pide z/y/x. */
object EsriSat : OnlineTileSourceBase("EsriSat", 0, 19, 256, "", arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"), "Imágenes © Esri, Maxar, Earthstar Geographics") {
    override fun getTileURLString(idx: Long) = baseUrl + MapTileIndex.getZoom(idx) + "/" + MapTileIndex.getY(idx) + "/" + MapTileIndex.getX(idx)
}

/** Capa de nombres y calles de Esri, encima del satélite (mapa híbrido). */
class EsriRef(name: String, private val path: String) : OnlineTileSourceBase(name, 0, 19, 256, "", arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/Reference/")) {
    override fun getTileURLString(idx: Long) = baseUrl + path + "/MapServer/tile/" + MapTileIndex.getZoom(idx) + "/" + MapTileIndex.getY(idx) + "/" + MapTileIndex.getX(idx)
}
val EsriRoads = EsriRef("EsriRoads", "World_Transportation")
val EsriPlaces = EsriRef("EsriPlaces", "World_Boundaries_and_Places")
/** Relieve: curvas de nivel y sombreado (OpenTopoMap, uso moderado y con atribución). */
val OpenTopo = XYTileSource("OpenTopo", 0, 17, 256, ".png", arrayOf("https://a.tile.opentopomap.org/", "https://b.tile.opentopomap.org/", "https://c.tile.opentopomap.org/"), "© OpenStreetMap · SRTM · OpenTopoMap (CC-BY-SA)")

/** Tipos de mapa: clave, nombre. "calles" es el gris de siempre. */
val MAP_TYPES = listOf("calles" to "Claro", "color" to "Estándar", "sat" to "Satélite", "hibrido" to "Híbrido", "relieve" to "Relieve")
fun mapAttribution(layer: String) = when (layer) { "sat" -> "© Esri, Maxar"; "hibrido" -> "© Esri, Maxar, HERE"; "relieve" -> "© OpenStreetMap · OpenTopoMap"; else -> "© OpenStreetMap" }

fun mapsInit(ctx: Context) {
    Configuration.getInstance().apply {
        userAgentValue = "Zancada/1.0 (com.zancada.nativa; https://github.com/miguelas93/zancada)"
        osmdroidBasePath = File(ctx.cacheDir, "osmdroid"); osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")
        tileFileSystemCacheMaxBytes = 120L * 1024 * 1024; tileFileSystemCacheTrimBytes = 90L * 1024 * 1024
    }
}

/** Una línea del mapa. [dash] en dp (trazo, hueco). */
data class MLine(val ll: List<LatLon>, val color: Color, val width: Float, val alpha: Float = 1f, val dash: Pair<Float, Float>? = null, val tap: Int? = null)
/** Un punto: círculo con borde, y opcionalmente un halo o una etiqueta tipo píldora ("5,2 km"). */
data class MDot(val p: LatLon, val fill: Color, val border: Color, val size: Float = 18f, val borderW: Float = 3f, val halo: Color? = null, val haloW: Float = 0f, val label: String? = null, val tap: Int? = null)

/** Un área (parque, ribera…): uno o varios anillos rellenos con borde. */
data class MArea(val rings: List<List<LatLon>>, val fill: Color, val stroke: Color, val tap: Int? = null)

private val INK = 0xFF0B0C0B.toInt()

/** Para mover el mapa desde fuera (encuadrar un trazado, seguir al corredor…). */
class MapCtl {
    var view: MapView? = null
    private var pending: (() -> Unit)? = null
    internal fun attach(v: MapView) {
        view = v
        v.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> pending?.let { if (v.width > 0) { pending = null; it() } } }
    }
    private fun whenLaid(f: () -> Unit) { val v = view; if (v != null && v.width > 0 && v.height > 0) f() else pending = f }

    /** Encuadra los puntos dejando márgenes (dp) a cada lado, como fitBounds de Leaflet con paddingTopLeft/BottomRight. */
    fun fit(ll: List<LatLon>, l: Float = 36f, t: Float = 36f, r: Float = 36f, b: Float = 36f, maxZoom: Double = 18.0) {
        if (ll.isEmpty()) return
        whenLaid {
            val v = view ?: return@whenLaid
            val dn = v.resources.displayMetrics.density
            fun mx(lon: Double) = (lon + 180) / 360
            fun my(lat: Double) = (1 - ln(tan(Math.toRadians(lat)) + 1 / cos(Math.toRadians(lat))) / PI) / 2
            val xs = ll.map { mx(it.lon) }; val ys = ll.map { my(it.lat) }
            val dx = max(1e-9, xs.max() - xs.min()); val dy = max(1e-9, ys.max() - ys.min())
            val aw = max(40f, v.width - (l + r) * dn); val ah = max(40f, v.height - (t + b) * dn)
            val T = 256.0 * dn
            val z = min(maxZoom, min(log2(aw / (dx * T)), log2(ah / (dy * T)))).coerceIn(2.0, 19.0)
            val world = T * Math.pow(2.0, z)
            val cx = (xs.max() + xs.min()) / 2 - ((l - r) * dn / 2) / world
            val cy = (ys.max() + ys.min()) / 2 - ((t - b) * dn / 2) / world
            val lat = Math.toDegrees(atan(sinh(PI * (1 - 2 * cy)))); val lon = cx * 360 - 180
            v.controller.setZoom(z); v.controller.setCenter(GeoPoint(lat, lon))
        }
    }
    fun center(p: LatLon, zoom: Double? = null, animate: Boolean = false) = whenLaid {
        val v = view ?: return@whenLaid
        zoom?.let { v.controller.setZoom(it) }
        if (animate) v.controller.animateTo(GeoPoint(p.lat, p.lon)) else v.controller.setCenter(GeoPoint(p.lat, p.lon))
    }
    fun zoom(): Double = view?.zoomLevelDouble ?: 13.0
}

private class ZOverlay(ctx: Context) : Overlay() {
    var lines: List<MLine> = emptyList()
    var dots: List<MDot> = emptyList()
    var areas: List<MArea> = emptyList()
    var onTap: ((Int) -> Unit)? = null
    var onTouch: (() -> Unit)? = null
    private val dn = ctx.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12 * dn; typeface = runCatching { ResourcesCompat.getFont(ctx, R.font.inter_600) }.getOrNull() ?: Typeface.DEFAULT_BOLD }
    private val pt = android.graphics.Point()
    private val path = Path()

    override fun draw(c: Canvas, map: MapView, shadow: Boolean) {
        if (shadow) return
        val pr = map.projection
        for (a in areas) {
            path.reset()
            for (r in a.rings) { if (r.size < 3) continue; r.forEachIndexed { i, p -> pr.toPixels(GeoPoint(p.lat, p.lon), pt); if (i == 0) path.moveTo(pt.x.toFloat(), pt.y.toFloat()) else path.lineTo(pt.x.toFloat(), pt.y.toFloat()) }; path.close() }
            fillP.color = a.fill.toArgb(); c.drawPath(path, fillP)
            paint.color = a.stroke.toArgb(); paint.alpha = a.stroke.alpha.times(255).toInt(); paint.strokeWidth = 2f * dn; paint.pathEffect = null; c.drawPath(path, paint)
        }
        for (l in lines) {
            if (l.ll.size < 2) continue
            path.reset()
            l.ll.forEachIndexed { i, p -> pr.toPixels(GeoPoint(p.lat, p.lon), pt); if (i == 0) path.moveTo(pt.x.toFloat(), pt.y.toFloat()) else path.lineTo(pt.x.toFloat(), pt.y.toFloat()) }
            paint.color = l.color.toArgb(); paint.alpha = (l.alpha * 255).toInt(); paint.strokeWidth = l.width * dn
            paint.pathEffect = l.dash?.let { DashPathEffect(floatArrayOf(it.first * dn, it.second * dn), 0f) }
            c.drawPath(path, paint)
        }
        paint.pathEffect = null
        for (d in dots) {
            pr.toPixels(GeoPoint(d.p.lat, d.p.lon), pt)
            val x = pt.x.toFloat(); val y = pt.y.toFloat()
            if (d.label != null) {
                val tw = textP.measureText(d.label); val h = 30 * dn; val w = tw + 26 * dn
                fillP.color = INK; c.drawRoundRect(RectF(x - 15 * dn, y - h / 2, x - 15 * dn + w, y + h / 2), h / 2, h / 2, fillP)
                fillP.color = Volt.toArgb(); c.drawCircle(x - 15 * dn + 11 * dn, y, 5 * dn, fillP)
                textP.color = Volt.toArgb(); c.drawText(d.label, x - 15 * dn + 22 * dn, y + 4.3f * dn, textP)
                continue
            }
            val r = d.size / 2 * dn
            d.halo?.let { fillP.color = it.toArgb(); c.drawCircle(x, y, r + d.haloW * dn, fillP) }
            fillP.color = d.border.toArgb(); c.drawCircle(x, y, r, fillP)
            fillP.color = d.fill.toArgb(); c.drawCircle(x, y, r - d.borderW * dn, fillP)
        }
    }

    override fun onTouchEvent(e: MotionEvent, map: MapView): Boolean { if (e.actionMasked == MotionEvent.ACTION_DOWN) onTouch?.invoke(); return false }

    override fun onSingleTapConfirmed(e: MotionEvent, map: MapView): Boolean {
        val cb = onTap ?: return false
        val pr = map.projection
        var best = Double.MAX_VALUE; var id: Int? = null
        for (d in dots) { val t = d.tap ?: continue; pr.toPixels(GeoPoint(d.p.lat, d.p.lon), pt); val dd = hypot(pt.x - e.x.toDouble(), pt.y - e.y.toDouble()); if (dd < best && dd < 30 * dn) { best = dd; id = t } }
        if (id == null) for (l in lines) { val t = l.tap ?: continue; for (p in l.ll) { pr.toPixels(GeoPoint(p.lat, p.lon), pt); val dd = hypot(pt.x - e.x.toDouble(), pt.y - e.y.toDouble()); if (dd < best && dd < 16 * dn) { best = dd; id = t } } }
        if (id == null) {
            val g = pr.fromPixels(e.x.toInt(), e.y.toInt()); val q = LatLon(g.latitude, g.longitude)
            id = areas.lastOrNull { a -> a.tap != null && a.rings.any { pointInRing(q, it) } }?.tap
        }
        return id?.let { cb(it); true } ?: false
    }
}

private fun streetsFilter(night: Boolean): ColorMatrixColorFilter {
    // grayscale(1) contrast(.92) brightness(1.04)  ·  de noche: invertido pero en grises (fondo ~#525252, letras claras), no casi negro
    val (k, o) = if (night) -0.55f to 215f else 0.9568f to 10.65f
    val r = 0.2126f * k; val g = 0.7152f * k; val b = 0.0722f * k
    return ColorMatrixColorFilter(ColorMatrix(floatArrayOf(r, g, b, 0f, o, r, g, b, 0f, o, r, g, b, 0f, o, 0f, 0f, 0f, 1f, 0f)))
}

/**
 * Mapa: [layer] "calles" o "sat". Si no es [interactive], no se puede mover (como el de Preparar o el del resumen).
 * [onTap] recibe el `tap` del punto o línea tocados.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun ZMap(
    modifier: Modifier, layer: String = "calles", lines: List<MLine> = emptyList(), dots: List<MDot> = emptyList(), ctl: MapCtl? = null,
    areas: List<MArea> = emptyList(),
    interactive: Boolean = true, onTap: ((Int) -> Unit)? = null, onTouch: (() -> Unit)? = null, attribution: Boolean = true,
    attrBottom: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val ctx = LocalContext.current
    val c = Z.c
    val night = c.night
    val holder = remember { arrayOfNulls<ZOverlay>(1) }
    val refs = remember { mutableListOf<TilesOverlay>() }
    val state = remember { arrayOf<String?>(null, null) }
    Box(modifier.background(c.mapLand)) {
        AndroidView(factory = {
            MapView(it).apply {
                setTilesScaledToDpi(true); isHorizontalMapRepetitionEnabled = false; isVerticalMapRepetitionEnabled = false
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                setMultiTouchControls(true); minZoomLevel = 3.0; maxZoomLevel = 19.0
                controller.setZoom(13.0); controller.setCenter(GeoPoint(40.4168, -3.7038))
                overlayManager.tilesOverlay.loadingBackgroundColor = c.mapLand.toArgb(); overlayManager.tilesOverlay.loadingLineColor = c.mapLand.toArgb()
                val ov = ZOverlay(it); holder[0] = ov; overlays.add(ov)
                ctl?.attach(this)
            }
        }, modifier = Modifier.matchParentSize(), update = { v ->
            val key = layer + night
            if (state[0] != key) {
                state[0] = key
                v.setTileSource(when (layer) { "sat", "hibrido" -> EsriSat; "relieve" -> OpenTopo; else -> TileSourceFactory.MAPNIK })
                v.overlayManager.tilesOverlay.setColorFilter(if (layer == "calles") streetsFilter(night) else null)
                refs.forEach { v.overlays.remove(it); it.onDetach(v) }; refs.clear()
                if (layer == "hibrido") listOf(EsriRoads, EsriPlaces).forEach { src ->
                    val o = TilesOverlay(MapTileProviderBasic(v.context, src), v.context).apply { loadingBackgroundColor = android.graphics.Color.TRANSPARENT; loadingLineColor = android.graphics.Color.TRANSPARENT }
                    refs += o; v.overlays.add(0, o)
                }
                v.overlayManager.tilesOverlay.loadingBackgroundColor = c.mapLand.toArgb()
            }
            if (interactive) v.setOnTouchListener(null) else v.setOnTouchListener { _, _ -> true }
            holder[0]?.let { o -> o.lines = lines; o.dots = dots; o.areas = areas; o.onTap = onTap; o.onTouch = onTouch }
            v.invalidate()
        }, onRelease = { it.onDetach() })
        if (attribution) Text(mapAttribution(layer), Modifier.align(Alignment.BottomEnd).padding(bottom = attrBottom).clip(RoundedCornerShape(topStart = 6.dp)).background(Color(0xAAFFFFFF)).padding(horizontal = 4.dp, vertical = 1.dp),
            style = bodyStyle(9.sp).copy(color = Color(0xFF333333), lineHeight = 11.sp))
    }
    DisposableEffect(Unit) { onDispose { ctl?.view = null } }
}

/** Trazo de ruta de la web: tinta 9 con alma lima 4. */
fun routeLines(ll: List<LatLon>): List<MLine> = listOf(MLine(ll, Ink, 9f), MLine(ll, Volt, 4f))
fun startDot(p: LatLon) = MDot(p, Color.White, Ink)
fun endDot(p: LatLon) = MDot(p, Volt, Ink)
fun List<List<Double>>.toLL() = map { LatLon(it[0], it[1]) }
