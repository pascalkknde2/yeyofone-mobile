//
//  AudioController.swift
//  Yeyofone
//
//  Microphone permission and call audio routing. PJSIP's CoreAudio backend owns the
//  audio session's category and activation; this only chooses the output route.
//

import AVFoundation

enum AudioController {
    static func requestMicrophone() async -> Bool {
        switch AVAudioApplication.shared.recordPermission {
        case .granted: return true
        case .denied: return false
        default: return await AVAudioApplication.requestRecordPermission()
        }
    }

    /// Routes available right now: the earpiece and speaker always, Bluetooth when a headset is connected.
    static func availableRoutes() -> [AudioRoute] {
        var routes: [AudioRoute] = [.earpiece, .speaker]
        if bluetoothInput() != nil { routes.append(.bluetooth) }
        return routes
    }

    static func select(_ route: AudioRoute) {
        let session = AVAudioSession.sharedInstance()
        switch route {
        case .speaker:
            try? session.overrideOutputAudioPort(.speaker)
        case .earpiece:
            try? session.overrideOutputAudioPort(.none)
            let builtIn = session.availableInputs?.first { $0.portType == .builtInMic }
            try? session.setPreferredInput(builtIn)
        case .bluetooth:
            try? session.overrideOutputAudioPort(.none)
            try? session.setPreferredInput(bluetoothInput())
        }
    }

    private static func bluetoothInput() -> AVAudioSessionPortDescription? {
        AVAudioSession.sharedInstance().availableInputs?.first { $0.portType == .bluetoothHFP }
    }
}
