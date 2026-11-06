package com.haseeb.circletosearch

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/** Supplies a launch shortcut to gesture apps such as Tap, Tap. */
class SearchShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent.action) {
            Intent.ACTION_CREATE_SHORTCUT -> {
                val shortcut = Intent(this, SearchShortcutActivity::class.java).setAction(ACTION_SEARCH)
                val info = ShortcutInfoCompat.Builder(this, "search")
                    .setShortLabel(getString(R.string.search_shortcut))
                    .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
                    .setIntent(shortcut)
                    .build()
                setResult(RESULT_OK, ShortcutManagerCompat.createShortcutResultIntent(this, info))
            }
            ACTION_SEARCH -> {
                // Finish before asking the assistant to capture the foreground app.
                finish()
                SearchAction.launch(this)
                return
            }
        }
        finish()
    }

    private companion object {
        const val ACTION_SEARCH = "com.haseeb.circletosearch.action.SEARCH"
    }
}
