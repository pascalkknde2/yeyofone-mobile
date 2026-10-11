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

/// A call on another device of the same account that can be continued on this one.
struct HandoffOffer: Identifiable, Equatable {
    /// The offering device's SIP Call-ID.
    let id: String
    let accountToken: UInt64
    let remote: String
    let remoteName: String?
    let deviceName: String
    let connectedAt: Date?
    let localTag: String
    let remoteTag: String
    var seen: Date
}

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
    static let offerLifetime: TimeInterval = 45

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

    static func query(device: String) -> Data? {
        try? JSONEncoder().encode(Message(type: "query", device: device))
    }

    /// An offer from another device's "call" announcement, or nil when it isn't usable.
    static func offer(from message: Message, account: UInt64) -> HandoffOffer? {
        guard message.type == "call", message.device.count <= 64,
              let callId = message.callId, isToken(callId, max: 128, callId: true),
              let localTag = message.localTag, isToken(localTag, max: 64),
              let remoteTag = message.remoteTag, isToken(remoteTag, max: 64),
              let remote = message.remote, isParty(remote) else { return nil }
        return HandoffOffer(
            id: callId, accountToken: account, remote: remote,
            remoteName: label(message.remoteName, max: 64),
            deviceName: label(message.name, max: 32) ?? String(localized: "another device"),
            connectedAt: message.connectedAt.map { Date(timeIntervalSince1970: Double($0) / 1000) },
            localTag: localTag, remoteTag: remoteTag, seen: Date()
        )
    }

    /// SIP token characters that are safe in a Replaces header value.
    private static func isToken(_ value: String, max: Int, callId: Bool = false) -> Bool {
        !value.isEmpty && value.utf8.count <= max && value.unicodeScalars.allSatisfy {
            $0.isASCII && (CharacterSet.alphanumerics.contains($0) || "-.!%*_+`'~".unicodeScalars.contains($0)
                || (callId && "@:/".unicodeScalars.contains($0)))
        }
    }

    private static func isParty(_ value: String) -> Bool {
        !value.isEmpty && value.utf8.count <= 64 && value.unicodeScalars.allSatisfy {
            $0.isASCII && (CharacterSet.alphanumerics.contains($0) || "+*#._-".unicodeScalars.contains($0))
        }
    }

    private static func label(_ value: String?, max: Int) -> String? {
        var scalars = String.UnicodeScalarView()
        scalars.append(contentsOf: (value ?? "").unicodeScalars.filter { !CharacterSet.controlCharacters.contains($0) }.prefix(max))
        let text = String(scalars).trimmingCharacters(in: .whitespaces)
        return text.isEmpty ? nil : text
    }

    static func ended(device: String, callId: String) -> Data? {
        try? JSONEncoder().encode(Message(type: "ended", device: device, callId: callId))
    }

    static func decode(_ data: Data) -> Message? {
        guard let message = try? JSONDecoder().decode(Message.self, from: data), message.v == 1 else { return nil }
        return message
    }
}
