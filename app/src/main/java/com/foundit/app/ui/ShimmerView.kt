package com.foundit.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.foundit.app.R

class ShimmerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var offset = -1f
    private var animator: ValueAnimator? = null
    private val cornerRadius: Float

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.ShimmerView)
        cornerRadius = typedArray.getDimension(R.styleable.ShimmerView_cornerRadius, 16f)
        typedArray.recycle()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator = ValueAnimator.ofFloat(-1f, 2f).apply {
            duration = 1450L
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                offset = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        val shimmerCenter = width * offset
        paint.shader = LinearGradient(
            shimmerCenter - width,
            0f,
            shimmerCenter,
            0f,
            intArrayOf(0xFF0E2028.toInt(), 0xFF1D4150.toInt(), 0xFF0E2028.toInt()),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
    }
}
