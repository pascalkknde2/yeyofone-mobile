//
//  Models.swift
//  Yeyofone
//
//  UI-facing models mirroring yeyofone-android's core domain types.
//

import Foundation

enum TransportProtocol: String, CaseIterable, Identifiable {
    case udp = "UDP", tcp = "TCP", tls = "TLS"
    var id: String { rawValue }
}

struct SipAccount: Identifiable, Equatable {
    let id: UUID
    var displayName: String
    var username: String
    var authenticationUsername: String
    var domain: String
    var registrarUri: String
    var outboundProxyUri: String
    var port: Int
    var transport: TransportProtocol
    var stunServer: String
    var turnServer: String
    var turnUsername: String
    var iceEnabled: Bool
    var srtpEnabled: Bool
    var registrationExpirySeconds: Int
    var voicemailNumber: String
    var callerId: String
    var enabled: Bool

    var sipIdentity: String { "sip:\(username)@\(domain)" }
}

struct AccountPreferences: Equatable {
    var autoAnswer = false
    var callWaiting = true
    var voicemail = true
    var doNotDisturb = false
    var allowIncoming = true
    var vibrate = true
    var flipToMute = false
    var announceCaller = false
}

enum RegistrationState: Equatable {
    case notRegistered, registering, registered, failed, unregistering
}

struct Contact: Identifiable, Equatable {
    let id: UUID
    var displayName: String
    var number: String
    var favorite: Bool
}

enum CallType {
    case incoming, outgoing, missed

    var label: String {
        switch self {
        case .incoming: String(localized: "Incoming")
        case .outgoing: String(localized: "Outgoing")
        case .missed: String(localized: "Missed")
        }
    }
}

struct CallLog: Identifiable, Equatable {
    let id: UUID
    var contactName: String
    var number: String
    var callType: CallType
    var timestamp: Date
    var duration: TimeInterval?
    var hasVoicemail = false
}

enum CallState: Equatable {
    case calling, ringing, connecting, connected, held, ended

    var label: String {
        switch self {
        case .calling: String(localized: "Calling…")
        case .ringing: String(localized: "Ringing…")
        case .connecting: String(localized: "Connecting…")
        case .connected: String(localized: "Connected")
        case .held: String(localized: "On hold")
        case .ended: String(localized: "Call ended")
        }
    }
}

enum CallDirection { case incoming, outgoing }

struct CallSession: Identifiable, Equatable {
    let id = UUID()
    var remoteName: String
    var remoteNumber: String
    var direction: CallDirection
    var state: CallState
    var connectedAt: Date?
    var muted = false
    var held = false
}

struct CallSummary: Equatable {
    var callerName: String
    var callerNumber: String
    var direction: CallDirection
    var wasAnswered: Bool
    var duration: TimeInterval
    var endedAt: Date

    var missed: Bool { direction == .incoming && !wasAnswered }
}

enum AudioRoute: String, CaseIterable, Identifiable {
    case earpiece = "Earpiece", speaker = "Speaker", bluetooth = "Bluetooth"
    var id: String { rawValue }

    var label: String {
        switch self {
        case .earpiece: String(localized: "Earpiece")
        case .speaker: String(localized: "Speaker")
        case .bluetooth: String(localized: "Bluetooth")
        }
    }
}

enum MessageStatus { case sent, delivered, read }

struct ChatMessage: Identifiable, Equatable {
    let id = UUID()
    var content: String
    var timestamp: String
    var isOutgoing: Bool
    var isVoice = false
    var status: MessageStatus = .sent
    var waveformHeights: [CGFloat] = []
}

enum DurationFormat {
    /// "mm:ss", used by the in-call timer and the call-ended summary.
    static func clock(_ seconds: TimeInterval) -> String {
        let total = max(0, Int(seconds))
        return String(format: "%02d:%02d", total / 60, total % 60)
    }

    /// "4m 05s" or "42s", used by call history rows.
    static func short(_ seconds: TimeInterval) -> String {
        let total = Int(seconds)
        return total >= 60
            ? String(format: String(localized: "%dm %02ds"), total / 60, total % 60)
            : String(format: String(localized: "%ds"), total)
    }
}
