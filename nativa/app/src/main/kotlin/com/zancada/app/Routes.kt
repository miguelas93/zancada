package com.zancada.app

import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.sync.withPermit
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zancada.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.pow

/** Fotos descargadas (Wikimedia): caché en memoria. */
object Img {
    private val cache = LruCache<String, ImageBitmap>(24)
    suspend fun get(url: String): ImageBitmap? {
        cache.get(url)?.let { return it }
        val b = Net.bytes(url) ?: return null
        return withContext(Dispatchers.Default) { runCatching { BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() }.getOrNull() }?.also { cache.put(url, it) }
    }
}

private val photoGate = kotlinx.coroutines.sync.Semaphore(2)

/** Primera foto de la zona de la ruta (Wikimedia Commons, con caché); null mientras no hay. */
@Composable
fun routePhoto(r: Route): String? {
    val cached = Repo.data.collectAsState().value.photos[r.id]?.p?.firstOrNull()?.src
    var url by remember(r.id) { mutableStateOf(cached) }
    LaunchedEffect(r.id) {
        if (url == null && r.ll.size > 1) url = runCatching { photoGate.withPermit { Repo.photos(r) }.firstOrNull()?.src }.getOrNull()
    }
    return url ?: cached
}

/** Miniatura con foto del sitio y el trazado encima; sin foto, solo el trazado. */
@Composable
fun RoutePhotoThumb(r: Route, w: Dp = 84.dp, h: Dp = 84.dp, bg: Color = Z.c.surface2, stroke: Color = Z.text) {
    val url = routePhoto(r)
    if (url == null) { RouteThumb(r.ll, w = w, h = h, bg = bg, stroke = stroke); return }
    Box(Modifier.size(w, h).clip(RoundedCornerShape(18.dp)).background(bg)) {
        NetImage(url, Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Color(0x55000000)))
        RouteSvg(r.ll, Modifier.matchParentSize(), Color.White)
    }
}

@Composable
fun NetImage(url: String, modifier: Modifier, fallback: String? = null, fit: ContentScale = ContentScale.Crop, onFail: () -> Unit = {}) {
    var img by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) { img = Img.get(url) ?: fallback?.let { Img.get(it) }; if (img == null) onFail() }
    Box(modifier) { img?.let { Image(it, null, Modifier.matchParentSize(), contentScale = fit) } }
}

object RtSt { var seed by mutableStateOf(0); var token = 0 }

@Composable
fun RoutesScreen() {
    val c = Z.c; val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val data by Repo.data.collectAsState()
    val here by Repo.here.collectAsState()
    val app = remember(data) { Repo.app() }
    LaunchedEffect(Unit) { Repo.locate(ctx) }
    val importGpx = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val r = withContext(Dispatchers.IO) { Share.readText(ctx, uri) }?.let { t -> runCatching { Repo.app().parseRouteGpx(t) }.getOrNull() }
            if (r == null) { toast("No he encontrado un trazado en ese GPX"); return@launch }
            Repo.update { it.copy(savedRoutes = listOf(r) + it.savedRoutes) }
            toast("Ruta importada: ${r.name} · ${commaNum(r.km)} km", 3000)
        }
    }
    val near = nearCity(here); val zone = St.rZone ?: near ?: "madrid"
    val list: List<Route> = when (St.rMode) {
        "rec" -> app.recList(RouteFilter(here, zone, St.rDist, St.rSurf, St.rSort))
        "mine" -> withAway(app.myRoutes(), here)
        "ask" -> St.askList
        else -> St.rSug
    }
    // Bucles desde aquí
    val sugKey = here?.let { "${toFixed(it.lat, 3)},${toFixed(it.lon, 3)},${St.rKm},${RtSt.seed}" }
    LaunchedEffect(St.rMode, sugKey) {
        if (St.rMode != "sug" || here == null || sugKey == null) return@LaunchedEffect
        Repo.data.value.routeCache[sugKey]?.let { St.rSug = it; St.rSugErr = null; return@LaunchedEffect }
        val tok = ++RtSt.token; St.rSug = emptyList(); St.rSugBusy = true; St.rSugErr = null
        val out = mutableListOf<Route>()
        for (k in 0..2) {
            val brg = (RtSt.seed + k * 120).toDouble()
            runCatching { Repo.loop(here!!, St.rKm.toDouble(), brg) }.getOrNull()?.let { r -> if (tok == RtSt.token) { out.add(r.copy(id = "sug-$sugKey-$k")); St.rSug = out.toList() } }
            if (tok != RtSt.token) return@LaunchedEffect
        }
        St.rSugBusy = false
        if (out.isEmpty()) { St.rSugErr = "x"; return@LaunchedEffect }
        Repo.update { d -> val m = LinkedHashMap(d.routeCache); m[sugKey] = out; while (m.size > 12) m.remove(m.keys.first()); d.copy(routeCache = m) }
    }
    // Búsqueda y dificultad se aplican sobre la lista del modo.
    val q = St.rQ.trim().lowercase()
    val shown = list.filter { r -> (q.isEmpty() || "${r.name} ${r.zone ?: ""} ${r.sub ?: ""}".lowercase().contains(q)) && (St.rDiff == "all" || app.routeMeta(r).diffCls == (if (St.rDiff == "easy") "" else St.rDiff)) }
    val sel = shown.getOrNull(St.rSel.coerceIn(0, maxOf(0, shown.size - 1)))
    LaunchedEffect(shown.map { it.id }) { if (St.rSel >= shown.size) St.rSel = 0 }
    LaunchedEffect(St.rMode) { if (St.rMode == "ask" || St.rMode == "sug") St.rSheet = 2 }
    RoutesMapScreen(shown, sel, here, app, near, zone) {
        when (St.rMode) {
            "mine" -> Btn("Importar una ruta GPX (de un amigo, Wikiloc, Komoot…)", { importGpx.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "application/octet-stream", "*/*")) }, kind = BtnKind.Ghost, height = 40.dp, size = 13.sp)
            "ask", "sug" -> {
                Seg(listOf("ask" to "Pídesela a Zeta", "sug" to "Bucle rápido"), St.rMode, { St.rMode = it }, h = 40.dp)
                if (St.rMode == "ask") AskBox(here, app)
                else Chips(wrap = true) { listOf(3, 5, 8, 10, 15).forEach { k -> ChipB("$k km", St.rKm == k, { St.rKm = k }) } }
            }
        }
        if (St.rMode == "rec" && St.rDist == "all" && St.rSurf == "all" && q.isEmpty()) app.pickToday(shown)?.let { TodayCard(it) }
        shown.forEach { RouteCard(it, app) }
        if (shown.isEmpty()) when (St.rMode) {
            "rec" -> if (app.zr.isEmpty()) InfoCard("No se han podido cargar las rutas", "Cierra la app y vuelve a abrirla. Si sigue igual, avísame desde Perfil › Avisar de un fallo.")
                else InfoCard("Ninguna ruta con esos filtros", "Hay ${app.zoneRoutes(zone, here).size} rutas en esta zona con otro tiempo, suelo o nombre.") { Btn("Quitar filtros", { St.rDist = "all"; St.rSurf = "all"; St.rSort = "near"; St.rDiff = "all"; St.rQ = "" }, height = 44.dp, size = 14.sp) }
            "mine" -> InfoCard("Aún no tienes recorridos", "Cada carrera que guardes aparecerá aquí para repetirla, y puedes guardar cualquier ruta desde su ficha.")
            "ask" -> if (here == null) InfoCard("Buscando tu posición…", "Las rutas se calculan desde donde estás. Si no has dado permiso de ubicación, ábrelo desde Correr.")
                else if (St.askErr != null && St.askBusy == null) InfoCard("No he podido calcular rutas ahora", St.askErr!!)
            else -> if (here == null) InfoCard("Buscando tu posición…", "Los bucles se calculan desde donde estás. Si no has dado permiso de ubicación, ábrelo desde Correr.")
                else if (St.rSugErr != null && !St.rSugBusy) InfoCard("No he podido calcular bucles ahora", "Hace falta conexión; el servicio de rutas (OpenStreetMap) puede estar saturado. Las rutas recomendadas sí funcionan sin él.") {
                    Btn("Reintentar", { RtSt.seed = (RtSt.seed + 37) % 360 }, kind = BtnKind.Sec, height = 46.dp)
                }
        }
        if (St.rMode == "ask" && St.askBusy != null) repeat(maxOf(0, 3 - St.askList.size)) { Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(24.dp)).background(c.surface2).alpha(0.6f)) }
        if (St.rMode == "sug" && St.rSugBusy) repeat(3 - St.rSug.size) { Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(24.dp)).background(c.surface2).alpha(0.6f)) }
        if (St.rMode == "sug" && !St.rSugBusy && St.rSug.isNotEmpty()) Btn("Generar otros tres bucles", { RtSt.seed = (RtSt.seed + 37) % 360 }, Modifier.padding(top = 4.dp), BtnKind.Sec, 48.dp)
    }
}

/**
 * Rutas como mapa: el mapa ocupa toda la pantalla y no se mueve; arriba el buscador y los filtros en una línea;
 * abajo una hoja que se arrastra: tarjetas que se pasan de lado (la ruta elegida se resalta en el mapa) o la lista entera.
 */
@Composable
private fun RoutesMapScreen(shown: List<Route>, sel: Route?, here: LatLon?, app: App, near: String?, zone: String, listContent: @Composable ColumnScope.() -> Unit) {
    val c = Z.c
    val ins = WindowInsets.systemBars.asPaddingValues()
    val ctl = remember { MapCtl() }
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val tabsH = ins.calculateBottomPadding() + 92.dp
    val topH = ins.calculateTopPadding() + 128.dp
    val peekH = 196.dp
    val sheetH by animateDpAsState(when (St.rSheet) { 0 -> 54.dp; 1 -> peekH; else -> screenH + ins.calculateTopPadding() - topH - tabsH + 20.dp }, label = "sheet")
    val pager = androidx.compose.foundation.pager.rememberPagerState { shown.size }
    // Mapa: todas las rutas finas; la elegida, con el trazo de la marca (tinta + volt) y sus pastillas de salida y meta.
    val lines = shown.mapIndexedNotNull { i, r -> if (r.ll.size < 2 || r === sel) null else MLine(r.ll.toLL(), Ink, 3f, 0.45f, tap = i) } +
        (sel?.takeIf { it.ll.size >= 2 }?.let { r -> routeLines(r.ll.toLL()).map { it.copy(tap = shown.indexOf(r)) } } ?: emptyList())
    val dots = shown.mapIndexedNotNull { i, r -> if (r.ll.size < 2 || r === sel) null else MDot(r.startLL(), Color.White, Ink, label = "${commaNum(r.km)} km", tap = i) } +
        listOfNotNull(sel?.takeIf { it.ll.size >= 2 }?.let { r -> MDot(r.startLL(), Volt, Ink, size = 22f, label = "${commaNum(r.km)} km", tap = shown.indexOf(r)) }) +
        listOfNotNull(here?.let { MDot(it, Color(0xFF2F6BFF), Color.White, size = 16f, halo = Color(0x332F6BFF), haloW = 14f) })
    val sheetPadDp = (if (St.rSheet == 2) peekH else sheetH).value + tabsH.value + 16f
    LaunchedEffect(shown.map { it.id }) { val all = shown.filter { it.ll.size >= 2 }.map { it.startLL() } + listOfNotNull(here); if (all.isNotEmpty() && sel == null) ctl.fit(all, 40f, topH.value + 10f, 40f, sheetPadDp, 14.0) }
    LaunchedEffect(sel?.id) { sel?.takeIf { it.ll.size >= 2 }?.let { ctl.fit(it.ll.toLL(), 50f, topH.value + 20f, 70f, sheetPadDp, 16.0) } }
    LaunchedEffect(pager) { androidx.compose.runtime.snapshotFlow { pager.currentPage }.collect { if (shown.isNotEmpty()) St.rSel = it } }
    LaunchedEffect(St.rSel, shown.size) { if (St.rSel < shown.size && pager.currentPage != St.rSel) pager.animateScrollToPage(St.rSel) }
    Box(Modifier.fillMaxSize().background(c.mapLand)) {
        ZMap(Modifier.matchParentSize(), St.rLayer, lines, dots, ctl, onTap = { i -> St.rSel = i; if (St.rSheet == 0) St.rSheet = 1 }, attrBottom = sheetH + tabsH)
        // Arriba: modo + buscador + Guardadas, y los filtros en una sola línea.
        Column(Modifier.fillMaxWidth().padding(top = ins.calculateTopPadding() + 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(52.dp).shadow(10.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(c.surface).padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ZMark(16.dp, if (c.night) c.volt else c.ink)
                Box(Modifier.width(1.dp).height(26.dp).background(c.line))
                Icon(Ico.search, null, Modifier.size(18.dp), tint = c.muted)
                val focus = remember { androidx.compose.ui.focus.FocusRequester() }
                LaunchedEffect(St.rSearch) { if (St.rSearch) { St.rSearch = false; runCatching { focus.requestFocus() } } }
                androidx.compose.foundation.text.BasicTextField(St.rQ, { St.rQ = it.take(60) }, Modifier.weight(1f).focusRequester(focus), singleLine = true,
                    textStyle = bodyStyle(15.sp).copy(color = c.ink), cursorBrush = androidx.compose.ui.graphics.SolidColor(c.ink),
                    decorationBox = { inner -> Box { if (St.rQ.isEmpty()) Txt(if (St.rMode == "mine") "Buscar en tus recorridos" else "Buscar ruta, parque, zona…", size = 15.sp, color = c.muted, maxLines = 1); inner() } })
                if (St.rQ.isNotEmpty()) IconBtn(Ico.close, { St.rQ = "" }, size = 34.dp, bg = Color.Transparent, iconSize = 16.dp)
                val mine = St.rMode == "mine"
                Row(Modifier.clip(RoundedCornerShape(12.dp)).background(if (mine) c.bar else Color.Transparent).clickable { St.rMode = if (mine) "rec" else "mine"; St.rSel = 0; St.rSheet = 1 }.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(if (mine) Ico.saved else Ico.save, null, Modifier.size(18.dp), tint = if (mine) c.volt else c.ink)
                    Txt("Guardadas", size = 13.sp, weight = FontWeight.SemiBold, color = if (mine) c.onBar else c.ink)
                }
            }
            if (St.rMode == "rec") Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterDrop("Zona", (if (near != null) listOf("cerca" to "Cerca de ti") else emptyList()) + CITIES.map { it.key to it.value.name }, zone, null) { St.rZone = it }
                FilterDrop("Tiempo", listOf("all" to "Cualquiera", "t20" to "20 min", "t30" to "30 min", "t45" to "45 min", "t60" to "Más de 45"), St.rDist, "all") { St.rDist = it }
                FilterDrop("Dificultad", listOf("all" to "Cualquiera", "easy" to "Fácil", "mid" to "Media", "hard" to "Exigente"), St.rDiff, "all") { St.rDiff = it }
                FilterDrop("Superficie", listOf("all" to "Cualquiera", "tierra" to "Tierra", "asfalto" to "Asfalto", "pista" to "Pista"), St.rSurf, "all") { St.rSurf = it }
                FilterDrop("Ordenar", listOf("near" to "Más cerca", "short" to "Más corta", "long" to "Más larga", "flat" to "Más llana", "new" to "Sin estrenar"), St.rSort, "near") { St.rSort = it }
            }
        }
        // Botones del mapa y «Crear ruta», encima de la hoja.
        if (St.rSheet < 2) Column(Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = tabsH + sheetH + 12.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            var layersOpen by remember { mutableStateOf(false) }
            Box {
                MapFab(Ico.layers, "Tipo de mapa") { layersOpen = true }
                androidx.compose.material3.DropdownMenu(layersOpen, { layersOpen = false }, containerColor = c.surface) {
                    MAP_TYPES.forEach { (k, l) -> androidx.compose.material3.DropdownMenuItem({ Txt(l, weight = if (St.rLayer == k) FontWeight.SemiBold else FontWeight.Normal) }, { St.rLayer = k; layersOpen = false }) }
                }
            }
            if (sel != null && sel.ll.size >= 2) Box(Modifier.size(48.dp).shadow(8.dp, CircleShape).clip(CircleShape).background(c.surface).clickable { Nav.go(V3D, route = sel) }, contentAlignment = Alignment.Center) { Txt("3D", size = 15.sp, weight = FontWeight.Bold) }
            MapFab(Ico.locate, "Mi ubicación") { here?.let { ctl.center(it, 15.0, animate = true) } ?: toast("Buscando tu posición…") }
            Row(Modifier.height(48.dp).shadow(8.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(c.volt).clickable { St.rMode = "ask"; St.rSheet = 2 }.padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Ico.plus, null, Modifier.size(18.dp), tint = c.onVolt); Txt("Crear ruta", size = 15.sp, weight = FontWeight.SemiBold, color = c.onVolt)
            }
        }
        // Hoja de abajo: se arrastra entre mínima, tarjetas y lista completa.
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = tabsH - 20.dp).fillMaxWidth().height(sheetH + 20.dp)
            .shadow(16.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(c.bg)) {
            var drag by remember { mutableStateOf(0f) }
            Column(Modifier.fillMaxWidth().pointerInput(Unit) {
                detectVerticalDragGestures(onDragStart = { drag = 0f }, onDragEnd = {
                    if (drag < -40f) St.rSheet = minOf(2, St.rSheet + 1) else if (drag > 40f) St.rSheet = maxOf(0, St.rSheet - 1)
                }) { ch, dy -> ch.consume(); drag += dy }
            }.clickable { St.rSheet = if (St.rSheet == 2) 1 else St.rSheet + 1 }.padding(top = 10.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.line))
                if (St.rSheet != 1) Txt(when (St.rMode) { "ask" -> "Crea tu ruta con Zeta"; "sug" -> "Bucles desde aquí"; "mine" -> "${shown.size} ${pl(shown.size, "recorrido guardado", "recorridos guardados")}"; else -> "${shown.size} ${pl(shown.size, "ruta")} · gratis, sin suscripción" },
                    Modifier.padding(top = 8.dp), size = 14.sp, weight = FontWeight.SemiBold)
            }
            when (St.rSheet) {
                1 -> if (shown.isEmpty()) Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { InfoCard(if (St.rMode == "mine") "Aún no tienes recorridos" else "Ninguna ruta con esos filtros", "Desliza hacia arriba para ver más opciones.") }
                    else androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp), pageSpacing = 10.dp) { i -> PeekCard(shown[i], app) }
                2 -> Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = listContent)
                else -> {}
            }
        }
    }
}

@Composable
private fun MapFab(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val c = Z.c
    Box(Modifier.size(48.dp).shadow(8.dp, CircleShape).clip(CircleShape).background(c.surface).clickable(onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(22.dp), tint = c.ink) }
}

/** Filtro de la fila superior: píldora que se rellena de tinta cuando está activo y abre su lista. */
@Composable
private fun FilterDrop(label: String, opts: List<Pair<String, String>>, cur: String, def: String?, onSel: (String) -> Unit) {
    val c = Z.c; var open by remember { mutableStateOf(false) }
    val on = def != null && cur != def
    val text = if (def == null) opts.firstOrNull { it.first == cur }?.second ?: label else if (on) opts.firstOrNull { it.first == cur }?.second ?: label else label
    Box {
        Row(Modifier.height(38.dp).shadow(6.dp, RoundedCornerShape(19.dp)).clip(RoundedCornerShape(19.dp)).background(if (on) c.bar else c.surface).clickable { open = true }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Txt(text, size = 14.sp, weight = FontWeight.SemiBold, color = if (on) c.onBar else c.ink, maxLines = 1)
            Icon(Ico.chevron, null, Modifier.size(14.dp).graphicsLayer { rotationZ = 90f }, tint = if (on) c.volt else c.muted)
        }
        androidx.compose.material3.DropdownMenu(open, { open = false }, containerColor = c.surface) {
            opts.forEach { (k, l) -> androidx.compose.material3.DropdownMenuItem({ Txt(l, weight = if (k == cur) FontWeight.SemiBold else FontWeight.Normal, color = c.ink) }, { onSel(k); St.rSel = 0; open = false }) }
        }
    }
}

/** Tarjeta de la hoja: foto a la izquierda, datos a la derecha (como las rutas de pago de otras apps, aquí gratis). */
@Composable
private fun PeekCard(r: Route, app: App) {
    val c = Z.c; val m = app.routeMeta(r)
    val sub = when (r.src) { "rec" -> "${r.zone ?: ""}${r.away?.let { " · " + fmtAway(it) } ?: ""}"; "sug" -> "Desde tu ubicación"; else -> r.sub ?: "" }
    Row(Modifier.fillMaxWidth().height(132.dp).shadow(6.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(c.surface).clickable { Nav.go(ROUTE, route = r) }) {
        RoutePhotoThumb(r, w = 112.dp, h = 132.dp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Txt(r.name, size = 16.sp, weight = FontWeight.SemiBold, maxLines = 1, lineHeight = 1.2)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(m.diff, m.diffCls); Txt("${commaNum(r.km)} km · ${r.gain?.let { "$it m" } ?: "—"} · ${fmtDur(m.time)}", size = 13.sp, maxLines = 1)
            }
            if (sub.isNotEmpty()) Sub(sub, size = 13.sp, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ZMark(12.dp, if (c.night) c.volt else c.ink); Txt(if (r.src == "sug" || r.src == "ask") "Diseñada para ti" else "Gratis en Zancada", size = 13.sp, weight = FontWeight.SemiBold)
            }
        }
    }
}

/** «Pídesela a Zeta»: escribes o dictas cómo quieres la ruta. */
@Composable
private fun AskBox(here: LatLon?, app: App) {
    val c = Z.c; val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var seed by remember { mutableStateOf(0) }
    fun search() {
        val q = St.askQ.trim(); val h = here
        if (q.isEmpty() || St.askBusy != null) return
        if (h == null) { toast("Aún no sé dónde estás"); return }
        St.askList = emptyList(); St.askErr = null; St.askSpec = null; St.askNote = null; St.askBusy = "Entendiendo lo que pides…"
        scope.launch {
            val out = runCatching { Repo.askRoutes(h, q, app.userPace(), seed, { St.askBusy = it }, { St.askSpec = it }, { St.askList = it }) }.getOrNull()
            St.askBusy = null
            if (out == null || out.routes.isEmpty()) St.askErr = "Hace falta conexión; el servicio de rutas de OpenStreetMap puede estar saturado. Prueba otra vez en un momento."
            else if (!out.allOk) St.askNote = "Ninguna cumple todo lo que pides por aquí (" + out.miss.joinToString(", ") + "). Te enseño las más cercanas: cambia algo de la frase si quieres."
        }
    }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        res.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { St.askQ = it.take(300); search() }
    }
    ZCard(dark = true, gap = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ZetaAv(34.dp, 18)
            Column(Modifier.weight(1f)) { Txt("Dime cómo la quieres", size = 16.sp, weight = FontWeight.SemiBold); Sub("Distancia o tiempo, llana o con cuestas, parques, río, fuentes…", size = 12.sp) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Field(St.askQ, { St.askQ = it }, "Ej.: 8 km llanos pasando por el río", Modifier.weight(1f), h = 52.dp, maxLen = 300, onDone = { search() }, radius = 14.dp)
            IconBtn(Ico.mic, {
                val i = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                    .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "¿Cómo quieres la ruta?")
                if (runCatching { voice.launch(i) }.isFailure) toast("Este móvil no tiene dictado por voz: escríbelo")
            }, size = 52.dp, bg = c.volt, tint = c.onVolt)
        }
        if (St.askQ.isBlank()) Chips(wrap = true) { ASK_EXAMPLES.forEach { e -> ChipB(e, false, { St.askQ = e; search() }, shadow = false) } }
        Btn(if (St.askBusy != null) St.askBusy!! else "Buscar ruta", { search() }, height = 50.dp, size = 15.sp, enabled = St.askBusy == null && St.askQ.isNotBlank())
        St.askSpec?.let { Sub("Entendido: " + it.summary(), size = 12.sp) }
    }
    LaunchedEffect(St.askAuto, here != null) { if (St.askAuto && here != null) { St.askAuto = false; search() } }
    St.askNote?.let { InfoCard("Lo más parecido que he encontrado", it) }
    if (St.askBusy == null && St.askList.isNotEmpty()) Underline("Probar otros recorridos con lo mismo", { seed = (seed + 37) % 360; search() })
}

@Composable
fun InfoCard(title: String, sub: String, extra: (@Composable ColumnScope.() -> Unit)? = null) {
    ZCard(gap = if (extra != null) 10.dp else 6.dp) { Txt(title, weight = FontWeight.SemiBold); Sub(sub); extra?.invoke(this) }
}

@Composable
private fun TodayCard(t: Today) {
    val c = Z.c; val r = t.r
    ZCard(dark = true, gap = 12.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Lbl("Para hoy", color = c.volt); Sub(r.zone ?: "", Modifier.alpha(0.65f), color = c.onBar) }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            RoutePhotoThumb(r, bg = Color(0x2E7F7F7F), stroke = c.onBar)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Txt(r.name, size = 19.sp, weight = FontWeight.SemiBold, lineHeight = 1.15); Sub(t.why.joinToString(" · "), Modifier.alpha(0.7f), color = c.onBar) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Correrla", { St.pickedRoute = r; Nav.go(PREP) }, Modifier.weight(1f), height = 44.dp, size = 14.sp)
            Btn("Ver ficha", { Nav.go(ROUTE, route = r) }, kind = BtnKind.Ghost, height = 44.dp, size = 14.sp, bg = Color(0x2E7F7F7F), color = c.onBar, fill = false)
        }
    }
}

@Composable
fun Tag(text: String, strong: Boolean = false) {
    val c = Z.c
    Box(Modifier.clip(RoundedCornerShape(50)).border(1.dp, if (strong) c.ink else c.line, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 3.dp)) {
        Txt(text, size = 11.sp, weight = FontWeight.SemiBold, color = if (strong) c.ink else c.muted, lineHeight = 1.2)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun RouteCard(r: Route, app: App) {
    val c = Z.c; val m = app.routeMeta(r)
    val sub = when (r.src) { "rec" -> "${r.zone ?: ""}${r.away?.let { " · " + fmtAway(it) } ?: ""}"; "sug" -> "Desde tu ubicación${r.away?.takeIf { it > 0.05 }?.let { " · " + fmtAway(it) } ?: ""}"; else -> r.sub ?: "" }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.surface).clickable { Nav.go(ROUTE, route = r) }.padding(start = 14.dp, top = 14.dp, end = 16.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        RoutePhotoThumb(r)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Txt(r.name, size = 15.sp, weight = FontWeight.SemiBold, lineHeight = 1.2)
            if (sub.isNotEmpty()) Sub(sub, size = 14.sp)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Num(commaNum(r.km), 20.sp, unit = "km"); Sub("·"); Txt(if (r.gain == null) "—" else "${r.gain} m", size = 13.sp, weight = FontWeight.SemiBold); Sub("D+"); Sub("·"); Txt(fmtDur(m.time), size = 13.sp, weight = FontWeight.SemiBold)
            }
            FlowRow(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(m.diff, m.diffCls)
                r.surf?.let { Tag(SURF[it] ?: r.surface ?: it) }
                if (r.loop == false) Tag("Ida y vuelta")
                app.routeDone(r)?.let { d -> Tag("✓ ${d.times} ${if (d.times == 1) "vez" else "veces"}${if (d.best > 0) " · mejor " + fmtTime(d.best) else ""}", strong = true) }
            }
        }
    }
}

// ---------- Ficha de ruta ----------

@Composable
fun RouteDetailScreen(r: Route) {
    val c = Z.c; val ctx = LocalContext.current; val ins = WindowInsets.systemBars.asPaddingValues()
    val data by Repo.data.collectAsState()
    val here by Repo.here.collectAsState()
    val app = remember(data) { Repo.app() }
    val m = app.routeMeta(r); val saved = data.savedRoutes.any { it.id == r.id }
    val ll = remember(r) { r.ll.toLL() }
    val ctl = remember { MapCtl() }
    var big by remember { mutableStateOf(false) }
    var layer by remember { mutableStateOf("calles") }
    var layersOpen by remember { mutableStateOf(false) }
    var photos by remember { mutableStateOf<List<Photo>>(emptyList()) }
    var pois by remember { mutableStateOf<PoiCache?>(null) }
    var wx by remember { mutableStateOf<Wx?>(null) }
    // recorrido animado
    var flying by remember { mutableStateOf(false) }
    var flyP by remember { mutableStateOf<LatLon?>(null) }; var flyI by remember { mutableStateOf(0) }; var flyInfo by remember { mutableStateOf("") }
    val st = r.startLL(); val away = here?.let { hav(it, st) }
    val screenH = LocalConfiguration.current.screenHeightDp
    val mapH by animateDpAsState(if (big) (screenH - 180).dp else (screenH * 0.46f).dp, label = "h")
    LaunchedEffect(r.id) { ctl.fit(ll, 36f, 36f, 36f, 36f); delay(650); flying = true }
    LaunchedEffect(r.id) { if (r.ll.size > 1) photos = runCatching { Repo.photos(r) }.getOrDefault(emptyList()) }
    LaunchedEffect(r.id) { pois = runCatching { Repo.pois(r) }.getOrNull() }
    LaunchedEffect(r.id) { wx = Repo.weather(st) }
    LaunchedEffect(flying) {
        if (!flying || ll.size < 2) return@LaunchedEffect
        val cum = DoubleArray(ll.size); for (i in 1 until ll.size) cum[i] = cum[i - 1] + hav(ll[i - 1], ll[i])
        val tot = cum.last(); val dur = min(9000.0, maxOf(5000.0, tot * 0.9))
        ctl.center(ll[0], if (tot > 9000) 15.0 else 16.0)
        delay(350); val t0 = System.nanoTime()
        while (isActive && flying) {
            val k = ((System.nanoTime() - t0) / 1e6 / dur).coerceIn(0.0, 1.0)
            val e = if (k < .5) 2 * k * k else 1 - (-2 * k + 2).pow(2) / 2; val d = e * tot
            var i = 1; while (i < cum.size - 1 && cum[i] < d) i++
            val a = ll[i - 1]; val b = ll[i]; val f = ((d - cum[i - 1]) / maxOf(1e-6, cum[i] - cum[i - 1])).coerceIn(0.0, 1.0)
            val p = LatLon(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f)
            flyP = p; flyI = i; ctl.center(p)
            val ele = r.ele?.filterNotNull()?.takeIf { it.size > 3 }?.let { it[min(it.size - 1, jsRoundI(e * (it.size - 1)))] }
            flyInfo = "km ${toFixed(d / 1000, 1).replace('.', ',')} de ${toFixed(tot / 1000, 1).replace('.', ',')}${ele?.let { " · $it m" } ?: ""}"
            if (k >= 1) { delay(500); break }
            delay(16)
        }
        if (flying) { flying = false; ctl.fit(ll, 36f, 36f, 36f, 36f) }
        flyP = null
    }
    val lines = if (flyP != null) listOf(MLine(ll, Ink, 9f, 0.3f), MLine(ll, Volt, 4f, 0.35f)) + routeLines(ll.take(flyI) + flyP!!) else routeLines(ll)
    val dots = buildList {
        pois?.p?.forEach { q -> add(MDot(LatLon(q.lat, q.lon), if (q.k == "w") Color(0xFF2F80ED) else Color(0xFF7A7F78), Color.White, 12f, 2f)) }
        add(startDot(ll[0]))
        if (here != null && away != null && away < 20000) add(MDot(here!!, Volt, Ink))
        flyP?.let { add(MDot(it, Volt, Ink, 22f, 4f, halo = Volt.copy(alpha = 0.3f), haloW = 9f)) }
    }
    Box(Modifier.fillMaxSize().background(c.bg)) {
        ScreenCol(withTabs = false, gap = 0.dp, pad = PaddingValues(0.dp)) {
            Box(Modifier.fillMaxWidth().height(mapH)) {
                ZMap(Modifier.matchParentSize(), layer, lines, dots, ctl, onTouch = { if (flying) flying = false })
                if (flyP != null) ZCard(Modifier.padding(start = 14.dp, top = ins.calculateTopPadding() + 72.dp).wrapContentWidth(), dark = true, pad = PaddingValues(horizontal = 14.dp, vertical = 8.dp), radius = 14.dp) {
                    Txt(flyInfo, size = 13.sp, weight = FontWeight.SemiBold)
                }
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = ins.calculateTopPadding() + 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconBtn(Ico.back2, { Nav.back() })
                    Spacer(Modifier.weight(1f))
                    ChipB("Vista 3D", false, { Nav.go(V3D, route = r) }, Modifier.padding(end = 10.dp, top = 3.dp), bg = c.bar, fg = c.onBar)
                    IconBtn(if (saved) Ico.saved else Ico.save, {
                        if (saved) { Repo.update { d -> d.copy(savedRoutes = d.savedRoutes.filter { it.id != r.id }) }; toast("Quitada de tus recorridos") }
                        else { Repo.update { d -> d.copy(savedRoutes = listOf(Route(r.id, r.name, r.ll, r.km, r.gain, created = System.currentTimeMillis())) + d.savedRoutes) }; toast("Guardada en Mis recorridos") }
                    }, bg = if (saved) c.volt else c.surface, tint = if (saved) c.onVolt else c.ink, iconSize = 20.dp)
                }
                Row(Modifier.align(Alignment.BottomEnd).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChipB(if (flying) "■ Parar" else "▶ Recorrido", false, { flying = !flying }, bg = c.bar, fg = c.onBar)
                    ChipB("Capas", false, { layersOpen = true })
                    ChipB(if (big) "Reducir mapa" else "Ampliar mapa", false, { big = !big; if (!flying) ctl.fit(ll, 36f, 36f, 36f, 36f) })
                }
            }
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = ins.calculateBottomPadding() + 100.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column {
                    Lbl(when (r.src) { "rec" -> r.zone ?: ""; "sug" -> "Bucle generado desde tu ubicación"; else -> r.sub ?: "Recorrido" })
                    H1(r.name, Modifier.padding(top = 4.dp), size = 26.sp)
                }
                ZCard(pad = PaddingValues(horizontal = 20.dp, vertical = 18.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stat("km", commaNum(r.km), 26.sp); Stat("m D+", r.gain?.toString() ?: "—", 26.sp); Stat("tiempo", fmtDur(m.time), 26.sp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Lbl("Nivel"); Chip(m.diff, m.diffCls, modifier = Modifier.padding(top = 4.dp)) }
                    }
                }
                if (photos.isNotEmpty()) ZCard(pad = PaddingValues(top = 18.dp, bottom = 14.dp), gap = 10.dp) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) { H2("Cómo es el sitio"); Sub("${photos.size} ${pl(photos.size, "foto")}") }
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        photos.forEachIndexed { i, p -> NetImage(p.src, Modifier.size(150.dp, 110.dp).clip(RoundedCornerShape(14.dp)).background(c.surface2).clickable { St.photos = photos; St.photoI = i; St.modal = "photo" }) }
                    }
                    Sub("Fotos de la zona en Wikimedia Commons · toca para ampliar", Modifier.padding(horizontal = 20.dp), size = 11.sp)
                }
                pois?.let { Sub(poiText(it.p), Modifier.padding(horizontal = 4.dp)) }
                val ele = r.ele?.filterNotNull()
                if (ele != null && ele.size > 3) ZCard(pad = PaddingValues(horizontal = 20.dp, vertical = 18.dp), gap = 8.dp) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { H2("Perfil"); Sub("${ele.min()} – ${ele.max()} m · ${if (r.loop == false) "ida y vuelta" else "bucle"}") }
                    ElevProfile(ele)
                }
                if (r.src == "rec") ZCard(pad = PaddingValues(horizontal = 20.dp, vertical = 18.dp), gap = 8.dp) {
                    H2("${r.surface ?: ""} · ${if (r.loop == false) "ida y vuelta" else "bucle"}")
                    r.why?.let { Txt(it, size = 14.sp) }
                    r.access?.let { Sub(it) }
                }
                ZCard(pad = PaddingValues(horizontal = 20.dp, vertical = 18.dp), gap = 10.dp) {
                    H2("Cómo llegar")
                    val hist = data.runs.count { it.route?.id == r.id || "run-" + it.id == r.id }; val bestR = app.bestOnRoute(r.id)
                    val a = if (away == null) "Activa la ubicación para saber a cuánto estás de la salida." else if (away <= 60) "Estás en la salida." else if (away <= 2500) "La salida está ${fmtAway(away / 1000)} de ti: al empezar te llevo hasta allí (cuenta como calentamiento)." else "La salida está ${fmtAway(away / 1000)} de ti: mejor ir en transporte."
                    Txt(a + (bestR?.let { " La has corrido ${if (hist == 1) "una vez" else "$hist veces"}; tu mejor tiempo, ${fmtTime(it.dur)}: al repetirla te comparo en directo con esa marca." } ?: ""), size = 14.sp)
                    pois?.s?.takeIf { it.isNotEmpty() }?.let { Sub(transitText(it)) }
                    Btn("Transporte público (Google Maps)", { Share.open(ctx, "https://www.google.com/maps/dir/?api=1&destination=${st.lat},${st.lon}&travelmode=transit") }, kind = BtnKind.Sec, height = 48.dp, size = 15.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("Compartir la ruta", { Share.text(ctx, routeShareText(r), r.name) }, Modifier.weight(1f), BtnKind.Ghost)
                        Btn("GPX de la ruta", { Share.file(ctx, "zancada-ruta-${r.id.ifEmpty { "bucle" }}.gpx", "application/gpx+xml", routeGpx(r).toByteArray(), r.name) }, Modifier.weight(1f), BtnKind.Ghost)
                    }
                }
                wx?.let { w ->
                    ZCard(pad = PaddingValues(horizontal = 20.dp, vertical = 18.dp), gap = 6.dp) {
                        H2("Ahora mismo allí")
                        val h = java.time.LocalTime.now().hour
                        val light = if (w.sunset != null) (if (h in 6..19) "Anochece a las ${w.sunset}." else "Amanece a las ${w.sunrise}.") else ""
                        val night = if (r.why != null && Regex("noche|iluminad|farol", RegexOption.IGNORE_CASE).containsMatchIn(r.why!!)) "" else if ((h >= 19 || h < 7) && r.surf == "tierra") " De noche, los caminos de tierra van peor: mejor asfalto." else ""
                        Sub(wxText(w) + (if (light.isNotEmpty()) " $light" else "") + night, size = 14.sp)
                    }
                }
                val sim = withAway(app.similarRoutes(r), here)
                if (sim.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { H2("Parecidas", Modifier.padding(horizontal = 4.dp)); sim.forEach { RouteCard(it, app) } }
            }
        }
        Btn("Correr esta ruta", { St.pickedRoute = r; Nav.go(PREP) }, Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = ins.calculateBottomPadding() + 14.dp).shadow(10.dp, RoundedCornerShape(50)))
        if (layersOpen) MapTypeSheet(layer, { layer = it }) { layersOpen = false }
    }
}
