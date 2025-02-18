package com.ungifted.netinfo

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import android.graphics.Typeface

class FaqDialog : DialogFragment() {
    private var currentPopup: DialogFragment? = null
    
    data class FaqItem(
        val title: String,
        val content: String
    )

    val faqItems = listOf(
        FaqItem(
            "App Basics",
            "You can Zoom and in out on all pages and results windows! (If Enabled in Settings).\n\n" +
            "There are 5 basic pages, first is Basic Netowrk Information...\n\n" +
            "2 preconfigure pages for most common basic tests (CloudFlare or Google),\n\n" +
            "One Local Network & Custom Buttons page and a Continuous Ping page.\n\n" +
            "Tap a button to use it!\n" +
            "Long press to configure the target and name.\n\n" +
            "You're Results can be Exported (Includes all Open and Minimized Results Windows).\n" +
            "Tap to Share Results\n\n" +
            "Use the Refresh button on the first page or local page to update network information.\n\n" +
            "Buttons and Settins will save to device."
        ),
        FaqItem(
            "Custom Targets",
            "You can create custom ping and trace buttons for frequently used targets. " +
            "An Unset Target can be tapped or held to be set.\n\n" +
            "Tap a button to use it, or long press to configure the target and name.\n\n" +
            "The name field is optional, if not used the Target IP/Domain will be used instead.\n\n" +
            "You can Reset the Custom Buttons all at once in the settings menu.\n\n" +
            "You can also delete a target by long pressing on it, clearing the fields and saving."
        ),
        FaqItem(
            "Continuous Ping Feature",
            "The continuous ping feature allows you to monitor a target continuously with optional sound alerts.\n\n" +
            "There is 3x2 options for sound alerts, firstly you can enable sounds for either Success or Fail or Both.\n\n" +
            "Secondly you can set the sound for every ping or every 10 pings or only on status changes.\n\n" +
            "Tap a button to use it, or long press to configure the target and name."
        ),
        FaqItem(
            "Local Network Tools",
            "The Local page provides tools for your local network including gateway ping.\n\n" +
            "It Detects the following information from your WIFI Adapter Automatically...\n\n" +
            "DNS Suffix\n" +
            "Gateway IP\n" +
            "DHCP Server IP (if available)\n" +
            "DNS server IP\n\n" +
            "These can then be easily tapped to test.\n" +
            "Use the refresh button to update gateway and DHCP information."
        ),
        FaqItem(
            "Settings",
            "The settings menu contains a variety of settings to make the app easier to use.\n\n" +
            "Default Page: Set you're starting page after app restart.\n\n" +
            "Pinch 2 Zoom: Allows you to zoom in and out on all pages and results windows or just one or the other.\n\n" +
            "Results Font Size: Here you to set the default font size for the results text (you can still zoom in and out with slider or pith Pinch 2 Zoom if enabled).\n\n" +
            "Show Logo: Gives you the ability to hide the logo from the 'Network Info' and 'Local / Custom' pages, for reduced scrolling.\n\n" +
            "Show Weolcome Message: Definitely doesn't allow you to fly!\n\n" +
            "The fifth is the Custom Sound Alerts, this allows you to set a custom sound for coutinuous pings, you can set different sounds for Sucess or Fail.\n\n" +
            "There is a reset button in the top right coner of the Alert Sounds section if you want to reset just the sounds back to defaults.\n\n" +
            "Finally there is a Reset Custom Buttons button, this allows you to reset the custom buttons to the default values."
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.CustomDialog)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.faq_popup, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup close button
        view.findViewById<ImageButton>(R.id.closeButton).setOnClickListener {
            dismiss()
        }

        // Add FAQ items
        val container = view.findViewById<LinearLayout>(R.id.faqItemsContainer)

        // Add initial line break
        addSpace(container)

        faqItems.forEach { item ->
            val itemView = TextView(requireContext()).apply {
                text = item.title
                textSize = 16f
                setTextColor(Color.WHITE)
                setPadding(0, 16, 0, 16)
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                // Add click listener to show content
                setOnClickListener {
                    AboutAppDialog.showWithContent(parentFragmentManager, item.content)
                }
            }
            container.addView(itemView)

            // Add divider after each item except the last one
            if (item != faqItems.last()) {
                val divider = View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1
                    ).apply {
                        setMargins(32, 8, 32, 8)
                    }
                    setBackgroundColor(Color.parseColor("#33FFFFFF"))
                }
                container.addView(divider)
            }
        }

        // Add final line break
        addSpace(container)

        // Setup about app link
        view.findViewById<TextView>(R.id.aboutAppLink).apply {
            setTypeface(null, Typeface.NORMAL)
            setOnClickListener {
                AboutAppDialog.show(parentFragmentManager)
            }
        }

        // Setup privacy policy link
        view.findViewById<TextView>(R.id.privacyPolicyLink).apply {
            setTypeface(null, Typeface.NORMAL)
            setOnClickListener {
                AboutAppDialog.showWithContent(parentFragmentManager, """
                    Privacy Policy

                    This app does not collect or store any personal information.
                    
                    Network information is only used locally on your device for testing purposes.
                    
                    The only permissions required are:
                    - Internet access (for ping and trace functions)
                    - Location (optional, for WiFi information)
                    - Storage (optional, for custom sound alerts)
                    
                    No data is transmitted to external servers.
                    """.trimIndent())
            }
        }
    }

    private fun addSpace(container: LinearLayout) {
        val space = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                32
            )
        }
        container.addView(space)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(null)
            decorView.background = null
        }
    }

    companion object {
        fun show(fragmentManager: FragmentManager) {
            FaqDialog().show(fragmentManager, "faq")
        }

        fun showAboutApp(fragmentManager: FragmentManager) {
            AboutAppDialog.show(fragmentManager)
        }

        fun showPrivacyPolicy(fragmentManager: FragmentManager) {
            AboutAppDialog.showWithContent(fragmentManager, """
                Privacy Policy

                This app does not collect or store any personal information.
                
                Network information is only used locally on your device for testing purposes.
                
                The only permissions required are:
                - Internet access (for ping and trace functions)
                - Location (optional, for WiFi information)
                - Storage (optional, for custom sound alerts)
                
                No data is transmitted to external servers.
                """.trimIndent())
        }
    }
} 