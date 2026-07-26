package com.example.gpxeditor.view.customviews

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.gpxeditor.R
import com.example.gpxeditor.model.database.DatabaseHelper
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class PerfilElevacionView(
    context: Context,
    private val profile1: List<DatabaseHelper.ElevationProfilePoint>,
    private val profile2: List<DatabaseHelper.ElevationProfilePoint>
) : View(context) {

    private val route1Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.naturutas_primary)
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2.5f
    }
    private val route2Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.naturutas_route)
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2.5f
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.naturutas_outline)
        strokeWidth = resources.displayMetrics.density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.naturutas_secondary_text)
        textSize = resources.displayMetrics.scaledDensity * 11f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val allPoints = profile1 + profile2
        if (allPoints.isEmpty()) return

        val density = resources.displayMetrics.density
        val left = 52f * density
        val right = width - 16f * density
        val top = 42f * density
        val bottom = height - 42f * density
        if (right <= left || bottom <= top) return

        val minAltitude = allPoints.minOf { it.altitudeMeters }
        val maxAltitude = allPoints.maxOf { it.altitudeMeters }
        val rawAltitudeRange = maxAltitude - minAltitude
        val altitudePadding = max(5.0, rawAltitudeRange * 0.08)
        val chartMinAltitude = minAltitude - altitudePadding
        val chartMaxAltitude = maxAltitude + altitudePadding
        val altitudeRange = chartMaxAltitude - chartMinAltitude
        val maxDistance = max(
            profile1.lastOrNull()?.distanceMeters ?: 0.0,
            profile2.lastOrNull()?.distanceMeters ?: 0.0
        ).takeIf { it > 0.0 } ?: 1.0

        drawGrid(canvas, left, right, top, bottom, chartMinAltitude, chartMaxAltitude, maxDistance)
        drawProfile(canvas, profile1, route1Paint, left, right, top, bottom, chartMinAltitude, altitudeRange, maxDistance)
        drawProfile(canvas, profile2, route2Paint, left, right, top, bottom, chartMinAltitude, altitudeRange, maxDistance)
        drawLegend(canvas, left, top, density)
    }

    private fun drawGrid(
        canvas: Canvas, left: Float, right: Float, top: Float, bottom: Float,
        minAltitude: Double, maxAltitude: Double, maxDistance: Double
    ) {
        val divisions = 4
        textPaint.textAlign = Paint.Align.RIGHT
        for (index in 0..divisions) {
            val ratio = index / divisions.toFloat()
            val y = bottom - ratio * (bottom - top)
            canvas.drawLine(left, y, right, y, gridPaint)
            val altitude = minAltitude + ratio * (maxAltitude - minAltitude)
            canvas.drawText(String.format("%.0f m", altitude), left - 6f * resources.displayMetrics.density, y + 4f * resources.displayMetrics.density, textPaint)
        }

        textPaint.textAlign = Paint.Align.CENTER
        for (index in 0..divisions) {
            val ratio = index / divisions.toFloat()
            val x = left + ratio * (right - left)
            canvas.drawLine(x, top, x, bottom, gridPaint)
            canvas.drawText(formatDistance(maxDistance * ratio), x, bottom + 18f * resources.displayMetrics.density, textPaint)
        }
    }

    private fun drawProfile(
        canvas: Canvas,
        profile: List<DatabaseHelper.ElevationProfilePoint>,
        paint: Paint,
        left: Float, right: Float, top: Float, bottom: Float,
        minAltitude: Double, altitudeRange: Double, maxDistance: Double
    ) {
        if (profile.size < 2) return
        val path = Path()
        profile.forEachIndexed { index, point ->
            val x = left + (point.distanceMeters / maxDistance * (right - left)).toFloat()
            val y = bottom - ((point.altitudeMeters - minAltitude) / altitudeRange * (bottom - top)).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.save()
        canvas.clipRect(left, top, right, bottom)
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun drawLegend(canvas: Canvas, left: Float, top: Float, density: Float) {
        val y = top - 18f * density
        route1Paint.style = Paint.Style.FILL
        route2Paint.style = Paint.Style.FILL
        canvas.drawCircle(left + 8f * density, y, 4f * density, route1Paint)
        canvas.drawCircle(left + 88f * density, y, 4f * density, route2Paint)
        route1Paint.style = Paint.Style.STROKE
        route2Paint.style = Paint.Style.STROKE
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Ruta 1", left + 16f * density, y + 4f * density, textPaint)
        canvas.drawText("Ruta 2", left + 96f * density, y + 4f * density, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN || width == 0) return true
        val density = resources.displayMetrics.density
        val left = 52f * density
        val right = width - 16f * density
        val maxDistance = max(
            profile1.lastOrNull()?.distanceMeters ?: 0.0,
            profile2.lastOrNull()?.distanceMeters ?: 0.0
        )
        if (maxDistance <= 0.0 || event.x !in left..right) return true

        val selectedDistance = ((event.x - left) / (right - left) * maxDistance).toDouble()
        val point1 = profile1.minByOrNull { abs(it.distanceMeters - selectedDistance) }
        val point2 = profile2.minByOrNull { abs(it.distanceMeters - selectedDistance) }
        val message = buildString {
            append(formatDistance(selectedDistance))
            point1?.let { append(" · Ruta 1: ${String.format("%.0f m", it.altitudeMeters)}") }
            point2?.let { append(" · Ruta 2: ${String.format("%.0f m", it.altitudeMeters)}") }
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        return true
    }

    private fun formatDistance(meters: Double): String =
        if (meters >= 1000.0) String.format("%.1f km", meters / 1000.0) else String.format("%.0f m", meters)
}
