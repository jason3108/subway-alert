package com.subwayalert

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.OSMDroidConfiguration

@HiltAndroidApp
class SubwayAlertApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize OSMDroid configuration
        OSMDroidConfiguration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        OSMDroidConfiguration.getInstance().userAgentValue = packageName
    }
}
