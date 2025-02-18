package com.ungifted.netinfo

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.*
import android.widget.ImageButton
import android.widget.PopupWindow
import android.widget.TextView

class ResultsPopupWindow(private val context: Context) {
    private var popupWindow: PopupWindow? = null
    private var currentX = 0
    private var currentY = 0
    private var isMinimized = false
    private var lastHeight = 0
    private var resultTextView: TextView? = null
    private var isNetworkScan = false

    fun show(result: String, title: String, showCheckboxes: Boolean = false) {
        isNetworkScan = (title == "Network Scan")
        
        if (popupWindow == null) {
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val view = inflater.inflate(R.layout.popup_results, null)

            // Hide sound controls for network scan
            view.findViewById<View>(R.id.soundControlsContainer)?.visibility = 
                if (showCheckboxes) View.VISIBLE else View.GONE

            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            // Set width based on whether it's network scan or not
            val width = if (isNetworkScan) screenWidth else 300.dpToPx()

            popupWindow = PopupWindow(
                view,
                width,
                WindowManager.LayoutParams.WRAP_CONTENT
            ).apply {
                elevation = 10f
                
                // Special handling for network scan window
                if (isNetworkScan) {
                    setWindowLayoutType(WindowManager.LayoutParams.TYPE_APPLICATION_PANEL)
                    isFocusable = true
                    isOutsideTouchable = false
                } else {
                    isFocusable = true
                    isOutsideTouchable = true
                }
                
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                
                // Prevent window from being dismissed when clicking outside
                setOnDismissListener(null)
            }

            // Position based on whether it's network scan or not
            if (isNetworkScan) {
                val statusBarHeight = getStatusBarHeight()
                currentX = 0
                currentY = statusBarHeight
            } else {
                currentX = (screenWidth - width) / 2 + (Math.random() * 200).toInt() - 100
                currentY = (screenHeight - 300) / 2 + (Math.random() * 200).toInt() - 100
            }

            resultTextView = view.findViewById<TextView>(R.id.resultText)
            setupWindow(view, title)
            
            try {
                popupWindow?.showAtLocation(
                    (context as Activity).window.decorView,
                    Gravity.NO_GRAVITY,
                    currentX,
                    currentY
                )
            } catch (e: Exception) {
                Log.e("ResultsPopupWindow", "Error showing popup", e)
            }
        }
        
        updateContent(result)
    }

    fun updateContent(content: String) {
        resultTextView?.text = content
    }

    fun dismiss() {
        try {
            popupWindow?.dismiss()
        } catch (e: Exception) {
            Log.e("ResultsPopupWindow", "Error dismissing popup", e)
        }
        popupWindow = null
    }

    private fun Int.dpToPx(): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    private fun getStatusBarHeight(): Int {
        val resources = context.resources
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun setupWindow(view: View, title: String) {
        val titleBar = view.findViewById<View>(R.id.titleBar)
        val titleText = view.findViewById<TextView>(R.id.titleText)
        val minimizeButton = view.findViewById<ImageButton>(R.id.minimizeButton)
        val closeButton = view.findViewById<ImageButton>(R.id.closeButton)

        titleText.text = title

        // Set up drag functionality
        titleBar.setOnTouchListener(object : View.OnTouchListener {
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false
            private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = (event.rawX - initialTouchX).toInt()
                        val deltaY = (event.rawY - initialTouchY).toInt()
                        
                        if (!isDragging && (Math.abs(deltaX) > touchSlop || Math.abs(deltaY) > touchSlop)) {
                            isDragging = true
                        }
                        
                        if (isDragging) {
                            // Allow dragging for all windows
                            currentX += deltaX
                            currentY += deltaY
                            try {
                                popupWindow?.update(currentX, currentY, -1, -1)
                            } catch (e: Exception) {
                                Log.e("ResultsPopupWindow", "Error updating position", e)
                            }
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        return true
                    }
                }
                return false
            }
        })

        // Set up minimize/maximize
        minimizeButton.setOnClickListener {
            if (isMinimized) {
                resultTextView?.visibility = View.VISIBLE
                popupWindow?.height = lastHeight
                minimizeButton.setImageResource(android.R.drawable.arrow_down_float)
                
                if (isNetworkScan) {
                    currentY = getStatusBarHeight()
                    popupWindow?.update(currentX, currentY, -1, -1)
                }
            } else {
                lastHeight = popupWindow?.height ?: 0
                resultTextView?.visibility = View.GONE
                popupWindow?.height = titleBar.height
                minimizeButton.setImageResource(android.R.drawable.arrow_up_float)
            }
            isMinimized = !isMinimized
        }

        closeButton.setOnClickListener {
            dismiss()
        }
    }
} 