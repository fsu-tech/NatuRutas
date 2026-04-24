package com.example.gpxeditor.view.customviews

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import android.content.res.Configuration
import com.example.gpxeditor.R

class PerfilElevacionView(
    context: Context,
    private val altitudes: List<Double>,
    private val nombreRuta: String,
    color: Int
) : View(context) {

    private val isDarkMode = isDarkMode(context)

    private val paint = Paint().apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val textPaint = Paint().apply {
        this.color = if (isDarkMode) ContextCompat.getColor(context, R.color.chart_text_dark) else ContextCompat.getColor(context,
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

        if (altitudes.isEmpty()) return

        val maxAltitud = altitudes.maxOrNull() ?: 0.0
        val minAltitud = altitudes.minOrNull() ?: 0.0
        val rangeAltitud = maxAltitud - minAltitud

        if (rangeAltitud == 0.0) return

        val points = mutableListOf<Float>()
        for (i in altitudes.indices) {
            val x = i.toFloat() / (altitudes.size - 1) * viewWidth
            val y = viewHeight - ((altitudes[i] - minAltitud) / rangeAltitud).toFloat() * viewHeight
            points.add(x)
            points.add(y)
        }

        for (i in 0 until points.size - 2 step 2) {
            canvas.drawLine(points[i], points[i + 1], points[i + 2], points[i + 3], paint)
        }

        canvas.drawText(nombreRuta, viewWidth / 2f, 30f, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.x
            val index = (x / viewWidth * (altitudes.size - 1)).toInt().coerceIn(0, altitudes.size - 1)
            val altitude = altitudes[index]
            Toast.makeText(context, "Altura: ${altitude} m", Toast.LENGTH_SHORT).show()
        }
        return true
    }

    private fun isDarkMode(context: Context): Boolean {
        return when (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
    }
}