package com.haseeb.circletosearch

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.haseeb.circletosearch.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    private val OVERLAY_PERM_REQUEST = 2084

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(Prefs.FILE, MODE_PRIVATE)

        applySysBarColors()
        restoreSettings()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI setup
    // ─────────────────────────────────────────────────────────────────────────

    private fun applySysBarColors() {
        window.statusBarColor     = ContextCompat.getColor(this, R.color.bg_primary)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.bg_primary)
    }

    /** Restore all switch/seekbar states from SharedPreferences. */
    private fun restoreSettings() {
        binding.switchGestureOverlay.isChecked =
            prefs.getBoolean(Prefs.GESTURE_OVERLAY_ENABLED, true)
        
        val sizeVal = prefs.getInt(Prefs.GESTURE_AREA_SIZE, 1) // default medium
        binding.seekBarSize.progress = sizeVal
        binding.tvSizeValue.text = sizeLabel(sizeVal)
        binding.switchLaunchVibrate.isChecked =
            prefs.getBoolean(Prefs.LAUNCH_VIBRATE, true)
        binding.switchTriggerVibrate.isChecked =
            prefs.getBoolean(Prefs.TRIGGER_VIBRATE, true)

        val thresholdMs = prefs.getInt(Prefs.THRESHOLD_MS, Prefs.DEFAULT_THRESHOLD_MS)
        val thresholdIdx = Prefs.THRESHOLD_OPTIONS.indexOfFirst { it == thresholdMs }
            .takeIf { it >= 0 } ?: 1
        binding.seekBarThreshold.progress = thresholdIdx
        binding.tvThresholdValue.text = "${Prefs.THRESHOLD_OPTIONS[thresholdIdx]}ms"

        val intensity = prefs.getInt(Prefs.VIBRATION_INTENSITY, Prefs.DEFAULT_INTENSITY)
        binding.seekBarIntensity.progress = intensity
        binding.tvIntensityValue.text = intensityLabel(intensity)
    }

    private fun setupListeners() {
        // ── Switch rows — tap anywhere on the row to toggle ─────────────────

        binding.rowGestureOverlay.setOnClickListener {
            val newVal = !binding.switchGestureOverlay.isChecked
            binding.switchGestureOverlay.isChecked = newVal
            prefs.edit().putBoolean(Prefs.GESTURE_OVERLAY_ENABLED, newVal).apply()
            restartServiceIfRunning()
        }

        // ── Size seekbar ────────────────────────────────────────────────

        binding.seekBarSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvSizeValue.text = sizeLabel(progress)
                if (fromUser) {
                    prefs.edit().putInt(Prefs.GESTURE_AREA_SIZE, progress).apply()
                    restartServiceIfRunning()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // ── Assistant settings button ────────────────────────────────────────

        binding.btnAssistantSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.rowLaunchVibrate.setOnClickListener {
            val newVal = !binding.switchLaunchVibrate.isChecked
            binding.switchLaunchVibrate.isChecked = newVal
            prefs.edit().putBoolean(Prefs.LAUNCH_VIBRATE, newVal).apply()
        }

        binding.rowTriggerVibrate.setOnClickListener {
            val newVal = !binding.switchTriggerVibrate.isChecked
            binding.switchTriggerVibrate.isChecked = newVal
            prefs.edit().putBoolean(Prefs.TRIGGER_VIBRATE, newVal).apply()
            restartServiceIfRunning()
        }

        // ── Threshold seekbar ────────────────────────────────────────────────

        binding.seekBarThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val ms = Prefs.THRESHOLD_OPTIONS[progress]
                binding.tvThresholdValue.text = "${ms}ms"
                if (fromUser) {
                    prefs.edit().putInt(Prefs.THRESHOLD_MS, ms).apply()
                    restartServiceIfRunning()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // ── Intensity seekbar ────────────────────────────────────────────────

        binding.seekBarIntensity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvIntensityValue.text = intensityLabel(progress)
                if (fromUser) {
                    prefs.edit().putInt(Prefs.VIBRATION_INTENSITY, progress).apply()
                    restartServiceIfRunning()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // ── Main action button ───────────────────────────────────────────────

        binding.btnAction.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Service control
    // ─────────────────────────────────────────────────────────────────────────

    private fun restartServiceIfRunning() {
        // Now handled completely by the SharedPrefs listener in CtsAccessibilityService
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI state
    // ─────────────────────────────────────────────────────────────────────────

    private fun refreshUi() {
        val running = CtsAccessibilityService.isRunning

        if (running) {
            setStatusBadge(
                text   = getString(R.string.status_active),
                dotColor  = R.color.status_active,
                pillColor = R.color.status_active,
                textColor = R.color.status_active,
            )
            binding.tvStatusDesc.text = getString(R.string.status_desc_running)
            binding.btnAction.text = getString(R.string.btn_deactivate)
            binding.btnAction.backgroundTintList =
                ContextCompat.getColorStateList(this, R.color.status_inactive)
        } else {
            setStatusBadge(
                text   = getString(R.string.status_inactive),
                dotColor  = R.color.status_inactive,
                pillColor = R.color.status_inactive,
                textColor = R.color.status_inactive,
            )
            binding.tvStatusDesc.text = getString(R.string.status_desc_stopped)
            binding.btnAction.text = getString(R.string.btn_activate)
            binding.btnAction.backgroundTintList =
                ContextCompat.getColorStateList(this, R.color.brand_blue)
        }
    }

    private fun setStatusBadge(text: String, dotColor: Int, pillColor: Int, textColor: Int) {
        val dotColorInt  = ContextCompat.getColor(this, dotColor)
        val pillColorInt = ContextCompat.getColor(this, pillColor)
        val textColorInt = ContextCompat.getColor(this, textColor)

        binding.tvStatusBadge.text = text
        binding.tvStatusBadge.setTextColor(textColorInt)
        binding.viewStatusDot.background.setTint(dotColorInt)

        // Pill background: same hue as status color but very low opacity (~14%)
        val pillAlpha = (pillColorInt and 0x00FFFFFF) or (36 shl 24)
        binding.statusBadge.background.setTint(pillAlpha)
    }

    // No longer needed, using accessibility settings directly instead

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun intensityLabel(progress: Int) = when (progress) {
        Prefs.INTENSITY_NONE   -> getString(R.string.intensity_none)
        Prefs.INTENSITY_LIGHT  -> getString(R.string.intensity_light)
        Prefs.INTENSITY_MEDIUM -> getString(R.string.intensity_medium)
        Prefs.INTENSITY_STRONG -> getString(R.string.intensity_strong)
        else                   -> getString(R.string.intensity_medium)
    }

    private fun sizeLabel(progress: Int) = when (progress) {
        0 -> getString(R.string.size_small)
        2 -> getString(R.string.size_large)
        3 -> getString(R.string.size_extra_large)
        else -> getString(R.string.size_medium)
    }
}
