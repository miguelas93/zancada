package com.zancada.core

// Textos fijos de la app (los mismos que la beta web).

/** Estiramientos guiados tras correr: 7 posturas de 40 s. */
val STRETCHES = listOf(
    "Gemelo izquierdo" to "Manos en una pared, pierna izquierda atrás y estirada, talón pegado al suelo. Inclínate hacia la pared.",
    "Gemelo derecho" to "Igual con la pierna derecha atrás: talón al suelo, rodilla estirada.",
    "Cuádriceps izquierdo" to "De pie, coge el pie izquierdo y llévalo hacia el glúteo, rodillas juntas. Apóyate si lo necesitas.",
    "Cuádriceps derecho" to "Ahora el pie derecho hacia el glúteo, cadera un poco hacia delante.",
    "Isquio izquierdo" to "Talón izquierdo sobre un bordillo o banco bajo, pierna estirada. Inclínate desde la cadera con la espalda recta.",
    "Isquio derecho" to "Cambia: talón derecho arriba, espalda recta, sin forzar.",
    "Glúteo y cadera" to "Sentado o tumbado, cruza un tobillo sobre la rodilla contraria y acércala al pecho. Cambia de lado a la mitad.",
)

/** Calentamiento dinámico antes de correr: 6 ejercicios de 30 s. */
val WARMUP = listOf(
    "Andar rápido" to "Camina con brazos activos y zancada amplia para subir pulsaciones.",
    "Elevación de rodillas" to "En el sitio, sube rodillas a la altura de la cadera con ritmo suave.",
    "Talones al glúteo" to "Trota en el sitio llevando los talones hacia atrás, sin forzar.",
    "Balanceo de piernas" to "Apóyate y balancea una pierna adelante y atrás; cambia a la mitad.",
    "Círculos de cadera" to "Manos en la cintura, círculos amplios hacia un lado y luego al otro.",
    "Skipping suave" to "Pasitos cortos y rápidos, apoyando la parte delantera del pie.",
)

/** Novedades de la app nativa (se enseñan una vez por versión a quien ya usaba la app). */
const val NEWS_VER = "n1"
val NEWS = listOf(
    "App nativa" to "Zancada ya es una app de Android de verdad, no una web dentro de una app: más rápida y fiable.",
    "GPS más preciso" to "el ruido del GPS ya no suma metros de más: la distancia sale hasta un 5 % más ajustada (bajo árboles, más).",
    "Desnivel real" to "el desnivel ya no se infla con el ruido del altímetro.",
    "Pantalla apagada" to "la carrera sigue grabando con el móvil bloqueado y se recupera si Android cierra la app.",
    "Pausa al terminar" to "si terminas estando en pausa, el tiempo ya sale bien.",
    "Todo lo de la beta" to "plan, rutas, Zeta, insignias, zapatillas, copia de seguridad y compartir, igual que antes.",
)

const val COACH_HELLO = "Soy Zeta, tu entrenador. Conozco tu plan y tus carreras: pregúntame lo que quieras o toca una de las sugerencias."

const val INVITE_TEXT = "Estoy probando Zancada, una app para correr hecha en Madrid: planes, rutas y guía por voz, sin cuentas ni anuncios.\nWeb: https://miguelas93.github.io/zancada/\nAndroid (APK): https://github.com/miguelas93/zancada/releases/download/apk-nativa/zancada-nativa.apk"
