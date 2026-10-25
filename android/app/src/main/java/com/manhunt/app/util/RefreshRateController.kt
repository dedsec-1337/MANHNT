package com.manhunt.app.util

import android.view.Display
import android.view.Window

object RefreshRateController {
    private const val STANDARD_REFRESH_RATE = 60f

    fun highestSupportedRefreshRate(display: Display?): Float? =
        display?.supportedModes
            ?.asSequence()
            ?.map { it.refreshRate }
            ?.filter { it > STANDARD_REFRESH_RATE }
            ?.maxOrNull()

    fun hasHighRefreshRate(display: Display?): Boolean =
        highestSupportedRefreshRate(display) != null

    /** Requests a window preference only. Android and the active display may still choose another rate. */
    fun apply(window: Window, enabled: Boolean): Boolean {
        val display = window.decorView.display
        val highestRate = highestSupportedRefreshRate(display)
        val requestedRate = if (enabled) highestRate ?: 0f else 0f
        val attributes = window.attributes
        if (attributes.preferredRefreshRate != requestedRate) {
            attributes.preferredRefreshRate = requestedRate
            window.attributes = attributes
        }
        return highestRate != null
    }
}
