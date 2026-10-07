package com.yeyofone.core.model

/**
 * An account's call-forwarding state as last confirmed by the PBX-side relay (see
 * `yeyofone-push-relay`'s `GET`/`PUT /v1/accounts/<account>/forwarding`). Cached locally (Room)
 * so the UI has something to show before a refresh completes, but the relay is the source of
 * truth: [destination] is only meaningful while [enabled] is true.
 */
data class ForwardingState(
    val enabled: Boolean = false,
    val destination: String? = null,
)
