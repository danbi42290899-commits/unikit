package com.unikit.glass.network

import com.unikit.glass.BuildConfig

/**
 * Single place that knows the Mock UNI-HUB's address. Everything else
 * (TelemetryClient, ControlClient) builds its URL from here -- nothing
 * else in the app hardcodes a host/port.
 *
 * Default host/port come from BuildConfig (app/build.gradle's
 * `buildConfigField` entries) so the common case -- pointing at a
 * different dev machine -- is a one-line edit with no code changes.
 *
 * For a same-session override without rebuilding, GlassHudActivity also
 * accepts launch Intent extras:
 *   adb shell am start -n com.unikit.glass/.ui.GlassHudActivity \
 *       --es uni_hub_host 192.168.0.42 --ei uni_hub_port 8000
 */
object UniHubConfig {
    var host: String = BuildConfig.UNI_HUB_HOST
    var port: Int = BuildConfig.UNI_HUB_PORT

    val telemetryUrl: String
        get() = "ws://$host:$port/ws/telemetry?client=GLASS"

    val controlUrl: String
        get() = "ws://$host:$port/ws/control"

    val cameraUrl: String
        get() = "ws://$host:$port/ws/camera?role=GLASS"

    val baseHttpUrl: String
        get() = "http://$host:$port"
}
