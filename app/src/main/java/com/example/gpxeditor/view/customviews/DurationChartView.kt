package com.example.gpxeditor.view.customviews

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.core.content.ContextCompat
import android.content.res.Configuration
import com.example.gpxeditor.R

class DurationChartView(context: Context, private val duration1: String, private val duration2: String) : View(context) {

    private val isDarkMode = isDarkMode(context)

    private val paint1 = Paint().apply {
        color = if (isDarkMode) ContextCompat.getColor(context, R.color.bar_color_dark_1) else ContextCompat.getColor(context,
            R.color.bar_color_light_1
        )
        strokeWidth = 50f
    }

    private val paint2 = Paint().apply {
        color = if (isDarkMode) ContextCompat.getColor(context, R.color.bar_color_dark_2) else ContextCompat.getColor(context,
            R.color.bar_color_light_2
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val duration1Value = duration1.replace(":", "").toIntOrNull() ?: 0
        val duration2Value = duration2.replace(":", "").toIntOrNull() ?: 0

        val maxDuration = maxOf(duration1Value, duration2Value).toFloat()
        if (maxDuration == 0f) return

        val barWidth = viewWidth / 3f
        val maxBarHeight = viewHeight * 0.6f

        paint1.strokeWidth = barWidth / 2
        paint2.strokeWidth = barWidth / 2

        // Barra 1 (Duración 1)
        val bar1Height = (duration1Value.toFloat() / maxDuration) * maxBarHeight
        val bar1Left = barWidth / 2f
        val bar1Top = viewHeight - bar1Height - (barWidth / 2)
        val bar1Right = bar1Left + barWidth / 2f
        val bar1Bottom = viewHeight - (barWidth / 2)

        canvas.drawRect(bar1Left, bar1Top, bar1Right, bar1Bottom, paint1)
        canvas.drawText(duration1, bar1Left + barWidth / 4f, bar1Top - 10f, textPaint)
        canvas.drawText("Ruta 1", bar1Left + barWidth / 4f, viewHeight - (barWidth / 2) + 20f, textPaint)

        // Barra 2 (Duración 2)
        val bar2Height = (duration2Value.toFloat() / maxDuration) * maxBarHeight
        val bar2Left = barWidth * 1.5f
        val bar2Top = viewHeight - bar2Height - (barWidth / 2)
        val bar2Right = bar2Left + barWidth / 2f
        val bar2Bottom = viewHeight - (barWidth / 2)

        canvas.drawRect(bar2Left, bar2Top, bar2Right, bar2Bottom, paint2)
        canvas.drawText(duration2, bar2Left + barWidth / 4f, bar2Top - 10f, textPaint)
        canvas.drawText("Ruta 2", bar2Left + barWidth / 4f, viewHeight - (barWidth / 2) + 20f, textPaint)
    }

    private fun isDarkMode(context: Context): Boolean {
        return when (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
    }
}