package com.zancada.core

import kotlin.math.max

// ---------- Aprende en 1 minuto ----------

/** Una tarjeta: [big] es la cifra o palabra grande, [text] una o dos frases. [viz] dibuja algo propio en la app. */
data class LCard(val big: String, val title: String, val text: String, val viz: String? = null)
data class Lesson(val id: String, val title: String, val sub: String, val icon: String, val cards: List<LCard>)

val LESSONS = listOf(
    Lesson("zonas", "Zonas de pulso", "Qué significa cada color", "heart", listOf(
        LCard("5", "Cinco zonas", "Se calculan sobre tu pulso máximo. Cuanto más alta la zona, menos tiempo aguantas.", "zones"),
        LCard("Z2", "La zona que más rinde", "Puedes hablar en frases completas. Aquí debería ir el 80 % de tu semana: construye la base sin romperte.", "zones"),
        LCard("Z4", "Umbral", "Frases cortas, respiración marcada. Es el ritmo del tempo: duro pero sostenible 20–40 min.", "zones"),
        LCard("80/20", "La regla", "80 % suave, 20 % fuerte. Correr todo «a medio gas» es el error más común.", null),
    )),
    Lesson("ritmo", "Ritmo", "Min/km sin líos", "gauge", listOf(
        LCard("6:00", "Qué es el ritmo", "Minutos que tardas en un km. 6:00 /km = 10 km/h. Número más bajo = vas más rápido.", "pace"),
        LCard("+1:00", "Rodaje suave", "Alrededor de un minuto más lento que tu ritmo de 10K. Si no puedes hablar, frena.", "pace"),
        LCard("10K", "Tempo y series", "El tempo va cerca de tu ritmo de media maratón; las series, a tu ritmo de 5K o algo más rápido.", "pace"),
    )),
    Lesson("lesiones", "Lesiones típicas", "Cómo evitarlas", "shield", listOf(
        LCard("10 %", "Sube poco a poco", "No subas más de un 10 % los km de una semana a otra. Zancada te avisa si te pasas.", null),
        LCard("Rodilla", "Rodilla del corredor", "Dolor alrededor de la rótula. Suele venir de subir carga rápido y glúteo débil: fuerza 2 días por semana.", "body"),
        LCard("Tibia", "Periostitis", "Dolor en la cara interna de la tibia. Baja km, corre en blando y revisa zapatillas.", "body"),
        LCard("Pie", "Fascitis plantar", "Dolor en el talón al levantarte. Estira gemelo y sóleo y no corras con dolor punzante.", "body"),
        LCard("STOP", "Cuándo parar", "Dolor que empeora al correr, cojeas o dura más de 3 días: descansa y consulta a un profesional.", null),
    )),
    Lesson("zapas", "Zapatillas", "Elegir y cuidar", "shoe", listOf(
        LCard("700", "Vida útil", "Entre 600 y 800 km. Añádelas en Perfil y Zancada cuenta los km por ti.", "shoe"),
        LCard("Tarde", "Pruébalas por la tarde", "El pie está algo más hinchado, como al correr. Deja un dedo de hueco delante.", "shoe"),
        LCard("2", "Rota dos pares", "Alternar pares reparte el impacto y la espuma recupera entre usos.", "shoe"),
    )),
    Lesson("tecnica", "Técnica", "Correr ligero", "steps", listOf(
        LCard("170+", "Cadencia", "Pasos por minuto. Subirla un 5 % acorta la zancada y reduce el impacto en rodillas.", "cadence"),
        LCard("Bajo", "Pisa bajo la cadera", "Si el pie cae muy por delante del cuerpo, frenas en cada paso.", null),
        LCard("90°", "Brazos", "Codos a unos 90°, manos relajadas, de la cadera al pecho. Sin cruzar por delante.", null),
    )),
    Lesson("descanso", "Descanso", "También entrena", "moon", listOf(
        LCard("7–9 h", "Sueño", "Es cuando el cuerpo asimila el entreno. Menos de 7 h seguidas = más riesgo de lesión.", null),
        LCard("1–2", "Días sin correr", "Al menos uno o dos a la semana. Caminar o estirar cuenta como descanso.", null),
        LCard("48 h", "Tras una sesión dura", "Deja dos días suaves entre series o tempo para llegar fresco a la siguiente.", null),
    )),
)

// ---------- Nutrición según el contexto ----------

data class Tip(val title: String, val text: String, val icon: String)

/**
 * Un consejo de comida o bebida según lo que toca: mañana largo o carrera, calor, frío, recuperar tras una larga.
 * [tempC] = temperatura actual si la hay. Siempre devuelve uno (si no hay contexto, uno general que rota por día).
 */
fun App.fuelTip(tempC: Long?): Tip {
    val today = dayStart(now()); val tomorrow = today + DAY
    val p = plan
    val sessions = p?.let { planSessions(it).flatten() }.orEmpty()
    val raceSoon = p?.raceDate?.let { it >= today && it - today <= 3 * DAY }
    if (raceSoon == true) return Tip("Carrera a la vista", "Estos días, un poco más de hidratos (arroz, pasta, pan) y nada nuevo el día de la carrera: desayuna lo de siempre 2–3 h antes.", "bolt")
    val tmr = sessions.firstOrNull { dayStart(it.date) == tomorrow }
    if (tmr != null && (tmr.type == "largo" || tmr.type == "carrera") && tmr.m >= 12000)
        return Tip("Mañana toca ${if (tmr.type == "largo") "largo" else "carrera"}", "Cena con hidratos (pasta, arroz, patata) y bebe agua durante el día. Si pasa de 75 min, lleva un gel o unos dátiles.", "moon")
    val last = runs.firstOrNull()
    if (last != null && now() - (last.start + (last.dur * 1000).toLong()) < 2 * 3600_000L && last.dist >= 10000)
        return Tip("Recupera bien", "En la próxima hora, algo con hidratos y proteína: yogur con fruta, un bocadillo o leche con cacao. Y agua.", "heart")
    if (tempC != null && tempC >= 26) return Tip("Hace calor (${tempC} °C)", "Bebe 400–500 ml una hora antes, sal temprano o al atardecer y lleva agua si vas más de 45 min.", "sun")
    if (tempC != null && tempC <= 5) return Tip("Hace frío (${tempC} °C)", "Con frío también se suda: bebe igual. Un desayuno caliente y ligero 1–2 h antes sienta mejor.", "cloud")
    val tdy = sessions.firstOrNull { dayStart(it.date) == today && it.run == null }
    if (tdy != null && (tdy.type == "series" || tdy.type == "tempo")) return Tip("Hoy hay calidad", "Come algo ligero 2–3 h antes (tostada con plátano, por ejemplo). Con el estómago lleno las series se hacen eternas.", "bolt")
    val gen = listOf(
        Tip("Agua, sin obsesión", "Bebe cuando tengas sed y mira la orina: clarita es buena señal. Más de una hora con calor: añade sales.", "drop"),
        Tip("Hidratos, tu gasolina", "Si corres 4 o más días, no les tengas miedo: arroz, pasta, pan, patata o avena.", "bolt"),
        Tip("Proteína repartida", "Algo de proteína en cada comida (huevos, legumbres, pescado, yogur) ayuda a reparar el músculo.", "heart"),
        Tip("Antes de correr", "Si sales nada más levantarte y es suave, puedes ir en ayunas. Si es largo o duro, mejor algo ligero.", "sun"),
    )
    return gen[(today / DAY % gen.size).toInt()]
}

// ---------- Clips de 15 s ----------

/** Ejercicio en bucle: [anim] elige el dibujo animado, [secs] o [reps] lo que dura, [cue] cómo hacerlo en una frase. */
data class Drill(val id: String, val name: String, val group: String, val anim: String, val dose: String, val cue: String)

val DRILL_GROUPS = listOf("calentar" to "Calentar", "tecnica" to "Técnica", "fuerza" to "Fuerza")

val DRILLS = listOf(
    Drill("rodillas", "Rodillas arriba", "calentar", "knees", "20 s", "Sube rodillas a la altura de la cadera, rápido y sobre la punta del pie."),
    Drill("talones", "Talones al glúteo", "calentar", "heels", "20 s", "Lleva el talón hacia el glúteo, rodillas mirando al suelo."),
    Drill("pendulo", "Balanceo de pierna", "calentar", "swing", "10 por pierna", "Apóyate en algo y balancea la pierna adelante y atrás, sin forzar."),
    Drill("skipping", "Skipping", "tecnica", "skip", "2 × 20 m", "Pasos cortos y rápidos, braceo activo, apoyo bajo la cadera."),
    Drill("braceo", "Braceo", "tecnica", "arms", "30 s", "Codos a 90°, de la cadera al pecho, hombros relajados."),
    Drill("cadencia", "Pasos rápidos", "tecnica", "run", "4 × 15 s", "Corre suave contando pasos: busca 3 por segundo (unos 180 por minuto)."),
    Drill("sentadilla", "Sentadilla", "fuerza", "squat", "3 × 12", "Pies al ancho de hombros, cadera atrás, rodillas en línea con los pies."),
    Drill("zancada", "Zancada", "fuerza", "lunge", "3 × 10 por pierna", "Paso largo, baja la rodilla de atrás casi al suelo, tronco recto."),
    Drill("puente", "Puente de glúteo", "fuerza", "bridge", "3 × 15", "Tumbado boca arriba, sube la cadera apretando glúteo, sin arquear la espalda."),
    Drill("gemelo", "Elevación de talones", "fuerza", "calf", "3 × 15", "De puntillas despacio y baja en 3 segundos. Protege el tendón de Aquiles."),
)

// ---------- Tu mes en Zancada ----------

data class MonthWrap(
    val from: Long, val label: String, val km: Double, val runs: Int, val dur: Double, val gain: Int,
    val longest: Run?, val fastest: Run?, val bestDow: Int?, val bestHour: Int?, val days: Int, val prevKm: Double, val dayKm: DoubleArray,
)

/** Inicio (día 1, 00:00) del mes de [ts], [back] meses atrás. */
fun App.monthStart(ts: Long, back: Int = 0): Long = es.z(ts).toLocalDate().withDayOfMonth(1).minusMonths(back.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()

/** Resumen del mes que empieza en [from]. null si ese mes no hubo carreras. */
fun App.monthWrap(from: Long): MonthWrap? {
    val to = es.z(from).toLocalDate().plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val rs = runs.filter { it.start in from until to }
    if (rs.isEmpty()) return null
    val prevFrom = es.z(from).toLocalDate().minusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val prev = runs.filter { it.start in prevFrom until from }.sumOf { it.dist } / 1000
    val nDays = es.z(from).toLocalDate().lengthOfMonth()
    val dayKm = DoubleArray(nDays); rs.forEach { r -> val d = es.z(r.start).dayOfMonth - 1; if (d in 0 until nDays) dayKm[d] += r.dist / 1000 }
    val byDow = rs.groupBy { es.dow(it.start) }.maxByOrNull { it.value.size }?.takeIf { it.value.size >= 2 }?.key
    val byHour = rs.groupBy { es.hour(it.start) }.maxByOrNull { it.value.size }?.takeIf { it.value.size >= 2 }?.key
    val fastest = rs.filter { it.dist >= 3000 && it.paceSec().isFinite() }.minByOrNull { it.paceSec() }
    return MonthWrap(from, cap(es.monYear(from)), rs.sumOf { it.dist } / 1000, rs.size, rs.sumOf { it.dur }, rs.sumOf { it.gain ?: 0 },
        rs.maxByOrNull { it.dist }, fastest, byDow, byHour, dayKm.count { it > 0 }, prev, dayKm)
}

/** El mes que conviene enseñar en Inicio: el anterior durante los 7 primeros días del mes. */
fun App.wrapToShow(): MonthWrap? {
    val d = es.z(now()).dayOfMonth
    return if (d <= 7) monthWrap(monthStart(now(), 1)) else null
}

/** Frase final del resumen: compara con el mes anterior. */
fun MonthWrap.verdict(): String = when {
    prevKm <= 0 -> "Tu primer mes con Zancada. Esto solo acaba de empezar."
    km >= prevKm * 1.1 -> "Un ${max(1, ((km / prevKm - 1) * 100).toInt())} % más que el mes anterior. Vas para arriba."
    km >= prevKm * 0.9 -> "Igual de constante que el mes anterior. Así se mejora."
    else -> "Algo menos que el mes anterior. Descansar también es entrenar."
}
