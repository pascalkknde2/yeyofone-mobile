//
//  RoomMedia.swift
//  Yeyofone
//
//  Local captures for the Team room, like the desktop app's roomMedia.ts. They are shown only on
//  this device; nothing is sent. Each capture stops as soon as it is turned off or the room closes,
//  so no camera or microphone is left running.
//

import AVFoundation
import SwiftUI

enum RoomMedia {
    /// Root-mean-square level (0–1) of audio samples in -1...1.
    nonisolated static func micLevel(_ samples: UnsafeBufferPointer<Float>) -> Float {
        guard !samples.isEmpty else { return 0 }
        var sum: Float = 0
        for sample in samples { sum += sample * sample }
        return (sum / Float(samples.count)).squareRoot()
    }

    /// Two thresholds so the speaking badge doesn't flicker around a single value: it turns on
    /// above 0.04 and only turns off again below 0.02.
    nonisolated static func nextTalking(level: Float, wasTalking: Bool) -> Bool {
        wasTalking ? level > 0.02 : level > 0.04
    }
}

enum RoomMediaError {
    static let cameraDenied: LocalizedStringResource = "Camera access was not allowed."
    static let cameraMissing: LocalizedStringResource = "No camera was found."
    static let cameraFailed: LocalizedStringResource = "The camera couldn’t start."
    static let micDenied: LocalizedStringResource = "Microphone access was not allowed."
    static let micFailed: LocalizedStringResource = "The microphone couldn’t start."
}

/// The front camera, shown mirrored in your own tile and on the stage.
final class RoomCamera {
    let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "com.yeyofone.room.camera")

    /// Starts the camera, or returns why it couldn't.
    func start() async -> LocalizedStringResource? {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: break
        case .notDetermined:
            guard await AVCaptureDevice.requestAccess(for: .video) else { return RoomMediaError.cameraDenied }
        default: return RoomMediaError.cameraDenied
        }
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .front)
            ?? AVCaptureDevice.default(for: .video) else { return RoomMediaError.cameraMissing }
        let session = session
        return await withCheckedContinuation { continuation in
            queue.async {
                session.beginConfiguration()
                session.inputs.forEach(session.removeInput)
                guard let input = try? AVCaptureDeviceInput(device: device), session.canAddInput(input) else {
                    session.commitConfiguration()
                    continuation.resume(returning: RoomMediaError.cameraFailed)
                    return
                }
                session.addInput(input)
                session.commitConfiguration()
                session.startRunning()
                continuation.resume(returning: session.isRunning ? nil : RoomMediaError.cameraFailed)
            }
        }
    }

    func stop() {
        let session = session
        queue.async {
            session.stopRunning()
            session.inputs.forEach(session.removeInput)
        }
    }
}

/// Lights your "speaking" badge from the microphone level.
final class RoomMicMeter {
    private var engine: AVAudioEngine?

    /// Starts listening, or returns why it couldn't. [onTalking] runs on the main actor.
    func start(onTalking: @escaping @MainActor (Bool) -> Void) async -> LocalizedStringResource? {
        guard await AVAudioApplication.requestRecordPermission() else { return RoomMediaError.micDenied }
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker, .allowBluetoothHFP])
            try session.setActive(true)
            let engine = AVAudioEngine()
            let input = engine.inputNode
            let format = input.outputFormat(forBus: 0)
            guard format.channelCount > 0 else { return RoomMediaError.micFailed }
            var talking = false
            input.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in
                guard let channel = buffer.floatChannelData?[0] else { return }
                let level = RoomMedia.micLevel(UnsafeBufferPointer(start: channel, count: Int(buffer.frameLength)))
                let next = RoomMedia.nextTalking(level: level, wasTalking: talking)
                guard next != talking else { return }
                talking = next
                Task { @MainActor in onTalking(next) }
            }
            try engine.start()
            self.engine = engine
            return nil
        } catch {
            stop()
            return RoomMediaError.micFailed
        }
    }

    func stop() {
        guard let engine else { return }
        engine.inputNode.removeTap(onBus: 0)
        engine.stop()
        self.engine = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
}

/// The live camera picture, mirrored like a mirror (the front camera's default preview).
struct RoomCameraView: UIViewRepresentable {
    let session: AVCaptureSession

    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.previewLayer.session = session
        view.previewLayer.videoGravity = .resizeAspectFill
        return view
    }

    func updateUIView(_ view: PreviewView, context: Context) {}

    final class PreviewView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }
}
