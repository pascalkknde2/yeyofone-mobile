//
//  AppStore.swift
//  Yeyofone
//
//  Screen state for the whole app, the iOS counterpart of Android's YeyoFoneViewModel.
//  There is no SIP engine on iOS yet: accounts, contacts and history are in-memory sample
//  data and calls are simulated, so every screen can be exercised end to end.
//

import Foundation
import Observation

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

    var accounts: [SipAccount] = SampleData.accounts
    var registration: [UUID: RegistrationState] = [:]
    var preferences: [UUID: AccountPreferences] = [:]
    var contacts: [Contact] = SampleData.contacts
    var history: [CallLog] = SampleData.history
    var forwardingDestination: String?

    var activeCall: CallSession?
    var incomingCall: CallSession?
    var consultationCall: CallSession?
    var callPage: CallPage = .main
    var endedSummary: CallSummary?

    var availableRoutes: [AudioRoute] = AudioRoute.allCases
    var selectedRoute: AudioRoute = .earpiece

    var chatContactName = "Noah Anderson"
    var chatContactOnline = true
    var chatMessages: [ChatMessage] = SampleData.chatMessages
    var contactTyping = true

    private var timers: [UUID: [Task<Void, Never>]] = [:]

    init() {
        for account in accounts {
            registration[account.id] = account.enabled ? .registered : .notRegistered
            preferences[account.id] = AccountPreferences()
        }
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
        if enabled { register(id) } else { registration[id] = .notRegistered }
    }

    func register(_ id: UUID) {
        registration[id] = .registering
        schedule(id, after: 1.2) { [weak self] in self?.registration[id] = .registered }
    }

    func save(_ account: SipAccount) {
        if let index = accounts.firstIndex(where: { $0.id == account.id }) {
            accounts[index] = account
        } else {
            accounts.append(account)
            preferences[account.id] = AccountPreferences()
        }
        if account.enabled { register(account.id) }
    }

    func deleteAccount(_ id: UUID) {
        accounts.removeAll { $0.id == id }
        registration[id] = nil
        preferences[id] = nil
        screen = .accounts
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
        let trimmed = destination.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty, activeCall == nil else { return }
        let session = CallSession(
            remoteName: contactName(for: trimmed) ?? trimmed,
            remoteNumber: trimmed,
            direction: .outgoing,
            state: .calling
        )
        activeCall = session
        callPage = .main
        schedule(session.id, after: 1.5) { [weak self] in self?.updateActive(session.id) { $0.state = .ringing } }
        schedule(session.id, after: 3.5) { [weak self] in
            self?.updateActive(session.id) {
                $0.state = .connected
                $0.connectedAt = Date()
            }
        }
    }

    /// Debug helper standing in for a real INVITE, so the incoming-call screen can be exercised.
    func simulateIncomingCall() {
        guard incomingCall == nil, activeCall == nil else { return }
        let contact = contacts.first
        incomingCall = CallSession(
            remoteName: contact?.displayName ?? "1001",
            remoteNumber: contact?.number ?? "1001",
            direction: .incoming,
            state: .ringing
        )
    }

    func acceptIncoming() {
        guard var call = incomingCall else { return }
        call.state = .connected
        call.connectedAt = Date()
        incomingCall = nil
        activeCall = call
        callPage = .main
    }

    func declineIncoming() {
        guard let call = incomingCall else { return }
        incomingCall = nil
        finish(call)
    }

    func endCall() {
        cancelTimers(for: consultationCall?.id)
        consultationCall = nil
        guard let call = activeCall else { return }
        activeCall = nil
        callPage = .main
        finish(call)
    }

    func setMuted(_ muted: Bool) { activeCall?.muted = muted }

    func setHeld(_ held: Bool) {
        guard var call = activeCall else { return }
        call.held = held
        if call.connectedAt != nil { call.state = held ? .held : .connected }
        activeCall = call
    }

    func sendDtmf(_ digit: String) {
        // Tones are sent by the SIP engine; nothing to do in the simulated build.
    }

    func blindTransfer(to destination: String) {
        guard !destination.isEmpty else { return }
        endCall()
    }

    func startConsultation(to destination: String) {
        let session = CallSession(
            remoteName: contactName(for: destination) ?? destination,
            remoteNumber: destination,
            direction: .outgoing,
            state: .calling
        )
        consultationCall = session
        schedule(session.id, after: 2) { [weak self] in
            guard self?.consultationCall?.id == session.id else { return }
            self?.consultationCall?.state = .connected
            self?.consultationCall?.connectedAt = Date()
        }
    }

    func completeTransfer() { endCall() }

    func returnToCaller() {
        cancelTimers(for: consultationCall?.id)
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

    private func updateActive(_ id: UUID, _ change: (inout CallSession) -> Void) {
        guard var call = activeCall, call.id == id else { return }
        change(&call)
        activeCall = call
    }

    private func finish(_ call: CallSession) {
        cancelTimers(for: call.id)
        let answered = call.connectedAt != nil
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

    private func schedule(_ owner: UUID, after seconds: Double, _ action: @escaping @MainActor () -> Void) {
        let task = Task { @MainActor in
            try? await Task.sleep(for: .seconds(seconds))
            guard !Task.isCancelled else { return }
            action()
        }
        timers[owner, default: []].append(task)
    }

    private func cancelTimers(for owner: UUID?) {
        guard let owner else { return }
        timers[owner]?.forEach { $0.cancel() }
        timers[owner] = nil
    }

    #if DEBUG
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
        case "incoming": simulateIncomingCall()
        case "call":
            startCall(to: "1001")
            activeCall?.state = .connected
            activeCall?.connectedAt = Date().addingTimeInterval(-83)
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
