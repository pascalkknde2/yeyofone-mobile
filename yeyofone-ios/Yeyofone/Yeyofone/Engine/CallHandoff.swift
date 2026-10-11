//
//  CallHandoff.swift
//  Yeyofone
//
//  Call handoff between devices of the same account. Devices exchange small JSON messages
//  through SIP MESSAGEs to their own address, which the PBX delivers to every registered device:
//  a device on a call announces it ("call", repeated while it lasts, then "ended"), and a device
//  that registers asks for current calls ("query"). Another device takes an announced call over
//  with an INVITE carrying Replaces; the PBX then ends this device's leg with "Call completed
//  elsewhere". The desktop app's receiving side is yeyofone-desktop/crates/voip/src/handoff.rs.
//

import Foundation
import UIKit

enum CallHandoff {
    struct Message: Codable {
        var v = 1
        var type: String
        var device: String
        var name: String?
        var callId: String?
        var localTag: String?
        var remoteTag: String?
        var remote: String?
        var remoteName: String?
        /// Unix milliseconds.
        var connectedAt: Int64?
    }

    /// How often a device on a call repeats its announcement; receivers drop offers after 45 s.
    static let repeatInterval: Duration = .seconds(15)

    static func announcement(device: String, dialog: EngineDialog, session: CallSession) -> Data? {
        let remote = dialog.remoteUser.isEmpty ? session.remoteNumber : dialog.remoteUser
        let message = Message(
            type: "call",
            device: device,
            name: UIDevice.current.model,
            callId: dialog.callId,
            localTag: dialog.localTag,
            remoteTag: dialog.remoteTag,
            remote: remote,
            remoteName: session.remoteName == remote ? nil : session.remoteName,
            connectedAt: session.connectedAt.map { Int64($0.timeIntervalSince1970 * 1000) }
        )
        return try? JSONEncoder().encode(message)
    }

    static func ended(device: String, callId: String) -> Data? {
        try? JSONEncoder().encode(Message(type: "ended", device: device, callId: callId))
    }

    static func decode(_ data: Data) -> Message? {
        guard let message = try? JSONDecoder().decode(Message.self, from: data), message.v == 1 else { return nil }
        return message
    }
}
