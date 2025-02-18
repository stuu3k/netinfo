package com.ungifted.netinfo

import android.media.AudioAttributes
import android.media.ToneGenerator
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import android.widget.Toast
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.widget.CheckBox
import android.widget.Button
import android.content.Context
import androidx.viewpager2.widget.ViewPager2
import android.app.Dialog
import android.widget.TextView
import android.widget.SeekBar

class SettingsOverlay : DialogFragment() {
    private lateinit var prefHelper: PreferenceHelper

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d("SettingsOverlay", "Permission result: $isGranted")
        if (isGranted) {
            Log.d("SettingsOverlay", "Permission granted, launching picker")
            launchAudioPicker()
        } else {
            Log.d("SettingsOverlay", "Permission denied by user")
            // Show a message to the user
            Toast.makeText(requireContext(), 
                "Permission needed to select audio files", 
                Toast.LENGTH_SHORT).show()
        }
    }

    private var isPickingSuccessSound = true

    private val pickSound = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        Log.d("SettingsOverlay", "Picker result URI: $uri")
        uri?.let {
            try {
                // Verify we can access the file
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                inputStream?.close()
                
                if (isPickingSuccessSound) {
                    prefHelper.successSoundUri = uri.toString()
                    Log.d("SettingsOverlay", "Saved success sound URI: ${uri}")
                } else {
                    prefHelper.failSoundUri = uri.toString()
                    Log.d("SettingsOverlay", "Saved fail sound URI: ${uri}")
                }
                playCustomSound(uri)
            } catch (e: Exception) {
                Log.e("SettingsOverlay", "Error accessing selected file: ${e.message}")
                Toast.makeText(requireContext(), 
                    "Unable to access selected file", 
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    init {
        Log.d("SettingsOverlay", "SettingsOverlay instance created")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("SettingsOverlay", "onCreate called")
        setStyle(STYLE_NO_TITLE, R.style.CustomDialog)
        // Prevent dialog from being cancelled by clicking outside
        isCancelable = false
        prefHelper = PreferenceHelper(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d("SettingsOverlay", "onCreateView called")
        return inflater.inflate(R.layout.settings_overlay, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("SettingsOverlay", "onViewCreated called")

        // Setup welcome checkbox
        view.findViewById<CheckBox>(R.id.showWelcomeCheckbox)?.apply {
            isChecked = prefHelper.showWelcomePopup
        }

        // Setup show logo checkbox
        val showLogoCheckbox = view.findViewById<CheckBox>(R.id.showLogoCheckbox)
        showLogoCheckbox.isChecked = prefHelper.showLogo
        showLogoCheckbox.setOnCheckedChangeListener { _, isChecked ->
            prefHelper.showLogo = isChecked
            // Update logo visibility in active fragments
            (activity as? MainActivity)?.updateLogoVisibility()
        }

        // Setup clear buttons
        view.findViewById<Button>(R.id.clearButtonsButton)?.setOnClickListener {
            // Show confirmation dialog
            val dialog = Dialog(requireContext(), R.style.DialogTheme)
            dialog.setContentView(R.layout.confirmation_dialog)
            
            dialog.findViewById<TextView>(R.id.confirmationMessage).text = "Are you sure you want to clear all custom buttons?"
            
            dialog.findViewById<Button>(R.id.yesButton).setOnClickListener {
                // Clear the buttons
                requireContext().getSharedPreferences("custom_buttons", Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply()
                
                // Notify MainActivity to refresh buttons
                (activity as? MainActivity)?.let { mainActivity ->
                    val currentFragment = mainActivity.supportFragmentManager
                        .findFragmentByTag("f${mainActivity.findViewById<ViewPager2>(R.id.viewPager).currentItem}")
                    if (currentFragment is MainFragment) {
                        currentFragment.refreshCustomButtons()
                    }
                }
                dialog.dismiss()
            }
            
            dialog.findViewById<Button>(R.id.noButton).setOnClickListener {
                dialog.dismiss()
            }
            
            dialog.show()
        }

        // Setup close and save buttons
        view.findViewById<ImageButton>(R.id.closeButton)?.setOnClickListener {
            dismiss()
        }

        // Setup default page spinner
        val spinner = view.findViewById<Spinner>(R.id.defaultPageSpinner)
        
        // Create mapping of friendly names to actual page identifiers
        val pageMapping = mapOf(
            "Info" to "HOME",
            "Cloudflare" to "CLOUDFLARE",
            "Google" to "GOOGLE",
            "Local" to "LOCAL",
            "Ping" to "CUSTOM"
        )
        
        // Create adapter with friendly names and right-aligned text
        ArrayAdapter(
            requireContext(),
            R.layout.spinner_item,  // Use our custom layout for the main view
            pageMapping.keys.toList()
        ).also { adapter ->
            // Use our custom layout for the dropdown items
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
            spinner.adapter = adapter
        }
        
        // Set initial selection based on saved preference
        val currentPage = prefHelper.defaultPage
        val friendlyName = pageMapping.entries.find { it.value == currentPage }?.key
        friendlyName?.let {
            val position = pageMapping.keys.indexOf(it)
            spinner.setSelection(position)
        }
        
        // Handle selection changes
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                val selectedFriendlyName = parent.getItemAtPosition(pos) as String
                val pageIdentifier = pageMapping[selectedFriendlyName]
                pageIdentifier?.let {
                    prefHelper.defaultPage = it
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }

        // Setup sound selection buttons
        view.findViewById<ImageButton>(R.id.selectSuccessSoundButton)?.let { button ->
            Log.d("SettingsOverlay", "Found success button")
            button.setOnClickListener {
                Log.d("SettingsOverlay", "Success button clicked")
                isPickingSuccessSound = true
                checkPermissionAndPickSound()
            }
        } ?: run {
            Log.e("SettingsOverlay", "Success button NOT found! View hierarchy: ${dumpViewHierarchy(view)}")
        }

        view.findViewById<ImageButton>(R.id.selectFailSoundButton)?.let { button ->
            Log.d("SettingsOverlay", "Found fail button")
            button.setOnClickListener {
                Log.d("SettingsOverlay", "Fail button clicked")
                isPickingSuccessSound = false
                checkPermissionAndPickSound()
            }
        } ?: run {
            Log.e("SettingsOverlay", "Fail button NOT found!")
        }

        // Add reset button functionality
        view.findViewById<ImageButton>(R.id.resetSoundsButton)?.setOnClickListener {
            // Reset sound preferences to defaults
            prefHelper.successSound = ToneGenerator.TONE_PROP_BEEP
            prefHelper.failSound = ToneGenerator.TONE_PROP_NACK
            
            // Clear any custom sound URIs
            prefHelper.successSoundUri = null
            prefHelper.failSoundUri = null
            
            // Update the UI to show default values
            view.findViewById<ImageButton>(R.id.selectSuccessSoundButton)?.setImageResource(android.R.drawable.ic_menu_add)
            view.findViewById<ImageButton>(R.id.selectFailSoundButton)?.setImageResource(android.R.drawable.ic_menu_add)
            
            Toast.makeText(requireContext(), "Sounds reset to defaults", Toast.LENGTH_SHORT).show()
        }

        // Update save button to also save the page selection
        view.findViewById<ImageButton>(R.id.saveButton)?.setOnClickListener {
            view.findViewById<CheckBox>(R.id.showWelcomeCheckbox)?.let { checkbox ->
                prefHelper.showWelcomePopup = checkbox.isChecked
            }
            // Page selection is already saved in the onItemSelected callback
            dismiss()
        }

        // Setup FAQ link
        view.findViewById<TextView>(R.id.faqLink)?.setOnClickListener {
            FaqDialog.show(parentFragmentManager)
        }

        // Setup pinch zoom spinner
        val pinchZoomSpinner = view.findViewById<Spinner>(R.id.pinchZoomSpinner)

        // Create list of options
        val pinchZoomOptions = listOf("Disabled", "Enabled", "Pages", "Results")

        // Create adapter with right-aligned text
        ArrayAdapter(
            requireContext(),
            R.layout.spinner_item,
            pinchZoomOptions
        ).also { adapter ->
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
            pinchZoomSpinner.adapter = adapter
        }

        // Set initial selection based on saved preference
        val currentPinchMode = prefHelper.pinchZoomMode
        val modePosition = pinchZoomOptions.indexOf(currentPinchMode)
        if (modePosition >= 0) {
            pinchZoomSpinner.setSelection(modePosition)
        }

        // Handle selection changes
        pinchZoomSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                val selectedMode = parent.getItemAtPosition(pos) as String
                prefHelper.pinchZoomMode = selectedMode
            }
            
            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }

        // Setup default text size slider
        val textSizeSeekBar = view.findViewById<SeekBar>(R.id.defaultTextSizeSeekBar)

        // Convert current text size to progress (12sp is default = 20 progress)
        val currentSize = prefHelper.defaultResultsTextSize
        val progress = ((currentSize - 10f) * (100f / 10f)).toInt()  // Convert 10-20sp to 0-100 progress
        textSizeSeekBar.progress = progress

        textSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Convert progress (0-100) to text size (10sp-20sp)
                val textSize = 10f + (progress / 100f * 10f)
                prefHelper.defaultResultsTextSize = textSize
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun dumpViewHierarchy(view: View, indent: String = ""): String {
        val sb = StringBuilder()
        sb.append("$indent${view.javaClass.simpleName} - ${view.id}\n")
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                sb.append(dumpViewHierarchy(view.getChildAt(i), "$indent  "))
            }
        }
        return sb.toString()
    }

    private fun checkPermissionAndPickSound() {
        Log.d("SettingsOverlay", "Checking permissions...")
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(requireContext(), permission) 
                == PackageManager.PERMISSION_GRANTED -> {
                Log.d("SettingsOverlay", "Permission already granted")
                launchAudioPicker()
            }
            shouldShowRequestPermissionRationale(permission) -> {
                Log.d("SettingsOverlay", "Should show permission rationale")
                // Show explanation to the user
                Toast.makeText(requireContext(), 
                    "Permission needed to select audio files", 
                    Toast.LENGTH_SHORT).show()
                requestPermissionLauncher.launch(permission)
            }
            else -> {
                Log.d("SettingsOverlay", "Requesting permission")
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun launchAudioPicker() {
        try {
            Log.d("SettingsOverlay", "Launching audio picker")
            pickSound.launch("audio/*")
        } catch (e: Exception) {
            Log.e("SettingsOverlay", "Error launching picker: ${e.message}")
            Toast.makeText(requireContext(), 
                "Error launching file picker", 
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun playCustomSound(uri: Uri) {
        Log.d("SettingsOverlay", "Playing custom sound from URI: $uri")
        try {
            val mediaPlayer = MediaPlayer().apply {
                setDataSource(requireContext(), uri)
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                prepare()
                start()
            }
            mediaPlayer.setOnCompletionListener { 
                it.release()
                Log.d("SettingsOverlay", "Sound playback completed")
            }
        } catch (e: Exception) {
            Log.e("SettingsOverlay", "Error playing custom sound: ${e.message}")
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            // Remove any margin around the dialog
            setBackgroundDrawable(null)
            decorView.background = null
        }
    }
} 