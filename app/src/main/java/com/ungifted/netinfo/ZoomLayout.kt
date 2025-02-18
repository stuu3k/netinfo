package com.ungifted.netinfo

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout

class ZoomLayout : FrameLayout {
    private var scale = 1.0f
    private var lastScaleFactor = 0f
    private lateinit var prefHelper: PreferenceHelper
    private var isResultsWindow: Boolean = false  // Default to false
    
    // For panning
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var posX = 0f
    private var posY = 0f
    private var mode = Mode.NONE

    // Zoom limits
    private val maxZoom = 4.0f
    private val minZoom = 1.0f

    private lateinit var scaleDetector: ScaleGestureDetector

    private enum class Mode {
        NONE,
        DRAG,
        ZOOM
    }

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context)
    }

    private fun init(context: Context) {
        prefHelper = PreferenceHelper(context)
        scaleDetector = ScaleGestureDetector(context, ScaleListener())
    }

    // Add method to set whether this is a results window
    fun setIsResultsWindow(isResults: Boolean) {
        isResultsWindow = isResults
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        handleTouch(ev)
        return true
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        handleTouch(ev)
        return true
    }

    private fun handleTouch(ev: MotionEvent) {
        // Check if pinch zoom is allowed before handling any zoom
        if (!prefHelper.isPinchZoomAllowed(isResultsWindow)) {
            return
        }

        scaleDetector.onTouchEvent(ev)

        when (ev.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                mode = Mode.DRAG
                lastTouchX = ev.x
                lastTouchY = ev.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Only allow zoom mode if pinch zoom is enabled
                mode = if (prefHelper.isPinchZoomAllowed(isResultsWindow)) Mode.ZOOM else Mode.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == Mode.DRAG && scale > 1.0f) {
                    val dx = ev.x - lastTouchX
                    val dy = ev.y - lastTouchY
                    
                    // Calculate bounds
                    val right = width * (scale - 1)
                    val bottom = height * (scale - 1)
                    
                    // Update position with bounds checking
                    posX = (posX + dx).coerceIn(-right, 0f)
                    posY = (posY + dy).coerceIn(-bottom, 0f)
                    
                    // Apply translation
                    getChildAt(0)?.translationX = posX
                    getChildAt(0)?.translationY = posY
                    
                    lastTouchX = ev.x
                    lastTouchY = ev.y
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                mode = Mode.NONE
            }
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val prevScale = scale
            scale *= detector.scaleFactor
            scale = scale.coerceIn(minZoom, maxZoom)
            
            if (scale != prevScale) {
                val focusX = detector.focusX
                val focusY = detector.focusY
                
                // Apply scale
                getChildAt(0)?.apply {
                    scaleX = scale
                    scaleY = scale
                    pivotX = focusX
                    pivotY = focusY
                }
                
                // Reset position when returning to normal scale
                if (scale == 1.0f) {
                    posX = 0f
                    posY = 0f
                    translationX = 0f
                    translationY = 0f
                }
            }
            
            return true
        }
    }
} 