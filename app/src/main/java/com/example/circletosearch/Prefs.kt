package com.example.circletosearch

/** Shared constants for SharedPreferences keys and intent extras. */
object Prefs {
    const val FILE = "cts_settings"

    // Overlay settings
    const val GESTURE_OVERLAY_ENABLED = "gesture_overlay_enabled"
    const val THRESHOLD_MS = "threshold_ms"
    const val GESTURE_AREA_SIZE = "gesture_area_size" // 0=Small 1=Medium 2=Large

    // Haptics
    const val LAUNCH_VIBRATE = "launch_vibrate"
    const val TRIGGER_VIBRATE = "trigger_vibrate"
    const val VIBRATION_INTENSITY = "vibration_intensity" // 0=None 1=Light 2=Medium 3=Strong

    // Intensity constants
    const val INTENSITY_NONE   = 0
    const val INTENSITY_LIGHT  = 1
    const val INTENSITY_MEDIUM = 2
    const val INTENSITY_STRONG = 3

    // Threshold options (ms)
    val THRESHOLD_OPTIONS = intArrayOf(300, 500, 700)

    // Default values
    const val DEFAULT_THRESHOLD_MS = 500
    const val DEFAULT_INTENSITY = INTENSITY_MEDIUM
}
