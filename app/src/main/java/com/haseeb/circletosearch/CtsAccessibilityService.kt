package com.haseeb.circletosearch

import android.annotation.SuppressLint
import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import kotlin.math.abs

class CtsAccessibilityService : AccessibilityService() {

    // ─────────────────────────────────────────────────────────────────────────
    // Companion
    // ─────────────────────────────────────────────────────────────────────────

    companion object {
        const val TAG = "CtsAccessibilityService"

        // Overlay geometry (dp) base sizes - updated via gestureAreaSize
        // Small:  100 x 20
        // Medium: 140 x 24
        // Large:  180 x 30

        /** Exposed so MainActivity can read live state without binding. */
        @Volatile var isRunning = false
            private set
    }

    // ─────────────────────────────────────────────────────────────────────────
    // State
    // ─────────────────────────────────────────────────────────────────────────

    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    // Settings (read from prefs)
    private var gestureOverlayEnabled = true
    private var gestureAreaSize       = 1 // 0=Small 1=Medium 2=Large
    private var thresholdMs           = Prefs.DEFAULT_THRESHOLD_MS.toLong()
    private var launchVibrateEnabled  = true
    private var triggerVibrateEnabled = true
    private var vibrationIntensity    = Prefs.INTENSITY_MEDIUM

    // Gesture nav overlay
    private var navOverlayView: View? = null
    private var navIsLongPressing = false
    private var navInitX = 0f
    private var navInitY = 0f
    private val navLongPress = Runnable {
        if (navIsLongPressing) {
            navIsLongPressing = false
            performTrigger()
        }
    }

    // Slop thresholds
    private val navSlopPx    by lazy { 14 * resources.displayMetrics.density }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        loadSettings()
        if (key == Prefs.GESTURE_OVERLAY_ENABLED || key == Prefs.GESTURE_AREA_SIZE) {
            refreshNavOverlay()
        }
    }

    private val powerSaveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (isRunning && getSystemService(PowerManager::class.java)?.isPowerSaveMode == false) {
                refreshNavOverlay()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this,
            powerSaveReceiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        loadSettings()
        getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(prefsListener)
            
        refreshNavOverlay()

        if (launchVibrateEnabled) {
            performLaunchVibration()
        }

        // Register the navigation bar accessibility button click listener
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                accessibilityButtonController.registerAccessibilityButtonCallback(
                    object : android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback() {
                        override fun onClicked(controller: android.accessibilityservice.AccessibilityButtonController) {
                            performTrigger()
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register accessibility button callback", e)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        isRunning = false
        unregisterReceiver(powerSaveReceiver)
        getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefsListener)
        handler.removeCallbacksAndMessages(null)
        removeNavOverlay()
        super.onDestroy()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Settings
    // ─────────────────────────────────────────────────────────────────────────

    private fun loadSettings() {
        val sp = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        gestureOverlayEnabled = sp.getBoolean(Prefs.GESTURE_OVERLAY_ENABLED, true)
        gestureAreaSize       = sp.getInt(Prefs.GESTURE_AREA_SIZE, 1)
        thresholdMs           = sp.getInt(Prefs.THRESHOLD_MS, Prefs.DEFAULT_THRESHOLD_MS).toLong()
        launchVibrateEnabled  = sp.getBoolean(Prefs.LAUNCH_VIBRATE, true)
        triggerVibrateEnabled = sp.getBoolean(Prefs.TRIGGER_VIBRATE, true)
        vibrationIntensity    = sp.getInt(Prefs.VIBRATION_INTENSITY, Prefs.DEFAULT_INTENSITY)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Overlay setup
    // ─────────────────────────────────────────────────────────────────────────

    private fun refreshNavOverlay() {
        removeNavOverlay()
        if (gestureOverlayEnabled) addNavOverlay()
    }

    // ── Gesture nav overlay ──────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun addNavOverlay() {
        val d = resources.displayMetrics.density
        
        val (widthDp, heightDp) = when (gestureAreaSize) {
            0 -> 100 to 20
            2 -> 180 to 30
            3 -> 220 to 40
            else -> 140 to 24
        }
        
        val wPx = (widthDp * d).toInt()
        val hPx = (heightDp * d).toInt()

        val overlay = View(this)

        val params = WindowManager.LayoutParams(
            wPx, hPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 0
        }

        overlay.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    navIsLongPressing = true
                    navInitX = event.rawX
                    navInitY = event.rawY
                    handler.postDelayed(navLongPress, thresholdMs)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (navIsLongPressing) {
                        if (abs(event.rawX - navInitX) > navSlopPx ||
                            abs(event.rawY - navInitY) > navSlopPx
                        ) {
                            // This is a swipe — cancel and pass through to system
                            navIsLongPressing = false
                            handler.removeCallbacks(navLongPress)
                            return@setOnTouchListener false
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    navIsLongPressing = false
                    handler.removeCallbacks(navLongPress)
                    false
                }
                else -> false
            }
        }

        try {
            windowManager.addView(overlay, params)
            navOverlayView = overlay
            Log.d(TAG, "Nav overlay added")
        } catch (e: WindowManager.BadTokenException) {
            Log.w(TAG, "Android rejected the navigation overlay", e)
        }
    }

    private fun removeNavOverlay() {
        navIsLongPressing = false
        handler.removeCallbacks(navLongPress)
        navOverlayView?.let {
            runCatching { windowManager.removeView(it) }
            navOverlayView = null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Trigger
    // ─────────────────────────────────────────────────────────────────────────

    private fun performTrigger() {
        if (triggerVibrateEnabled) vibrate(vibrationIntensity)
        SearchAction.launch(this)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Vibration
    // ─────────────────────────────────────────────────────────────────────────

    /** Called once when service starts — gives a double-pulse "activated" feeling. */
    private fun performLaunchVibration() {
        try {
            val v = getVibrator()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Two short taps to signal "on"
                val pattern = VibrationEffect.createWaveform(
                    longArrayOf(0, 40, 80, 40), -1
                )
                v.vibrate(pattern)
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(longArrayOf(0, 40, 80, 40), -1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Launch vibration failed: ${e.message}")
        }
    }

    /**
     * Trigger vibration at [intensity]:
     *   0 = None
     *   1 = Light  → EFFECT_TICK
     *   2 = Medium → EFFECT_CLICK
     *   3 = Strong → EFFECT_HEAVY_CLICK
     */
    private fun vibrate(intensity: Int) {
        if (intensity == Prefs.INTENSITY_NONE) return
        try {
            val v = getVibrator()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effect = when (intensity) {
                    Prefs.INTENSITY_LIGHT  -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    Prefs.INTENSITY_STRONG -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                    else                   -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                }
                v.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val durationMs = when (intensity) {
                    Prefs.INTENSITY_LIGHT  -> 20L
                    Prefs.INTENSITY_STRONG -> 80L
                    else                   -> 50L
                }
                v.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                val durationMs = when (intensity) {
                    Prefs.INTENSITY_LIGHT  -> 20L
                    Prefs.INTENSITY_STRONG -> 80L
                    else                   -> 50L
                }
                @Suppress("DEPRECATION")
                v.vibrate(durationMs)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed: ${e.message}")
        }
    }

    private fun getVibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
}
