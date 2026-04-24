package com.example.gpxeditor.view.customviews

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import android.content.res.Configuration
import com.example.gpxeditor.R

class DistanceChartView(context: Context, private val distance1: Double, private val distance2: Double) : View(context) {

    private val isDarkMode = isDarkMode(context)

    private val paint1 = Paint().apply {
        color = if (isDarkMode) ContextCompat.getColor(context, R.color.bar_color_dark_blue) else ContextCompat.getColor(context,
            R.color.bar_color_light_blue
        )
        strokeWidth = 50f
    }

    private val paint2 = Paint().apply {
        color = if (isDarkMode) ContextCompat.getColor(context, R.color.bar_color_dark_red) else ContextCompat.getColor(context,
            R.color.bar_color_light_red
        )
        strokeWidth = 50f
    }

    private val textPaint = Paint().apply {
        color = if (isDarkMode) ContextCompat.getColor(context, R.color.chart_text_dark) else ContextCompat.getColor(context,
            R.color.chart_text_light
        )
        textSize = 30f
        textAlign = Paint.Align.CENTER
    }

    private var viewWidth: Float = 0f
    private var viewHeight: Float = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w.toFloat()
        viewHeight = h.toFloat()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val maxDistance = maxOf(distance1, distance2).toFloat()
        if (maxDistance == 0f) return

        val barWidth = viewWidth / 3f
        val maxBarHeight = viewHeight * 0.6f

        paint1.strokeWidth = barWidth / 4
        paint2.strokeWidth = barWidth / 4

        // Barra 1 (Distancia 1)
        val bar1Height = (distance1.toFloat() / maxDistance) * maxBarHeight
        val bar1Left = barWidth / 2f
        val bar1Top = viewHeight - bar1Height - (barWidth / 2)
        val bar1Right = bar1Left + barWidth / 2f
        val bar1Bottom = viewHeight - (barWidth / 2)

        canvas.drawRect(bar1Left, bar1Top, bar1Right, bar1Bottom, paint1)

        val textSize1 = if (bar1Height < 50) 15f else 30f
        textPaint.textSize = textSize1

        val textY1 = if (bar1Height < 50) bar1Top - 5f else bar1Top - 10f
        canvas.drawText(String.format("%.2f km", distance1), bar1Left + barWidth / 4f, textY1, textPaint)

        canvas.drawText("Ruta 1", bar1Left + barWidth / 4f, viewHeight - (barWidth / 2) + 20f, textPaint)

        // Barra 2 (Distancia 2)
        val bar2Height = (distance2.toFloat() / maxDistance) * maxBarHeight
        val bar2Left = barWidth * 1.5f
        val bar2Top = viewHeight - bar2Height - (barWidth / 2)
        val bar2Right = bar2Left + barWidth / 2f
        val bar2Bottom = viewHeight - (barWidth / 2)

        canvas.drawRect(bar2Left, bar2Top, bar2Right, bar2Bottom, paint2)

        val textSize2 = if (bar2Height < 50) 15f else 30f
        textPaint.textSize = textSize2

        val textY2 = if (bar2Height < 50) bar2Top - 5f else bar2Top - 10f
        canvas.drawText(String.format("%.2f km", distance2), bar2Left + barWidth / 4f, textY2, textPaint)

        canvas.drawText("Ruta 2", bar2Left + barWidth / 4f, viewHeight - (barWidth / 2) + 20f, textPaint)
    }

    private fun isDarkMode(context: Context): Boolean {
        return when (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
    }
}