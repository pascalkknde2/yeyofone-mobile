//
//  AppStore.swift
//  Yeyofone
//
//  Screen state for the whole app, the iOS counterpart of Android's YeyoFoneViewModel.
//  Accounts, contacts and history are stored on the device; registration and calls run on
//  SipEngine (PJSIP through the shared YeyoFone bridge). A demo store with sample data and
//  no engine backs SwiftUI previews and the debug-only -YFScreen launch argument.
//

import Foundation
import Observation
import os

/// State changes only; never account secrets. View with Console or `log stream --predicate 'subsystem == "com.yeyofone.app"'`.
nonisolated private let log = Logger(subsystem: "com.yeyofone.app", category: "sip")

enum AppScreen: Equatable {
    case home, contacts, history, chat, settings
    case dial(destination: String)
    case accounts
    case accountDetail(UUID)
    case accountEditor(UUID?)
    case audioSettings, videoSettings, incomingCallsSettings, languageSettings, recordings
}

enum Tab: Int, CaseIterable {
    case home, keypad, contacts, calls, chat, settings

    /// Chat isn't implemented, so like Android's release builds the tab only shows in debug builds.
    static var visible: [Tab] {
        #if DEBUG
        allCases
        #else
        allCases.filter { $0 != .chat }
        #endif
    }
}

/// Which page of the in-call flow is showing over the active call.
enum CallPage { case main, options, transfer }

@Observable
final class AppStore {
    var screen: AppScreen = .home

    var accounts: [SipAccount] = [] {
        didSet { save(accounts, to: "accounts") }
    }
    var registration: [UUID: RegistrationState] = [:]
    var preferences: [UUID: AccountPreferences] = [:] {
        didSet { save(preferences, to: "preferences") }
    }
    var contacts: [Contact] = [] {
        didSet { save(contacts, to: "contacts") }
    }
    var history: [CallLog] = [] {
        didSet { save(history, to: "history") }
    }
    var forwardingDestination: String?

    var activeCall: CallSession?
    var incomingCall: CallSession?
    var consultationCall: CallSession?
    var callPage: CallPage = .main
    var endedSummary: CallSummary?
    /// A short message shown over the current screen, such as a failed call.
    var notice: String?

    var availableRoutes: [AudioRoute] = [.earpiece, .speaker]
    var selectedRoute: AudioRoute = .earpiece {
        didSet { if engine != nil, activeCall != nil { AudioController.select(selectedRoute) } }
    }

    var chatContactName = "Noah Anderson"
    var chatContactOnline = true
    var chatMessages: [ChatMessage] = SampleData.chatMessages
    var contactTyping = true

    @ObservationIgnored private let persists: Bool
    @ObservationIgnored private var engine: SipEngine?
    @ObservationIgnored private var accountTokens: [UUID: UInt64] = [:]
    @ObservationIgnored private var nextAccountToken: UInt64 = 1
    @ObservationIgnored private var nextCallToken: UInt64 = 1
    @ObservationIgnored private var finishedCalls: Set<UInt64> = []
    @ObservationIgnored private var pendingTransfers: Set<UInt64> = []
    @ObservationIgnored private var retries: [UUID: Task<Void, Never>] = [:]
    @ObservationIgnored private var callKit: CallKitController?
    /// True while a call that CallKit doesn't manage has activated the audio session itself.
    @ObservationIgnored private var ownsAudioSession = false

    init(demo: Bool = false) {
        persists = !demo
        if demo {
            accounts = SampleData.accounts
            contacts = SampleData.contacts
            history = SampleData.history
            for account in accounts {
                registration[account.id] = account.enabled ? .registered : .notRegistered
            }
            return
        }
        accounts = LocalStore.load([SipAccount].self, from: "accounts") ?? []
        preferences = LocalStore.load([UUID: AccountPreferences].self, from: "preferences") ?? [:]
        contacts = LocalStore.load([Contact].self, from: "contacts") ?? []
        history = LocalStore.load([CallLog].self, from: "history") ?? []
        startEngine()
    }

    var primaryAccount: SipAccount? { accounts.first }

    var isPrimaryRegistered: Bool {
        primaryAccount.map { registration[$0.id] == .registered } ?? false
    }

    var selectedTab: Tab? {
        switch screen {
        case .home: .home
        case .dial: .keypad
        case .contacts: .contacts
        case .history: .calls
        case .chat: .chat
        case .settings, .accounts: .settings
        default: nil
        }
    }

    func select(_ tab: Tab) {
        switch tab {
        case .home: screen = .home
        case .keypad: screen = .dial(destination: "")
        case .contacts: screen = .contacts
        case .calls: screen = .history
        case .chat: screen = .chat
        case .settings: screen = .settings
        }
    }

    // MARK: Accounts

    func preferences(for id: UUID) -> AccountPreferences { preferences[id] ?? AccountPreferences() }

    func setPreferences(_ value: AccountPreferences, for id: UUID) { preferences[id] = value }

    func setEnabled(_ id: UUID, _ enabled: Bool) {
        guard let index = accounts.firstIndex(where: { $0.id == id }) else { return }
        accounts[index].enabled = enabled
        if enabled {
            register(id)
        } else {
            retries.removeValue(forKey: id)?.cancel()
            if let token = accountTokens[id] { engine?.register(token: token, renew: false) }
            registration[id] = .notRegistered
        }
    }

    /// Sends a fresh REGISTER, adding the account to the engine first if needed.
    func register(_ id: UUID) {
        guard let account = accounts.first(where: { $0.id == id }), account.enabled else { return }
        guard engine != nil else { return }
        if let token = accountTokens[id] {
            registration[id] = .registering
            engine?.register(token: token, renew: true)
        } else {
            addToEngine(account)
        }
    }

    /// Saves the account; a non-empty `password` replaces the stored one.
    func save(_ account: SipAccount, password: String?) {
        if let password, !password.isEmpty {
            PasswordStore.setPassword(password, for: account.id)
        }
        if let index = accounts.firstIndex(where: { $0.id == account.id }) {
            accounts[index] = account
        } else {
            accounts.append(account)
        }
        removeFromEngine(account.id)
        if account.enabled { addToEngine(account) } else { registration[account.id] = .notRegistered }
    }

    func deleteAccount(_ id: UUID) {
        removeFromEngine(id)
        PasswordStore.deletePassword(for: id)
        accounts.removeAll { $0.id == id }
        registration[id] = nil
        preferences[id] = nil
        screen = .accounts
    }

    /// Registration can lapse while iOS suspends the app, so refresh it when the app comes back.
    func refreshRegistrations() {
        for account in accounts where account.enabled { register(account.id) }
    }

    private func addToEngine(_ account: SipAccount) {
        guard let engine else { return }
        // The iOS engine is built without TLS for now; see docs/pjsip-build.md.
        guard account.transport != .tls, let password = PasswordStore.password(for: account.id) else {
            registration[account.id] = .failed
            return
        }
        let token = nextAccountToken
        nextAccountToken += 1
        accountTokens[account.id] = token
        registration[account.id] = .registering
        engine.addAccount(
            token: token, username: account.username, host: account.domain, password: password,
            port: account.port, transport: account.transport == .tcp ? .tcp : .udp
        ) { [weak self] code in
            guard let self, accountTokens[account.id] == token else { return }
            log.info("Account \(token) added to engine: \(code)")
            if code == 0 {
                self.engine?.register(token: token, renew: true)
            } else {
                accountTokens[account.id] = nil
                registration[account.id] = .failed
            }
        }
    }

    private func removeFromEngine(_ id: UUID) {
        retries.removeValue(forKey: id)?.cancel()
        guard let token = accountTokens.removeValue(forKey: id) else { return }
        engine?.removeAccount(token: token)
    }

    private func scheduleRetry(_ id: UUID) {
        guard retries[id] == nil else { return }
        retries[id] = Task { [weak self] in
            try? await Task.sleep(for: .seconds(30))
            guard let self, !Task.isCancelled else { return }
            retries[id] = nil
            if registration[id] == .failed { register(id) }
        }
    }

    // MARK: Contacts

    func saveContact(_ contact: Contact) {
        if let index = contacts.firstIndex(where: { $0.id == contact.id }) {
            contacts[index] = contact
        } else {
            contacts.append(contact)
        }
    }

    func deleteContact(_ id: UUID) { contacts.removeAll { $0.id == id } }

    func toggleFavorite(_ id: UUID) {
        guard let index = contacts.firstIndex(where: { $0.id == id }) else { return }
        contacts[index].favorite.toggle()
    }

    func contactName(for number: String) -> String? {
        contacts.first { $0.number == number }?.displayName
    }

    // MARK: Calls

    func startCall(to destination: String) {
        let number = destination.trimmingCharacters(in: .whitespaces)
        guard !number.isEmpty, activeCall == nil, incomingCall == nil, let engine else { return }
        guard let account = callingAccount, let accountToken = accountTokens[account.id] else {
            notice = String(localized: "Add a SIP account to make calls")
            return
        }
        Task {
            guard await AudioController.requestMicrophone() else {
                notice = String(localized: "Microphone permission is required to place a call")
                return
            }
            guard activeCall == nil else { return }
            let token = newCallToken()
            activeCall = CallSession(token: token, remoteName: contactName(for: number) ?? number,
                                     remoteNumber: number, direction: .outgoing, state: .calling)
            callPage = .main
            AudioController.activateSession()
            ownsAudioSession = true
            prepareAudioRoutes()
            engine.startCall(account: accountToken, token: token, uri: sipUri(number, account: account)) { [weak self] code in
                log.info("Call \(token) start: \(code)")
                guard let self, code != 0, activeCall?.token == token else { return }
                activeCall = nil
                notice = String(localized: "Call failed")
            }
        }
    }

    // The in-app buttons below route through CallKit for calls it manages, so the system UI stays in
    // step; CallKit then calls back into the matching answer/end/mute/hold implementation.

    func acceptIncoming() {
        guard let call = incomingCall else { return }
        if let callKit, callKit.isManaged(call.id) {
            callKit.requestAnswer(call.id)
        } else {
            answerIncoming(id: call.id) { _ in }
        }
    }

    func declineIncoming() {
        guard let call = incomingCall else { return }
        if let callKit, callKit.isManaged(call.id) {
            callKit.requestEnd(call.id)
        } else {
            rejectIncoming(call)
        }
    }

    func endCall() {
        guard let call = activeCall else { return }
        if let callKit, callKit.isManaged(call.id) {
            callKit.requestEnd(call.id)
        } else {
            hangUpActive()
        }
    }

    func setMuted(_ muted: Bool) {
        guard let call = activeCall else { return }
        if let callKit, callKit.isManaged(call.id) {
            callKit.requestMute(call.id, muted)
        } else {
            applyMute(id: call.id, muted)
        }
    }

    func setHeld(_ held: Bool) {
        guard let call = activeCall else { return }
        if let callKit, callKit.isManaged(call.id) {
            callKit.requestHold(call.id, held)
        } else {
            applyHold(id: call.id, held) { _ in }
        }
    }

    private func answerIncoming(id: UUID, completion: @escaping (Bool) -> Void) {
        guard let call = incomingCall, call.id == id else {
            completion(false)
            return
        }
        guard let engine else {
            incomingCall = nil
            activeCall = CallSession(remoteName: call.remoteName, remoteNumber: call.remoteNumber,
                                     direction: .incoming, state: .connected, connectedAt: Date())
            completion(true)
            return
        }
        let viaCallKit = callKit?.isManaged(id) == true
        Task {
            guard await AudioController.requestMicrophone() else {
                notice = String(localized: "Microphone permission is required to place a call")
                completion(false)
                return
            }
            guard incomingCall?.token == call.token else {
                completion(false)
                return
            }
            incomingCall = nil
            var answered = call
            answered.state = .connecting
            activeCall = answered
            callPage = .main
            if !viaCallKit {
                AudioController.activateSession()
                ownsAudioSession = true
            }
            prepareAudioRoutes()
            engine.answer(token: call.token) { [weak self] code in
                log.info("Call \(call.token) answer: \(code)")
                if code != 0 { self?.notice = String(localized: "Call failed") }
                completion(code == 0)
            }
        }
    }

    private func rejectIncoming(_ call: CallSession) {
        incomingCall = nil
        finishedCalls.insert(call.token)
        engine?.reject(token: call.token)
        finish(call)
    }

    /// End requested by CallKit: from the system UI, or our own end transaction.
    private func endFromSystem(_ id: UUID) {
        if let call = incomingCall, call.id == id {
            rejectIncoming(call)
        } else if activeCall?.id == id {
            hangUpActive()
        }
    }

    private func applyMute(id: UUID, _ muted: Bool) {
        guard let call = activeCall, call.id == id else { return }
        activeCall?.muted = muted
        engine?.mute(token: call.token, muted: muted)
    }

    private func applyHold(id: UUID, _ held: Bool, completion: @escaping (Bool) -> Void) {
        guard var call = activeCall, call.id == id else {
            completion(false)
            return
        }
        call.held = held
        if call.connectedAt != nil { call.state = held ? .held : .connected }
        activeCall = call
        guard let engine else {
            completion(true)
            return
        }
        // The engine refuses while a transfer or another hold is in progress; its next snapshot restores the state.
        engine.hold(token: call.token, held: held) { code in completion(code == 0) }
    }

    /// CallKit reset (for example the system's call service restarted): end everything.
    private func endAllCalls() {
        if let call = incomingCall { rejectIncoming(call) }
        if activeCall != nil { hangUpActive() }
    }

    private func hangUpActive() {
        guard let call = activeCall else { return }
        guard let engine, call.token != 0 else {
            activeCall = nil
            consultationCall = nil
            callPage = .main
            finish(call)
            return
        }
        if let consultation = consultationCall { engine.hangup(token: consultation.token) }
        engine.hangup(token: call.token)
    }

    func sendDtmf(_ digit: String) {
        guard let call = activeCall, let character = digit.first else { return }
        engine?.sendDtmf(token: call.token, digit: character)
    }

    func blindTransfer(to destination: String) {
        let number = destination.trimmingCharacters(in: .whitespaces)
        guard !number.isEmpty, let call = activeCall else { return }
        callPage = .main
        guard let engine, let account = callingAccount else {
            endCall()
            return
        }
        pendingTransfers.insert(call.token)
        engine.transfer(token: call.token, uri: sipUri(number, account: account)) { [weak self] code in
            guard let self, code != 0 else { return }
            pendingTransfers.remove(call.token)
            notice = String(localized: "Transfer failed")
        }
    }

    func startConsultation(to destination: String) {
        let number = destination.trimmingCharacters(in: .whitespaces)
        guard !number.isEmpty, activeCall != nil, consultationCall == nil else { return }
        guard let engine else {
            consultationCall = CallSession(remoteName: contactName(for: number) ?? number, remoteNumber: number,
                                           direction: .outgoing, state: .connected, connectedAt: Date())
            return
        }
        guard let account = callingAccount, let accountToken = accountTokens[account.id] else { return }
        let token = newCallToken()
        consultationCall = CallSession(token: token, remoteName: contactName(for: number) ?? number,
                                       remoteNumber: number, direction: .outgoing, state: .calling)
        engine.startCall(account: accountToken, token: token, uri: sipUri(number, account: account)) { [weak self] code in
            guard let self, code != 0, consultationCall?.token == token else { return }
            consultationCall = nil
            notice = String(localized: "Call failed")
        }
    }

    func completeTransfer() {
        guard let call = activeCall, let consultation = consultationCall else { return }
        guard let engine else {
            endCall()
            return
        }
        pendingTransfers.insert(call.token)
        engine.transfer(token: call.token, uri: nil, consultation: consultation.token) { [weak self] code in
            guard let self, code != 0 else { return }
            pendingTransfers.remove(call.token)
            notice = String(localized: "Transfer failed")
        }
    }

    func returnToCaller() {
        if let consultation = consultationCall { engine?.hangup(token: consultation.token) }
        consultationCall = nil
        setHeld(false)
        callPage = .main
    }

    func dismissSummary() {
        endedSummary = nil
        screen = .history
    }

    func callAgain() {
        guard let summary = endedSummary else { return }
        endedSummary = nil
        startCall(to: summary.callerNumber)
    }

    /// The account calls go out on: the first enabled account the engine knows about.
    private var callingAccount: SipAccount? {
        accounts.first { $0.enabled && accountTokens[$0.id] != nil }
    }

    /// Like Android, a short number or user part is dialled at the account's own domain and port.
    private func sipUri(_ destination: String, account: SipAccount) -> String {
        let user = destination.split(separator: "@").first.map(String.init) ?? destination
        let bare = user.replacingOccurrences(of: "sip:", with: "")
        let transport = account.transport == .tcp ? ";transport=tcp" : ""
        return "sip:\(bare)@\(account.domain):\(account.port)\(transport)"
    }

    private func newCallToken() -> UInt64 {
        defer { nextCallToken += 1 }
        return nextCallToken
    }

    private func prepareAudioRoutes() {
        availableRoutes = AudioController.availableRoutes()
        selectedRoute = .earpiece
    }

    private func finish(_ call: CallSession) {
        let answered = call.connectedAt != nil
        callKit?.reportEnded(id: call.id, answered: answered)
        if ownsAudioSession, activeCall == nil, incomingCall == nil {
            ownsAudioSession = false
            AudioController.deactivateSession()
        }
        let duration = call.connectedAt.map { Date().timeIntervalSince($0) } ?? 0
        let type: CallType = call.direction == .outgoing ? .outgoing : (answered ? .incoming : .missed)
        history.insert(
            CallLog(
                id: UUID(),
                contactName: call.remoteName,
                number: call.remoteNumber,
                callType: type,
                timestamp: Date(),
                duration: answered ? duration : nil
            ),
            at: 0
        )
        endedSummary = CallSummary(
            callerName: call.remoteName,
            callerNumber: call.remoteNumber,
            direction: call.direction,
            wasAnswered: answered,
            duration: duration,
            endedAt: Date()
        )
    }

    // MARK: Engine

    private func startEngine() {
        let engine = SipEngine(
            onSnapshot: { snapshot in
                Task { @MainActor [weak self] in self?.apply(snapshot) }
            },
            onStartFailure: { code in
                log.error("Engine failed to start: \(code)")
                Task { @MainActor [weak self] in
                    self?.engine = nil
                    self?.notice = String(localized: "The calling engine couldn't start (\(Int(code))).")
                }
            }
        )
        self.engine = engine
        engine.start()
        #if !targetEnvironment(simulator)
        // CallKit rings for incoming calls; the Simulator has no CallKit ringing, so keep the bridge's tone there.
        engine.setRingtone(false)
        #endif
        callKit = CallKitController(handlers: .init(
            answer: { [weak self] id, done in
                guard let self else { return done(false) }
                answerIncoming(id: id, completion: done)
            },
            end: { [weak self] id in self?.endFromSystem(id) },
            mute: { [weak self] id, muted in self?.applyMute(id: id, muted) },
            hold: { [weak self] id, held, done in
                guard let self else { return done(false) }
                applyHold(id: id, held, completion: done)
            },
            dtmf: { [weak self] _, digits in digits.forEach { self?.sendDtmf(String($0)) } },
            audioSession: { [weak self] active in
                log.info("CallKit audio session active: \(active)")
                self?.engine?.audioDevice(open: active)
            },
            reset: { [weak self] in self?.endAllCalls() }
        ))
        log.info("Engine started with \(self.accounts.count) account(s)")
        for account in accounts where account.enabled { addToEngine(account) }
    }

    private func apply(_ snapshot: EngineSnapshot) {
        for (id, token) in accountTokens {
            guard let update = snapshot.registrations[token],
                  let account = accounts.first(where: { $0.id == id }) else { continue }
            let state = Self.registrationState(update, enabled: account.enabled)
            if registration[id] != state {
                log.info("Account \(token): \(String(describing: state)) (status \(update.status), SIP \(update.sipCode), expires \(update.expires)s, failure kind \(update.failureKind))")
                registration[id] = state
            }
            if state == .failed, account.enabled { scheduleRetry(id) }
        }
        for call in snapshot.calls { apply(call) }
        finishedCalls.formIntersection(snapshot.calls.map(\.token))
    }

    private func apply(_ call: EngineCall) {
        log.info("Call \(call.token): state \(call.state), SIP \(call.sipCode), incoming \(call.incoming), held \(call.held), media \(call.mediaActive), audio \(call.audioActive), audio error \(call.audioError), transfer pending \(call.transferPending) (\(call.transferCode))")
        if call.state == InviteState.disconnected {
            if !finishedCalls.contains(call.token) {
                finishedCalls.insert(call.token)
                callEnded(call)
            }
            engine?.release(token: call.token)
            return
        }
        if pendingTransfers.contains(call.token), !call.transferPending {
            pendingTransfers.remove(call.token)
            if (200..<300).contains(Int(call.transferCode)) {
                // The other party now has the call; end our leg.
                engine?.hangup(token: call.token)
            } else {
                notice = String(localized: "Transfer failed")
            }
        }
        if let current = activeCall, current.token == call.token {
            let updated = Self.merge(current, call)
            if updated != current { activeCall = updated }
        } else if let current = consultationCall, current.token == call.token {
            let updated = Self.merge(current, call)
            if updated != current { consultationCall = updated }
        } else if let current = incomingCall, current.token == call.token {
            let updated = Self.merge(current, call)
            if updated != current { incomingCall = updated }
        } else if call.incoming, incomingCall == nil, activeCall == nil, !finishedCalls.contains(call.token),
                  call.state == InviteState.incoming || call.state == InviteState.early {
            let number = call.caller.isEmpty ? String(localized: "Unknown caller") : call.caller
            let session = CallSession(token: call.token, remoteName: contactName(for: number) ?? number,
                                      remoteNumber: number, direction: .incoming, state: .ringing)
            incomingCall = session
            callKit?.reportIncoming(id: session.id, number: number, name: session.remoteName) { [weak self] outcome in
                log.info("Call \(call.token) reported to CallKit: \(String(describing: outcome))")
                guard let self, outcome == .declined, let current = incomingCall, current.id == session.id else { return }
                rejectIncoming(current)
            }
        }
    }

    private func callEnded(_ call: EngineCall) {
        pendingTransfers.remove(call.token)
        if consultationCall?.token == call.token {
            // The original caller stays on hold until the user returns to them.
            consultationCall = nil
            return
        }
        if let session = activeCall, session.token == call.token {
            activeCall = nil
            callPage = .main
            if let consultation = consultationCall {
                engine?.hangup(token: consultation.token)
                consultationCall = nil
            }
            finish(session)
        } else if let session = incomingCall, session.token == call.token {
            incomingCall = nil
            finish(session)
        }
    }

    private static func merge(_ session: CallSession, _ call: EngineCall) -> CallSession {
        var session = session
        session.muted = call.muted
        session.held = call.held
        switch call.state {
        case InviteState.calling:
            session.state = .calling
        case InviteState.incoming, InviteState.early:
            session.state = .ringing
        case InviteState.connecting:
            session.state = .connecting
        case InviteState.confirmed:
            session.state = call.held ? .held : .connected
            if session.connectedAt == nil {
                session.connectedAt = Date().addingTimeInterval(-Double(call.connectedMilliseconds) / 1000)
            }
        default:
            break
        }
        return session
    }

    private static func registrationState(_ update: EngineRegistration, enabled: Bool) -> RegistrationState {
        switch update.phase {
        case 1:
            return update.renew ? .registering : .unregistering
        case 2:
            if update.status == 0, (200..<300).contains(Int(update.sipCode)) {
                return update.expires > 0 ? .registered : .notRegistered
            }
            return enabled ? .failed : .notRegistered
        default:
            return enabled ? .registering : .notRegistered
        }
    }

    private func save<Value: Encodable>(_ value: Value, to name: String) {
        if persists { LocalStore.save(value, to: name) }
    }

    #if DEBUG
    /// Opens a screen directly for screenshots; only used with the demo store.
    func openForDebugging(_ name: String) {
        switch name {
        case "keypad": screen = .dial(destination: "1001")
        case "contacts": screen = .contacts
        case "history": screen = .history
        case "chat": screen = .chat
        case "settings": screen = .settings
        case "accounts": screen = .accounts
        case "account": screen = accounts.first.map { .accountDetail($0.id) } ?? .accounts
        case "editor": screen = .accountEditor(accounts.first?.id)
        case "audio": screen = .audioSettings
        case "video": screen = .videoSettings
        case "incomingSettings": screen = .incomingCallsSettings
        case "language": screen = .languageSettings
        case "recordings": screen = .recordings
        case "incoming":
            incomingCall = CallSession(remoteName: "Noah Anderson", remoteNumber: "1001", direction: .incoming, state: .ringing)
        case "call":
            activeCall = CallSession(remoteName: "Noah Anderson", remoteNumber: "1001", direction: .outgoing,
                                     state: .connected, connectedAt: Date().addingTimeInterval(-83))
        case "options": openForDebugging("call"); callPage = .options
        case "transfer": openForDebugging("call"); callPage = .transfer
        case "ended":
            endedSummary = CallSummary(callerName: "Noah Anderson", callerNumber: "1001", direction: .outgoing,
                                       wasAnswered: true, duration: 245, endedAt: Date())
        default: break
        }
    }
    #endif

    // MARK: Chat

    func sendChatMessage(_ text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        let time = Date().formatted(.dateTime.hour(.twoDigits(amPM: .omitted)).minute(.twoDigits))
        chatMessages.append(ChatMessage(content: String(trimmed.prefix(2_000)), timestamp: time, isOutgoing: true))
        contactTyping = false
    }
}

/// Sample content for SwiftUI previews and screenshots; the real app starts empty.
enum SampleData {
    static let accounts: [SipAccount] = [
        SipAccount(
            id: UUID(), displayName: "Pascal Kanyama", username: "1005", authenticationUsername: "1005",
            domain: "pbx.yeyofone.com", registrarUri: "sip:pbx.yeyofone.com", outboundProxyUri: "", port: 5060,
            transport: .udp, stunServer: "", turnServer: "", turnUsername: "", iceEnabled: false,
            srtpEnabled: false, registrationExpirySeconds: 300, voicemailNumber: "*97", callerId: "", enabled: true
        ),
    ]

    static let contacts: [Contact] = [
        Contact(id: UUID(), displayName: "Noah Anderson", number: "1001", favorite: true),
        Contact(id: UUID(), displayName: "Reception", number: "1000", favorite: true),
        Contact(id: UUID(), displayName: "Amina Diallo", number: "1002", favorite: true),
        Contact(id: UUID(), displayName: "Support Desk", number: "1010", favorite: false),
    ]

    static let history: [CallLog] = {
        let now = Date()
        return [
            CallLog(id: UUID(), contactName: "Noah Anderson", number: "1001", callType: .incoming,
                    timestamp: now.addingTimeInterval(-1_800), duration: 245),
            CallLog(id: UUID(), contactName: "Reception", number: "1000", callType: .missed,
                    timestamp: now.addingTimeInterval(-5_400), duration: nil, hasVoicemail: true),
            CallLog(id: UUID(), contactName: "Amina Diallo", number: "1002", callType: .outgoing,
                    timestamp: now.addingTimeInterval(-9_000), duration: 42),
        ]
    }()

    static let chatMessages: [ChatMessage] = [
        ChatMessage(content: "Hey! Are you available for a quick call?", timestamp: "09:42", isOutgoing: false),
        ChatMessage(content: "Sure, give me 5 minutes to wrap up something.", timestamp: "09:43", isOutgoing: true, status: .read),
        ChatMessage(content: "0:24", timestamp: "09:45", isOutgoing: false, isVoice: true,
                    waveformHeights: [8, 14, 10, 18, 12, 16, 8, 14, 10, 6, 16, 12]),
        ChatMessage(content: "Got your voice note. Calling you now!", timestamp: "09:46", isOutgoing: true, status: .delivered),
        ChatMessage(content: "Perfect, thanks! 👍", timestamp: "09:46", isOutgoing: false),
    ]
}
