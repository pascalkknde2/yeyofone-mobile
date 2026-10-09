//
//  AccountScreens.swift
//  Yeyofone
//
//  Ports of yeyofone-android ui/accounts/AccountsScreen.kt, AccountDetailScreen.kt and
//  the AccountEditor in MainActivity.kt.
//

import SwiftUI

// MARK: Accounts list

struct AccountsScreen: View {
    @Environment(AppStore.self) private var store
    @State private var selectedTab = 0
    private let tabs = ["SIP", "IAX", "WebRTC"]

    var body: some View {
        VStack(spacing: 0) {
            VStack(spacing: 4) {
                TabHeader(title: "SIP accounts") {
                    Button { store.screen = .accountEditor(nil) } label: {
                        Image(systemName: "plus")
                            .font(.system(size: 20, weight: .medium))
                            .foregroundStyle(Palette.textPrimary)
                            .frame(width: 44, height: 44)
                    }
                    .accessibilityLabel("Add account")
                }
                ChipTabs(titles: tabs, selection: $selectedTab, fontSize: 12)
                    .padding(.horizontal, 24)
                    .padding(.bottom, 16)
            }
            .padding(.top, 6)
            .background(Palette.cardWhite)

            if selectedTab == 0, !store.accounts.isEmpty {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        ForEach(store.accounts) { account in
                            AccountCard(account: account) { store.setEnabled(account.id, $0) }
                                .onTapGesture { store.screen = .accountDetail(account.id) }
                        }
                    }
                    .padding(24)
                }
            } else {
                EmptyStateView(
                    title: "No \(tabs[selectedTab]) accounts found",
                    subtitle: selectedTab == 0 ? "Tap the + button to add a SIP account" : "This account type is not supported yet"
                )
            }
        }
        .background(Palette.backgroundGray)
    }
}

private struct AccountCard: View {
    let account: SipAccount
    let onEnabledChange: (Bool) -> Void

    var body: some View {
        HStack(spacing: 14) {
            InitialsAvatar(
                name: account.displayName,
                background: account.enabled ? Palette.primaryLight : Palette.backgroundGray,
                foreground: account.enabled ? Palette.textPrimary : Palette.inactiveGray
            )
            VStack(alignment: .leading, spacing: 4) {
                Text(account.displayName)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(account.enabled ? Palette.textPrimary : Palette.inactiveGray)
                    .strikethrough(!account.enabled)
                Text(account.sipIdentity)
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundStyle(Palette.textSecondary)
                    .lineLimit(1)
            }
            Spacer(minLength: 8)
            Toggle("", isOn: Binding(get: { account.enabled }, set: onEnabledChange))
                .labelsHidden()
                .tint(Palette.accentGreen)
        }
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 16).fill(Palette.cardWhite))
        .shadow(color: .black.opacity(0.05), radius: 3, y: 1)
        .contentShape(Rectangle())
    }
}

// MARK: Account details

struct AccountDetailScreen: View {
    @Environment(AppStore.self) private var store
    let accountId: UUID
    @State private var confirmSignOut = false

    var body: some View {
        if let account = store.accounts.first(where: { $0.id == accountId }) {
            content(account)
        } else {
            Color.clear.onAppear { store.screen = .accounts }
        }
    }

    private func content(_ account: SipAccount) -> some View {
        let state = store.registration[account.id] ?? .notRegistered
        let preferences = Binding(
            get: { store.preferences(for: account.id) },
            set: { store.setPreferences($0, for: account.id) }
        )
        return VStack(spacing: 0) {
            HStack {
                Button { store.screen = .accounts } label: {
                    Image(systemName: "arrow.left").frame(width: 44, height: 44)
                }
                .accessibilityLabel("Back")
                Spacer()
                Text("Account Details").font(.system(size: 20, weight: .bold))
                Spacer()
                Button { store.screen = .accountEditor(account.id) } label: {
                    Image(systemName: "pencil").frame(width: 44, height: 44)
                }
                .accessibilityLabel("Edit account")
            }
            .font(.system(size: 18, weight: .semibold))
            .foregroundStyle(Palette.textPrimary)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)

            ScrollView {
                VStack(spacing: 20) {
                    profileHeader(account, state: state)
                    DetailSection(title: "Connection") {
                        InfoRow(label: "SIP Server", value: account.domain, mono: true)
                        HairlineDivider()
                        InfoRow(label: "Username", value: account.username, mono: true)
                        HairlineDivider()
                        InfoRow(label: "Password", value: "••••••••••••", mono: true)
                        HairlineDivider()
                        InfoRow(label: "Transport", value: account.transport.rawValue)
                    }
                    DetailSection(title: "Preferences") {
                        SwitchRow(label: "Auto Answer", isOn: preferences.autoAnswer)
                        HairlineDivider()
                        SwitchRow(label: "Call Waiting", isOn: preferences.callWaiting)
                        HairlineDivider()
                        SwitchRow(label: "Voicemail", isOn: preferences.voicemail)
                        HairlineDivider()
                        SwitchRow(label: "Do Not Disturb", isOn: preferences.doNotDisturb)
                    }
                    actionButtons(account, state: state)
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 40)
            }
        }
        .background(Palette.backgroundGray)
        .alert("Sign out of \(account.displayName)?", isPresented: $confirmSignOut) {
            Button("Sign Out", role: .destructive) { store.setEnabled(account.id, false) }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("The account stays on this device but goes offline.")
        }
    }

    private func profileHeader(_ account: SipAccount, state: RegistrationState) -> some View {
        let (label, color) = status(account, state)
        return VStack(spacing: 0) {
            Text(account.displayName.initials)
                .font(.system(size: 30, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 84, height: 84)
                .background(Circle().fill(Palette.textPrimary))
                .overlay(Circle().stroke(.white.opacity(0.12), lineWidth: 1).padding(4))
                .overlay(Circle().stroke(.white, lineWidth: 3))
                .frame(width: 96, height: 96)
                .background(Circle().fill(Palette.primaryLight))
            Text(account.displayName)
                .font(.system(size: 24, weight: .bold))
                .tracking(-0.3)
                .foregroundStyle(Palette.textPrimary)
                .padding(.top, 16)
            Text("Ext. \(account.username)")
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(Palette.textSecondary)
                .padding(.top, 4)
            HStack(spacing: 6) {
                Circle().fill(color).frame(width: 8, height: 8)
                    .frame(width: 12, height: 12)
                    .background(Circle().fill(color.opacity(0.2)))
                Text(label)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(color)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(Capsule().fill(color.opacity(0.1)))
            .padding(.top, 12)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 12)
        .padding(.bottom, 8)
    }

    private func actionButtons(_ account: SipAccount, state: RegistrationState) -> some View {
        VStack(spacing: 12) {
            Button {
                store.register(account.id)
                if !account.enabled { store.setEnabled(account.id, true) }
            } label: {
                HStack(spacing: 8) {
                    if account.enabled { Image(systemName: "arrow.clockwise") }
                    account.enabled ? Text("Re-register Account") : Text("Sign In")
                }
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity, minHeight: 56)
                .background(RoundedRectangle(cornerRadius: 16).fill(Palette.textPrimary))
            }
            .disabled(state == .registering)
            .opacity(state == .registering ? 0.6 : 1)

            if account.enabled {
                Button { confirmSignOut = true } label: {
                    Text("Sign Out")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Palette.accentRed)
                        .frame(maxWidth: .infinity, minHeight: 56)
                        .background(RoundedRectangle(cornerRadius: 16).fill(Palette.accentRed.opacity(0.1)))
                }
            }
        }
        .buttonStyle(PressScaleStyle(scale: 0.98))
    }

    private func status(_ account: SipAccount, _ state: RegistrationState) -> (String, Color) {
        guard account.enabled else { return (String(localized: "Disabled"), Palette.inactiveGray) }
        switch state {
        case .registered: return (String(localized: "Registered"), Palette.accentGreen)
        case .registering: return (String(localized: "Registering…"), Palette.accentOrange)
        case .failed: return (String(localized: "Registration failed"), Palette.accentRed)
        case .unregistering: return (String(localized: "Unregistering…"), Palette.inactiveGray)
        case .notRegistered: return (String(localized: "Not registered"), Palette.inactiveGray)
        }
    }
}

private struct DetailSection<Content: View>: View {
    let title: LocalizedStringKey
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.system(size: 13, weight: .semibold))
                .tracking(0.5)
                .foregroundStyle(Palette.textSecondary)
                .padding(.leading, 4)
            VStack(spacing: 0) { content }
                .padding(.vertical, 8)
                .background(RoundedRectangle(cornerRadius: 16).fill(Palette.cardWhite))
                .shadow(color: .black.opacity(0.05), radius: 3, y: 1)
        }
    }
}

private struct HairlineDivider: View {
    var body: some View {
        Rectangle().fill(Palette.borderLight).frame(height: 1).padding(.horizontal, 20)
    }
}

private struct InfoRow: View {
    let label: LocalizedStringKey
    let value: String
    var mono = false

    var body: some View {
        HStack {
            Text(label)
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(Palette.textPrimary)
            Spacer()
            Text(value)
                .font(.system(size: mono ? 14 : 15, design: mono ? .monospaced : .default))
                .foregroundStyle(Palette.textSecondary)
                .lineLimit(1)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 14)
    }
}

private struct SwitchRow: View {
    let label: LocalizedStringKey
    @Binding var isOn: Bool

    var body: some View {
        Toggle(isOn: $isOn) {
            Text(label)
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(Palette.textPrimary)
        }
        .tint(Palette.accentGreen)
        .padding(.horizontal, 20)
        .padding(.vertical, 10)
    }
}

// MARK: Add / edit account

struct AccountEditorScreen: View {
    @Environment(AppStore.self) private var store
    let existing: SipAccount?

    @State private var displayName = ""
    @State private var username = ""
    @State private var authUsername = ""
    @State private var password = ""
    @State private var domain = ""
    @State private var registrar = ""
    @State private var proxy = ""
    @State private var port = "5060"
    @State private var transport: TransportProtocol = .udp
    @State private var stun = ""
    @State private var turn = ""
    @State private var turnUsername = ""
    @State private var ice = false
    @State private var srtp = false
    @State private var expiry = "300"
    @State private var voicemail = ""
    @State private var callerId = ""
    @State private var error: String?
    @State private var confirmDelete = false

    var body: some View {
        VStack(spacing: 0) {
            BackHeader(title: existing == nil ? "Add account" : "Edit account") { done() }
                .background(Palette.backgroundGray)
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    EditorLabel("Account identity")
                    EditorCard {
                        EditorField(label: "Display name", text: $displayName)
                        EditorField(label: "SIP username", text: $username)
                            .onChange(of: username) { old, new in
                                if authUsername.isEmpty || authUsername == old { authUsername = new }
                            }
                        EditorField(label: "Authentication username", text: $authUsername)
                        EditorField(label: existing == nil ? "Password" : "New password (optional)", text: $password, secure: true)
                    }
                    EditorLabel("Connection")
                    EditorCard {
                        EditorField(label: "Domain", text: $domain, keyboard: .URL)
                        EditorField(label: "Registrar URI", text: $registrar, keyboard: .URL)
                        EditorField(label: "Outbound proxy URI (optional)", text: $proxy, keyboard: .URL)
                        EditorField(label: "Port", text: $port, keyboard: .numberPad)
                        Picker("Transport", selection: $transport) {
                            ForEach(TransportProtocol.allCases) { Text($0.rawValue).tag($0) }
                        }
                        .pickerStyle(.segmented)
                        .onChange(of: transport) { _, new in
                            // SRTP needs TLS signaling; new TLS accounts default to it.
                            if new != .tls { srtp = false } else if existing == nil { srtp = true }
                        }
                        EditorField(label: "Registration expiry seconds", text: $expiry, keyboard: .numberPad)
                    }
                    EditorLabel("Network & security")
                    EditorCard {
                        EditorField(label: "STUN server (optional)", text: $stun, keyboard: .URL)
                        EditorField(label: "TURN server (optional)", text: $turn, keyboard: .URL)
                        EditorField(label: "TURN username (optional)", text: $turnUsername)
                        CheckRow(label: "Enable ICE", isOn: $ice)
                        CheckRow(label: "Require SRTP media (TLS only)", isOn: $srtp, enabled: transport == .tls)
                    }
                    EditorLabel("Optional details")
                    EditorCard {
                        EditorField(label: "Voicemail number (optional)", text: $voicemail, keyboard: .phonePad)
                        EditorField(label: "Caller ID (optional)", text: $callerId)
                    }
                    if let error {
                        Text(error).foregroundStyle(Palette.accentRed).font(.system(size: 14))
                    }
                    Button(action: save) {
                        Text("Save")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 16)
                            .background(RoundedRectangle(cornerRadius: 16).fill(Palette.accentBlue))
                    }
                    .buttonStyle(PressScaleStyle(scale: 0.98))
                    .padding(.top, 8)
                    Button("Cancel") { done() }
                        .foregroundStyle(Palette.textSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                    if existing != nil {
                        Button("Delete", role: .destructive) { confirmDelete = true }
                            .foregroundStyle(Palette.accentRed)
                            .frame(maxWidth: .infinity)
                    }
                }
                .padding(20)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .background(Palette.backgroundGray)
        .alert("Delete this account and its stored secret?", isPresented: $confirmDelete) {
            Button("Delete", role: .destructive) { if let existing { store.deleteAccount(existing.id) } }
            Button("Cancel", role: .cancel) {}
        }
        .onAppear(perform: load)
    }

    private func load() {
        guard let existing else { return }
        displayName = existing.displayName
        username = existing.username
        authUsername = existing.authenticationUsername
        domain = existing.domain
        registrar = existing.registrarUri
        proxy = existing.outboundProxyUri
        port = String(existing.port)
        transport = existing.transport
        stun = existing.stunServer
        turn = existing.turnServer
        turnUsername = existing.turnUsername
        ice = existing.iceEnabled
        srtp = existing.srtpEnabled
        expiry = String(existing.registrationExpirySeconds)
        voicemail = existing.voicemailNumber
        callerId = existing.callerId
    }

    private func done() {
        password = ""
        if let existing { store.screen = .accountDetail(existing.id) } else { store.screen = .accounts }
    }

    /// Same rules as Android's AccountValidator for the fields the UI can check locally.
    private func save() {
        let name = displayName.trimmingCharacters(in: .whitespaces)
        let user = username.trimmingCharacters(in: .whitespaces)
        let host = domain.trimmingCharacters(in: .whitespaces)
        guard !name.isEmpty, !user.isEmpty, !host.isEmpty else {
            error = String(localized: "Display name, SIP username and domain are required")
            return
        }
        guard existing != nil || !password.isEmpty else {
            error = String(localized: "A password is required for a new account")
            return
        }
        guard let portValue = Int(port), (1...65_535).contains(portValue) else {
            error = String(localized: "Port must be between 1 and 65535")
            return
        }
        guard let expiryValue = Int(expiry), (60...86_400).contains(expiryValue) else {
            error = String(localized: "Registration expiry must be between 60 and 86400 seconds")
            return
        }
        if ice, stun.trimmingCharacters(in: .whitespaces).isEmpty {
            error = String(localized: "ICE needs a STUN server")
            return
        }
        let trimmedRegistrar = registrar.trimmingCharacters(in: .whitespaces)
        let account = SipAccount(
            id: existing?.id ?? UUID(),
            displayName: name,
            username: user,
            authenticationUsername: authUsername.isEmpty ? user : authUsername,
            domain: host,
            registrarUri: trimmedRegistrar.isEmpty || trimmedRegistrar.hasPrefix("sip:") ? trimmedRegistrar : "sip:\(trimmedRegistrar)",
            outboundProxyUri: proxy,
            port: portValue,
            transport: transport,
            stunServer: stun,
            turnServer: turn,
            turnUsername: turnUsername,
            iceEnabled: ice,
            srtpEnabled: srtp,
            registrationExpirySeconds: expiryValue,
            voicemailNumber: voicemail,
            callerId: callerId,
            enabled: existing?.enabled ?? true
        )
        // The password would go to the Keychain here once a SIP engine exists; it is never kept in UI state.
        password = ""
        store.save(account)
        store.screen = existing == nil ? .accounts : .accountDetail(account.id)
    }
}

private struct EditorLabel: View {
    let text: LocalizedStringKey
    init(_ text: LocalizedStringKey) { self.text = text }

    var body: some View {
        Text(text)
            .sectionLabelStyle(color: Palette.textSecondary, tracking: 0.7)
            .padding(.leading, 4)
            .padding(.top, 4)
    }
}

private struct EditorCard<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        VStack(spacing: 12) { content }
            .padding(16)
            .background(RoundedRectangle(cornerRadius: 20).fill(Palette.cardWhite))
    }
}

private struct EditorField: View {
    let label: LocalizedStringKey
    @Binding var text: String
    var secure = false
    var keyboard: UIKeyboardType = .default
    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(focused ? Palette.accentBlue : Palette.textSecondary)
            Group {
                if secure {
                    SecureField("", text: $text)
                } else {
                    TextField("", text: $text)
                        .keyboardType(keyboard)
                }
            }
            .font(.system(size: 15))
            .foregroundStyle(Palette.textPrimary)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .focused($focused)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(RoundedRectangle(cornerRadius: 14).fill(Palette.backgroundGray))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(focused ? Palette.accentBlue : Palette.borderLight, lineWidth: 1))
        .contentShape(Rectangle())
        .onTapGesture { focused = true }
    }
}

private struct CheckRow: View {
    let label: LocalizedStringKey
    @Binding var isOn: Bool
    var enabled = true

    var body: some View {
        Button { isOn.toggle() } label: {
            HStack(spacing: 12) {
                Image(systemName: isOn ? "checkmark.square.fill" : "square")
                    .font(.system(size: 20))
                    .foregroundStyle(isOn ? Palette.accentBlue : Palette.inactiveGray)
                Text(label)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(enabled ? Palette.textPrimary : Palette.textSecondary)
                Spacer()
            }
            .padding(.vertical, 4)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.6)
    }
}
