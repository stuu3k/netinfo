package com.ungifted.netinfo

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ScrollView
import android.view.ScaleGestureDetector

class CustomScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    var scaleGestureDetector: ScaleGestureDetector? = null
    var isPinchZoomAllowed: Boolean = true

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Just pass events to scale detector, let it decide whether to handle them
        scaleGestureDetector?.onTouchEvent(event)
        return super.onTouchEvent(event)
    }
} 