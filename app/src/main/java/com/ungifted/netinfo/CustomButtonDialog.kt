package com.ungifted.netinfo

import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class CustomButtonDialog : DialogFragment() {
    private var listener: CustomButtonDialogListener? = null

    interface CustomButtonDialogListener {
        fun onSave(name: String, target: String)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext(), R.style.FullScreenDialog)
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_custom_button, null)

        // Get references to views
        val targetInput = view.findViewById<EditText>(R.id.targetInput)
        val nameInput = view.findViewById<EditText>(R.id.nameInput)

        // Set initial values
        targetInput.setText(arguments?.getString(ARG_TARGET))
        nameInput.setText(arguments?.getString(ARG_NAME))  // Show existing name if it exists

        builder.setView(view)
            .setTitle("Custom Button Setup")
            .setPositiveButton("Save") { _, _ ->
                val target = targetInput.text.toString()
                if (target.isNotEmpty()) {
                    val name = nameInput.text.toString()
                    listener?.onSave(name, target)  // Pass the actual name text
                }
            }
            .setNegativeButton("Cancel", null)

        return builder.create()
    }

    fun setListener(listener: CustomButtonDialogListener) {
        this.listener = listener
    }

    companion object {
        private const val ARG_NAME = "name"
        private const val ARG_TARGET = "target"

        fun newInstance(name: String?, target: String?) =
            CustomButtonDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_NAME, name)  // Pass the current name
                    putString(ARG_TARGET, target)
                }
            }
    }
} 