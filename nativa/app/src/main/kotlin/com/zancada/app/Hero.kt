package com.zancada.app

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Foto de portada del plan de entrenamiento: corredor a contraluz en carretera.
 * Foto de Lucas Favre en Unsplash (licencia Unsplash: uso libre, sin pedir permiso).
 * Se descarga una vez y se guarda en la caché de la app; sin red, la tarjeta se ve oscura como antes.
 */
object Hero {
    const val URL = "https://images.unsplash.com/photo-1502224562085-639556652f33?fm=jpg&q=70&w=1200&fit=crop"
    const val CREDIT = "Foto: Lucas Favre · Unsplash"
    private var mem: ImageBitmap? = null

    suspend fun get(ctx: Context): ImageBitmap? {
        mem?.let { return it }
        val f = File(ctx.cacheDir, "hero-plan-v1.jpg")
        val bytes = withContext(Dispatchers.IO) { f.takeIf { it.exists() && it.length() > 0 }?.readBytes() }
            ?: Net.bytes(URL, 15_000)?.also { b -> withContext(Dispatchers.IO) { runCatching { f.writeBytes(b) } } }
            ?: return null
        return withContext(Dispatchers.Default) { runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull() }?.also { mem = it }
    }
}

private val mono = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** Tarjeta oscura con la foto en blanco y negro detrás y un degradado para que se lea el texto. */
@Composable
fun PhotoCard(height: Dp = 230.dp, radius: Dp = 28.dp, onClick: (() -> Unit)? = null, credit: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val c = Z.c; val ctx = LocalContext.current
    var img by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(Unit) { img = Hero.get(ctx.applicationContext) }
    CompositionLocalProvider(LocalDark provides true) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = height).clip(RoundedCornerShape(radius)).background(c.bar)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        ) {
            img?.let { Image(it, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop, alignment = Alignment.Center, colorFilter = mono) }
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color(0x33000000), 0.45f to Color(0x66000000), 1f to Color(0xE6000000))))
            if (credit && img != null) Txt(Hero.CREDIT, Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 16.dp), size = 9.sp, color = Color(0x99FFFFFF))
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(20.dp).padding(top = if (img != null) 70.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp), content = content,
            )
        }
    }
}
