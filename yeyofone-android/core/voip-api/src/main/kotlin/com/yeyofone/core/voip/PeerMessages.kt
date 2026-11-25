package com.yeyofone.core.voip

import com.yeyofone.core.model.SipAccountId
import kotlinx.coroutines.flow.Flow

/** A short app-to-app message (JSON) from another extension, delivered through the PBX as a SIP MESSAGE. */
data class PeerMessage(val accountId: SipAccountId, val fromUser: String, val body: String)

/**
 * Messages between YeyoFone apps on different extensions, used to share who is on a conference
 * with everyone in it. Apps that don't understand a message ignore it.
 */
interface SipPeerMessageGateway {
    val peerMessages: Flow<PeerMessage>
    /** Sends [body] to the extension [toUser] on the account's domain. */
    suspend fun sendPeerMessage(accountId: SipAccountId, toUser: String, body: String)
}
