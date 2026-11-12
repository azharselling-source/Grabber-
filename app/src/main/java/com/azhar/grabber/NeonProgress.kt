package com.azhar.grabber

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View

/** Thin gradient progress bar. Set [fraction] (0..1) or switch on [indeterminate]. */
class NeonProgress(context: Context) : View(context) {

    var fraction: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var indeterminate: Boolean = false
        set(value) {
            field = value
            if (value) {
                if (!anim.isRunning) anim.start()
            } else {
                anim.cancel()
            }
            invalidate()
        }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.TRACK }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f

    private val anim: ValueAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1100
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f
        canvas.drawRoundRect(0f, 0f, w, h, r, r, trackPaint)

        val left: Float
        val right: Float
        if (indeterminate) {
            val seg = w * 0.35f
            left = -seg + (w + seg) * phase
            right = left + seg
        } else {
            left = 0f
            right = w * fraction
        }
        if (right - left > 0f) {
            fillPaint.shader = LinearGradient(
                left, 0f, right, 0f, Theme.CYAN, Theme.VIOLET, Shader.TileMode.CLAMP
            )
            canvas.save()
            canvas.clipRect(maxOf(0f, left), 0f, minOf(w, right), h)
            canvas.drawRoundRect(left, 0f, right, h, r, r, fillPaint)
            canvas.restore()
        }
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }
}
