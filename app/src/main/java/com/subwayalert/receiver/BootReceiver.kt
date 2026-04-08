package com.subwayalert.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.subwayalert.service.LocationMonitoringService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Will be handled by MainActivity to check if there are stations to monitor
        }
    }
}
