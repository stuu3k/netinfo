package com.ungifted.netinfo

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import androidx.core.content.ContextCompat
import android.widget.GridLayout
import android.widget.FrameLayout
import android.media.AudioManager
import android.media.ToneGenerator
import android.widget.CheckBox
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.widget.EditText
import android.app.Dialog
import android.util.TypedValue
import android.content.Intent
import android.os.Build
import java.net.HttpURLConnection
import java.net.URL
import android.content.pm.PackageManager

class MainFragment : Fragment() {
    companion object {
        private const val ARG_PROVIDER = "provider"
        private const val ARG_PRIMARY_IP = "primary_ip"
        private const val ARG_SECONDARY_IP = "secondary_ip"
        private const val ARG_DOMAIN = "domain"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001

        @JvmStatic
        fun newInstance(provider: String, primaryIp: String, secondaryIp: String, domain: String) =
            MainFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PROVIDER, provider)
                    putString(ARG_PRIMARY_IP, primaryIp)
                    putString(ARG_SECONDARY_IP, secondaryIp)
                    putString(ARG_DOMAIN, domain)
                }
            }
    }

    private var provider: String = "LOCAL"  // Default provider
    private var primaryIp: String? = null
    private var secondaryIp: String? = null
    private var domain: String? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private val IP_HISTORY_KEY = "ip_history"
    private val DNS_HISTORY_KEY = "dns_history"
    private val MAX_HISTORY = 10
    private lateinit var requestPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private var continuousPingButton: Button? = null
    private var continuousPingJob: Job? = null  // Add at class level to track the continuous ping job
    private var showDnsSuffix = false  // Controls DNS suffix visibility
    private var detectedDnsSuffix: String? = null
    private var scaleFactor = 1f
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var lastPingStatus: Boolean? = null  // Add this to track status changes
    private var pingCounter = 0  // Add this to track ping count
    private lateinit var prefHelper: PreferenceHelper
    private var dnsServer: String? = null
    private var dhcpServer: String? = null
    private var gateway: String? = null
    private lateinit var networkInfo: NetworkInfo  // Add this line

    interface MainFragmentListener {
        fun showSettingsOverlay()
        fun shareResults()
    }

    private var listener: MainFragmentListener? = null

    private data class CustomButton(
        var name: String,
        var target: String
    )

    private val customPingButtons = mutableMapOf<Int, CustomButton>()
    private val customTraceButtons = mutableMapOf<Int, CustomButton>()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MainFragmentListener) {
            listener = context
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefHelper = PreferenceHelper(requireContext())
        arguments?.let {
            provider = it.getString(ARG_PROVIDER) ?: "LOCAL"
            primaryIp = it.getString(ARG_PRIMARY_IP)
            secondaryIp = it.getString(ARG_SECONDARY_IP)
            domain = it.getString(ARG_DOMAIN)
        }

        networkInfo = NetworkInfo(requireContext())  // Initialize networkInfo

        // Initialize permission launcher
        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // Permission granted, update network info
                view?.let { view ->
                    val networkInfoBox = view.findViewById<View>(R.id.networkInfoBox)
                    val mobileDataBox = view.findViewById<View>(R.id.mobileDataBox)
                    val deviceInfoBox = view.findViewById<View>(R.id.deviceInfoBox)
                    updateNetworkInfo(networkInfoBox, mobileDataBox)
                    updateDeviceInfo(deviceInfoBox)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Update logo visibility based on provider and settings
        view?.findViewById<ImageView>(R.id.networkLogo)?.apply {
            if (provider == "HOME" || provider == "LOCAL") {
                val prefHelper = PreferenceHelper(requireContext())
                visibility = if (prefHelper.showLogo) View.VISIBLE else View.GONE
            } else {
                // Always show logo for other pages
                visibility = View.VISIBLE
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Use different layout for home page
        return if (provider == "HOME") {
            inflater.inflate(R.layout.fragment_home, container, false)
        } else {
            inflater.inflate(R.layout.fragment_main, container, false)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        provider = arguments?.getString(ARG_PROVIDER) ?: "LOCAL"
        
        // Set initial logo visibility
        view.findViewById<ImageView>(R.id.networkLogo)?.let { logo ->
            if (provider == "HOME" || provider == "LOCAL") {
                val prefHelper = PreferenceHelper(requireContext())
                logo.visibility = if (prefHelper.showLogo) View.VISIBLE else View.GONE
            } else {
                // Always show logo for other pages
                logo.visibility = View.VISIBLE
            }
        }
        
        // Find the hint texts
        val customPingHint = view.findViewById<TextView>(R.id.customPingHint)
        val customTraceHint = view.findViewById<TextView>(R.id.customTraceHint)
        
        // Only show hints on LOCAL page
        val showHints = (provider == "LOCAL")
        customPingHint?.visibility = if (showHints) View.VISIBLE else View.GONE
        customTraceHint?.visibility = if (showHints) View.VISIBLE else View.GONE
        
        if (provider == "CUSTOM") {
            // Hide individual elements instead of the entire buttonsContainer
            view.findViewById<TextView>(R.id.primaryIpText)?.visibility = View.GONE
            view.findViewById<TextView>(R.id.secondaryIpText)?.visibility = View.GONE
            view.findViewById<TextView>(R.id.domainText)?.visibility = View.GONE
            view.findViewById<ViewGroup>(R.id.button1_container)?.visibility = View.GONE
            view.findViewById<ViewGroup>(R.id.button1_trace_container)?.visibility = View.GONE
            view.findViewById<ViewGroup>(R.id.button2_container)?.visibility = View.GONE
            view.findViewById<ViewGroup>(R.id.button3_container)?.visibility = View.GONE
            view.findViewById<ViewGroup>(R.id.button3_trace_container)?.visibility = View.GONE
            view.findViewById<ViewGroup>(R.id.dns_button_container)?.visibility = View.GONE
            view.findViewById<ImageView>(R.id.providerLogo)?.visibility = View.GONE
            view.findViewById<TextView>(R.id.customPingHeader)?.visibility = View.GONE
            view.findViewById<GridLayout>(R.id.customPingGrid)?.visibility = View.GONE
            view.findViewById<TextView>(R.id.customTraceHeader)?.visibility = View.GONE
            view.findViewById<GridLayout>(R.id.customTraceGrid)?.visibility = View.GONE
            
            // Show continuous ping elements
            view.findViewById<TextView>(R.id.continuousPingSection)?.visibility = View.VISIBLE
            view.findViewById<Button>(R.id.continuousPingButton)?.visibility = View.VISIBLE
            view.findViewById<LinearLayout>(R.id.pingAlertContainer)?.visibility = View.VISIBLE
        }

        // Set the page title
        view.findViewById<TextView>(R.id.pageTitle)?.text = when (provider) {
            "HOME" -> "Network Info"
            "CLOUDFLARE" -> "CloudFlare"
            "GOOGLE" -> "Google"
            "LOCAL" -> "Local / Custom"
            "CUSTOM" -> "Continuous Ping"
            else -> ""
        }

        // Get status bar height and position toolbar
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                24f,
                resources.displayMetrics
            ).toInt()
        }

        // Position toolbar right below status bar
        view.findViewById<LinearLayout>(R.id.toolbar)?.apply {
            val params = layoutParams
            if (params is FrameLayout.LayoutParams) {
                params.topMargin = statusBarHeight - 8
                params.height = 48
                layoutParams = params
            } else if (params is LinearLayout.LayoutParams) {
                params.topMargin = statusBarHeight - 8
                params.height = 48
                layoutParams = params
            }
        }
        
        // Initialize scale gesture detector
        scaleGestureDetector = ScaleGestureDetector(requireContext(), ScaleListener())
        
        // Add touch listener to the ScrollView
        view.findViewById<ScrollView>(R.id.scrollView)?.setOnTouchListener { _, event ->
            // Check if pinch zoom is allowed for pages
            if (prefHelper.isPinchZoomAllowed(isResultsWindow = false)) {
            scaleGestureDetector.onTouchEvent(event)
            }
            false
        }

        if (provider == "HOME") {
            setupUI(view)
            setupNetworkInfo(view)
            return  // Exit early for HOME layout
        }

        // Only proceed with these for non-HOME layouts
        loadCustomButtons()
        setupUI(view)
        setupNetworkInfo(view)
        setupCustomButtons(view)

        // Hide DNS button container if on LOCAL page
        if (provider == "LOCAL") {
            view.findViewById<ViewGroup>(R.id.dns_button_container)?.visibility = View.GONE
        }

        // Add this line to set up the share button
        setupShareButton(view)

        if (provider == "CUSTOM") {
            view.findViewById<LinearLayout>(R.id.buttonsContainer)?.visibility = View.GONE
            view.findViewById<LinearLayout>(R.id.localServicesContainer)?.visibility = View.GONE
            view.findViewById<LinearLayout>(R.id.continuousPingContainer)?.visibility = View.VISIBLE
        } else if (provider == "LOCAL") {
            // Keep buttonsContainer visible for LOCAL page
            view.findViewById<LinearLayout>(R.id.buttonsContainer)?.visibility = View.VISIBLE
            view.findViewById<LinearLayout>(R.id.localServicesContainer)?.visibility = View.VISIBLE
            view.findViewById<LinearLayout>(R.id.continuousPingContainer)?.visibility = View.GONE
        } else {
            // For Google and Cloudflare pages
            view.findViewById<LinearLayout>(R.id.buttonsContainer)?.visibility = View.VISIBLE
            view.findViewById<LinearLayout>(R.id.localServicesContainer)?.visibility = View.GONE
            view.findViewById<LinearLayout>(R.id.continuousPingContainer)?.visibility = View.GONE
        }

        // Set up both refresh buttons with animation
        view.findViewById<ImageButton>(R.id.refreshButton)?.setOnClickListener {
            // Show refresh animation
            val rotateAnimation = AnimationUtils.loadAnimation(context, R.anim.rotate)
            it.startAnimation(rotateAnimation)
            
            // Clear and refresh with fade
            clearAllData(view)
            refreshAllInfoWithFade(view)
        }

        // Network Info page refresh button
        view.findViewById<View>(R.id.networkInfoBox)?.findViewById<ImageButton>(R.id.refreshButton)?.setOnClickListener {
            // Show refresh animation
            val rotateAnimation = AnimationUtils.loadAnimation(context, R.anim.rotate)
            it.startAnimation(rotateAnimation)
            
            // Clear and refresh with fade
            clearAllData(view)
            refreshAllInfoWithFade(view)
        }

        // Initial data load with delay to ensure views are ready
        if (provider == "LOCAL") {
            view.post {
                refreshAllInfoWithFade(view)
            }
        }

        // If this is the LOCAL page, initialize buttons as disabled
        if (provider == "LOCAL") {
            updateLocalPageButtons()
        }

        // If this is the HOME page, update network info for LOCAL page
        if (provider == "HOME") {
            val networkInfo = NetworkInfo(requireContext())
            val dns = networkInfo.getDnsServer()
            val dhcp = networkInfo.getDhcpServer()
            val gateway = networkInfo.getGateway()

            // Find and update LOCAL page
            (activity as? MainActivity)?.let { activity ->
                activity.supportFragmentManager.findFragmentByTag("f3")?.let { fragment ->
                    if (fragment is MainFragment) {
                        fragment.updateNetworkInfo(dns, dhcp, gateway)
                    }
                }
            }
        }
    }

    private fun setupUI(view: View) {
        if (provider == "HOME") {
            // Only set up elements that exist in HOME layout
            view.findViewById<ImageButton>(R.id.shareButton)?.setOnClickListener {
                listener?.shareResults()
            }
            view.findViewById<ImageButton>(R.id.settingsButton)?.setOnClickListener {
                listener?.showSettingsOverlay()
            }
            
            // Setup FAQ button
            view.findViewById<TextView>(R.id.faqButton)?.setOnClickListener {
                FaqDialog.show(childFragmentManager)
            }
            return  // Exit early for HOME layout
        }

        // Rest of setupUI code for main layout
        view.findViewById<ImageButton>(R.id.shareButton).setOnClickListener {
            listener?.shareResults()
        }
        view.findViewById<ImageButton>(R.id.settingsButton).setOnClickListener {
            listener?.showSettingsOverlay()
        }

        // If this is not the home page, setup all other UI elements
        if (provider != "HOME") {
            // Set IP addresses and domain
            val primaryIpText = view.findViewById<TextView>(R.id.primaryIpText)
            if (provider == "LOCAL") {
                // Get gateway address for Local page
                val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                val gateway = formatIp(wifiManager.dhcpInfo.gateway)
                primaryIpText?.apply {
                    text = "Gateway: $gateway"
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    alpha = 0f  // Start faded out
                    animate().alpha(1f).setDuration(500).start()  // Fade in
                }
                primaryIp = gateway  // Update primaryIp to use gateway for ping
            } else {
                primaryIpText?.text = primaryIp
            }
            
            view.findViewById<TextView>(R.id.secondaryIpText)?.apply {
                text = if (provider == "LOCAL") {
                    "Local Services"
                } else {
                    secondaryIp
                }
            }

            view.findViewById<TextView>(R.id.domainText)?.apply {
                text = domain
                if (provider == "LOCAL") {
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
            }
            
            // Setup all other buttons and functionality
            setupPingButtons(view)
            setupTraceButtons(view)
            setupNSLookupButton(view)
            setupScreenOnButton(view)

            // Handle provider logo visibility
            val providerLogo = view.findViewById<ImageView>(R.id.providerLogo)
            when (provider) {
                "LOCAL" -> providerLogo.visibility = View.GONE
                "GOOGLE" -> {
                    providerLogo.visibility = View.VISIBLE
                    providerLogo.setImageResource(R.drawable.google)
                }
                "CLOUDFLARE" -> {
                    providerLogo.visibility = View.VISIBLE
                    providerLogo.setImageResource(R.drawable.cloudflare)
                }
                "CUSTOM" -> providerLogo.visibility = View.GONE  // Hide logo on page 4
                else -> providerLogo.visibility = View.VISIBLE
            }

            // Handle DNS button visibility
            val dnsButtonContainer = view.findViewById<ViewGroup>(R.id.dns_button_container)
            if (provider == "LOCAL") {
                // For LOCAL page, DNS button is under Local Services
                dnsButtonContainer.visibility = View.GONE
            } else {
                // For other pages, DNS button is at the bottom
                dnsButtonContainer.visibility = View.VISIBLE
            }

            // Inside setupUI function where we handle the CUSTOM page
            if (provider == "CUSTOM") {
                // Hide the primary text instead of using it as a header
                view.findViewById<TextView>(R.id.primaryIpText).visibility = View.GONE
                
                // Hide all other buttons and containers
                view.findViewById<TextView>(R.id.secondaryIpText).visibility = View.GONE
                view.findViewById<TextView>(R.id.domainText).visibility = View.GONE
                view.findViewById<ViewGroup>(R.id.button1_container).visibility = View.GONE
                view.findViewById<ViewGroup>(R.id.button1_trace_container).visibility = View.GONE
                view.findViewById<ViewGroup>(R.id.button2_container).visibility = View.GONE
                view.findViewById<ViewGroup>(R.id.button3_container).visibility = View.GONE
                view.findViewById<ViewGroup>(R.id.button3_trace_container).visibility = View.GONE
                view.findViewById<ViewGroup>(R.id.dns_button_container).visibility = View.GONE

                // Show continuous ping elements
                view.findViewById<ViewGroup>(R.id.pingAlertContainer).visibility = View.VISIBLE
                view.findViewById<Button>(R.id.continuousPingButton).visibility = View.VISIBLE

                // Get button reference from layout
                continuousPingButton = view.findViewById<Button>(R.id.continuousPingButton)
                
                // Load saved configuration
                val savedConfig = customPingButtons[10]
                
                // Set initial text
                continuousPingButton?.text = when {
                    savedConfig?.name?.isNotEmpty() == true -> savedConfig.name
                    savedConfig?.target?.isNotEmpty() == true -> savedConfig.target
                    else -> "Set Target"
                }

                // Setup click listeners
                continuousPingButton?.setOnClickListener {
                    val target = customPingButtons[10]?.target
                    if (target != null) {
                        startPing(target)
                    } else {
                        showCustomButtonDialog(10, true)
                    }
                }
                
                // Setup long click listener
                continuousPingButton?.setOnLongClickListener {
                    showCustomButtonDialog(10, true)
                    true
                }
            } else {
                // Hide continuous ping elements on all other pages
                view.findViewById<ViewGroup>(R.id.pingAlertContainer).visibility = View.GONE
                view.findViewById<Button>(R.id.continuousPingButton).visibility = View.GONE
            }

            // Show refresh button only on LOCAL page
            val refreshButton = view.findViewById<ImageButton>(R.id.refreshButton)
            refreshButton.visibility = if (provider == "LOCAL") View.VISIBLE else View.GONE
            
            if (provider == "LOCAL") {
                refreshButton.setOnClickListener {
                    refreshAllInfo(view)
                }
                
                // Hide the DNS button container for LOCAL page
                view.findViewById<ViewGroup>(R.id.dns_button_container)?.visibility = View.GONE
            }

            // Setup alert checkboxes
            val alertEveryPing = view.findViewById<CheckBox>(R.id.alertEveryPing)
            val alertTenPings = view.findViewById<CheckBox>(R.id.alertTenPings)
            val alertStatusChange = view.findViewById<CheckBox>(R.id.alertStatusChange)

            // First uncheck all
            alertEveryPing.isChecked = false
            alertTenPings.isChecked = false
            alertStatusChange.isChecked = false

            // Load saved preference and set only one
            val prefHelper = PreferenceHelper(requireContext())
            when (prefHelper.alertMode) {
                "every" -> alertEveryPing.isChecked = true
                "ten" -> alertTenPings.isChecked = true
                else -> alertStatusChange.isChecked = true  // Default or "change"
            }

            // Setup mutual exclusivity
            val checkBoxes = listOf(alertEveryPing, alertTenPings, alertStatusChange)
            checkBoxes.forEach { checkbox ->
                checkbox.setOnCheckedChangeListener { buttonView, isChecked ->
                    if (isChecked) {
                        // Uncheck others
                        checkBoxes.forEach { other ->
                            if (other != buttonView) other.isChecked = false
                        }
                        // Save preference
                        prefHelper.alertMode = when (buttonView.id) {
                            R.id.alertEveryPing -> "every"
                            R.id.alertTenPings -> "ten"
                            else -> "change"
                        }
                    } else {
                        // Don't allow unchecking if it's the only one checked
                        if (checkBoxes.none { it.isChecked }) {
                            buttonView.isChecked = true
                        }
                    }
                }
            }

            // Hide sound controls on all pages except CUSTOM
            val soundControlsContainer = view.findViewById<ViewGroup>(R.id.soundControlsContainer)
            soundControlsContainer?.visibility = if (provider == "CUSTOM") View.VISIBLE else View.GONE

            // Setup main success checkbox
            view.findViewById<CheckBox>(R.id.mainOnSuccessCheck)?.apply {
                isChecked = prefHelper.onSuccessCheck
                setOnCheckedChangeListener { _, isChecked ->
                    prefHelper.onSuccessCheck = isChecked
                }
            }

            // Setup main fail checkbox
            view.findViewById<CheckBox>(R.id.mainOnFailCheck)?.apply {
                isChecked = prefHelper.onFailCheck
                setOnCheckedChangeListener { _, isChecked ->
                    prefHelper.onFailCheck = isChecked
                }
            }
        }
        // No else needed - home page layout is already set up correctly

        setupNetworkInfo(view)

        if (provider == "HOME") {
            view.findViewById<TextView>(R.id.faqButton)?.setOnClickListener {
                FaqDialog.show(childFragmentManager)
            }
        }

        // Show Network Scan button only on LOCAL page
        val networkScanButton = view.findViewById<Button>(R.id.networkScanButton)
        val networkScanContainer = networkScanButton.parent as? ViewGroup
        networkScanContainer?.visibility = if (provider == "LOCAL") View.VISIBLE else View.GONE

        // Setup Network Scan button with functionality
        networkScanButton.setOnClickListener {
            startNetworkScan()
        }
    }

    private fun setupNetworkInfo(view: View) {
        if (provider == "HOME") {
            // Request location permission if needed
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (requireContext().checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) 
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }
            
            val networkInfoBox = view.findViewById<View>(R.id.networkInfoBox)
            val mobileDataBox = view.findViewById<View>(R.id.mobileDataBox)
            val deviceInfoBox = view.findViewById<View>(R.id.deviceInfoBox)
            
            // Get views
            val extendedInfoContainer = networkInfoBox.findViewById<LinearLayout>(R.id.extendedInfoContainer)
            val showMoreLink = networkInfoBox.findViewById<TextView>(R.id.showMoreLink)
            val refreshButton = networkInfoBox.findViewById<ImageButton>(R.id.refreshButton)

            // Setup click listeners
            showMoreLink.setOnClickListener {
                if (extendedInfoContainer.visibility == View.VISIBLE) {
                    extendedInfoContainer.visibility = View.GONE
                    showMoreLink.text = "▼"
                } else {
                    extendedInfoContainer.visibility = View.VISIBLE
                    showMoreLink.text = "▲"
                }
            }

            // Set initial state
            showMoreLink.text = "▼"

            // Setup refresh button with new fade functionality
            refreshButton.setOnClickListener {
                // Show refresh animation
                val rotateAnimation = AnimationUtils.loadAnimation(context, R.anim.rotate)
                it.startAnimation(rotateAnimation)
                
                // Clear and refresh with fade
                clearAllData(view)
                refreshAllInfoWithFade(view)
            }

            // Add bottom padding to device info box
            (deviceInfoBox.layoutParams as ViewGroup.MarginLayoutParams).apply {
                bottomMargin = 500  // Add 500px padding at the bottom
            }

            // Initial updates
            updateNetworkInfo(networkInfoBox, mobileDataBox)
            updateDeviceInfo(deviceInfoBox)
        }
    }

    private fun clearAllData(view: View) {
        // Clear Network Info box data
        view.findViewById<View>(R.id.networkInfoBox)?.apply {
            findTextViews(this).forEach { textView ->
                // Skip title TextViews and the expand arrow
                if (!textView.text.toString().endsWith(":") && 
                    textView.id != R.id.showMoreLink && 
                    !textView.text.toString().matches(Regex("[▼▲]"))) {
                    textView.alpha = 0f
                    textView.text = ""
                }
            }
        }
        
        // Clear Mobile Data box content
        view.findViewById<View>(R.id.mobileDataBox)?.apply {
            findTextViews(this).forEach { textView ->
                // Skip title TextViews
                if (!textView.text.toString().endsWith(":")) {
                    textView.alpha = 0f
                    textView.text = ""
                }
            }
        }
        
        // Clear Local page data if on LOCAL provider
        if (provider == "LOCAL") {
            view.findViewById<TextView>(R.id.localDnsSuffixText)?.apply {
                alpha = 0f
                text = ""
            }
            view.findViewById<TextView>(R.id.primaryIpText)?.apply {
                alpha = 0f
                text = ""
            }
        }
    }

    private fun refreshAllInfoWithFade(view: View) {
        coroutineScope.launch {
            delay(300) // Short delay to show cleared state
            
            withContext(Dispatchers.Main) {
                // Refresh all info
                refreshAllInfo(view)
                
                if (provider == "LOCAL") {
                    // Get and set gateway address
                    val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    @Suppress("DEPRECATION")
                    val gateway = formatIp(wifiManager.dhcpInfo.gateway)
                    view.findViewById<TextView>(R.id.primaryIpText)?.apply {
                        text = "Gateway: $gateway"
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        animate().alpha(1f).setDuration(500).start()
                    }
                    primaryIp = gateway
                    
                    // Fade in DNS suffix
                    view.findViewById<TextView>(R.id.localDnsSuffixText)?.animate()?.alpha(1f)?.setDuration(500)?.start()
                }
                
                // Update and fade in network info boxes if on HOME page
                if (provider == "HOME") {
                    view.findViewById<View>(R.id.networkInfoBox)?.let { networkInfoBox ->
                        view.findViewById<View>(R.id.mobileDataBox)?.let { mobileDataBox ->
                            view.findViewById<View>(R.id.deviceInfoBox)?.let { deviceInfoBox ->
                                updateNetworkInfo(networkInfoBox, mobileDataBox)
                                updateDeviceInfo(deviceInfoBox)
                                
                                // Fade in only the result TextViews
                                listOf(networkInfoBox, mobileDataBox, deviceInfoBox).forEach { box ->
                                    findTextViews(box).forEach { textView ->
                                        // Skip title TextViews and the expand arrow
                                        if (!textView.text.toString().endsWith(":") && 
                                            textView.id != R.id.showMoreLink && 
                                            !textView.text.toString().matches(Regex("[▼▲]"))) {
                                            textView.animate().alpha(1f).setDuration(500).start()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Helper function specifically for finding TextViews
    private fun findTextViews(root: View): List<TextView> {
        val textViews = mutableListOf<TextView>()
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i)
                if (child is TextView) {
                    textViews.add(child)
                }
                if (child is ViewGroup) {
                    textViews.addAll(findTextViews(child))
                }
            }
        }
        return textViews
    }

    private fun clearAndUpdateInfo(networkInfoBox: View, mobileDataBox: View) {
        // Clear all text views first
        networkInfoBox.findViewById<TextView>(R.id.ssidText).text = ""
        networkInfoBox.findViewById<TextView>(R.id.ipAddressText).text = ""
        networkInfoBox.findViewById<TextView>(R.id.gatewayText).text = ""
        networkInfoBox.findViewById<TextView>(R.id.dnsServersText).text = ""
        networkInfoBox.findViewById<TextView>(R.id.dnsSuffixText).text = ""
        networkInfoBox.findViewById<TextView>(R.id.signalStrengthText).text = ""
        networkInfoBox.findViewById<TextView>(R.id.dhcpStatusText).text = ""
        networkInfoBox.findViewById<TextView>(R.id.dhcpServerText).text = ""
        networkInfoBox.findViewById<TextView>(R.id.subnetMaskText).text = ""
        networkInfoBox.findViewById<TextView>(R.id.macAddressText).text = ""
        networkInfoBox.findViewById<TextView>(R.id.adapterInfoText).text = ""
        
        mobileDataBox.findViewById<TextView>(R.id.mobileDataStatusText).text = ""
        mobileDataBox.findViewById<TextView>(R.id.mobileDataTypeText).text = ""
        mobileDataBox.findViewById<TextView>(R.id.mobileDataIpText).text = ""

        // Update info after a brief delay
        coroutineScope.launch {
            delay(100) // Small delay before starting update
            updateNetworkInfo(networkInfoBox, mobileDataBox)
            
            // Apply fade-in animation to all text views
            val fadeIn = AnimationUtils.loadAnimation(context, R.anim.fade_in)
            networkInfoBox.findViewById<TextView>(R.id.ssidText).startAnimation(fadeIn)
            networkInfoBox.findViewById<TextView>(R.id.ipAddressText).startAnimation(fadeIn)
            networkInfoBox.findViewById<TextView>(R.id.gatewayText).startAnimation(fadeIn)
            networkInfoBox.findViewById<TextView>(R.id.dnsServersText).startAnimation(fadeIn)
            networkInfoBox.findViewById<TextView>(R.id.dnsSuffixText).startAnimation(fadeIn)
            networkInfoBox.findViewById<TextView>(R.id.signalStrengthText).startAnimation(fadeIn)
            networkInfoBox.findViewById<TextView>(R.id.dhcpStatusText).startAnimation(fadeIn)
            networkInfoBox.findViewById<TextView>(R.id.dhcpServerText).startAnimation(fadeIn)
            networkInfoBox.findViewById<TextView>(R.id.subnetMaskText).startAnimation(fadeIn)
            networkInfoBox.findViewById<TextView>(R.id.macAddressText).startAnimation(fadeIn)
            networkInfoBox.findViewById<TextView>(R.id.adapterInfoText).startAnimation(fadeIn)
            
            mobileDataBox.findViewById<TextView>(R.id.mobileDataStatusText).startAnimation(fadeIn)
            mobileDataBox.findViewById<TextView>(R.id.mobileDataTypeText).startAnimation(fadeIn)
            mobileDataBox.findViewById<TextView>(R.id.mobileDataIpText).startAnimation(fadeIn)
        }
    }

    private fun updateNetworkInfo(networkInfoBox: View, mobileDataBox: View) {
        val networkInfoTriple = getNetworkInfo()
        val dhcpInfo = getDhcpInfo()
        val mobileInfo = getMobileInfo()

        // Update WIFI info
        networkInfoBox.findViewById<TextView>(R.id.ssidText).text = networkInfoTriple.third
        networkInfoBox.findViewById<TextView>(R.id.ipAddressText).text = networkInfoTriple.first
        networkInfoBox.findViewById<TextView>(R.id.gatewayText).text = dhcpInfo.gateway
        networkInfoBox.findViewById<TextView>(R.id.dnsServersText).text = dhcpInfo.dns
        networkInfoBox.findViewById<TextView>(R.id.dnsSuffixText).text = detectDnsSuffix()

        // Update extended info
        networkInfoBox.findViewById<TextView>(R.id.signalStrengthText).text = "${getSignalStrength()} dBm"
        
        val dhcpEnabled = isDhcpEnabled()
        networkInfoBox.findViewById<TextView>(R.id.dhcpStatusText).text = if (dhcpEnabled) "Enabled" else "Disabled"
        
        // Show/hide DHCP Server line based on DHCP status
        networkInfoBox.findViewById<View>(R.id.dhcpServerContainer).visibility = 
            if (dhcpEnabled) View.VISIBLE else View.GONE
        if (dhcpEnabled) {
            networkInfoBox.findViewById<TextView>(R.id.dhcpServerText).text = dhcpInfo.server
        }

        networkInfoBox.findViewById<TextView>(R.id.subnetMaskText).text = networkInfoTriple.second
        networkInfoBox.findViewById<TextView>(R.id.macAddressText).text = getMacAddress()  // This is where MAC is set
        networkInfoBox.findViewById<TextView>(R.id.adapterInfoText).text = getAdapterInfo()

        // Update Mobile Data info
        mobileDataBox.findViewById<TextView>(R.id.mobileDataStatusText).text = mobileInfo.status
        mobileDataBox.findViewById<TextView>(R.id.mobileDataTypeText).text = mobileInfo.type
        mobileDataBox.findViewById<TextView>(R.id.mobileDataIpText).text = mobileInfo.ipAddress
    }

    private fun isDhcpEnabled(): Boolean {
        val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        return wifiManager.dhcpInfo.serverAddress != 0
    }

    private fun getNetworkInfo(): Triple<String, String, String> {
        return try {
            val networkInterfaces = java.net.NetworkInterface.getNetworkInterfaces().toList()
            val wifiInterface = networkInterfaces.find { it.name.startsWith("wlan") }
            val addresses = wifiInterface?.inetAddresses?.toList()
                ?.filterIsInstance<java.net.Inet4Address>()
                ?.filter { !it.isLoopbackAddress }
            
            val address = addresses?.firstOrNull()
            val subnet = wifiInterface?.interfaceAddresses
                ?.find { it.address == address }
                ?.networkPrefixLength
                ?.let { prefix ->
                    val mask = (0xffffffff).ushr(32 - prefix).shl(32 - prefix)
                    "${mask.ushr(24) and 0xff}.${mask.ushr(16) and 0xff}.${mask.ushr(8) and 0xff}.${mask and 0xff}"
                }

            // Get actual SSID from WifiManager
            val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val connectionInfo = wifiManager.connectionInfo
            val ssid = connectionInfo.ssid.let {
                when {
                    it.isNullOrEmpty() -> "Not Connected"
                    it == "<unknown ssid>" -> "Not Connected"
                    it.startsWith("\"") && it.endsWith("\"") -> it.substring(1, it.length - 1)
                    else -> it
                }
            }

            Triple(
                address?.hostAddress ?: "Not Available",
                subnet ?: "Not Available",
                ssid
            )
        } catch (e: Exception) {
            getFallbackNetworkInfo()
        }
    }

    private fun getFallbackNetworkInfo(): Triple<String, String, String> {
        val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        return Triple(
            formatIp(wifiManager.connectionInfo.ipAddress),
            formatIp(wifiManager.dhcpInfo.netmask),
            wifiManager.connectionInfo.ssid.removeSurrounding("\"")
        )
    }

    private data class DhcpInformation(
        val gateway: String,
        val dns: String,
        val server: String
    )

    private fun getDhcpInfo(): DhcpInformation {
        val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val dhcpInfo = wifiManager.dhcpInfo

        // Format DNS servers and remove "Not Available" entries
        val dns1 = formatIp(dhcpInfo.dns1)
        val dns2 = formatIp(dhcpInfo.dns2)
        val dnsServers = listOf(dns1, dns2)
            .filter { it != "Not Available" }
            .joinToString(", ")

        return DhcpInformation(
            gateway = formatIp(dhcpInfo.gateway),
            dns = dnsServers.ifEmpty { "Not Available" },
            server = formatIp(dhcpInfo.serverAddress)
        )
    }

    private fun getSignalStrength(): Int {
        val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        return wifiManager.connectionInfo.rssi
    }

    private fun getMacAddress(): String {
        val networkInfo = NetworkInfo(requireContext())
        return networkInfo.getMacAddress()
    }

    private fun formatIp(ip: Int): String {
        return if (ip == 0) {
            "Not Available"
        } else {
            "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}"
        }
    }

    private fun getAdapterInfo(): String {
        // Implementation depends on your requirements
        return "Wireless Network Adapter"
    }

    private fun setupPingButtons(view: View) {
        if (provider == "LOCAL") {
            // Get gateway address and DHCP info
            val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val dhcpInfo = wifiManager.dhcpInfo
            val gateway = formatIp(dhcpInfo.gateway)
            val dhcpServer = formatIp(dhcpInfo.serverAddress)
            val dnsServer = formatIp(dhcpInfo.dns1)
            
            // Get DNS suffix and display if available
            val dnsSuffixView = view.findViewById<TextView>(R.id.dnsSuffixText)
            
            dnsSuffixView?.apply {
                text = detectDnsSuffix()
                if (text == "Not Available") {
                    visibility = View.GONE
                } else {
                    textSize = 19.2f
                    setTextColor(Color.WHITE)
                    visibility = View.VISIBLE
                }
            }

            // Update primaryIp to use gateway for ping
            primaryIp = gateway
            // Update the header text (primaryIpText) but keep button as PING
            view.findViewById<TextView>(R.id.primaryIpText).text = "Gateway: $gateway"
            
            // Update the header text for Local Services
            view.findViewById<TextView>(R.id.secondaryIpText).text = "Local Services"

            // Setup all buttons with gateway status
            val pingButton1 = view.findViewById<Button>(R.id.pingButton1)
            val traceButton1 = view.findViewById<Button>(R.id.traceButton1)
            val dhcpButton = view.findViewById<Button>(R.id.dhcpButton)
            val dnsButton = view.findViewById<Button>(R.id.dnsButton)

            if (gateway != "Not Available") {
                // Enable and set orange color for all buttons
                listOf(pingButton1, traceButton1).forEach { button ->
                    button.setTextColor(ContextCompat.getColor(requireContext(), R.color.button_text_orange))
                    button.isEnabled = true
                }
                
                // Set up gateway button clicks
                pingButton1.setOnClickListener {
                    startPing(gateway, R.id.pingButton1)
                }
                traceButton1.setOnClickListener {
                    startTraceroute(gateway)
                }

                // Setup DHCP button
                dhcpButton.text = "DHCP"
                if (isDhcpEnabled() && dhcpServer != "Not Available") {
                    dhcpButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.button_text_orange))
                    dhcpButton.isEnabled = true
                    dhcpButton.setOnClickListener { 
                        Log.d("ProgressBar", "DHCP Button clicked")
                        startPing(getDhcpServer(), R.id.dhcpButton) 
                    }
                } else {
                    dhcpButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.button_text_dark_red))
                    dhcpButton.isEnabled = false
                }

                // Setup DNS button
                dnsButton.text = "DNS"
                if (dnsServer != "Not Available") {
                    dnsButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.button_text_orange))
                    dnsButton.isEnabled = true
                    dnsButton.setOnClickListener { 
                        startPing(getDnsServer(), R.id.dnsButton)
                    }
                } else {
                    dnsButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.button_text_dark_red))
                    dnsButton.isEnabled = false
                }
            } else {
                // Disable and set red color for all buttons if gateway is not available
                listOf(pingButton1, traceButton1, dhcpButton, dnsButton).forEach { button ->
                    button.setTextColor(ContextCompat.getColor(requireContext(), R.color.button_text_dark_red))
                    button.isEnabled = false
                }
                dhcpButton.text = "DHCP"
                dnsButton.text = "DNS"
            }

            dnsButton.visibility = View.VISIBLE
            secondaryIp = dhcpServer

            // Hide the third row and DNS button containers for Local page
            view.findViewById<TextView>(R.id.domainText)?.visibility = View.GONE
            view.findViewById<ViewGroup>(R.id.button3_container)?.visibility = View.GONE
            view.findViewById<ViewGroup>(R.id.button3_trace_container)?.visibility = View.GONE
            view.findViewById<ViewGroup>(R.id.dns_button_container)?.visibility = View.GONE
        } else {
            // Normal setup for other pages
            view.findViewById<FrameLayout>(R.id.button3_trace_container).visibility = View.VISIBLE  // Show trace button
            
            // Reset button text and color for non-LOCAL pages
            val pingButton2 = view.findViewById<Button>(R.id.dhcpButton)
            pingButton2.text = "PING"
            pingButton2.setTextColor(ContextCompat.getColor(requireContext(), R.color.button_text_orange))
            
            val traceButton2 = view.findViewById<Button>(R.id.dnsButton)
            traceButton2.text = "TRACE"  // Change back to TRACE for non-LOCAL pages
            traceButton2.setTextColor(ContextCompat.getColor(requireContext(), R.color.button_text_orange))
            
            // Setup second row buttons for non-LOCAL pages
            pingButton2.setOnClickListener {
                startPing(secondaryIp ?: return@setOnClickListener, R.id.dhcpButton)
            }
            traceButton2.setOnClickListener {
                startTraceroute(secondaryIp ?: return@setOnClickListener)
            }
            
            // Setup third row buttons
            val pingButton3 = view.findViewById<Button>(R.id.pingButton3)
            pingButton3.setOnClickListener {
                startPing(domain ?: return@setOnClickListener, R.id.pingButton3)
            }
            val traceButton3 = view.findViewById<Button>(R.id.traceButton3)
            traceButton3.setOnClickListener {  // Add click listener for third row trace button
                startTraceroute(domain ?: return@setOnClickListener)
            }
        }

        val pingButton1 = view.findViewById<Button>(R.id.pingButton1)
        pingButton1.setOnClickListener {
            startPing(primaryIp ?: return@setOnClickListener, R.id.pingButton1)
        }
    }

    private fun setupTraceButtons(view: View) {
        view.findViewById<Button>(R.id.traceButton1).setOnClickListener {
            startTraceroute(primaryIp ?: return@setOnClickListener)
        }
        view.findViewById<Button>(R.id.traceButton3).setOnClickListener {
            startTraceroute(domain ?: return@setOnClickListener)
        }
    }

    private fun setupNSLookupButton(view: View) {
        view.findViewById<Button>(R.id.nslookupButton).setOnClickListener {
            startNSLookup(domain ?: return@setOnClickListener)
        }
    }

    private fun setupScreenOnButton(view: View) {
        val keepScreenOnButton = view.findViewById<Button>(R.id.keepScreenOnButton)
        val screenOnProgressBar = view.findViewById<View>(R.id.progressBar_screen_on)
        
        if (provider == "CUSTOM") {
            keepScreenOnButton.visibility = View.VISIBLE
            var isScreenOnEnabled = false
            keepScreenOnButton.setOnClickListener {
                isScreenOnEnabled = !isScreenOnEnabled
                activity?.window?.let { window ->
                    if (isScreenOnEnabled) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        screenOnProgressBar.visibility = View.VISIBLE
                        keepScreenOnButton.text = "SCREEN ON ENABLED"
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        hideProgressBarWithFade(screenOnProgressBar)
                        keepScreenOnButton.text = "KEEP SCREEN ON"
                    }
                }
            }
        } else {
            keepScreenOnButton.visibility = View.GONE
            screenOnProgressBar.visibility = View.GONE
        }
    }

    private fun hideProgressBarWithFade(progressBar: View?) {
        progressBar?.let {
            val fadeOut = AnimationUtils.loadAnimation(context, R.anim.fade_out)
            fadeOut.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationRepeat(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {
                    it.visibility = View.INVISIBLE
                }
            })
            it.startAnimation(fadeOut)
        }
    }

    private fun startPing(target: String, buttonId: Int? = null) {
        // Find progress bar before starting coroutine
        val progressBar = when (buttonId) {
            R.id.pingButton1 -> view?.findViewById<View>(R.id.progressBar1)
            R.id.dhcpButton -> view?.findViewById<View>(R.id.dhcp_progress_bar)
            R.id.dnsButton -> view?.findViewById<View>(R.id.dns_progress_bar)
            R.id.pingButton3 -> view?.findViewById<View>(R.id.progressBar3)
            null -> when {
                customPingButtons[10]?.target == target -> view?.findViewById<View>(R.id.continuous_ping_progress_bar)
                customPingButtons.any { it.value.target == target } -> {
                    val container = view?.findViewById<GridLayout>(R.id.customPingGrid)
                        ?.getChildAt(customPingButtons.entries.find { it.value.target == target }?.key ?: -1)
                    container?.findViewById<View>(R.id.progressBar)
                }
                else -> null
            }
            else -> null
        }

        // Show progress bar immediately on UI thread
        progressBar?.visibility = View.VISIBLE

        // Launch coroutine for background work
        coroutineScope.launch(Dispatchers.IO) {
            val isContinuousPing = provider == "CUSTOM" && customPingButtons[10]?.target == target
            val isDnsButton = buttonId == R.id.dnsButton
            val isDhcpButton = buttonId == R.id.dhcpButton

            val uniqueTitle = when {
                isContinuousPing -> ResultsManager.getUniqueTitle("Continuous Ping Results for $target")
                isDnsButton -> ResultsManager.getUniqueTitle("DNS Server Ping Results")
                isDhcpButton -> ResultsManager.getUniqueTitle("DHCP Server Ping Results")
                else -> ResultsManager.getUniqueTitle("Ping Results for $target")
            }

            val initialMessage = when {
                isContinuousPing -> "Starting continuous ping to $target...\n"
                isDnsButton -> "Pinging DNS server at $target...\n"
                isDhcpButton -> "Pinging DHCP server at $target...\n"
                else -> "Pinging $target...\n"
            }

            // Switch to main thread for UI updates
            withContext(Dispatchers.Main) {
                val dialog = ResultsDialog.newInstance(
                    uniqueTitle,
                    initialMessage,
                    true,
                    isContinuousPing
                ).apply {
                    setCloseListener(object : ResultsDialog.DialogCloseListener {
                        override fun onDialogClosed() {
                            ResultsManager.removeResult(uniqueTitle)
                            if (isContinuousPing) {
                                continuousPingJob?.cancel()
                                hideProgressBarWithFade(progressBar)
                            }
                        }
                    })

                    if (isContinuousPing) {
                        setStopListener(object : ResultsDialog.StopButtonListener {
                            override fun onStopPressed() {
                                continuousPingJob?.cancel()
                                hideProgressBarWithFade(progressBar)
                            }
                        })
                    }
                }

                ResultsManager.addResult(uniqueTitle, initialMessage)
                dialog.show(childFragmentManager, "results")

                // Continue with ping in background
                launch(Dispatchers.IO) {
                    try {
                        if (isContinuousPing) {
                            startContinuousPing(target, dialog, uniqueTitle)
                        } else {
                            executePing(target, dialog, uniqueTitle)
                        }
                    } finally {
                        withContext(Dispatchers.Main) {
                            hideProgressBarWithFade(progressBar)
                        }
                    }
                }
            }
        }
    }

    private suspend fun executePing(target: String, dialog: ResultsDialog, uniqueTitle: String) {
        val process = Runtime.getRuntime().exec("ping -c 4 $target")
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val output = StringBuilder("Pinging $target...\n")
        
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            output.append(line).append("\n")
            withContext(Dispatchers.Main) {
                val currentOutput = output.toString()
                dialog.arguments?.putString("result", currentOutput)
                dialog.view?.findViewById<TextView>(R.id.resultText)?.text = currentOutput
                ResultsManager.updateResult(uniqueTitle, currentOutput)
            }
        }
        process.waitFor()
    }

    private fun startContinuousPing(target: String, resultsDialog: ResultsDialog, uniqueTitle: String) {
        // Add initial result to activeResults before starting the ping
        ResultsManager.addResult(uniqueTitle, "Starting continuous ping to $target...\n")
        val completeOutput = StringBuilder()
        
        continuousPingJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val process = Runtime.getRuntime().exec("/system/bin/ping -c 1 $target")
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    val output = StringBuilder()
                    var isSuccess = false
                    var line: String?

                    // Read output in IO context
                    while (reader.readLine().also { line = it } != null) {
                        output.append(line).append("\n")
                        if (line?.contains("1 received") == true) {
                            isSuccess = true
                        }
                    }

                    val newOutput = output.toString()
                    completeOutput.append(newOutput)

                    // Switch to Main thread only for UI updates
                    withContext(Dispatchers.Main) {
                        pingCounter++
                        resultsDialog.appendResult(newOutput)
                        ResultsManager.updateResult(uniqueTitle, completeOutput.toString())

                        // Get alert preferences once
                        val alertPrefs = getAlertPreferences(resultsDialog)
                        
                        // Check if we should play sound
                        if (shouldPlaySound(alertPrefs, isSuccess)) {
                            playPingSound(isSuccess)
                        }

                        lastPingStatus = isSuccess
                    }
                } catch (e: Exception) {
                    val errorOutput = "Error: ${e.message}\n"
                    completeOutput.append(errorOutput)
                    
                    withContext(Dispatchers.Main) {
                        resultsDialog.appendResult(errorOutput)
                        ResultsManager.updateResult(uniqueTitle, completeOutput.toString())
                    }
                }
                delay(1000)
            }
        }
    }

    private data class AlertPreferences(
        val onSuccessCheck: Boolean,
        val onFailCheck: Boolean,
        val alertEveryPing: Boolean,
        val alertTenPings: Boolean,
        val alertStatusChange: Boolean
    )

    private fun getAlertPreferences(resultsDialog: ResultsDialog): AlertPreferences {
        return AlertPreferences(
            onSuccessCheck = resultsDialog.view?.findViewById<CheckBox>(R.id.onSuccessCheck)?.isChecked ?: true,
            onFailCheck = resultsDialog.view?.findViewById<CheckBox>(R.id.onFailCheck)?.isChecked ?: true,
            alertEveryPing = view?.findViewById<CheckBox>(R.id.alertEveryPing)?.isChecked ?: false,
            alertTenPings = view?.findViewById<CheckBox>(R.id.alertTenPings)?.isChecked ?: false,
            alertStatusChange = view?.findViewById<CheckBox>(R.id.alertStatusChange)?.isChecked ?: true
        )
    }

    private fun shouldPlaySound(prefs: AlertPreferences, isSuccess: Boolean): Boolean {
        val shouldAlert = when {
            prefs.alertEveryPing -> true
            prefs.alertTenPings -> pingCounter % 10 == 0
            prefs.alertStatusChange -> lastPingStatus != isSuccess
            else -> false
        }

        return shouldAlert && (
            (isSuccess && prefs.onSuccessCheck) || 
            (!isSuccess && prefs.onFailCheck)
        )
    }

    private fun playPingSound(success: Boolean) {
        try {
            val prefHelper = PreferenceHelper(requireContext())
            
            // Check for custom sound URIs first
            val customSoundUri = if (success) prefHelper.successSoundUri else prefHelper.failSoundUri
            
            if (customSoundUri != null) {
                // Play custom sound using MediaPlayer
                android.media.MediaPlayer().apply {  // Create MediaPlayer inline without variable
                    setDataSource(requireContext(), android.net.Uri.parse(customSoundUri))
                    setOnPreparedListener { it.start() }
                    setOnCompletionListener { it.release() }
                    prepareAsync()
                }
            } else {
                // Fall back to default tones if no custom sound is set
                val toneType = if (success) {
                    prefHelper.successSound
                } else {
                    prefHelper.failSound
                }
                
                val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                toneGen.startTone(toneType, 150)
                
                // Release the ToneGenerator after a delay
                coroutineScope.launch {
                    delay(200)
                    toneGen.release()
                }
            }
        } catch (e: Exception) {
            Log.e("MainFragment", "Error playing sound: ${e.message}")
        }
    }

    private fun startTraceroute(target: String) {
        coroutineScope.launch {
            val networkInfo = NetworkInfo(requireContext())
            val progressBar: View? = when {
                provider == "LOCAL" -> when {
                    target == primaryIp -> view?.findViewById<View>(R.id.progressBar1_trace)
                    target == networkInfo.getDhcpServer() -> view?.findViewById<View>(R.id.dhcp_progress_bar)
                    target == networkInfo.getDnsServer() -> view?.findViewById<View>(R.id.dns_progress_bar)
                    else -> null
                }
                target == primaryIp -> view?.findViewById<View>(R.id.progressBar1_trace)
                target == secondaryIp -> view?.findViewById<View>(R.id.dns_progress_bar)
                target == domain -> view?.findViewById<View>(R.id.progressBar3_trace)
                customTraceButtons.any { it.value.target == target } -> {
                    val container = view?.findViewById<GridLayout>(R.id.customTraceGrid)
                        ?.getChildAt(customTraceButtons.entries.find { it.value.target == target }?.key ?: -1)
                    container?.findViewById(R.id.progressBar)
                }
                else -> null
            }
            progressBar?.visibility = View.VISIBLE

            // Show dialog immediately with initial message
            val dialog = ResultsDialog.newInstance(
                "Traceroute Results for $target",
                "Starting traceroute to $target...\n",
                false
            ).apply {
                setCloseListener(object : ResultsDialog.DialogCloseListener {
                    override fun onDialogClosed() {
                        ResultsManager.removeResult("Traceroute Results for $target")
                    }
                })
            }

            // Add initial result
            ResultsManager.addResult("Traceroute Results for $target", "Starting traceroute to $target...\n")
            
            dialog.show(childFragmentManager, "results")

            // Wait for dialog to be created
            delay(100)
            val resultText = dialog.view?.findViewById<TextView>(R.id.resultText)

            withContext(Dispatchers.IO) {
                try {
                    val output = StringBuilder("Tracing route to $target\n\n")
                    withContext(Dispatchers.Main) {
                        dialog.arguments?.putString("result", output.toString())
                        resultText?.text = output.toString()
                    }
                    
                    // Try up to 30 hops
                    for (ttl in 1..30) {
                        val process = Runtime.getRuntime().exec("ping -c 1 -t $ttl $target")
                        val reader = BufferedReader(InputStreamReader(process.inputStream))
                        val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                        var hopResult = ""
                        var line: String?
                        
                        while (reader.readLine().also { line = it } != null) {
                            if (line?.contains("From") == true || line?.contains("64 bytes from") == true) {
                                hopResult = line ?: ""
                                break
                            }
                        }
                        
                        var errorOutput = ""
                        while (errorReader.readLine().also { line = it } != null) {
                            errorOutput += line + "\n"
                        }
                        
                        process.waitFor()
                        
                        // Update output and UI for each hop
                        if (hopResult.isNotEmpty()) {
                            output.append("$ttl: $hopResult\n")
                        } else if (errorOutput.isNotEmpty()) {
                            output.append("$ttl: * * * (Error or timeout)\n")
                        } else {
                            output.append("$ttl: * * * No response\n")
                        }

                        // Update UI on main thread
                        withContext(Dispatchers.Main) {
                            val currentOutput = output.toString()
                            dialog.arguments?.putString("result", currentOutput)
                            resultText?.text = currentOutput
                            // Update active results
                            ResultsManager.updateResult("Traceroute Results for $target", currentOutput)
                        }
                        
                        // Check if we reached target
                        if (hopResult.isNotEmpty() && 
                            (target.contains(".") && hopResult.contains("64 bytes from") || 
                             !target.contains(".") && hopResult.contains(target))) {
                            break
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        val errorOutput = "Traceroute failed: ${e.message}"
                        dialog.arguments?.putString("result", errorOutput)
                        resultText?.text = errorOutput
                        // Update active results with error
                        ResultsManager.updateResult("Traceroute Results for $target", errorOutput)
                    }
                }
            }
            
            hideProgressBarWithFade(progressBar)
        }
    }

    private fun startNSLookup(target: String) {
        coroutineScope.launch {
            val progressBar = view?.findViewById<View>(R.id.progressBar3_dns)
            progressBar?.visibility = View.VISIBLE

            val dialog = ResultsDialog.newInstance(
                "NSLookup Results for $target",
                "Looking up $target...\n",
                false
            ).apply {
                setCloseListener(object : ResultsDialog.DialogCloseListener {
                    override fun onDialogClosed() {
                        ResultsManager.removeResult("NSLookup Results for $target")
                    }
                })
            }

            // Add initial result
            ResultsManager.addResult("NSLookup Results for $target", "Looking up $target...\n")
            
            dialog.show(childFragmentManager, "results")

            delay(100)
            val resultText = dialog.view?.findViewById<TextView>(R.id.resultText)

            withContext(Dispatchers.IO) {
                try {
                    val output = StringBuilder("Looking up DNS servers...\n")
                    withContext(Dispatchers.Main) {
                        dialog.arguments?.putString("result", output.toString())
                        resultText?.text = output.toString()
                    }

                    output.append("\nServer(s):\n")
                    val dnsServers = mutableSetOf<String>()

                    // Get DNS servers using ConnectivityManager
                    val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    val network = connectivityManager.activeNetwork
                    val linkProperties = connectivityManager.getLinkProperties(network)
                    linkProperties?.dnsServers?.forEach { server ->
                        server.hostAddress?.let { address ->
                            dnsServers.add(address)
                            output.append("\t").append(address).append("\n")
                            withContext(Dispatchers.Main) {
                                dialog.arguments?.putString("result", output.toString())
                                resultText?.text = output.toString()
                            }
                        }
                    }

                    // Try to get additional DNS servers from getprop
                    try {
                        for (i in 1..4) {
                            val process = Runtime.getRuntime().exec("getprop net.dns$i")
                            val reader = BufferedReader(InputStreamReader(process.inputStream))
                            reader.readLine()?.trim()?.let { dns ->
                                if (dns.isNotEmpty() && dns != "0.0.0.0") {
                                    dnsServers.add(dns)
                                    output.append("\t").append(dns).append("\n")
                                    withContext(Dispatchers.Main) {
                                        dialog.arguments?.putString("result", output.toString())
                                        resultText?.text = output.toString()
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MainFragment", "Failed to get DNS from getprop", e)
                    }

                    // Add Google DNS as fallback if no servers found
                    if (dnsServers.isEmpty()) {
                        dnsServers.add("8.8.8.8")
                        output.append("\t8.8.8.8 (fallback)\n")
                        withContext(Dispatchers.Main) {
                            dialog.arguments?.putString("result", output.toString())
                            resultText?.text = output.toString()
                        }
                    }

                    // Resolve domain using InetAddress
                    output.append("\nResolving $target...\n")
                    withContext(Dispatchers.Main) {
                        dialog.arguments?.putString("result", output.toString())
                        resultText?.text = output.toString()
                    }

                    output.append("Name:\t").append(target).append("\n")
                    val addresses = java.net.InetAddress.getAllByName(target)
                    for (address in addresses) {
                        output.append("Address:\t").append(address.hostAddress).append("\n")
                        withContext(Dispatchers.Main) {
                            val currentOutput = output.toString()
                            dialog.arguments?.putString("result", currentOutput)
                            resultText?.text = currentOutput
                            // Update ResultsManager with the current complete output
                            ResultsManager.updateResult("NSLookup Results for $target", currentOutput)
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        val errorOutput = "NSLookup failed: ${e.message}"
                        dialog.arguments?.putString("result", errorOutput)
                        resultText?.text = errorOutput
                        ResultsManager.updateResult("NSLookup Results for $target", errorOutput)
                    }
                }
            }
            
            hideProgressBarWithFade(progressBar)
        }
    }

    private data class MobileInfo(
        val status: String,
        val ipAddress: String,
        val type: String
    )

    private fun getMobileInfo(): MobileInfo {
        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                val linkProperties = connectivityManager.getLinkProperties(network)
                
                if (capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true) {
                    // Get network type without using telephony manager
                    val networkType = when {
                        capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> "5G/4G"
                        capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED) -> "4G/3G"
                        else -> "Connected"
                    }
                    
                    MobileInfo(
                        status = "Connected",
                        ipAddress = linkProperties?.linkAddresses?.firstOrNull()?.address?.hostAddress ?: "Not Available",
                        type = networkType
                    )
                } else {
                    MobileInfo("Not Connected", "Not Available", "Not Available")
                }
            } else {
                MobileInfo("Not Available", "Not Available", "Not Available")
            }
        } catch (e: Exception) {
            MobileInfo("Not Connected", "Not Available", "Not Available")  // Changed from "Error" to "Not Connected"
        }
    }

    private fun updateDeviceInfo(deviceInfoBox: View) {
        deviceInfoBox.findViewById<TextView>(R.id.deviceModelText).text = android.os.Build.MODEL
        
        // Fix Samsung capitalization
        val manufacturer = android.os.Build.MANUFACTURER
        deviceInfoBox.findViewById<TextView>(R.id.deviceManufacturerText).text = 
            if (manufacturer.equals("samsung", ignoreCase = true)) "Samsung" else manufacturer
        
        deviceInfoBox.findViewById<TextView>(R.id.androidVersionText).text = 
            "Android ${android.os.Build.VERSION.RELEASE}"
    }

    override fun onDestroy() {
        super.onDestroy()
        continuousPingJob?.cancel()
        coroutineScope.cancel()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
                    // Permissions granted, update info
                    view?.let { view ->
                        val networkInfoBox = view.findViewById<View>(R.id.networkInfoBox)
                        val mobileDataBox = view.findViewById<View>(R.id.mobileDataBox)
                        val deviceInfoBox = view.findViewById<View>(R.id.deviceInfoBox)
                        updateNetworkInfo(networkInfoBox, mobileDataBox)
                        updateDeviceInfo(deviceInfoBox)
                    }
                }
            }
        }
    }

    private fun setupCustomButtons(view: View) {
        // Skip setup for HOME page
        if (provider == "HOME") return

        // Get references to custom elements
        val customPingHeader = view.findViewById<TextView>(R.id.customPingHeader)
        val customTraceHeader = view.findViewById<TextView>(R.id.customTraceHeader)
        val customPingGrid = view.findViewById<GridLayout>(R.id.customPingGrid)
        val customTraceGrid = view.findViewById<GridLayout>(R.id.customTraceGrid)
        
        if (provider == "LOCAL") {
            // Show custom elements
            customPingHeader.visibility = View.VISIBLE
            customTraceHeader.visibility = View.VISIBLE
            customPingGrid.visibility = View.VISIBLE
            customTraceGrid.visibility = View.VISIBLE
            
            // Create 10 buttons for each grid
            for (i in 0..9) {
                // Create ping button with container and progress bar
                val pingContainer = createButtonContainer()
                val pingButton = pingContainer.findViewById<Button>(R.id.button)
                
                // Set initial button text using saved configuration
                val pingConfig = customPingButtons[i]
                pingButton.text = when {
                    pingConfig?.name?.isNotEmpty() == true -> pingConfig.name
                    pingConfig?.target?.isNotEmpty() == true -> pingConfig.target
                    else -> (i + 1).toString()
                }
                
                // Setup click listeners
                pingButton.setOnClickListener {
                    val target = customPingButtons[i]?.target
                    if (target != null) {
                        startPing(target)
                    } else {
                        showCustomButtonDialog(i, true)
                    }
                }
                
                pingButton.setOnLongClickListener {
                    showCustomButtonDialog(i, true)
                    true
                }
                
                customPingGrid.addView(pingContainer)

                // Create trace button with container and progress bar
                val traceContainer = createButtonContainer()
                val traceButton = traceContainer.findViewById<Button>(R.id.button)
                
                // Set initial button text using saved configuration
                val traceConfig = customTraceButtons[i]
                traceButton.text = when {
                    traceConfig?.name?.isNotEmpty() == true -> traceConfig.name
                    traceConfig?.target?.isNotEmpty() == true -> traceConfig.target
                    else -> (i + 1).toString()
                }
                
                // Setup click listeners
                traceButton.setOnClickListener {
                    val target = customTraceButtons[i]?.target
                    if (target != null) {
                        startTraceroute(target)
                    } else {
                        showCustomButtonDialog(i, false)
                    }
                }
                
                traceButton.setOnLongClickListener {
                    showCustomButtonDialog(i, false)
                    true
                }
                
                customTraceGrid.addView(traceContainer)
            }
        } else {
            // Hide custom elements on non-LOCAL pages
            customPingHeader.visibility = View.GONE
            customTraceHeader.visibility = View.GONE
            customPingGrid.visibility = View.GONE
            customTraceGrid.visibility = View.GONE
        }
    }

    private fun createButtonContainer(): FrameLayout {
        return (layoutInflater.inflate(
            R.layout.custom_button_container, 
            null
        ) as FrameLayout).apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(4, 4, 4, 4)
            }
        }
    }

    private fun showCustomButtonDialog(index: Int, isPing: Boolean) {
        val buttons = if (isPing) customPingButtons else customTraceButtons
        val currentButton = buttons[index]

        // Create dialog with proper style
        val dialog = Dialog(requireContext(), R.style.FullScreenDialog)
        val popupView = LayoutInflater.from(context).inflate(R.layout.custom_button_popup, null)
        dialog.setContentView(popupView)
        
        // Get references to views
        val nameInput = popupView.findViewById<EditText>(R.id.nameInput)
        val targetInput = popupView.findViewById<EditText>(R.id.targetInput)
        val saveButton = popupView.findViewById<ImageButton>(R.id.saveButton)
        val closeButton = popupView.findViewById<ImageButton>(R.id.closeButton)

        // Set current values if they exist
        nameInput.setText(currentButton?.name?.takeUnless { it == (index + 1).toString() })
        targetInput.setText(currentButton?.target)

        // Handle save button click
        saveButton.setOnClickListener {
            val name = nameInput.text.toString()
            val target = targetInput.text.toString()
            
            val buttonName = when {
                name.isNotEmpty() -> name
                target.isNotEmpty() -> target
                else -> (index + 1).toString()
            }

            buttons[index] = CustomButton(buttonName, target)
            saveCustomButtons()

            // Update button text
            if (provider == "CUSTOM" && index == 10) {
                continuousPingButton?.text = buttonName
            } else {
                val gridId = if (isPing) R.id.customPingGrid else R.id.customTraceGrid
                view?.findViewById<GridLayout>(gridId)
                    ?.getChildAt(index)
                    ?.findViewById<Button>(R.id.button)
                    ?.text = buttonName
            }

            dialog.dismiss()
        }

        // Handle close button click
        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        // Show dialog
        dialog.show()

        // Set dialog window attributes
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            attributes?.windowAnimations = R.style.DialogAnimation
        }
    }

    private fun saveCustomButtons() {
        val sharedPrefs = requireContext().getSharedPreferences("custom_buttons", Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()

        // Save ping buttons
        customPingButtons.forEach { (index, button) ->
            editor.putString("ping_name_$index", button.name)
            editor.putString("ping_target_$index", button.target)
        }

        // Save trace buttons
        customTraceButtons.forEach { (index, button) ->
            editor.putString("trace_name_$index", button.name)
            editor.putString("trace_target_$index", button.target)
        }

        editor.apply()
    }

    private fun loadCustomButtons() {
        val sharedPrefs = requireContext().getSharedPreferences("custom_buttons", Context.MODE_PRIVATE)

        // Load ping buttons (including continuous ping button)
        for (i in 0..10) {  // Changed from 0..9 to 0..10
            val name = sharedPrefs.getString("ping_name_$i", null)
            val target = sharedPrefs.getString("ping_target_$i", null)
            if (target != null) {
                customPingButtons[i] = CustomButton(name ?: target, target)
            }
        }

        // Load trace buttons
        for (i in 0..9) {
            val name = sharedPrefs.getString("trace_name_$i", null)
            val target = sharedPrefs.getString("trace_target_$i", null)
            if (target != null) {
                customTraceButtons[i] = CustomButton(name ?: target, target)
            }
        }
    }

    fun refreshCustomButtons() {
        // Clear local button maps
        customPingButtons.clear()
        customTraceButtons.clear()
        
        // Load saved configurations
        loadCustomButtons()
        
        // Reload the custom buttons view
        view?.let { view ->
            // Refresh regular custom buttons
            val customPingGrid = view.findViewById<GridLayout>(R.id.customPingGrid)
            val customTraceGrid = view.findViewById<GridLayout>(R.id.customTraceGrid)
            
            // Clear existing buttons
            customPingGrid?.removeAllViews()
            customTraceGrid?.removeAllViews()
            
            // Re-setup buttons
            setupCustomButtons(view)

            // Refresh continuous ping button if on CUSTOM page
            if (provider == "CUSTOM") {
                val savedConfig = customPingButtons[10]
                continuousPingButton?.text = when {
                    savedConfig?.name?.isNotEmpty() == true -> savedConfig.name
                    savedConfig?.target?.isNotEmpty() == true -> savedConfig.target
                    else -> "Set Target"
                }
            }
        }
    }

    private fun hideStopButton() {
        // Find the current dialog and hide its stop button
        (childFragmentManager.findFragmentByTag("results") as? ResultsDialog)?.hideStopButton()
    }

    private fun refreshAllInfo(view: View) {
        // Reset the cached DNS suffix so it will be detected again
        detectedDnsSuffix = null
        
        // Only try to update these views if we're not on the HOME page
        if (provider != "HOME") {
            // Get DNS suffix and display if available
            val networkInfo = NetworkInfo(requireContext())
                val dnsSuffix = networkInfo.getDnsSuffix()
                
            Log.d("MainFragment", "Refreshing DNS Suffix: $dnsSuffix for provider: $provider")
            
            // Update Local page DNS suffix
            if (provider == "LOCAL") {
                view.findViewById<TextView>(R.id.localDnsSuffixText)?.apply {
                    Log.d("MainFragment", "Updating LOCAL page DNS suffix view")
                if (dnsSuffix != "Not Available") {
                    text = dnsSuffix
                        textSize = 16f
                    setTextColor(Color.WHITE)
                    visibility = View.VISIBLE
                        Log.d("MainFragment", "LOCAL DNS suffix view properties: " +
                            "text='${text}' " +
                            "visibility=${visibility} " +
                            "width=${width} " +
                            "height=${height} " +
                            "parent=${parent?.javaClass?.simpleName}")
                } else {
                    visibility = View.GONE
                        Log.d("MainFragment", "LOCAL DNS suffix view hidden")
                    }
                } ?: Log.e("MainFragment", "Could not find localDnsSuffixText view")
            }
        }
    }

    private fun detectDnsSuffix(): String {
        if (detectedDnsSuffix == null) {
            val networkInfo = NetworkInfo(requireContext())
            detectedDnsSuffix = networkInfo.getDnsSuffix()
        }
        return detectedDnsSuffix ?: "Not Available"
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(0.5f, 3.0f)

            // Apply scale to the main layout
            view?.findViewById<LinearLayout>(R.id.mainLayout)?.apply {
                scaleX = scaleFactor
                scaleY = scaleFactor
            }
            return true
        }
    }

    private fun setupShareButton(view: View) {
        view.findViewById<ImageButton>(R.id.shareButton)?.setOnClickListener {
            shareNetworkInfo()
        }
    }

    private fun shareNetworkInfo() {
            val networkInfo = NetworkInfo(requireContext())
            val shareText = buildString {
                // Network Information Section
                append("Network Information\n")
                append("==================\n\n")
                
                // WiFi Section
                append("WiFi Adapter\n")
                append("--------------\n")
                append("Status: ${networkInfo.getWifiStatus()}\n")
                append("SSID: ${networkInfo.getSSID()}\n")
                append("Signal Strength: ${networkInfo.getSignalStrength()} dBm\n")
                append("MAC Address: ${networkInfo.getMacAddress()}\n")
                append("Gateway: ${networkInfo.getGateway()}\n")
                append("DHCP Server: ${networkInfo.getDhcpServer()}\n")
                append("DNS Server: ${networkInfo.getDnsServer()}\n")
                
                val dnsSuffix = networkInfo.getDnsSuffix()
                if (dnsSuffix != "Not Available") {
                    append("DNS Suffix: $dnsSuffix\n")
                }
                append("\n")

                // Mobile Data Section
                networkInfo.getMobileDataInfo()?.let { mobileInfo ->
                    append("Mobile Data\n")
                    append("--------------\n")
                    append("Network Type: ${mobileInfo.networkType}\n")
                    append("Operator: ${mobileInfo.operator}\n")
                    append("Signal Strength: ${mobileInfo.signalStrength}\n")
                    append("\n")
                }

                // Device Information Section
                append("Device Information\n")
                append("-----------------\n")
                append("Model: ${Build.MODEL}\n")
                append("Manufacturer: ${Build.MANUFACTURER}\n")
                append("Android Version: ${Build.VERSION.RELEASE}\n")
                append("\n")

                // Test Results Section
                val allResults = ResultsManager.getAllResults()
                if (allResults.isNotEmpty()) {
                    append("Test Results\n")
                    append("============\n\n")
                    
                    allResults.forEach { (title, content) ->
                        append("$title\n")
                        append("-".repeat(title.length))
                        append("\n")
                        append("$content\n")
                        append("\n")
                    }
                }
            }

            // Create and start share intent
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Network Information"))
    }

    private fun getDhcpServer(): String {
        val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        return formatIp(wifiManager.dhcpInfo.serverAddress)
    }

    private fun getDnsServer(): String {
        val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        return formatIp(wifiManager.dhcpInfo.dns1)
    }

    private fun startNetworkScan() {
        // Check for location permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (requireContext().checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                return
            }
        }

        val progressBar = view?.findViewById<View>(R.id.progressBar)
        progressBar?.visibility = View.VISIBLE

        // Create and show the dialog
        val dialog = ResultsDialog.newInstance("Network Scan", "", true, false)
        dialog.show(parentFragmentManager, "network_scan")

        // Set window attributes for full width at top
        dialog.setWindowAttributes { window ->
            window.attributes = window.attributes.apply {
                width = WindowManager.LayoutParams.MATCH_PARENT
                y = 150  // Position below taskbar
            }
        }

        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val networkInfo = NetworkInfo(requireContext())
                    val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    @Suppress("DEPRECATION")
                    val dhcpInfo = wifiManager.dhcpInfo
                    val ipAddress = formatIp(dhcpInfo.ipAddress)
                    val gateway = formatIp(dhcpInfo.gateway)

                    // Get domain name
                    val domainName = networkInfo.getDnsSuffix()

                    // Get subnet mask
                    val subnetMask = if (dhcpInfo.netmask != 0) {
                        formatIp(dhcpInfo.netmask)
                    } else {
                        "255.255.255.0"  // Default subnet mask for class C networks
                    }

                    val output = StringBuilder()
                    output.append("Network Scan Results\n")
                    output.append("====================\n\n")
                    output.append("Local Network: ${getNetworkAddress(ipAddress, subnetMask)}\n")
                    output.append("Subnet Mask: $subnetMask\n")
                    output.append("Domain Name: $domainName\n")
                    output.append("Gateway: $gateway\n\n")
                    output.append("Scanning for devices...\n\n")

                    withContext(Dispatchers.Main) {
                        dialog.appendResult(output.toString())
                        ResultsManager.updateResult("Network Scan", output.toString())
                    }

                    // Get base IP for scanning
                    val baseIp = ipAddress.substring(0, ipAddress.lastIndexOf(".") + 1)
                    val activeIps = mutableSetOf<String>()

                    // Scan network and populate ARP cache
                    val jobs = (1..254).map { i ->
                        async {
                            val targetIp = baseIp + i
                            if (isReachable(targetIp)) {
                                activeIps.add(targetIp)
                            }
                        }
                    }
                    jobs.awaitAll()

                    // Give ARP cache time to update
                    delay(2000)

                    // Get MAC addresses
                    val arpResults = getArpResults()

                    // Sort and display results
                    output.append("Found ${activeIps.size} devices:\n\n")

                    // Sort IPs numerically
                    val sortedIps = activeIps.sortedBy { ip ->
                        ip.split(".").map { it.padStart(3, '0') }.joinToString(".")
                    }

                    // Process each IP
                    sortedIps.forEach { ip ->
                        val hostname = getHostname(ip)
                        val macAddress = if (ip == gateway) arpResults[ip] else getMacFromIp(ip)
                        
                        val deviceInfo = StringBuilder()
                        deviceInfo.append(String.format("%-15s  %s\n", ip, hostname))
                        
                        if (macAddress != null) {
                            val vendor = getMacVendor(macAddress)
                            deviceInfo.append(String.format("%-15s  %s\n", 
                                " " + macAddress,
                                vendor ?: ""
                            ))
                        }
                        
                        deviceInfo.append("\n")
                        output.append(deviceInfo)

                        withContext(Dispatchers.Main) {
                            dialog.appendResult(deviceInfo.toString())
                            ResultsManager.updateResult("Network Scan", output.toString())
                        }
                    }

                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        val errorOutput = "Scan failed: ${e.message}"
                        dialog.appendResult(errorOutput)
                        ResultsManager.updateResult("Network Scan", errorOutput)
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        progressBar?.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun getNetworkAddress(ip: String, subnet: String): String {
        try {
            val ipParts = ip.split(".").map { it.toInt() }
            val subnetParts = subnet.split(".").map { it.toInt() }
            val networkParts = ipParts.zip(subnetParts) { i, s -> i and s }
            return networkParts.joinToString(".") + "/24"
        } catch (e: Exception) {
            return "$ip/24"
        }
    }

    private suspend fun isReachable(ip: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("ping -c 1 -W 1 $ip")
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun getArpResults(): Map<String, String> {
        val results = mutableMapOf<String, String>()
        try {
            val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val dhcpInfo = wifiManager.dhcpInfo
            val gateway = formatIp(dhcpInfo.gateway)
            
            @Suppress("DEPRECATION")
            wifiManager.connectionInfo.bssid?.uppercase()?.let { mac ->
                if (mac != "00:00:00:00:00:00") {
                    results[gateway] = mac
                }
            }
        } catch (e: Exception) {
            Log.e("NetworkScan", "Error reading MAC addresses", e)
        }
        return results
    }

    private suspend fun getHostname(ip: String): String = withContext(Dispatchers.IO) {
        try {
            val methods = listOf(
                { getHostnameFromGetent(ip) },
                { getHostnameFromNbtstat(ip) },
                { getHostnameFromDns(ip) }
            )

            for (method in methods) {
                val result = method()
                if (result != null && result != ip) {
                    return@withContext result
                }
            }
            return@withContext ip
        } catch (e: Exception) {
            ip
        }
    }

    private fun getMacFromIp(ip: String): String? {
        try {
            val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiManager.dhcpInfo?.let { dhcpInfo ->
                val gateway = formatIp(dhcpInfo.gateway)
                if (ip == gateway) {
                    return wifiManager.connectionInfo.bssid?.uppercase()
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                wifiManager.scanResults.forEach { scanResult ->
                    if (scanResult.BSSID.uppercase() == ip.uppercase()) {
                        return scanResult.BSSID.uppercase()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("NetworkScan", "Error getting MAC for IP: $ip", e)
        }
        return null
    }

    private suspend fun getMacVendor(mac: String): String? = withContext(Dispatchers.IO) {
        try {
            val oui = mac.substring(0, 8).replace(":", "-").uppercase()
            val url = URL("https://api.macvendors.com/$oui")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                return@withContext reader.readLine()
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun updateDialog(dialog: ResultsDialog, resultText: TextView?, content: String) {
        withContext(Dispatchers.Main) {
            dialog.arguments?.putString("result", content)
            resultText?.text = content
            ResultsManager.updateResult("Network Scan", content)
        }
    }

    private fun getHostnameFromGetent(ip: String): String? {
        return try {
            val process = Runtime.getRuntime().exec("getent hosts $ip")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            if (!line.isNullOrEmpty()) {
                line.split("\\s+".toRegex())[1]
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun getHostnameFromNbtstat(ip: String): String? {
        return try {
            val process = Runtime.getRuntime().exec("nmblookup -A $ip")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line?.contains("<00>") == true) {
                    return line?.trim()?.split("\\s+".toRegex())?.firstOrNull()
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun getHostnameFromDns(ip: String): String? {
        return try {
            java.net.InetAddress.getByName(ip).hostName.let { 
                if (it != ip) it else null 
            }
        } catch (e: Exception) {
            null
            }
    }

    // Add this method to update network info and button states
    fun updateNetworkInfo(dns: String?, dhcp: String?, gw: String?) {
        Log.d("NetworkDebug", "Updating network info - DNS: $dns, DHCP: $dhcp, Gateway: $gw")
        dnsServer = dns
        dhcpServer = dhcp
        gateway = gw
        updateLocalPageButtons()
    }

    private fun updateLocalPageButtons() {
        if (provider != "LOCAL") return
        if (view?.findViewById<Button>(R.id.pingButton1) == null) return

        // Initialize network info values
        gateway = networkInfo.getGateway()
        dhcpServer = networkInfo.getDhcpServer()
        dnsServer = networkInfo.getDnsServer()

        view?.let { view ->
            Log.d("ButtonDebug", "Updating LOCAL page buttons")
            Log.d("ButtonDebug", "Gateway: $gateway")
            Log.d("ButtonDebug", "DHCP: $dhcpServer")
            Log.d("ButtonDebug", "DNS: $dnsServer")

            // DNS Button
            view.findViewById<Button>(R.id.dnsButton)?.apply {
                val isDnsAvailable = dnsServer != null && isValidIpv4(dnsServer!!)
                Log.d("ButtonDebug", "DNS Available: $isDnsAvailable")
                isEnabled = isDnsAvailable
                setTextColor(if (isDnsAvailable)
                    ContextCompat.getColor(context, R.color.button_text_orange)
                else
                    Color.parseColor("#FF0000"))
            }

            // DHCP Button
            view.findViewById<Button>(R.id.dhcpButton)?.apply {
                val isDhcpAvailable = dhcpServer != null && isValidIpv4(dhcpServer!!)
                Log.d("ButtonDebug", "DHCP Available: $isDhcpAvailable")
                isEnabled = isDhcpAvailable
                setTextColor(if (isDhcpAvailable)
                    ContextCompat.getColor(context, R.color.button_text_orange)
                else
                    Color.parseColor("#FF0000"))
            }

            // Gateway-dependent buttons
            val isGatewayAvailable = gateway != null && isValidIpv4(gateway!!)
            Log.d("ButtonDebug", "Gateway Available: $isGatewayAvailable")

            val gatewayDependentButtons = listOf(
                R.id.pingButton1,
                R.id.traceButton1,
                R.id.networkScanButton
            )

            gatewayDependentButtons.forEach { buttonId ->
                view.findViewById<Button>(buttonId)?.apply {
                    isEnabled = isGatewayAvailable
                    setTextColor(if (isGatewayAvailable)
                        ContextCompat.getColor(context, R.color.button_text_orange)
                    else
                        Color.parseColor("#FF0000"))
                    Log.d("ButtonDebug", "Setting ${resources.getResourceEntryName(id)} enabled: $isGatewayAvailable")
                }
            }
        }
    }

    private fun updateNetworkInfoBox(view: View) {
        if (provider != "HOME") return

        val networkInfo = NetworkInfo(requireContext())
        
        // Update network info box views
        view.findViewById<TextView>(R.id.ssidText)?.text = networkInfo.getSSID()
        view.findViewById<TextView>(R.id.ipAddressText)?.text = networkInfo.getIpAddress()
        view.findViewById<TextView>(R.id.gatewayText)?.text = networkInfo.getGateway()  // Add this line
        view.findViewById<TextView>(R.id.dnsServersText)?.text = networkInfo.getDnsServer()
        view.findViewById<TextView>(R.id.dhcpServerText)?.text = networkInfo.getDhcpServer()
        view.findViewById<TextView>(R.id.subnetMaskText)?.text = networkInfo.getSubnetMask()
        view.findViewById<TextView>(R.id.macAddressText)?.text = networkInfo.getMacAddress()
        view.findViewById<TextView>(R.id.dnsSuffixText)?.text = networkInfo.getDnsSuffix()
        
        // Update LOCAL page buttons
        (activity as? MainActivity)?.let { activity ->
            activity.supportFragmentManager.findFragmentByTag("f3")?.let { fragment ->
                if (fragment is MainFragment) {
                    fragment.updateNetworkInfo(
                        networkInfo.getDnsServer(),
                        networkInfo.getDhcpServer(),
                        networkInfo.getGateway()
                    )
                }
            }
        }

        // Update Results Manager
        val networkInfoContent = StringBuilder().apply {
            appendLine("WIFI Adapter:")
            appendLine("SSID: ${networkInfo.getSSID()}")
            appendLine("IP Address: ${networkInfo.getIpAddress()}")
            appendLine("Gateway: ${networkInfo.getGateway()}")
            appendLine("DNS Server: ${networkInfo.getDnsServer()}")
            appendLine("DHCP Server: ${networkInfo.getDhcpServer()}")
            appendLine("Subnet Mask: ${networkInfo.getSubnetMask()}")
            appendLine("Physical Address: ${networkInfo.getMacAddress()}")
            appendLine("DNS Suffix: ${networkInfo.getDnsSuffix()}")
        }.toString()

        if (ResultsManager.hasResult("Network Info")) {
            ResultsManager.updateResult("Network Info", networkInfoContent)
        } else {
            ResultsManager.addResult("Network Info", networkInfoContent)
        }
    }

    private fun isValidIpv4(ip: String): Boolean {
        // Check if the string matches IPv4 format (x.x.x.x where x is 0-255)
        val ipv4Regex = """^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$""".toRegex()
        return ipv4Regex.matches(ip)
    }
} 