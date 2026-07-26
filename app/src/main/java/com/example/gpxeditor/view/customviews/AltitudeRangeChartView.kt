package com.example.gpxeditor.view.customviews

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.example.gpxeditor.R
import kotlin.math.min

class AltitudeRangeChartView(
    context: Context,
    private val min1: Double, private val max1: Double, private val range1: Double,
    private val distance1: Double, private val slope1: Double,
    private val min2: Double, private val max2: Double, private val range2: Double,
    private val distance2: Double, private val slope2: Double
) : View(context) {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.naturutas_on_surface)
        textAlign = Paint.Align.CENTER
    }
    private val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.naturutas_secondary_text)
        textAlign = Paint.Align.CENTER
    }
    private val route1Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.naturutas_primary)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val route2Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.naturutas_route)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val half = height / 2f
        drawDiagram(canvas, 0f, half, "Ruta 1", min1, max1, range1, distance1, slope1, route1Paint)
        drawDiagram(canvas, half, half, "Ruta 2", min2, max2, range2, distance2, slope2, route2Paint)
    }

    private fun drawDiagram(
        canvas: Canvas, top: Float, sectionHeight: Float, route: String,
        minimum: Double, maximum: Double, range: Double, distance: Double,
        slope: Double, linePaint: Paint
    ) {
        val density = resources.displayMetrics.density
        val bodySize = min(13f * density, sectionHeight * 0.10f)
        val smallSize = min(11f * density, sectionHeight * 0.08f)
        val left = width * 0.14f
        val right = width * 0.86f
        val highY = top + sectionHeight * 0.22f
        val lowY = top + sectionHeight * 0.58f

        textPaint.textSize = bodySize
        canvas.drawText(route, width * 0.5f, top + sectionHeight * 0.10f, textPaint)

        val fillPaint = Paint(linePaint).apply { style = Paint.Style.FILL; alpha = 35 }
        val triangle = Path().apply {
            moveTo(left, lowY); lineTo(right, highY); lineTo(right, lowY); close()
        }
        canvas.drawPath(triangle, fillPaint)
        linePaint.strokeWidth = density * 3f
        canvas.drawLine(left, lowY, right, highY, linePaint)
        canvas.drawLine(left, lowY, right, lowY, linePaint)
        canvas.drawLine(right, lowY, right, highY, linePaint)

        secondaryPaint.textSize = smallSize
        secondaryPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("A  ${meters(minimum)}", left, lowY - sectionHeight * 0.035f, secondaryPaint)
        secondaryPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("B  ${meters(maximum)}", right, highY - sectionHeight * 0.035f, secondaryPaint)
        secondaryPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(formatDistance(distance), (left + right) / 2f, lowY + sectionHeight * 0.11f, secondaryPaint)
        canvas.drawText(meters(range), right - width * 0.02f, (highY + lowY) / 2f, secondaryPaint)

        textPaint.textSize = bodySize
        canvas.drawText(
            "Desnivel ${meters(range)}  ·  Pendiente ${String.format("%.1f%%", slope)}",
            width * 0.5f, top + sectionHeight * 0.88f, textPaint
        )
    }

    private fun meters(value: Double) = String.format("%.0f m", value)

    private fun formatDistance(value: Double): String =
        if (value >= 1000.0) String.format("%.2f km", value / 1000.0) else String.format("%.0f m", value)
}
