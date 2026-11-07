package mx.ipn.escom.circuito.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import mx.ipn.escom.circuito.game.E
import mx.ipn.escom.circuito.game.GameController
import mx.ipn.escom.circuito.game.N
import mx.ipn.escom.circuito.game.S
import mx.ipn.escom.circuito.game.W

/** Partícula del sistema dibujado en Canvas. */
class Particle(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var life: Float, val maxLife: Float, val color: Color, val size: Float,
)

/**
 * Efectos visuales del tablero: ángulos de giro animados por loseta y un
 * sistema de partículas (se actualiza por frame solo mientras hay partículas).
 */
class BoardFx(private val scope: CoroutineScope) {
    val angles = mutableStateListOf<Float>()
    val particles = ArrayList<Particle>()
    var frame by mutableIntStateOf(0)
    var burstId by mutableIntStateOf(0)
    var boardPx = 0f
    var grid = 1
    private val jobs = HashMap<Int, Job>()

    fun reset(n: Int, gridSize: Int) {
        jobs.values.forEach { it.cancel() }; jobs.clear()
        angles.clear(); repeat(n) { angles.add(0f) }
        particles.clear(); grid = gridSize
    }

    /** Giro con rebote: el estado ya cambió, el ángulo visual vuelve de ∓90° a 0°. */
    fun spin(i: Int, undo: Boolean, animated: Boolean) {
        if (i !in angles.indices) return
        jobs[i]?.cancel()
        if (!animated) { angles[i] = 0f; return }
        jobs[i] = scope.launch {
            animate(
                if (undo) 90f else -90f, 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 500f),
            ) { v, _ -> if (i in angles.indices) angles[i] = v }
        }
    }

    fun burstAt(i: Int, colors: List<Color>, count: Int) {
        val cell = boardPx / grid
        burst(((i % grid) + 0.5f) * cell, ((i / grid) + 0.5f) * cell, colors, count, cell * 0.05f)
    }

    fun burst(x: Float, y: Float, colors: List<Color>, count: Int, speed: Float) {
        repeat(count) {
            val a = Random.nextFloat() * 6.2831855f
            val v = speed * (3f + Random.nextFloat() * 8f)
            val life = 0.5f + Random.nextFloat() * 0.6f
            particles.add(
                Particle(x, y, cos(a) * v, sin(a) * v, life, life, colors.random(), speed * (0.6f + Random.nextFloat() * 1.2f)),
            )
        }
        if (particles.size > 400) repeat(particles.size - 400) { particles.removeAt(0) }
        burstId++
    }

    fun celebrate(colors: List<Color>) {
        val cell = boardPx / grid
        repeat(6) {
            burst(Random.nextFloat() * boardPx, Random.nextFloat() * boardPx, colors, 30, cell * 0.06f)
        }
    }

    fun step(dt: Float) {
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) { it.remove(); continue }
            p.x += p.vx * dt; p.y += p.vy * dt
            p.vy += boardPx * 0.9f * dt // gravedad leve
        }
        frame++
    }
}

/** Dibuja y recibe toques del tablero. */
@Composable
fun BoardView(controller: GameController, fx: BoardFx, glow: Boolean, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val rev = controller.rev // suscripción a cambios del juego
    val state = controller.state
    val n = state.size
    val cs = MaterialTheme.colorScheme
    val pulse by rememberInfiniteTransition().animateFloat(
        0.35f, 0.9f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
    )

    // Bucle de partículas: solo corre mientras existan.
    LaunchedEffect(fx.burstId) {
        var last = 0L
        while (fx.particles.isNotEmpty()) {
            androidx.compose.runtime.withFrameNanos { t ->
                val dt = if (last == 0L) 0.016f else ((t - last) / 1e9f).coerceAtMost(0.05f)
                last = t
                fx.step(dt)
            }
        }
    }

    val bg = cs.surfaceVariant
    val off = cs.onSurfaceVariant.copy(alpha = 0.55f)
    val on = cs.tertiary
    val sourceRing = cs.primary

    Canvas(
        modifier
            .onSizeChanged { fx.boardPx = it.width.toFloat() }
            .pointerInput(n, state) {
                detectTapGestures { pos ->
                    val cell = size.width.toFloat() / n
                    val x = (pos.x / cell).toInt()
                    val y = (pos.y / cell).toInt()
                    if (x in 0 until n && y in 0 until n) controller.rotate(y * n + x)
                }
            }
            .fillMaxSize(),
    ) {
        val cell = size.width / n
        val powered = controller.powered
        for (i in 0 until n * n) {
            val cx = (i % n) * cell
            val cy = (i / n) * cell
            val center = Offset(cx + cell / 2, cy + cell / 2)
            val isOn = i in powered
            drawRoundRect(
                if (isOn) cs.primaryContainer else bg,
                Offset(cx + cell * 0.04f, cy + cell * 0.04f), Size(cell * 0.92f, cell * 0.92f),
                CornerRadius(cell * 0.14f),
            )
            val ang = fx.angles.getOrElse(i) { 0f }
            rotate(ang, center) {
                val mask = state.tiles[i]
                val w = cell * 0.2f
                if (isOn && glow) drawPipes(center, cell, mask, on.copy(alpha = pulse * 0.45f), w * 2f)
                drawPipes(center, cell, mask, if (isOn) on else off, w)
                drawCircle(if (isOn) on else off, w * 0.9f, center)
                if (i == state.source) {
                    drawCircle(sourceRing, cell * 0.2f, center, style = Stroke(cell * 0.05f))
                    drawCircle(cs.onSurface, cell * 0.09f, center)
                }
            }
        }
        // Partículas encima de todo (leer frame las redibuja cada paso).
        @Suppress("UNUSED_VARIABLE") val f = fx.frame
        for (p in fx.particles) {
            drawCircle(p.color.copy(alpha = (p.life / p.maxLife).coerceIn(0f, 1f)), p.size, Offset(p.x, p.y))
        }
    }
}

/** Dibuja los brazos de una loseta desde el centro hacia los lados activos. */
private fun DrawScope.drawPipes(c: Offset, cell: Float, mask: Int, color: Color, width: Float) {
    val half = cell * 0.46f
    fun arm(bit: Int, to: Offset) {
        if (mask and bit != 0) drawLine(color, c, to, width, StrokeCap.Round)
    }
    arm(N, Offset(c.x, c.y - half)); arm(E, Offset(c.x + half, c.y))
    arm(S, Offset(c.x, c.y + half)); arm(W, Offset(c.x - half, c.y))
}
