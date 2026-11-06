package mx.ipn.escom.circuito.core

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Milisegundos epoch actuales (multiplataforma, sin librerías extra). */
@OptIn(ExperimentalTime::class)
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** mm:ss */
fun formatClock(seconds: Int): String =
    "${(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
