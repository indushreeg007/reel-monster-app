package com.reelmonster.app.monster

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.PI
import kotlin.math.sin

enum class Stage(val label: String, val min: Int, val body: Int, val scale: Float, val plump: Float) {
    BABY("Tiny baby monster", 0, 0xFFFFC8DD.toInt(), 0.50f, 1.00f),
    SMALL("Small cute monster", 6, 0xFFE2CFEA.toInt(), 0.62f, 1.00f),
    MEDIUM("Medium monster", 16, 0xFFBDE0FE.toInt(), 0.74f, 1.05f),
    CHUBBY("Chubby monster", 31, 0xFFFFD6A5.toInt(), 0.85f, 1.18f),
    BIG("Big dramatic monster", 51, 0xFFCDB4DB.toInt(), 0.95f, 1.20f),
    GIANT("Giant exhausted monster", 76, 0xFFB8B8D1.toInt(), 1.05f, 1.30f);

    companion object { fun of(c: Int) = values().last { c >= it.min } }
}

enum class Mood { HAPPY, SMUG, SHOCK, TIRED }

fun moodFor(c: Int) = when { c <= 15 -> Mood.HAPPY; c <= 30 -> Mood.SMUG; c <= 50 -> Mood.SHOCK; else -> Mood.TIRED }

/** Smooth growth: interpolates between this stage's size and the next stage's size. */
fun scaleFor(c: Int): Float {
    val s = Stage.of(c)
    val next = Stage.values().getOrNull(s.ordinal + 1) ?: return s.scale
    val t = (c - s.min).toFloat() / (next.min - s.min)
    return s.scale + (next.scale - s.scale) * t
}

class MonsterView @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : View(c, a) {
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var scale = 0.5f
    private var count = -1
    private var phase = 0f
    private var jump = 0f
    private var shake = 0f
    private var blink = false
    private var yawn = false
    private val ink = 0xFF4A3F5C.toInt()

    private val loop = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
        duration = 1600; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }
    private val blinker = object : Runnable {
        override fun run() {
            blink = true; invalidate()
            postDelayed({ blink = false; invalidate() }, 160)
            if (moodFor(count.coerceAtLeast(0)) == Mood.TIRED && (0..2).random() == 0) yawnNow()
            postDelayed(this, (2500L..5000L).random())
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); loop.start(); postDelayed(blinker, 2000) }
    override fun onDetachedFromWindow() { loop.cancel(); removeCallbacks(blinker); super.onDetachedFromWindow() }
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension((140 * d).toInt(), (150 * d).toInt())

    fun setCount(n: Int, animate: Boolean = true) {
        if (n == count) return
        val old = count; count = n
        val target = scaleFor(n)
        if (animate && old >= 0) {
            ValueAnimator.ofFloat(scale, target).apply {
                duration = 900; interpolator = OvershootInterpolator(2f)
                addUpdateListener { scale = it.animatedValue as Float; invalidate() }
            }.start()
        } else { scale = target; invalidate() }
    }

    private fun anim(ms: Long, onFrame: (Float) -> Unit, onEnd: () -> Unit) =
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ms
            addUpdateListener { onFrame(it.animatedFraction); invalidate() }
            addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(a: Animator) { onEnd() } })
        }.start()

    fun celebrate() = anim(750, { jump = sin(it * PI).toFloat() * 30 * d }, { jump = 0f })
    fun shakeHead() = anim(600, { shake = sin(it * PI * 6).toFloat() * 10f * (1 - it) }, { shake = 0f })
    fun yawnNow() { yawn = true; invalidate(); postDelayed({ yawn = false; invalidate() }, 1300) }
    fun hop() = anim(450, { jump = sin(it * PI).toFloat() * 14 * d }, { jump = 0f })

    override fun onDraw(cv: Canvas) {
        val n = count.coerceAtLeast(0)
        val stage = Stage.of(n); val mood = moodFor(n)
        val cx = width / 2f
        val ground = height - 12 * d
        val r = 44 * d * scale
        val rx = r * stage.plump
        val legH = 8 * d
        val cy = ground - legH - r
        val bob = sin(phase) * 3 * d

        // shadow
        p.style = Paint.Style.FILL; p.color = 0x22000000
        cv.drawOval(cx - rx * 0.8f, ground - 3 * d, cx + rx * 0.8f, ground + 3 * d, p)

        cv.save()
        cv.translate(0f, -jump + bob)
        cv.rotate(shake, cx, cy)

        // legs
        p.color = darker(stage.body)
        cv.drawOval(cx - rx * 0.5f - 9 * d, ground - legH * 2, cx - rx * 0.5f + 9 * d, ground, p)
        cv.drawOval(cx + rx * 0.5f - 9 * d, ground - legH * 2, cx + rx * 0.5f + 9 * d, ground, p)

        // horns (behind body)
        p.color = 0xFFB79CED.toInt()
        val hornH = 14 * d * (0.7f + scale * 0.4f)
        for (sgn in listOf(-1f, 1f)) {
            val path = Path().apply {
                moveTo(cx + sgn * rx * 0.30f, cy - r * 0.85f)
                lineTo(cx + sgn * rx * 0.62f, cy - r * 0.85f - hornH)
                lineTo(cx + sgn * rx * 0.72f, cy - r * 0.55f)
                close()
            }
            cv.drawPath(path, p)
        }

        // tiny hands
        p.color = darker(stage.body)
        val handY = cy + r * 0.25f + sin(phase + 1f) * 2 * d
        cv.drawCircle(cx - rx - 2 * d, handY, 7 * d * (0.7f + scale * 0.3f), p)
        cv.drawCircle(cx + rx + 2 * d, handY, 7 * d * (0.7f + scale * 0.3f), p)

        // body
        p.color = stage.body
        cv.drawOval(cx - rx, cy - r, cx + rx, cy + r, p)
        // belly
        p.color = 0x55FFFFFF
        cv.drawOval(cx - rx * 0.55f, cy + r * 0.1f, cx + rx * 0.55f, cy + r * 0.85f, p)

        // cheeks
        p.color = 0x88FF9EBB.toInt()
        cv.drawCircle(cx - rx * 0.68f, cy + r * 0.25f, r * 0.14f, p)
        cv.drawCircle(cx + rx * 0.68f, cy + r * 0.25f, r * 0.14f, p)

        drawEyes(cv, cx, cy, r, rx, mood, stage)
        drawMouth(cv, cx, cy, r, mood)

        // sweat drop for exhausted stages
        if (mood == Mood.TIRED) {
            p.style = Paint.Style.FILL; p.color = 0xFF9ED8FF.toInt()
            cv.drawOval(cx + rx * 0.85f, cy - r * 0.6f, cx + rx * 0.85f + 7 * d, cy - r * 0.6f + 11 * d, p)
        }
        cv.restore()
    }

    private fun drawEyes(cv: Canvas, cx: Float, cy: Float, r: Float, rx: Float, mood: Mood, stage: Stage) {
        val er = r * 0.24f
        val ey = cy - r * 0.12f
        for (sgn in listOf(-1f, 1f)) {
            val ex = cx + sgn * rx * 0.36f
            if (blink || yawn) {
                p.style = Paint.Style.STROKE; p.color = ink; p.strokeWidth = 2.5f * d; p.strokeCap = Paint.Cap.ROUND
                cv.drawLine(ex - er * 0.7f, ey, ex + er * 0.7f, ey, p)
                continue
            }
            p.style = Paint.Style.FILL; p.color = Color.WHITE
            cv.drawCircle(ex, ey, er, p)
            p.color = ink
            val look = if (mood == Mood.SMUG) er * 0.25f * sgn.let { 1f } else 0f
            cv.drawCircle(ex + look, ey + er * 0.1f, er * 0.55f, p)
            p.color = Color.WHITE
            cv.drawCircle(ex + look - er * 0.18f, ey - er * 0.12f, er * 0.18f, p)
            if (mood == Mood.TIRED || stage == Stage.GIANT) {
                p.color = stage.body   // heavy eyelids
                cv.drawRect(ex - er - 1, ey - er - 1, ex + er + 1, ey - er * 0.15f, p)
                p.style = Paint.Style.STROKE; p.color = ink; p.strokeWidth = 2f * d
                cv.drawLine(ex - er, ey - er * 0.15f, ex + er, ey - er * 0.15f, p)
                p.color = 0x449B72CF; cv.drawArc(ex - er, ey + er * 0.4f, ex + er, ey + er * 1.4f, 20f, 140f, false, p)
            } else if (mood == Mood.SMUG) {
                p.style = Paint.Style.FILL; p.color = stage.body
                cv.drawRect(ex - er - 1, ey - er - 1, ex + er + 1, ey - er * 0.45f, p)
            }
        }
    }

    private fun drawMouth(cv: Canvas, cx: Float, cy: Float, r: Float, mood: Mood) {
        val my = cy + r * 0.38f
        p.color = ink; p.strokeWidth = 2.6f * d; p.strokeCap = Paint.Cap.ROUND
        if (yawn) {
            p.style = Paint.Style.FILL; cv.drawOval(cx - r * 0.18f, my - r * 0.1f, cx + r * 0.18f, my + r * 0.3f, p)
            p.color = 0xFFFF8FAB.toInt(); cv.drawOval(cx - r * 0.1f, my + r * 0.1f, cx + r * 0.1f, my + r * 0.28f, p)
            return
        }
        when (mood) {
            Mood.HAPPY -> { p.style = Paint.Style.STROKE; cv.drawArc(cx - r * 0.2f, my - r * 0.15f, cx + r * 0.2f, my + r * 0.12f, 15f, 150f, false, p) }
            Mood.SMUG -> {
                p.style = Paint.Style.STROKE
                val path = Path().apply { moveTo(cx - r * 0.18f, my + r * 0.04f); quadTo(cx, my + r * 0.12f, cx + r * 0.2f, my - r * 0.04f) }
                cv.drawPath(path, p)
            }
            Mood.SHOCK -> { p.style = Paint.Style.FILL; cv.drawOval(cx - r * 0.1f, my - r * 0.06f, cx + r * 0.1f, my + r * 0.2f, p) }
            Mood.TIRED -> {
                p.style = Paint.Style.STROKE
                val path = Path().apply {
                    moveTo(cx - r * 0.2f, my + r * 0.05f)
                    quadTo(cx - r * 0.1f, my - r * 0.03f, cx, my + r * 0.05f)
                    quadTo(cx + r * 0.1f, my + r * 0.13f, cx + r * 0.2f, my + r * 0.05f)
                }
                cv.drawPath(path, p)
            }
        }
    }

    private fun darker(c: Int): Int {
        val hsv = FloatArray(3); Color.colorToHSV(c, hsv); hsv[2] *= 0.88f; return Color.HSVToColor(hsv)
    }
}
