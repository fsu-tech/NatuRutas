package com.example.gpxeditor.view.customviews

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.core.content.ContextCompat
import java.text.DecimalFormat
import android.content.res.Configuration
import com.example.gpxeditor.R

class VelocidadPromedioView(context: Context, private val velocidadPromedio1: Double, private val velocidadPromedio2: Double) : View(context) {

    private val isDarkMode = isDarkMode(context)
    private val paint = Paint()
    private val decimalFormat = DecimalFormat("#.##")

    private val barColor1 = if (isDarkMode) ContextCompat.getColor(context,
        R.color.bar_color_dark_blue
    ) else ContextCompat.getColor(context, R.color.bar_color_light_blue)
    private val barColor2 = if (isDarkMode) ContextCompat.getColor(context,
        R.color.bar_color_dark_green
    ) else ContextCompat.getColor(context, R.color.bar_color_light_green)
    private val textColor = if (isDarkMode) ContextCompat.getColor(context, R.color.chart_text_dark) else ContextCompat.getColor(context,
        R.color.chart_text_light
    )

    private var viewWidth: Float = 0f
    private var viewHeight: Float = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w.toFloat()
        viewHeight = h.toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val maxSpeed = Math.max(velocidadPromedio1, velocidadPromedio2).toFloat() * 1.2f
        val barWidth = viewWidth / 4f
        val barSpacing = viewWidth / 10f
        val maxBarHeight = viewHeight * 0.6f

        val bar1Height = if (velocidadPromedio1 > 0) (velocidadPromedio1.toFloat() / maxSpeed) * maxBarHeight else 0f
        val bar2Height = if (velocidadPromedio2 > 0) (velocidadPromedio2.toFloat() / maxSpeed) * maxBarHeight else 0f

        val startX1 = barSpacing
        val startX2 = startX1 + barWidth + barSpacing
        val startY = viewHeight - barSpacing

        paint.strokeWidth = barWidth / 2

        // Dibuja la barra 1
        paint.color = barColor1
        canvas.drawRect(startX1, startY - bar1Height, startX1 + barWidth, startY, paint)

        // Dibuja la barra 2
        paint.color = barColor2
        canvas.drawRect(startX2, startY - bar2Height, startX2 + barWidth, startY, paint)

        // Dibuja el texto de la velocidad
        paint.color = textColor
        paint.textSize = barWidth / 3f

        val formattedSpeed1 = if (velocidadPromedio1 == 0.0) "0 km/h" else decimalFormat.format(velocidadPromedio1) + " km/h"
        val formattedSpeed2 = if (velocidadPromedio2 == 0.0) "0 km/h" else decimalFormat.format(velocidadPromedio2) + " km/h"

        canvas.drawText(formattedSpeed1, startX1 + barWidth / 2, startY - bar1Height - (barWidth / 10), paint)
        canvas.drawText(formattedSpeed2, startX2 + barWidth / 2, startY - bar2Height - (barWidth / 10), paint)

        // Dibuja las etiquetas "Ruta 1" y "Ruta 2"
        paint.textSize = barWidth / 4f
        canvas.drawText("Ruta 1", startX1 + barWidth / 2, startY + barWidth / 3, paint)
        canvas.drawText("Ruta 2", startX2 + barWidth / 2, startY + barWidth / 3, paint)
    }

    private fun isDarkMode(context: Context): Boolean {
        return when (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
    }
}