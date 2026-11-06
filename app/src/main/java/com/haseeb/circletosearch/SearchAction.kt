package com.haseeb.circletosearch

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import org.lsposed.hiddenapibypass.HiddenApiBypass

internal object SearchAction {
    private const val TAG = "SearchAction"
    private const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
    private const val LENS_APP = "com.google.ar.lens"

    fun launch(context: Context) {
        val prefs = context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        if (prefs.getBoolean(Prefs.USE_GOOGLE_LENS, false)) {
            launchGoogleLens(context)
        } else {
            val success = triggerCircleToSearch()
            Log.i(TAG, "Circle to Search trigger result: $success")
        }
    }

    private fun launchGoogleLens(context: Context) {
        val deepLink = Intent(Intent.ACTION_VIEW, Uri.parse("googleapp://lens"))
            .setPackage(GOOGLE_APP)
        if (tryLaunch(context, deepLink)) return

        val lensApp = context.packageManager.getLaunchIntentForPackage(LENS_APP)
        if (lensApp != null && tryLaunch(context, lensApp)) return

        Toast.makeText(context, R.string.lens_unavailable, Toast.LENGTH_LONG).show()
    }

    private fun tryLaunch(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            Log.d(TAG, "Lens activity unavailable", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Unable to open Lens activity", e)
            false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Circle to Search trigger (IVoiceInteractionManagerService reflection)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calls the internal Android API `IVoiceInteractionManagerService.showSessionFromSession`
     * via HiddenApiBypass. This is the same path used by the Samsung Galaxy launcher to
     * trigger Circle to Search, and does not require root on most devices.
     *
     * Bundle keys:
     *   invocation_time_ms — elapsed realtime at moment of trigger
     *   omni.entry_point   — 1 = home / gesture source (replicates what the launcher sends)
     */
    @SuppressLint("PrivateApi")
    private fun triggerCircleToSearch(): Boolean {
        return try {
            val bundle = Bundle().apply {
                putLong("invocation_time_ms", SystemClock.elapsedRealtime())
                putInt("omni.entry_point", 1)
            }

            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val binder = serviceManagerClass
                .getMethod("getService", String::class.java)
                .invoke(null, "voiceinteraction") as? IBinder
                ?: run {
                    Log.e(TAG, "voiceinteraction service binder is null")
                    return false
                }

            val iVimsClass = Class.forName(
                "com.android.internal.app.IVoiceInteractionManagerService"
            )
            val stub = Class.forName(
                "com.android.internal.app.IVoiceInteractionManagerService\$Stub"
            )
            val vims = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)

            val result = if (Build.VERSION.SDK_INT >= 34) {
                HiddenApiBypass.invoke(
                    iVimsClass, vims,
                    "showSessionFromSession",
                    null, bundle, 7, "circle_to_search"
                )
            } else {
                HiddenApiBypass.invoke(
                    iVimsClass, vims,
                    "showSessionFromSession",
                    null, bundle, 7
                )
            }

            result as? Boolean ?: false
        } catch (e: Exception) {
            Log.e(TAG, "triggerCircleToSearch failed", e)
            false
        }
    }
}
