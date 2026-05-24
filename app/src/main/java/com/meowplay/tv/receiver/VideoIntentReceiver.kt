package com.meowplay.tv.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver for handling video intents from external apps.
 *
 * This receiver is declared in the manifest for:
 * - ACTION_VIEW with video MIME types
 * - ACTION_SEND with text/plain (URLs)
 * - Magnet links
 *
 * Most intent handling is done directly in PlayerActivity's intent-filters,
 * but this receiver can be used for additional processing or logging.
 */
class VideoIntentReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "VideoIntentReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        Log.d(TAG, "Received intent: ${intent.action}, data: ${intent.data}")

        // The PlayerActivity handles intents directly via its intent-filters.
        // This receiver is for any additional processing, such as:
        // - Logging which app sent the link
        // - Pre-processing magnet links
        // - Validating URLs before launching player
    }
}
