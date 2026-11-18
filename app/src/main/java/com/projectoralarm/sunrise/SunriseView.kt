package com.projectoralarm.sunrise

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Full-screen sunrise and pitch-black night canvas tailored for projectors.
 */
class SunriseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private enum class Mode {
        DASHBOARD,
        BLACKOUT,
        SUNRISE
    }

    private data class SkyKeyframe(
        val stop: Float,
        val topColor: Int,
        val midColor: Int,
        val horizonColor: Int
    )

    private data class BirdSpec(
        val startXFraction: Float,
        val baseYFraction: Float,
        val speed: Float,
        val scale: Float,
        val flapSpeed: Float,
        val phaseOffset: Float
    )

    private val skyKeyframes = listOf(
        SkyKeyframe(0.00f, 0xFF000000.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt()),
        SkyKeyframe(0.20f, 0xFF0B1026.toInt(), 0xFF141B3A.toInt(), 0xFF2D193E.toInt()),
        SkyKeyframe(0.45f, 0xFF221F4B.toInt(), 0xFF4A1942.toInt(), 0xFFB84A39.toInt()),
        SkyKeyframe(0.72f, 0xFF33679B.toInt(), 0xFFFF8C42.toInt(), 0xFFFFD166.toInt()),
        SkyKeyframe(1.00f, 0xFF4BA3D9.toInt(), 0xFF72C6EF.toInt(), 0xFFFFF5BA.toInt())
    )

    private val birds = listOf(
        BirdSpec(0.12f, 0.28f, 0.035f, 1.0f, 3.6f, 0.0f),
        BirdSpec(0.22f, 0.24f, 0.038f, 0.78f, 4.1f, 1.2f),
        BirdSpec(0.05f, 0.34f, 0.030f, 0.88f, 3.3f, 2.5f),
        BirdSpec(0.42f, 0.20f, 0.028f, 0.65f, 4.4f, 0.8f),
        BirdSpec(0.55f, 0.31f, 0.032f, 0.72f, 3.8f, 3.1f)
    )

    private val starPoints = floatArrayOf(
        0.08f, 0.14f, 0.19f, 0.22f, 0.31f, 0.11f, 0.44f, 0.18f,
        0.58f, 0.09f, 0.69f, 0.25f, 0.81f, 0.13f, 0.91f, 0.21f,
        0.14f, 0.38f, 0.27f, 0.33f, 0.63f, 0.36f, 0.86f, 0.39f
    )

    private var mode = Mode.DASHBOARD
    private var sunriseProgress = 0f
    private var blackoutStartedAtMs = 0L
    private var sunriseStartedAtMs = 0L
    private var alarmTimeText = "07:00"
    private var currentClockText = "07:00"
    private var showDismissHint = false

    private val skyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sunGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sunCorePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hillBackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val hillFrontPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val birdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF333333.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    private val backHillPath = Path()
    private val frontHillPath = Path()
    private val birdPath = Path()
    private val gradientPositions = floatArrayOf(0f, 0.55f, 1f)

    fun enterDashboard() {
        mode = Mode.DASHBOARD
        sunriseProgress = 0f
        showDismissHint = false
        invalidate()
    }

    fun enterBlackout(alarmTime: String) {
        mode = Mode.BLACKOUT
        sunriseProgress = 0f
        alarmTimeText = alarmTime
        blackoutStartedAtMs = SystemClock.elapsedRealtime()
        showDismissHint = false
        invalidate()
    }

    fun updateSunrise(progress: Float, clockText: String, showHint: Boolean) {
        if (mode != Mode.SUNRISE) {
            sunriseStartedAtMs = SystemClock.elapsedRealtime()
        }
        mode = Mode.SUNRISE
        sunriseProgress = progress.coerceIn(0f, 1f)
        currentClockText = clockText
        showDismissHint = showHint
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildLandscapePaths(w.toFloat(), h.toFloat())
        hintPaint.textSize = (h * 0.032f).coerceIn(18f, 34f)
        clockPaint.textSize = (h * 0.16f).coerceIn(64f, 160f)
        subtitlePaint.textSize = (h * 0.036f).coerceIn(20f, 38f)
    }

    private fun rebuildLandscapePaths(w: Float, h: Float) {
        backHillPath.reset()
        backHillPath.moveTo(0f, h)
        backHillPath.lineTo(0f, h * 0.87f)
        backHillPath.cubicTo(
            w * 0.22f, h * 0.78f,
            w * 0.38f, h * 0.90f,
            w * 0.58f, h * 0.84f
        )
        backHillPath.cubicTo(
            w * 0.75f, h * 0.79f,
            w * 0.88f, h * 0.87f,
            w, h * 0.83f
        )
        backHillPath.lineTo(w, h)
        backHillPath.close()

        frontHillPath.reset()
        frontHillPath.moveTo(0f, h)
        frontHillPath.lineTo(0f, h * 0.91f)
        frontHillPath.cubicTo(
            w * 0.26f, h * 0.95f,
            w * 0.44f, h * 0.85f,
            w * 0.68f, h * 0.91f
        )
        frontHillPath.cubicTo(
            w * 0.84f, h * 0.95f,
            w * 0.93f, h * 0.89f,
            w, h * 0.90f
        )
        frontHillPath.lineTo(w, h)
        frontHillPath.close()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        when (mode) {
            Mode.BLACKOUT -> drawBlackout(canvas, w, h)
            Mode.DASHBOARD -> drawDashboardBackdrop(canvas, w, h)
            Mode.SUNRISE -> drawSunriseScene(canvas, w, h)
        }
    }

    private fun drawBlackout(canvas: Canvas, w: Float, h: Float) {
        // Pure pitch black so the projector emits minimum possible light
        canvas.drawColor(Color.BLACK)

        val elapsed = SystemClock.elapsedRealtime() - blackoutStartedAtMs
        if (elapsed < 4000L) {
            val hint = context.getString(R.string.blackout_hint, alarmTimeText)
            canvas.drawText(hint, w * 0.5f, h * 0.52f, hintPaint)
            postInvalidateDelayed(500L)
        }
    }

    private fun drawDashboardBackdrop(canvas: Canvas, w: Float, h: Float) {
        skyPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            0xFF060914.toInt(),
            0xFF131D38.toInt(),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, skyPaint)

        starPaint.alpha = 110
        for (i in starPoints.indices step 2) {
            canvas.drawCircle(starPoints[i] * w, starPoints[i + 1] * h, 2.2f, starPaint)
        }

        hillBackPaint.color = 0xFF090E1B.toInt()
        hillFrontPaint.color = 0xFF04070E.toInt()
        canvas.drawPath(backHillPath, hillBackPaint)
        canvas.drawPath(frontHillPath, hillFrontPaint)
    }

    private fun drawSunriseScene(canvas: Canvas, w: Float, h: Float) {
        val (topColor, midColor, horizonColor) = interpolateSky(sunriseProgress)
        skyPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(topColor, midColor, horizonColor),
            gradientPositions,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, skyPaint)

        // Subtle fading stars early in dawn
        if (sunriseProgress in 0.05f..0.48f) {
            val starAlpha = ((1f - (sunriseProgress - 0.05f) / 0.43f) * 140f).toInt().coerceIn(0, 140)
            starPaint.alpha = starAlpha
            for (i in starPoints.indices step 2) {
                canvas.drawCircle(starPoints[i] * w, starPoints[i + 1] * h, 2.0f, starPaint)
            }
        }

        // Smooth sun trajectory from below horizon to upper-center sky
        val eased = sunriseProgress * sunriseProgress * (3f - 2f * sunriseProgress)
        val sunX = w * 0.5f
        val sunY = h * (1.15f - 0.85f * eased)
        val sunRadius = min(w, h) * 0.115f

        if (sunriseProgress > 0.02f) {
            val outerGlowRadius = sunRadius * (2.6f + 2.2f * eased)
            val glowAlpha = (sunriseProgress * 220f).toInt().coerceIn(0, 220)
            val glowInner = lerpColor(0xFFFF6B2B.toInt(), 0xFFFFE58F.toInt(), eased)
            val glowMid = (glowAlpha / 2 shl 24) or (glowInner and 0x00FFFFFF)
            val glowEdge = glowInner and 0x00FFFFFF

            sunGlowPaint.shader = RadialGradient(
                sunX, sunY, outerGlowRadius,
                intArrayOf((glowAlpha shl 24) or (glowInner and 0x00FFFFFF), glowMid, glowEdge),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(sunX, sunY, outerGlowRadius, sunGlowPaint)

            val discColor = lerpColor(0xFFFF5E24.toInt(), 0xFFFFF8D6.toInt(), eased)
            sunCorePaint.color = discColor
            sunCorePaint.alpha = ((sunriseProgress * 4f).coerceAtMost(1f) * 255f).toInt()
            canvas.drawCircle(sunX, sunY, sunRadius, sunCorePaint)
        }

        // Landscape silhouette hills over lower sun edge
        hillBackPaint.color = lerpColor(0xFF000000.toInt(), 0xFF1F273B.toInt(), eased)
        hillFrontPaint.color = lerpColor(0xFF000000.toInt(), 0xFF0F1422.toInt(), eased)
        canvas.drawPath(backHillPath, hillBackPaint)
        canvas.drawPath(frontHillPath, hillFrontPaint)

        // Flying bird silhouettes emerge after dawn breaks
        if (sunriseProgress > 0.25f) {
            drawFlyingBirds(canvas, w, h)
        }

        // Digital clock and gentle dismiss hint
        if (sunriseProgress > 0.08f) {
            val textAlpha = (((sunriseProgress - 0.08f) / 0.25f).coerceIn(0f, 1f) * 235f).toInt()
            clockPaint.alpha = textAlpha
            clockPaint.setShadowLayer(12f, 0f, 3f, Color.argb((textAlpha * 0.55f).toInt(), 0, 0, 0))
            canvas.drawText(currentClockText, w * 0.5f, h * 0.19f, clockPaint)

            if (showDismissHint) {
                subtitlePaint.alpha = (textAlpha * 0.85f).toInt()
                subtitlePaint.setShadowLayer(8f, 0f, 2f, Color.argb((textAlpha * 0.6f).toInt(), 0, 0, 0))
                canvas.drawText(
                    context.getString(R.string.sunrise_dismiss_hint),
                    w * 0.5f,
                    h * 0.26f,
                    subtitlePaint
                )
            }
        }
    }

    private fun drawFlyingBirds(canvas: Canvas, w: Float, h: Float) {
        val birdAlpha = (((sunriseProgress - 0.25f) / 0.25f).coerceIn(0f, 1f) * 190f).toInt()
        birdPaint.color = Color.argb(birdAlpha, 22, 26, 38)
        birdPaint.strokeWidth = (min(w, h) * 0.0045f).coerceIn(2.5f, 5.5f)

        val elapsedSec = (SystemClock.elapsedRealtime() - sunriseStartedAtMs) / 1000f
        val baseWingSpan = min(w, h) * 0.024f

        for (bird in birds) {
            val xFraction = (bird.startXFraction + elapsedSec * bird.speed) % 1.16f - 0.08f
            val bx = xFraction * w
            val bob = sin((elapsedSec * 1.4f + bird.phaseOffset).toDouble()).toFloat() * (h * 0.012f)
            val by = bird.baseYFraction * h + bob
            val wingSpan = baseWingSpan * bird.scale
            val flap = sin((elapsedSec * bird.flapSpeed * 2.0 + bird.phaseOffset * 3.0)).toFloat() * (wingSpan * 0.45f)

            birdPath.reset()
            birdPath.moveTo(bx - wingSpan, by - flap)
            birdPath.quadTo(bx - wingSpan * 0.45f, by - wingSpan * 0.42f - flap * 0.3f, bx, by)
            birdPath.quadTo(bx + wingSpan * 0.45f, by - wingSpan * 0.42f - flap * 0.3f, bx + wingSpan, by - flap)
            canvas.drawPath(birdPath, birdPaint)
        }
    }

    private fun interpolateSky(progress: Float): Triple<Int, Int, Int> {
        for (i in 0 until skyKeyframes.size - 1) {
            val a = skyKeyframes[i]
            val b = skyKeyframes[i + 1]
            if (progress <= b.stop) {
                val span = (b.stop - a.stop).coerceAtLeast(0.0001f)
                val t = ((progress - a.stop) / span).coerceIn(0f, 1f)
                return Triple(
                    lerpColor(a.topColor, b.topColor, t),
                    lerpColor(a.midColor, b.midColor, t),
                    lerpColor(a.horizonColor, b.horizonColor, t)
                )
            }
        }
        val last = skyKeyframes.last()
        return Triple(last.topColor, last.midColor, last.horizonColor)
    }

    private fun lerpColor(startColor: Int, endColor: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        val a1 = (startColor ushr 24) and 0xFF
        val r1 = (startColor ushr 16) and 0xFF
        val g1 = (startColor ushr 8) and 0xFF
        val b1 = startColor and 0xFF

        val a2 = (endColor ushr 24) and 0xFF
        val r2 = (endColor ushr 16) and 0xFF
        val g2 = (endColor ushr 8) and 0xFF
        val b2 = endColor and 0xFF

        val a = (a1 + (a2 - a1) * f).toInt()
        val r = (r1 + (r2 - r1) * f).toInt()
        val g = (g1 + (g2 - g1) * f).toInt()
        val b = (b1 + (b2 - b1) * f).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
