package com.yeyofone.core.model

enum class PreferenceToggle { AutoAnswer, CallWaiting, Voicemail, DoNotDisturb, AllowIncoming, Vibrate, FlipToMute, AnnounceCaller }

/**
 * Per-account incoming-call behavior, persisted and enforced (see [[com.yeyofone.core.calling]]
 * `CallCoordinator` for [allowIncoming]/[doNotDisturb]/[autoAnswer] enforcement). [flipToMute] and
 * [announceCaller] are persisted but not yet enforced - see `CALL-FEATURES-AUDIT.md` Phase B.
 *
 * [autoAnswer] defaults to false: this preference was previously cosmetic (toggle existed but did
 * nothing), so defaulting it to true the moment it becomes real would silently make every
 * incoming call answer itself with no ring for anyone who had left the old, inert toggle on.
 */
data class AccountPreferences(
    val autoAnswer: Boolean = false,
    val callWaiting: Boolean = true,
    val voicemail: Boolean = false,
    val doNotDisturb: Boolean = false,
    val allowIncoming: Boolean = true,
    val vibrate: Boolean = true,
    val flipToMute: Boolean = true,
    val announceCaller: Boolean = false,
)
