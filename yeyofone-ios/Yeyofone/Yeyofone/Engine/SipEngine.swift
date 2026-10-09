//
//  SipEngine.swift
//  Yeyofone
//
//  Runs the shared YeyoFone C bridge (yeyofone-desktop/native/bridge.h) over PJSIP.
//  The bridge must be called from the thread that created it, and PJSIP runs without
//  worker threads, so one dedicated thread executes every command and pumps PJSIP's
//  event loop. The UI only ever sees immutable snapshots published from that thread.
//

import Foundation
import YeyofoneVoIP

nonisolated struct EngineRegistration: Equatable, Sendable {
    /// 0 not started, 1 request in progress, 2 final response received.
    var phase: Int32 = 0
    var renew = false
    var status: Int32 = 0
    var sipCode: Int32 = 0
    var expires: UInt32 = 0
    /// 0 none, 1 SIP, 2 DNS, 3 TLS, 4 offline, 5 transport.
    var failureKind: Int32 = 0
}

nonisolated struct EngineCall: Equatable, Sendable {
    var token: UInt64
    var accountToken: UInt64
    /// A pjsip_inv_state value; see InviteState.
    var state: Int32
    var sipCode: Int32
    var incoming: Bool
    var caller: String
    var connectedMilliseconds: UInt64
    var muted: Bool
    var held: Bool
    var transferPending: Bool
    var transferCode: Int32
}

nonisolated struct EngineSnapshot: Equatable, Sendable {
    var registrations: [UInt64: EngineRegistration] = [:]
    var calls: [EngineCall] = []
}

/// pjsip_inv_state values reported in YvCall.state.
nonisolated enum InviteState {
    static let calling: Int32 = 1
    static let incoming: Int32 = 2
    static let early: Int32 = 3
    static let connecting: Int32 = 4
    static let confirmed: Int32 = 5
    static let disconnected: Int32 = 6
}

nonisolated enum EngineTransport: Int32, Sendable {
    case udp = 0, tcp = 1, tls = 2
}

nonisolated final class SipEngine: Thread, @unchecked Sendable {
    typealias Completion = @MainActor @Sendable (Int32) -> Void

    private let lock = NSLock()
    private var jobs: [(OpaquePointer) -> Void] = []
    private let onSnapshot: @Sendable (EngineSnapshot) -> Void
    private let onStartFailure: @Sendable (Int32) -> Void

    // Owned by the engine thread only.
    private var accountTokens: Set<UInt64> = []
    private var published = EngineSnapshot()
    private var callBuffer = [YvCall](repeating: YvCall(), count: 16)

    init(onSnapshot: @escaping @Sendable (EngineSnapshot) -> Void, onStartFailure: @escaping @Sendable (Int32) -> Void) {
        self.onSnapshot = onSnapshot
        self.onStartFailure = onStartFailure
        super.init()
        name = "YeyoFone SIP"
        qualityOfService = .userInteractive
    }

    override func main() {
        var created: OpaquePointer?
        guard yv_create(&created) == 0, let handle = created else {
            onStartFailure(-1)
            return
        }
        let started = yv_start(handle, 0)
        guard started == 0 else {
            _ = yv_destroy(handle)
            onStartFailure(started)
            return
        }
        var lastPoll = Date.distantPast
        var event = YvEvent()
        while !isCancelled {
            let pending = lock.withLock {
                let current = jobs
                jobs.removeAll()
                return current
            }
            pending.forEach { $0(handle) }
            _ = yv_pump(handle)
            while yv_next_event(handle, &event) == 1 {}
            if Date().timeIntervalSince(lastPoll) >= 0.1 || !pending.isEmpty {
                publish(handle)
                lastPoll = Date()
            }
            Thread.sleep(forTimeInterval: 0.01)
        }
        _ = yv_stop(handle)
        _ = yv_destroy(handle)
    }

    // MARK: Commands (any thread)

    func addAccount(token: UInt64, username: String, host: String, password: String, port: Int,
                    transport: EngineTransport, completion: Completion? = nil) {
        run(completion) { [weak self] handle in
            var user = Array(username.utf8)
            var server = Array(host.utf8)
            var secret = Array(password.utf8)
            defer {
                // Don't leave the password in this buffer after PJSUA has copied it.
                for index in secret.indices { secret[index] = 0 }
            }
            let code = user.withUnsafeBufferPointer { userBytes in
                server.withUnsafeBufferPointer { serverBytes in
                    secret.withUnsafeBufferPointer { secretBytes in
                        var config = YvAccountConfig(
                            token: token,
                            username: userBytes.baseAddress, username_len: UInt32(userBytes.count),
                            host: serverBytes.baseAddress, host_len: UInt32(serverBytes.count),
                            password: secretBytes.baseAddress, password_len: UInt32(secretBytes.count),
                            port: UInt32(port), transport: transport.rawValue
                        )
                        return yv_account_add(handle, &config)
                    }
                }
            }
            user.removeAll()
            server.removeAll()
            if code == 0 { self?.accountTokens.insert(token) }
            return code
        }
    }

    func removeAccount(token: UInt64, completion: Completion? = nil) {
        run(completion) { [weak self] handle in
            let code = yv_account_remove(handle, token)
            if code == 0 { self?.accountTokens.remove(token) }
            return code
        }
    }

    /// `renew` true sends REGISTER; false unregisters.
    func register(token: UInt64, renew: Bool, completion: Completion? = nil) {
        run(completion) { yv_account_register($0, token, renew ? 1 : 0) }
    }

    func startCall(account: UInt64, token: UInt64, uri: String, completion: Completion? = nil) {
        run(completion) { handle in
            Array(uri.utf8).withUnsafeBufferPointer { bytes in
                yv_call_start(handle, account, token, bytes.baseAddress, UInt32(bytes.count), 1)
            }
        }
    }

    func answer(token: UInt64, completion: Completion? = nil) {
        run(completion) { yv_call_answer($0, token) }
    }

    /// 603 Decline, or 480 Temporarily Unavailable.
    func reject(token: UInt64, status: Int32 = 603, completion: Completion? = nil) {
        run(completion) { yv_call_reject($0, token, status) }
    }

    func hangup(token: UInt64, completion: Completion? = nil) {
        run(completion) { yv_call_hangup($0, token) }
    }

    func hold(token: UInt64, held: Bool, completion: Completion? = nil) {
        run(completion) { yv_call_hold($0, token, held ? 1 : 0) }
    }

    func mute(token: UInt64, muted: Bool, completion: Completion? = nil) {
        run(completion) { yv_call_mute($0, token, muted ? 1 : 0) }
    }

    func sendDtmf(token: UInt64, digit: Character, completion: Completion? = nil) {
        run(completion) { handle in
            Array(String(digit).utf8).withUnsafeBufferPointer { bytes in
                yv_call_dtmf(handle, token, bytes.baseAddress, UInt32(bytes.count))
            }
        }
    }

    /// Blind transfer to `uri`, or attended transfer onto the connected `consultation` call.
    func transfer(token: UInt64, uri: String?, consultation: UInt64 = 0, completion: Completion? = nil) {
        run(completion) { handle in
            Array((uri ?? "").utf8).withUnsafeBufferPointer { bytes in
                yv_call_transfer(handle, token, bytes.count > 0 ? bytes.baseAddress : nil, UInt32(bytes.count), consultation)
            }
        }
    }

    /// Frees an ended call once the UI has recorded it.
    func release(token: UInt64) {
        run(nil) { yv_call_release($0, token) }
    }

    // MARK: Engine thread

    private func run(_ completion: Completion?, _ body: @escaping (OpaquePointer) -> Int32) {
        lock.withLock {
            jobs.append { handle in
                let code = body(handle)
                if let completion {
                    Task { @MainActor in completion(code) }
                }
            }
        }
    }

    private func publish(_ handle: OpaquePointer) {
        var snapshot = EngineSnapshot()
        for token in accountTokens {
            var registration = YvRegistration()
            guard yv_registration(handle, token, &registration) == 0 else { continue }
            snapshot.registrations[token] = EngineRegistration(
                phase: registration.phase,
                renew: registration.renew != 0,
                status: registration.status,
                sipCode: registration.sip_code,
                expires: registration.expires,
                failureKind: registration.failure_kind
            )
        }
        var count: UInt32 = 0
        let listed = callBuffer.withUnsafeMutableBufferPointer { buffer in
            yv_call_list(handle, buffer.baseAddress, UInt32(buffer.count), &count)
        }
        if listed == 0 {
            for call in callBuffer.prefix(Int(count)) {
                var controls = YvCallControls()
                _ = yv_call_controls(handle, call.token, &controls)
                snapshot.calls.append(EngineCall(
                    token: call.token,
                    accountToken: call.account_token,
                    state: call.state,
                    sipCode: call.sip_code,
                    incoming: call.incoming != 0,
                    caller: Self.caller(of: call),
                    connectedMilliseconds: call.connected_ms,
                    muted: call.muted != 0,
                    held: controls.held != 0,
                    transferPending: controls.transfer_pending != 0,
                    transferCode: controls.transfer_code
                ))
            }
        }
        guard snapshot != published else { return }
        published = snapshot
        onSnapshot(snapshot)
    }

    private static func caller(of call: YvCall) -> String {
        let length = max(0, min(Int(call.caller_len), 64))
        return withUnsafeBytes(of: call.caller) { bytes in
            String(decoding: bytes.prefix(length), as: UTF8.self)
        }
    }
}
