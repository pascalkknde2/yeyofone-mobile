//
//  CallKitController.swift
//  Yeyofone
//
//  Presents incoming SIP calls through CallKit: the system ringing screen (also on the lock
//  screen), the system's answer/decline/mute/hold controls, and CallKit-owned audio session
//  activation. Without PushKit, this covers calls that arrive while the app is running.
//

import AVFoundation
@preconcurrency import CallKit

@MainActor
final class CallKitController: NSObject {
    enum ReportOutcome {
        /// CallKit shows the call and now owns its actions.
        case reported
        /// The system filtered the call (Do Not Disturb or a blocked number); decline it.
        case declined
        /// CallKit couldn't take the call (for example in the Simulator); use the in-app screen.
        case unavailable
    }

    /// Answer, end, mute, hold and DTMF requests coming from the system UI or from our own transactions.
    struct Handlers {
        var answer: (UUID, @escaping (Bool) -> Void) -> Void
        var end: (UUID) -> Void
        var mute: (UUID, Bool) -> Void
        var hold: (UUID, Bool, @escaping (Bool) -> Void) -> Void
        var dtmf: (UUID, String) -> Void
        var audioSession: (Bool) -> Void
        var reset: () -> Void
    }

    private let provider: CXProvider
    private let callController = CXCallController()
    private let handlers: Handlers
    private var managed: Set<UUID> = []

    init(handlers: Handlers) {
        self.handlers = handlers
        let configuration = CXProviderConfiguration()
        configuration.supportsVideo = false
        configuration.maximumCallGroups = 1
        configuration.maximumCallsPerCallGroup = 1
        configuration.supportedHandleTypes = [.generic, .phoneNumber]
        // Tapping a call in the Phone app's Recents would need INStartCallIntent handling, which isn't built yet.
        configuration.includesCallsInRecents = false
        provider = CXProvider(configuration: configuration)
        super.init()
        provider.setDelegate(self, queue: nil)
    }

    func isManaged(_ id: UUID) -> Bool { managed.contains(id) }

    func reportIncoming(id: UUID, number: String, name: String, completion: @escaping (ReportOutcome) -> Void) {
        let update = CXCallUpdate()
        update.remoteHandle = CXHandle(type: .generic, value: number)
        update.localizedCallerName = name
        update.hasVideo = false
        update.supportsHolding = true
        update.supportsDTMF = true
        update.supportsGrouping = false
        update.supportsUngrouping = false
        AudioController.configureSession()
        provider.reportNewIncomingCall(with: id, update: update) { error in
            Task { @MainActor in
                #if DEBUG
                EngineLog.note("CallKit report \(id): \(error.map { "\(($0 as NSError).domain) \(($0 as NSError).code) \($0.localizedDescription)" } ?? "accepted")")
                #endif
                guard let error else {
                    self.managed.insert(id)
                    completion(.reported)
                    return
                }
                let code = (error as? CXErrorCodeIncomingCallError)?.code
                completion(code == .filteredByDoNotDisturb || code == .filteredByBlockList ? .declined : .unavailable)
            }
        }
    }

    /// Tells CallKit a call ended for a reason other than a CallKit action (remote hang-up, failure).
    func reportEnded(id: UUID, answered: Bool) {
        guard managed.remove(id) != nil else { return }
        provider.reportCall(with: id, endedAt: Date(), reason: answered ? .remoteEnded : .unanswered)
    }

    func requestAnswer(_ id: UUID) { request(CXAnswerCallAction(call: id)) }
    func requestEnd(_ id: UUID) { request(CXEndCallAction(call: id)) }
    func requestMute(_ id: UUID, _ muted: Bool) { request(CXSetMutedCallAction(call: id, muted: muted)) }
    func requestHold(_ id: UUID, _ held: Bool) { request(CXSetHeldCallAction(call: id, onHold: held)) }

    private func request(_ action: CXCallAction) {
        #if DEBUG
        EngineLog.note("CallKit request \(type(of: action)) \(action.callUUID)")
        #endif
        callController.request(CXTransaction(action: action)) { error in
            #if DEBUG
            if let error { EngineLog.note("CallKit request failed: \((error as NSError).domain) \((error as NSError).code)") }
            #endif
            guard error != nil else { return }
            // CallKit refused (for example the call already ended there); apply the request directly.
            Task { @MainActor in self.performDirectly(action) }
        }
    }

    private func performDirectly(_ action: CXCallAction) {
        switch action {
        case let answer as CXAnswerCallAction: handlers.answer(answer.callUUID) { _ in }
        case let end as CXEndCallAction:
            managed.remove(end.callUUID)
            handlers.end(end.callUUID)
        case let mute as CXSetMutedCallAction: handlers.mute(mute.callUUID, mute.isMuted)
        case let hold as CXSetHeldCallAction: handlers.hold(hold.callUUID, hold.isOnHold) { _ in }
        default: break
        }
    }
}

extension CallKitController: CXProviderDelegate {
    nonisolated func providerDidReset(_ provider: CXProvider) {
        MainActor.assumeIsolated {
            #if DEBUG
            EngineLog.note("CallKit reset")
            #endif
            managed.removeAll()
            handlers.reset()
        }
    }

    nonisolated func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
        MainActor.assumeIsolated {
            #if DEBUG
            EngineLog.note("CallKit answer action")
            #endif
            AudioController.configureSession()
            handlers.answer(action.callUUID) { answered in
                if answered { action.fulfill() } else { action.fail() }
            }
        }
    }

    nonisolated func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
        MainActor.assumeIsolated {
            #if DEBUG
            EngineLog.note("CallKit end action")
            #endif
            managed.remove(action.callUUID)
            handlers.end(action.callUUID)
            action.fulfill()
        }
    }

    nonisolated func provider(_ provider: CXProvider, perform action: CXSetMutedCallAction) {
        MainActor.assumeIsolated {
            handlers.mute(action.callUUID, action.isMuted)
            action.fulfill()
        }
    }

    nonisolated func provider(_ provider: CXProvider, perform action: CXSetHeldCallAction) {
        MainActor.assumeIsolated {
            handlers.hold(action.callUUID, action.isOnHold) { done in
                if done { action.fulfill() } else { action.fail() }
            }
        }
    }

    nonisolated func provider(_ provider: CXProvider, perform action: CXPlayDTMFCallAction) {
        MainActor.assumeIsolated {
            handlers.dtmf(action.callUUID, action.digits)
            action.fulfill()
        }
    }

    nonisolated func provider(_ provider: CXProvider, didActivate audioSession: AVAudioSession) {
        MainActor.assumeIsolated {
            #if DEBUG
            EngineLog.note("CallKit audio activated")
            #endif
            handlers.audioSession(true)
        }
    }

    nonisolated func provider(_ provider: CXProvider, didDeactivate audioSession: AVAudioSession) {
        MainActor.assumeIsolated {
            #if DEBUG
            EngineLog.note("CallKit audio deactivated")
            #endif
            handlers.audioSession(false)
        }
    }
}
