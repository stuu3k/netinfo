package com.ungifted.netinfo

import android.os.Bundle
import android.view.*
import androidx.fragment.app.DialogFragment
import android.widget.ImageButton
import android.widget.TextView
import android.animation.ValueAnimator
import androidx.fragment.app.FragmentManager
import android.widget.Button
import android.content.DialogInterface
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.CheckBox
import android.util.Log
import android.view.ScaleGestureDetector
import android.util.TypedValue

class ResultsDialog : DialogFragment() {
    private var currentX = 0
    private var currentY = 0
    var isMinimized = false
    private var lastHeight = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialWindowX = 0f
    private var initialWindowY = 0f
    private var minimizedTab: View? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var prefHelper: PreferenceHelper
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    // Add field to track if this is a continuous ping dialog
    private val isContinuousPing by lazy { arguments?.getBoolean("showStop") == true }

    // Add interface for dialog callbacks
    interface DialogCloseListener {
        fun onDialogClosed()
    }
    
    private var closeListener: DialogCloseListener? = null
    
    fun setCloseListener(listener: DialogCloseListener) {
        closeListener = listener
    }

    private var contentUpdateListener: ((String) -> Unit)? = null
    
    fun setContentUpdateListener(listener: (String) -> Unit) {
        contentUpdateListener = listener
    }

    // Add a new interface for stop button
    interface StopButtonListener {
        fun onStopPressed()
    }
    
    private var stopListener: StopButtonListener? = null
    
    fun setStopListener(listener: StopButtonListener) {
        stopListener = listener
    }

    private fun animateWindow(fromX: Int, fromY: Int, toX: Int, toY: Int) {
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 200

        animator.addUpdateListener { animation ->
            val fraction = animation.animatedValue as Float
            dialog?.window?.apply {
                attributes = attributes.apply {
                    x = fromX + ((toX - fromX) * fraction).toInt()
                    y = fromY + ((toY - fromY) * fraction).toInt()
                }
            }
        }
        animator.start()
    }

    companion object {
        fun newInstance(title: String, result: String, showMinimize: Boolean, showStop: Boolean = false): ResultsDialog {
            return ResultsDialog().apply {
                arguments = Bundle().apply {
                    putString("title", title)
                    putString("result", result)
                    putBoolean("showMinimize", showMinimize)
                    putBoolean("showStop", showStop)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.DialogTheme)
        prefHelper = PreferenceHelper(requireContext())
        
        // Add fade animations
        dialog?.window?.setWindowAnimations(R.style.DialogAnimation)
    }

    private var stopButton: Button? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.popup_results, container, false)
        
        // Add result to manager when dialog is created
        val title = arguments?.getString("title") ?: "Results"
        val content = arguments?.getString("result") ?: ""
        ResultsManager.addResult(title, content, isContinuousPing)
        
        dialog?.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            
            // Set window flags for multiple windows
            setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
            
            // Set attributes for proper window management
            attributes = attributes.apply {
                gravity = Gravity.TOP or Gravity.START
                type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL

                // Position based on dialog type
                if (isContinuousPing || arguments?.getString("title")?.contains("Network Scan") == true) {
                    // Full width dialogs appear just below taskbar
                    x = 0
                    y = 150  // Increased from 100 to 150 to move it lower
                    // Set full width
                    setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
                } else {
                    // Regular dialogs (PING, TRACE, DNS) appear 3/4 down and centered
                    val screenHeight = resources.displayMetrics.heightPixels
                    val screenWidth = resources.displayMetrics.widthPixels
                    y = (screenHeight * 0.75).toInt()  // 3/4 down the screen
                    x = (screenWidth - 300.dpToPx()) / 2  // Centered horizontally
                    // Set normal width
                    setLayout(300.dpToPx(), WindowManager.LayoutParams.WRAP_CONTENT)
                }
            }
        }

        // Initialize scale gesture detector
        scaleGestureDetector = ScaleGestureDetector(requireContext(), object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                // Strict check - if not allowed, don't do anything
                if (!prefHelper.isPinchZoomAllowed(isResultsWindow = true)) {
                    return false
                }
                
                val textView = view.findViewById<TextView>(R.id.resultText)
                textView?.let {
                    it.setTextSize(TypedValue.COMPLEX_UNIT_PX, it.textSize * detector.scaleFactor)
                    return true
                }
                return false
            }

            // Add this to prevent any scale begin when not allowed
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                return prefHelper.isPinchZoomAllowed(isResultsWindow = true)
            }
        })

        // Set up the custom ScrollView
        view.findViewById<CustomScrollView>(R.id.scrollView)?.apply {
            this.scaleGestureDetector = this@ResultsDialog.scaleGestureDetector
            this.isPinchZoomAllowed = prefHelper.isPinchZoomAllowed(isResultsWindow = true)
        }

        // Set up ZoomLayout
        view.findViewById<ZoomLayout>(R.id.zoomLayout)?.setIsResultsWindow(true)

        // Set up views
        val titleBar = view.findViewById<View>(R.id.titleBar)
        val titleText = view.findViewById<TextView>(R.id.titleText)
        val resultText = view.findViewById<TextView>(R.id.resultText)
        val minimizeButton = view.findViewById<ImageButton>(R.id.minimizeButton)
        val closeButton = view.findViewById<ImageButton>(R.id.closeButton)

        titleText.text = arguments?.getString("title")
        resultText.text = arguments?.getString("result")

        // Set up drag functionality
        val dragListener = View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    dialog?.window?.attributes?.let { params ->
                        initialWindowX = params.x.toFloat()
                        initialWindowY = params.y.toFloat()
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    dialog?.window?.apply {
                        attributes = attributes.apply {
                            x = (initialWindowX + dx).toInt()
                            y = (initialWindowY + dy).toInt()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        titleBar.setOnTouchListener(dragListener)

        minimizeButton.setOnClickListener {
            Log.d("ResultsDialog", "Minimize button clicked, isMinimized=$isMinimized")
            if (isMinimized) {
                restore()
            } else {
                minimize()
            }
        }

        // Set up close
        closeButton.setOnClickListener {
            closeListener?.onDialogClosed()  // Notify listener before closing
            dismiss()
        }

        // Set up stop button
        stopButton = view.findViewById<Button>(R.id.stopButton)
        if (arguments?.getBoolean("showStop") == true) {
            stopButton?.visibility = View.VISIBLE
            stopButton?.setOnClickListener {
                stopListener?.onStopPressed()  // Only notify stop listener
                stopButton?.visibility = View.GONE  // Hide button immediately when clicked
            }
        }

        // Setup zoom slider
        val zoomSlider = view.findViewById<SeekBar>(R.id.zoomSlider)
        
        zoomSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Convert progress (0-200) to text size (8sp-24sp)
                val textSize = 8f + (progress / 200f * 16f)  // Range from 8sp to 24sp
                resultText.textSize = textSize
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Set initial text size from preferences
        resultText.textSize = prefHelper.defaultResultsTextSize
        
        // Update zoom slider to match the default text size
        val progress = ((prefHelper.defaultResultsTextSize - 8f) * (200f / 16f)).toInt()
        zoomSlider.progress = progress

        // Only show sound controls for continuous ping from CUSTOM page
        view.findViewById<ViewGroup>(R.id.soundControlsContainer)?.visibility = 
            if (arguments?.getBoolean("showStop") == true) View.VISIBLE else View.GONE

        // Set default values for sound checkboxes and load saved preferences
        view.findViewById<CheckBox>(R.id.onSuccessCheck)?.apply {
            isChecked = prefHelper.onSuccessCheck
            setOnCheckedChangeListener { _, isChecked ->
                prefHelper.onSuccessCheck = isChecked
            }
        }
        view.findViewById<CheckBox>(R.id.onFailCheck)?.apply {
            isChecked = prefHelper.onFailCheck
            setOnCheckedChangeListener { _, isChecked ->
                prefHelper.onFailCheck = isChecked
            }
        }

        return view
    }

    // Add this method to ResultsDialog class
    private var windowAttributesCallback: ((Window) -> Unit)? = null

    fun setWindowAttributes(callback: (Window) -> Unit) {
        windowAttributesCallback = callback
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            windowAttributesCallback?.invoke(window)
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * requireContext().resources.displayMetrics.density).toInt()
    }

    override fun show(manager: FragmentManager, tag: String?) {
        val ft = manager.beginTransaction()
        ft.add(this, tag)
        ft.commitAllowingStateLoss()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // Only remove if not minimized
        if (!isMinimized) {
            val title = arguments?.getString("title") ?: "Results"
            ResultsManager.removeResult(title)
        }
        closeListener?.onDialogClosed()
    }

    fun hideStopButton() {
        stopButton?.visibility = View.GONE  // Use the stored reference
    }

    fun appendResult(text: String) {
        view?.findViewById<TextView>(R.id.resultText)?.apply {
            val newContent = "${this.text}$text"
            this.text = newContent
            contentUpdateListener?.invoke(newContent)
            
            val title = arguments?.getString("title") ?: "Results"
            ResultsManager.updateResult(title, newContent, isContinuousPing)
            
            if (parent is ScrollView) {
                (parent as ScrollView).fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private fun updateContent(newContent: String) {
        view?.findViewById<TextView>(R.id.resultText)?.let { textView ->
            textView.text = newContent
            contentUpdateListener?.invoke(newContent)
        }
    }

    fun getTitle(): String {
        return arguments?.getString("title") ?: "Results"
    }

    fun getCurrentContent(): String {
        return view?.findViewById<TextView>(R.id.resultText)?.text?.toString() ?: ""
    }

    private fun getMinimizedTitle(): String {
        val fullTitle = arguments?.getString("title") ?: "Results"
        Log.d("ResultsDialog", "getMinimizedTitle() START - Full title: '$fullTitle'")
        Log.d("ResultsDialog", "isContinuousPing=$isContinuousPing")
        
        val minimizedTitle = when {
            fullTitle == "Network Scan" -> {
                Log.d("ResultsDialog", "Network Scan case matched - using SCAN")
                "SCAN"
            }
            isContinuousPing -> {
                Log.d("ResultsDialog", "Continuous ping case - using CPING")
                "CPING"
            }
            fullTitle.contains("NSLookup", ignoreCase = true) -> {
                Log.d("ResultsDialog", "NSLookup case matched - using NS")
                "NS"
            }
            fullTitle.contains("DHCP") -> {
                Log.d("ResultsDialog", "DHCP case matched - using DHCP")
                "DHCP"
            }
            fullTitle.contains("Ping") -> {
                Log.d("ResultsDialog", "Ping case matched - using PING")
                "PING"
            }
            fullTitle.contains("Trace") -> {
                Log.d("ResultsDialog", "Trace case matched - using TRACE")
                "TRACE"
            }
            fullTitle.contains("DNS") -> {
                Log.d("ResultsDialog", "DNS case matched - using DNS")
                "DNS"
            }
            else -> {
                Log.d("ResultsDialog", "No match - using full title: $fullTitle")
                fullTitle
            }
        }
        
        Log.d("ResultsDialog", "getMinimizedTitle() END - Returning: '$minimizedTitle'")
        return minimizedTitle
    }

    fun minimize() {
        Log.d("ResultsDialog", "minimize() START")
        Log.d("ResultsDialog", "Current state: isMinimized=$isMinimized")
        if (!isMinimized) {
            Log.d("ResultsDialog", "Starting minimize operation")
            
            // Store current position
            currentX = dialog?.window?.attributes?.x ?: 0
            currentY = dialog?.window?.attributes?.y ?: 0
            Log.d("ResultsDialog", "Stored position: x=$currentX, y=$currentY")
            
            // Get title
            val fullTitle = arguments?.getString("title") ?: "Results"
            Log.d("ResultsDialog", "Full title: '$fullTitle'")
            val tabTitle = getMinimizedTitle()
            Log.d("ResultsDialog", "Tab title: '$tabTitle'")
            
            // Add tab
            minimizedTab = (activity as? MainActivity)?.addMinimizedTab(tabTitle, this)
            Log.d("ResultsDialog", "Added minimized tab: ${minimizedTab != null}")
            
            minimizedTab?.let { tab ->
                val tabLocation = IntArray(2)
                tab.getLocationInWindow(tabLocation)
                
                // Animate to just below the tab position (off screen)
                val screenHeight = requireContext().resources.displayMetrics.heightPixels
                animateWindow(currentX, currentY, tabLocation[0], screenHeight + 100)
                
                // After animation completes, hide the window
                handler.postDelayed({
                    view?.visibility = View.GONE
                    dialog?.window?.apply {
                        addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                        setLayout(0, 0)
                    }
                }, 200)
            }
            
            isMinimized = true
            Log.d("ResultsDialog", "minimize() END")
        } else {
            Log.d("ResultsDialog", "minimize() - Already minimized, ignoring")
        }
    }

    fun restore() {
        Log.d("ResultsDialog", "restore() called")
        if (isMinimized) {
            Log.d("ResultsDialog", "Performing restore operation")
            
            // Restore from minimized state
            minimizedTab?.let { tab ->
                val tabLocation = IntArray(2)
                tab.getLocationInWindow(tabLocation)
                
                // Start from tab position
                dialog?.window?.apply {
                    attributes = attributes.apply {
                        x = tabLocation[0]
                        y = tabLocation[1]
                    }
                    setLayout(0, 0)
                    clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                }
                
                // Make window visible before animation
                view?.visibility = View.VISIBLE
                
                // Set appropriate size based on dialog type
                if (isContinuousPing || arguments?.getString("title") == "Network Scan") {
                    val metrics = requireContext().resources.displayMetrics
                    dialog?.window?.setLayout(metrics.widthPixels, WindowManager.LayoutParams.WRAP_CONTENT)
                } else {
                    dialog?.window?.setLayout(300.dpToPx(), WindowManager.LayoutParams.WRAP_CONTENT)
                }
                
                animateWindow(tabLocation[0], tabLocation[1], currentX, currentY)
                
                // Restore original title
                view?.findViewById<TextView>(R.id.titleText)?.text = arguments?.getString("title")
                
                tab.findViewById<ImageButton>(R.id.tabCloseButton).visibility = View.VISIBLE
                (activity as? MainActivity)?.removeMinimizedTab(tab)
                
                view?.findViewById<TextView>(R.id.resultText)?.visibility = View.VISIBLE
                view?.findViewById<ImageButton>(R.id.minimizeButton)?.setImageResource(android.R.drawable.arrow_down_float)
            }
            minimizedTab = null
            isMinimized = false
        }
    }
} 