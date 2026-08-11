package com.unikit.phone.network

import com.unikit.phone.BuildConfig

/**
 * Single place that knows the Mock UNI-HUB's address. Everything else
 * (TelemetryClient, ControlClient, UniHubApiClient, CameraUploadClient)
 * builds its URL from here -- nothing else in the app hardcodes a
 * host/port. Same pattern as glass-app's UniHubConfig.
 *
 * For a same-session override without rebuilding:
 *   adb shell am start -n com.unikit.phone/.ui.MainActivity \
 *       --es uni_hub_host 192.168.0.42 --ei uni_hub_port 8000
 */
object UniHubConfig {
    var host: String = BuildConfig.UNI_HUB_HOST
    var port: Int = BuildConfig.UNI_HUB_PORT

    val telemetryUrl: String
        get() = "ws://$host:$port/ws/telemetry"

    val controlUrl: String
        get() = "ws://$host:$port/ws/control"

    val cameraUploadUrl: String
        get() = "ws://$host:$port/ws/camera?role=PHONE"

    val baseHttpUrl: String
        get() = "http://$host:$port"
}
