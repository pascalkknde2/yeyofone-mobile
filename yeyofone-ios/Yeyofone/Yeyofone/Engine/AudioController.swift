//
//  AudioController.swift
//  Yeyofone
//
//  Microphone permission, the call audio session, and output routing. PJSIP's iOS CoreAudio
//  backend leaves the session's category to the app (SETUP_AV_AUDIO_SESSION is 0), so calls
//  set it here. CallKit activates the session for incoming calls; outgoing calls activate it
//  themselves.
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

    /// Voice-call category and mode, with Bluetooth headsets allowed. Doesn't activate the session.
    static func configureSession() {
        try? AVAudioSession.sharedInstance().setCategory(.playAndRecord, mode: .voiceChat, options: [.allowBluetoothHFP])
    }

    /// For calls CallKit doesn't manage.
    static func activateSession() {
        configureSession()
        try? AVAudioSession.sharedInstance().setActive(true)
    }

    static func deactivateSession() {
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
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
