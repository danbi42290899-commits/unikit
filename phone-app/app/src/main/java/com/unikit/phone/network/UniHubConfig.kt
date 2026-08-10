package com.unikit.phone.network

import com.unikit.phone.BuildConfig

/**
 * Same pattern as glass-app's UniHubConfig: one place that knows UNI-HUB's
 * address, overridable per-launch without a rebuild:
 *   adb shell am start -n com.unikit.phone/.ui.MainActivity \
 *       --es uni_hub_host 192.168.0.42 --ei uni_hub_port 8000
 */
object UniHubConfig {
    var host: String = BuildConfig.UNI_HUB_HOST
    var port: Int = BuildConfig.UNI_HUB_PORT

    val cameraUploadUrl: String
        get() = "ws://$host:$port/ws/camera?role=PHONE"
}
