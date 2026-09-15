package com.yeyofone.core.model

import kotlin.time.Duration

data class MediaState(
    val muted: Boolean = false,
    val held: Boolean = false,
    val route: AudioRoute? = null,
    val negotiatedCodec: Codec? = null,
    val secure: Boolean = false,
)

sealed interface AudioRoute {
    data object Earpiece : AudioRoute
    data object Speaker : AudioRoute
    data class WiredHeadset(val name: String?) : AudioRoute
    data class Bluetooth(val id: String, val name: String) : AudioRoute
}

data class Codec(val id: String, val displayName: String, val clockRateHz: Int)

data class CallQualityMetrics(
    val roundTripTime: Duration? = null,
    val jitter: Duration? = null,
    val packetLossPercent: Double? = null,
    val packetsSent: Long? = null,
    val packetsReceived: Long? = null,
)
