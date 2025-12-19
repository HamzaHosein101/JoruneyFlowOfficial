package com.example.travelpractice.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent

object UrlOpener {

    private const val TAG = "UrlOpener"


    fun openUrl(context: Context, url: String) {
        Log.d(TAG, "Opening URL: $url")

        try {

            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setUrlBarHidingEnabled(false)
                .build()

            customTabsIntent.launchUrl(context, Uri.parse(url))
            Log.d(TAG, "Opened URL in Custom Tab")
        } catch (e: Exception) {
            Log.e(TAG, "Custom Tab failed, trying fallback", e)

            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                intent.data = Uri.parse(url)
                context.startActivity(intent)
                Log.d(TAG, "Opened URL in default browser")
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to open URL", e2)
            }
        }
    }
}