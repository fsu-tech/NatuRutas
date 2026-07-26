package com.example.gpxeditor.view.customviews

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max
import kotlin.math.min

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private var zoom = 1f
    private var lastX = 0f
    private var lastY = 0f

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoom = min(MAX_ZOOM, max(MIN_ZOOM, zoom * detector.scaleFactor))
                pivotX = detector.focusX
                pivotY = detector.focusY
                scaleX = zoom
                scaleY = zoom
                if (zoom == MIN_ZOOM) resetPosition()
                return true
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onDoubleTap(event: MotionEvent): Boolean {
                resetZoom()
                return true
            }
        }
    )

    init {
        isClickable = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && event.pointerCount == 1 && zoom > MIN_ZOOM) {
                    translationX += event.x - lastX
                    translationY += event.y - lastY
                }
                lastX = event.x
                lastY = event.y
            }
        }
        return true
    }

    private fun resetZoom() {
        animate()
            .scaleX(MIN_ZOOM)
            .scaleY(MIN_ZOOM)
            .translationX(0f)
            .translationY(0f)
            .setDuration(180L)
            .start()
        zoom = MIN_ZOOM
    }

    private fun resetPosition() {
        translationX = 0f
        translationY = 0f
    }

    private companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 5f
    }
}
