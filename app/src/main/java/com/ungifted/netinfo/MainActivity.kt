package com.ungifted.netinfo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.view.View
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.ImageButton
import android.content.Context
import androidx.activity.OnBackPressedCallback
import android.content.Intent
import android.app.Dialog
import android.widget.CheckBox
import androidx.viewpager2.widget.ViewPager2
import android.net.wifi.WifiManager
import android.widget.ImageView

class MainActivity : AppCompatActivity(), MainFragment.MainFragmentListener {
    private val TAG = "MainActivity"
    private lateinit var minimizedTabsContainer: LinearLayout
    private lateinit var preferenceHelper: PreferenceHelper
    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        // Hide the action bar
        supportActionBar?.hide()
        
        // Make status bar same color as background
        window.statusBarColor = resources.getColor(android.R.color.transparent, theme)
        window.setBackgroundDrawableResource(R.color.background_color)
        
        setContentView(R.layout.activity_main_pager)
        
        // Initialize views
        minimizedTabsContainer = findViewById(R.id.minimizedTabsContainer)
        preferenceHelper = PreferenceHelper(this)
        
        // Initialize ViewPager
        viewPager = findViewById(R.id.viewPager)
        viewPager.adapter = MainPagerAdapter(this)
        
        viewPager.apply {
            isUserInputEnabled = true  // Enable swiping
            offscreenPageLimit = 3  // Keep all pages in memory
        }

        // Enable circular scrolling
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            private var previousState = ViewPager2.SCROLL_STATE_IDLE
            private var previousPosition = 0

            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                    previousPosition = viewPager.currentItem
                } else if (state == ViewPager2.SCROLL_STATE_IDLE && previousState == ViewPager2.SCROLL_STATE_DRAGGING) {
                    val lastPage = (viewPager.adapter?.itemCount ?: 5) - 1
                    // Only wrap when actually scrolling from edge pages
                    if (previousPosition == lastPage && viewPager.currentItem == 0) {
                        // Natural scroll from last to first - let it happen
                    } else if (previousPosition == 0 && viewPager.currentItem == lastPage) {
                        // Natural scroll from first to last - let it happen
                    } else if (previousPosition == lastPage) {
                        viewPager.setCurrentItem(0, false)
                    } else if (previousPosition == 0) {
                        viewPager.setCurrentItem(lastPage, false)
                    }
                }
                previousState = state
            }
        })

        // Handle back press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val openDialogs = supportFragmentManager.fragments.filterIsInstance<ResultsDialog>()
                if (openDialogs.isNotEmpty()) {
                    openDialogs.forEach { dialog ->
                        dialog.view?.findViewById<ImageButton>(R.id.minimizeButton)?.performClick()
                    }
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Show welcome popup if needed
        if (preferenceHelper.showWelcomePopup) {
            showWelcomePopup()
        }

        // Set initial page based on preference
        val defaultPage = preferenceHelper.defaultPage
        val pageIndex = when (defaultPage) {
            "HOME" -> 0
            "CLOUDFLARE" -> 1
            "GOOGLE" -> 2
            "LOCAL" -> 3
            "CUSTOM" -> 4
            else -> 0
        }
        viewPager.setCurrentItem(pageIndex, false)
    }

    fun addMinimizedTab(title: String, dialog: ResultsDialog): View {
        val tabView = layoutInflater.inflate(R.layout.minimized_tab, minimizedTabsContainer, false)
        tabView.tag = dialog
        
        tabView.findViewById<TextView>(R.id.tabTitle).text = title
        
        tabView.setOnClickListener {
            dialog.view?.findViewById<ImageButton>(R.id.minimizeButton)?.performClick()
        }
        
        val closeButton = tabView.findViewById<ImageButton>(R.id.tabCloseButton)
        closeButton.visibility = View.GONE
        closeButton.setOnClickListener {
            removeMinimizedTab(tabView)
            dialog.dismiss()
        }
        
        minimizedTabsContainer.addView(tabView)
        return tabView
    }

    fun removeMinimizedTab(tabView: View) {
        minimizedTabsContainer.removeView(tabView)
    }

    private fun showWelcomePopup() {
        val dialog = Dialog(this, R.style.DialogTheme)
        dialog.setContentView(R.layout.welcome_popup)
        
        dialog.findViewById<TextView>(R.id.privacyPolicyLink).setOnClickListener {
            showPrivacyPolicy()
        }
        
        val checkbox = dialog.findViewById<CheckBox>(R.id.dontShowAgainCheckbox)
        dialog.findViewById<ImageButton>(R.id.okButton).setOnClickListener {
            if (checkbox.isChecked) {
                preferenceHelper.showWelcomePopup = false
            }
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun showPrivacyPolicy() {
        val dialog = Dialog(this, R.style.FullScreenDialog)
        dialog.setContentView(R.layout.privacy_policy)
        dialog.findViewById<ImageButton>(R.id.confirmButton).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showAboutApp() {
        val dialog = Dialog(this, R.style.FullScreenDialog)
        dialog.setContentView(R.layout.about_app)
        dialog.findViewById<ImageButton>(R.id.confirmButton).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    override fun showSettingsOverlay() {
        // Replace the regular Dialog with our SettingsOverlay DialogFragment
        val settingsOverlay = SettingsOverlay()
        settingsOverlay.show(supportFragmentManager, "settings")
    }

    override fun shareResults() {
        // Create share intent
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            
            // Get network info to share
            val networkInfo = StringBuilder().apply {
                append("Network Information:\n")
                // Add network details here
                append("SSID: ${getWifiSSID()}\n")
                append("IP: ${getIPAddress()}\n")
                // Add other network details as needed
            }
            
            putExtra(Intent.EXTRA_TEXT, networkInfo.toString())
        }
        
        startActivity(Intent.createChooser(shareIntent, "Share Network Info"))
    }

    private fun getWifiSSID(): String {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        return wifiManager.connectionInfo.ssid.removeSurrounding("\"")
    }

    private fun getIPAddress(): String {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val ipInt = wifiManager.connectionInfo.ipAddress
        return "${ipInt and 0xff}.${ipInt shr 8 and 0xff}.${ipInt shr 16 and 0xff}.${ipInt shr 24 and 0xff}"
    }

    fun updateLogoVisibility() {
        val prefHelper = PreferenceHelper(this)
        val shouldShowLogo = prefHelper.showLogo
        
        // Update logo visibility on Network Info and LOCAL pages
        listOf(0, 3).forEach { index ->
            val fragment = supportFragmentManager.findFragmentByTag("f$index")
            if (fragment is MainFragment) {
                fragment.view?.findViewById<ImageView>(R.id.networkLogo)?.apply {
                    visibility = if (shouldShowLogo) View.VISIBLE else View.GONE
                }
            }
        }
    }
} 