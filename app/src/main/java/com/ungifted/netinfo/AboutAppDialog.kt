package com.ungifted.netinfo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager

class AboutAppDialog : DialogFragment() {
    private var content: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.CustomDialog)
        content = arguments?.getString("content")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.about_app, container, false)

        // Setup close button
        view.findViewById<ImageButton>(R.id.closeButton)?.setOnClickListener {
            dismiss()
        }

        // If custom content is provided, use a simple layout with just the content
        content?.let { customContent ->
            // Inflate a simple layout for custom content
            return inflater.inflate(R.layout.about_app_custom, container, false).apply {
                findViewById<TextView>(R.id.contentText)?.text = customContent
                findViewById<ImageButton>(R.id.closeButton)?.setOnClickListener {
                    dismiss()
                }
            }
        }

        return view
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
            AboutAppDialog().show(fragmentManager, "about_app_dialog")
        }

        fun showWithContent(fragmentManager: FragmentManager, content: String) {
            AboutAppDialog().apply {
                arguments = Bundle().apply {
                    putString("content", content)
                }
            }.show(fragmentManager, "about_app_dialog")
        }
    }
} 