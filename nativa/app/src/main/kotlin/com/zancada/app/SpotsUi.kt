package com.zancada.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zancada.core.*

/** Colores de cada tipo de sitio en el mapa (relleno suave y borde). */
fun spotColor(kind: String): Color = when (kind) { "rio" -> Color(0xFF3D8BFF); "pista" -> Color(0xFFF2541B); "bosque" -> Color(0xFF2E9E5B); else -> Volt }
fun spotArea(s: Spot, tap: Int? = null, strong: Boolean = false) = spotColor(s.kind).let { k -> MArea(s.rings, k.copy(alpha = if (strong) 0.38f else 0.26f), k.copy(alpha = 0.95f), tap) }

/** Sitios cerca: mapa con las áreas y lista debajo. */
@Composable
fun SpotsBlock(here: LatLon?) {
    val c = Z.c
    val key = here?.let { "${toFixed(it.lat, 2)},${toFixed(it.lon, 2)}" }
    LaunchedEffect(key) {
        val h = here ?: return@LaunchedEffect
        if (St.spots.isNotEmpty() && St.spotsKey == key) return@LaunchedEffect
        St.spotsBusy = true
        val s = runCatching { Repo.spots(h) }.getOrDefault(emptyList())
        St.spotsBusy = false
        if (s.isNotEmpty()) { St.spots = s; St.spotsKey = key }
    }
    val list = St.spots
    val ctl = remember { MapCtl() }
    LaunchedEffect(list, here) { if (list.isNotEmpty()) ctl.fit(list.take(8).flatMap { it.rings.flatten() } + listOfNotNull(here), 24f, 24f, 24f, 24f, 16.0) else here?.let { ctl.center(it, 14.0) } }
    Box(Modifier.fillMaxWidth().height(380.dp).clip(RoundedCornerShape(28.dp))) {
        ZMap(Modifier.matchParentSize(), areas = list.mapIndexed { i, s -> spotArea(s, i) },
            dots = list.take(14).mapIndexed { i, s -> MDot(s.center, Volt, Ink, label = s.name.take(22), tap = i) } + listOfNotNull(here?.let { MDot(it, Color(0xFF2F80ED), Color.White, 16f, 3f) }),
            ctl = ctl, onTap = { i -> list.getOrNull(i)?.let { St.spot = it; Nav.go(SPOT) } })
        if (list.isNotEmpty()) Box(Modifier.align(Alignment.BottomStart).padding(12.dp).clip(RoundedCornerShape(50)).background(c.bar).padding(horizontal = 14.dp, vertical = 8.dp)) {
            Txt("${list.size} sitios · toca un área para ver la ficha", size = 12.sp, weight = FontWeight.SemiBold, color = c.onBar)
        }
    }
    when {
        here == null -> InfoCard("Buscando tu posición…", "Los sitios se buscan alrededor de donde estás.")
        St.spotsBusy && list.isEmpty() -> repeat(3) { SkeletonRow() }
        list.isEmpty() -> InfoCard("No he podido cargar los sitios", "Sin conexión. Prueba en un momento.")
        else -> list.forEach { s -> SpotCard(s, here) }
    }
}

@Composable
private fun SpotCard(s: Spot, here: LatLon?) {
    val c = Z.c
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.surface).clickable { St.spot = s; Nav.go(SPOT) }.padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(spotColor(s.kind).copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(18.dp).clip(RoundedCornerShape(50)).background(spotColor(s.kind)))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Txt(s.name, size = 15.sp, weight = FontWeight.SemiBold, maxLines = 1)
            Sub(spotSub(s) + (here?.let { " · " + fmtAway(hav(it, s.center) / 1000) } ?: ""), size = 13.sp)
        }
        Icon(Ico.chevron, null, Modifier.size(22.dp), tint = Z.muted)
    }
}

/** Ficha de un sitio: forma, entrenamientos, fotos, alrededores y rutas que pasan por él. */
@Composable
fun SpotScreen() {
    val c = Z.c; val ctx = LocalContext.current
    val s = St.spot ?: run { LaunchedEffect(Unit) { Nav.back() }; return }
    val data by Repo.data.collectAsState()
    val here by Repo.here.collectAsState()
    val app = remember(data) { Repo.app() }
    val r = remember(s.id) { s.toRoute() }
    var photos by remember { mutableStateOf<List<Photo>>(emptyList()) }
    var pois by remember { mutableStateOf<PoiCache?>(null) }
    LaunchedEffect(s.id) { photos = runCatching { Repo.photos(r) }.getOrDefault(emptyList()) }
    LaunchedEffect(s.id) { pois = runCatching { Repo.pois(r) }.getOrNull() }
    val ctl = remember { MapCtl() }
    LaunchedEffect(s.id) { ctl.fit(s.rings.flatten(), 30f, 80f, 30f, 30f, 17.0) }
    val routes = remember(s.id, app.zr.size) {
        app.zr.filter { rr -> rr.ll.size > 4 && rr.ll.count { s.contains(LatLon(it[0], it[1])) } >= rr.ll.size * 0.3 }.take(5)
    }
    val ins = WindowInsets.systemBars.asPaddingValues()
    ScreenCol(withTabs = false, gap = 14.dp, pad = PaddingValues(0.dp)) {
        Box(Modifier.fillMaxWidth().height(320.dp)) {
            ZMap(Modifier.matchParentSize(), areas = listOf(spotArea(s, strong = true)),
                dots = (pois?.p ?: emptyList()).map { q -> MDot(LatLon(q.lat, q.lon), if (q.k == "w") Color(0xFF2F80ED) else Color(0xFF7A7F78), Color.White, 12f, 2f) } + listOfNotNull(here?.let { MDot(it, Color(0xFF2F80ED), Color.White, 16f, 3f) }),
                ctl = ctl)
            Row(Modifier.padding(start = 20.dp, top = ins.calculateTopPadding() + 14.dp)) { IconBtn(Ico.back2, { Nav.back() }) }
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = ins.calculateBottomPadding() + 100.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Lbl(spotSub(s) + (here?.let { " · " + fmtAway(hav(it, s.center) / 1000) } ?: ""))
                H1(s.name, Modifier.padding(top = 4.dp), size = 26.sp)
            }
            Btn("Crear una ruta aquí con Zeta", {
                St.rMode = "ask"; St.askQ = "${if (s.kind == "pista") 5 else 6} km por ${s.name}"; St.askAuto = true; Nav.back()
            }, height = 52.dp, size = 15.sp)
            ZCard(gap = 12.dp) {
                H2("Entrenamientos que encajan")
                spotWorkouts(s, app.userPace()).forEach { w -> Column { Txt(w.title, weight = FontWeight.SemiBold); Sub(w.text, size = 13.sp) } }
            }
            if (photos.isNotEmpty()) ZCard(pad = PaddingValues(top = 18.dp, bottom = 14.dp), gap = 10.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) { H2("Cómo es"); Sub("${photos.size} ${pl(photos.size, "foto")}") }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    photos.forEachIndexed { i, p -> NetImage(p.src, Modifier.size(150.dp, 110.dp).clip(RoundedCornerShape(14.dp)).background(c.surface2).clickable { St.photos = photos; St.photoI = i; St.modal = "photo" }) }
                }
            }
            ZCard(gap = 8.dp) {
                H2("Alrededor")
                pois?.let { p -> Sub(poiText(p.p)); p.s.takeIf { it.isNotEmpty() }?.let { Sub(transitText(it)) } } ?: Sub("Buscando fuentes, aseos y transporte…")
                Btn("Cómo llegar (Google Maps)", { Share.open(ctx, "https://www.google.com/maps/dir/?api=1&destination=${s.center.lat},${s.center.lon}&travelmode=walking") }, kind = BtnKind.Sec, height = 46.dp, size = 14.sp)
            }
            if (routes.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                H2("Rutas que pasan por aquí")
                routes.forEach { RouteCard(it.copy(src = "rec"), app) }
            }
            Sub("Forma y datos del sitio: © OpenStreetMap.", size = 11.sp)
        }
    }
}
