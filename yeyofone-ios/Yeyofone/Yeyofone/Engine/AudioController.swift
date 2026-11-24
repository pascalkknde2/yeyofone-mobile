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
import os

enum AudioController {
    private static let log = Logger(subsystem: "com.yeyofone.app", category: "audio-route")

    enum RouteError: LocalizedError {
        case unavailable
        var errorDescription: String? { "The selected audio device is no longer available." }
    }

    /// Read the system's actual route, rather than assuming a requested change succeeded.
    static func currentRoute() -> AudioRoute {
        let outputs = AVAudioSession.sharedInstance().currentRoute.outputs
        if outputs.contains(where: { [.bluetoothHFP, .bluetoothA2DP, .bluetoothLE].contains($0.portType) }) {
            return .bluetooth
        }
        return outputs.contains(where: { $0.portType == .builtInSpeaker }) ? .speaker : .earpiece
    }

    static func requestMicrophone() async -> Bool {
        switch AVAudioApplication.shared.recordPermission {
        case .granted: return true
        case .denied: return false
        default: return await AVAudioApplication.requestRecordPermission()
        }
    }

    /// Voice-call category and mode, with Bluetooth headsets allowed. Doesn't activate the session.
    static func configureSession() throws {
        try AVAudioSession.sharedInstance().setCategory(.playAndRecord, mode: .voiceChat, options: [.allowBluetoothHFP])
    }

    /// For calls CallKit doesn't manage.
    static func activateSession() throws {
        try configureSession()
        try AVAudioSession.sharedInstance().setActive(true)
    }

    /// Do not pin the next call to a microphone or speaker chosen for the previous call.
    static func resetRoute() {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.overrideOutputAudioPort(.none)
            try session.setPreferredInput(nil)
        } catch {
            log.error("Audio route reset failed: \(error.localizedDescription)")
        }
    }

    static func deactivateSession() {
        do {
            try AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        } catch {
            log.error("Audio session deactivation failed: \(error.localizedDescription)")
        }
    }

    /// Routes available right now: the earpiece and speaker always, Bluetooth when a headset is connected.
    static func availableRoutes() -> [AudioRoute] {
        var routes: [AudioRoute] = [.earpiece, .speaker]
        if bluetoothInput() != nil { routes.append(.bluetooth) }
        return routes
    }

    static func select(_ route: AudioRoute) throws {
        let session = AVAudioSession.sharedInstance()
        switch route {
        case .speaker:
            try session.setPreferredInput(nil)
            try session.overrideOutputAudioPort(.speaker)
        case .earpiece:
            try session.overrideOutputAudioPort(.none)
            guard let builtIn = session.availableInputs?.first(where: { $0.portType == .builtInMic }) else {
                throw RouteError.unavailable
            }
            try session.setPreferredInput(builtIn)
        case .bluetooth:
            guard let input = bluetoothInput() else { throw RouteError.unavailable }
            try session.overrideOutputAudioPort(.none)
            try session.setPreferredInput(input)
        }
    }

    private static func bluetoothInput() -> AVAudioSessionPortDescription? {
        AVAudioSession.sharedInstance().availableInputs?.first { $0.portType == .bluetoothHFP }
    }
}
