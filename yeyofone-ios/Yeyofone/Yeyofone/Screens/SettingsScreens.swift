//
//  SettingsScreens.swift
//  Yeyofone
//
//  Ports of yeyofone-android ui/settings: SettingsScreen, AudioSettingsScreen, VideoSettingsScreen,
//  IncomingCallsSettingsScreen, LanguageSettingsScreen and RecordingsScreen.
//

import SwiftUI

// MARK: Settings

private struct SettingEntry: Identifiable {
    let icon: String
    let color: Color
    let title: String
    let subtitle: String
    var badge: String?
    var action: (() -> Void)?
    var id: String { title }
}

struct SettingsScreen: View {
    @Environment(AppStore.self) private var store
    @State private var searching = false
    @State private var query = ""
    @State private var toast: String?

    private var sections: [(String, [SettingEntry])] {
        let accountCount = store.accounts.filter(\.enabled).count
        var sections: [(String, [SettingEntry])] = [
            (String(localized: "Account"), [
                SettingEntry(icon: "person.crop.circle.fill", color: Palette.accentBlue, title: String(localized: "Accounts"),
                             subtitle: String(localized: "\(accountCount) accounts connected")) { store.screen = .accounts },
                SettingEntry(icon: "building.2.fill", color: Palette.textPrimary, title: String(localized: "Enterprise Sign In"),
                             subtitle: String(localized: "Connect with your work account"), badge: String(localized: "New")),
            ]),
            (String(localized: "Audio & Video"), [
                SettingEntry(icon: "music.note", color: Palette.accentOrange, title: String(localized: "Audio"),
                             subtitle: String(localized: "Speaker, Microphone, Ringtones")) { store.screen = .audioSettings },
                // Video, chat and recording aren't implemented; debug builds only, as on Android.
                SettingEntry(icon: "video.fill", color: Palette.accentRed, title: String(localized: "Video"), subtitle: String(localized: "Camera, Resolution, Layout"),
                             action: debugOnly { store.screen = .videoSettings }),
                SettingEntry(icon: "character.bubble.fill", color: Palette.accentRed, title: String(localized: "Translate"),
                             subtitle: String(localized: "Real-time language translation")),
            ]),
            (String(localized: "Call Settings"), [
                SettingEntry(icon: "phone.arrow.down.left.fill", color: Palette.accentGreen, title: String(localized: "Incoming Calls"),
                             subtitle: String(localized: "Ringtone, Call Waiting, Forwarding")) { store.screen = .incomingCallsSettings },
                SettingEntry(icon: "record.circle", color: Palette.accentBlue, title: String(localized: "Recording Calls"), subtitle: String(localized: "Auto-record, Storage, Format"),
                             action: debugOnly { store.screen = .recordings }),
            ]),
            (String(localized: "More"), [
                SettingEntry(icon: "globe", color: Palette.accentBlue, title: String(localized: "Language"),
                             subtitle: String(localized: "App language and voice preferences")) { store.screen = .languageSettings },
                SettingEntry(icon: "gearshape.fill", color: Palette.accentBlue, title: String(localized: "Advanced"), subtitle: String(localized: "Network, Codecs, Debugging")),
                SettingEntry(icon: "square.and.arrow.up", color: Palette.accentGreen, title: String(localized: "Social"), subtitle: String(localized: "Share, Invite friends, Community")),
                SettingEntry(icon: "info.circle.fill", color: Palette.inactiveGray, title: String(localized: "About"),
                             subtitle: String(localized: "Version \(AppInfo.version) • Terms • Privacy")),
            ]),
        ]
        #if DEBUG
        sections.append((String(localized: "Developer"), [
                SettingEntry(icon: "phone.badge.waveform.fill", color: Palette.accentGreen, title: String(localized: "Simulate incoming call"),
                             subtitle: String(localized: "Preview the incoming-call screen")) { store.simulateIncomingCall() },
            ]))
        #endif
        let trimmed = query.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return sections }
        return sections.compactMap { title, entries in
            let visible = entries.filter {
                $0.title.localizedCaseInsensitiveContains(trimmed) || $0.subtitle.localizedCaseInsensitiveContains(trimmed)
            }
            return visible.isEmpty ? nil : (title, visible)
        }
    }

    /// Keeps an entry's action in debug builds only; release builds show "Coming soon" instead.
    private func debugOnly(_ action: @escaping () -> Void) -> (() -> Void)? {
        #if DEBUG
        action
        #else
        nil
        #endif
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                if searching {
                    TextField("Search settings", text: $query)
                        .font(.system(size: 17))
                        .autocorrectionDisabled()
                } else {
                    Text("Settings")
                        .font(.system(size: 24, weight: .bold))
                        .tracking(-0.5)
                        .foregroundStyle(Palette.textPrimary)
                }
                Spacer()
                Button {
                    searching.toggle()
                    if !searching { query = "" }
                } label: {
                    Image(systemName: searching ? "xmark" : "magnifyingglass")
                        .font(.system(size: 18, weight: .medium))
                        .foregroundStyle(Palette.textPrimary)
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel(searching ? "Cancel" : "Search settings")
            }
            .padding(.leading, 24)
            .padding(.trailing, 12)
            .padding(.vertical, 10)
            .background(Palette.cardWhite)

            ScrollView {
                VStack(spacing: 20) {
                    if query.isEmpty {
                        premiumBanner
                    }
                    let visible = sections
                    ForEach(visible, id: \.0) { title, entries in
                        VStack(alignment: .leading, spacing: 8) {
                            Text(title)
                                .sectionLabelStyle(color: Palette.textSecondary, size: 13, tracking: 0.5)
                                .padding(.leading, 4)
                            VStack(spacing: 0) {
                                ForEach(Array(entries.enumerated()), id: \.element.id) { index, entry in
                                    Button { (entry.action ?? { toast = String(localized: "Coming soon") })() } label: { row(entry) }
                                        .buttonStyle(.plain)
                                    if index < entries.count - 1 { RowDivider(leading: 66) }
                                }
                            }
                            .background(RoundedRectangle(cornerRadius: 16).fill(Palette.cardWhite))
                        }
                    }
                    if visible.isEmpty {
                        Text("No matching settings")
                            .foregroundStyle(Palette.textSecondary)
                            .padding(.vertical, 48)
                    }
                    Text("YeyoFone • Version \(AppInfo.version)")
                        .font(.system(size: 12))
                        .foregroundStyle(Palette.inactiveGray)
                        .padding(.vertical, 10)
                }
                .padding(.horizontal, 24)
                .padding(.top, 16)
                .padding(.bottom, 32)
            }
        }
        .background(Palette.backgroundGray)
        .toast($toast)
    }

    private var premiumBanner: some View {
        Button { toast = String(localized: "Coming soon") } label: {
            HStack(spacing: 14) {
                Image(systemName: "star.fill")
                    .font(.system(size: 20))
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(.white.opacity(0.3)))
                VStack(alignment: .leading, spacing: 2) {
                    Text("Premium Features")
                        .font(.system(size: 16, weight: .bold))
                    Text("Unlock HD calls, unlimited recording & more")
                        .font(.system(size: 12))
                        .opacity(0.9)
                }
                .foregroundStyle(.white)
                Spacer()
            }
            .padding(18)
            .background(
                RoundedRectangle(cornerRadius: 20)
                    .fill(LinearGradient(colors: [Palette.cardGradientStart, Palette.cardGradientEnd], startPoint: .topLeading, endPoint: .bottomTrailing))
            )
            .shadow(color: Palette.accentBlue.opacity(0.2), radius: 8, y: 4)
        }
        .buttonStyle(PressScaleStyle(scale: 0.98))
    }

    private func row(_ entry: SettingEntry) -> some View {
        HStack(spacing: 14) {
            IconTile(icon: entry.icon, tint: entry.color, size: 36, corner: 10)
            VStack(alignment: .leading, spacing: 2) {
                Text(entry.title)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Palette.textPrimary)
                Text(entry.subtitle)
                    .font(.system(size: 13))
                    .foregroundStyle(Palette.textSecondary)
            }
            Spacer(minLength: 8)
            if let badge = entry.badge {
                Text(badge.uppercased())
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(Palette.accentGreen)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(RoundedRectangle(cornerRadius: 8).fill(Palette.successLight))
            }
            Chevron()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .contentShape(Rectangle())
    }
}

// MARK: Audio

struct AudioSettingsScreen: View {
    @Environment(AppStore.self) private var store
    @AppStorage("speakerVolume") private var volume = 0.7
    @AppStorage("ringtone") private var ringtone = "Default"
    @State private var showDevices = false
    @State private var showRingtones = false

    var body: some View {
        VStack(spacing: 0) {
            BackHeader(title: "Audio") { store.screen = .settings }
            ScrollView {
                VStack(spacing: 20) {
                    GroupSection(title: "Output") {
                        HStack(spacing: 16) {
                            IconTile(icon: "speaker.wave.2.fill", tint: Palette.accentBlue, background: Palette.primaryLight)
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Speaker volume")
                                    .font(.system(size: 15, weight: .semibold))
                                    .foregroundStyle(Palette.textPrimary)
                                HStack(spacing: 8) {
                                    Image(systemName: "speaker.fill").font(.system(size: 13))
                                    Slider(value: $volume).tint(Palette.accentBlue)
                                    Image(systemName: "speaker.wave.3.fill").font(.system(size: 13))
                                }
                                .foregroundStyle(Palette.textSecondary)
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                        if store.availableRoutes.count > 1 {
                            RowDivider(leading: 76)
                            Button { showDevices = true } label: {
                                IconRow(icon: "music.note", tint: Palette.accentBlue, tileBackground: Palette.primaryLight,
                                        title: "Output device", subtitle: store.selectedRoute.label)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    GroupSection(title: "Ringtones") {
                        Button { showRingtones = true } label: {
                            IconRow(icon: "music.note", tint: Palette.accentBlue, tileBackground: Palette.primaryLight,
                                    title: "Ringtone", subtitle: Ringtone.label(ringtone))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 24)
                .padding(.bottom, 32)
            }
        }
        .background(Palette.backgroundGray)
        .confirmationDialog("Output device", isPresented: $showDevices, titleVisibility: .visible) {
            ForEach(store.availableRoutes) { route in
                Button(route == store.selectedRoute ? "\(route.label) ✓" : route.label) { store.selectedRoute = route }
            }
        }
        .confirmationDialog("Ringtone", isPresented: $showRingtones, titleVisibility: .visible) {
            ForEach(Ringtone.all, id: \.self) { name in
                Button(name == ringtone ? "\(Ringtone.label(name)) ✓" : Ringtone.label(name)) { ringtone = name }
            }
        }
    }
}

/// Ringtone choices are stored by English name and shown in the current language.
private enum Ringtone {
    static let all = ["Default", "Chime", "Silent"]

    static func label(_ name: String) -> String {
        switch name {
        case "Chime": String(localized: "Chime")
        case "Silent": String(localized: "Silent")
        default: String(localized: "Default")
        }
    }
}

// MARK: Video

struct VideoSettingsScreen: View {
    @Environment(AppStore.self) private var store
    @State private var frontCamera = true
    @State private var resolution = 1
    @State private var layout = 0
    private let resolutions = ["480p", "720p HD", "1080p Full HD", "4K Ultra HD"]
    private let layouts = [
        (String(localized: "Speaker view"), String(localized: "Active speaker fills screen")),
        (String(localized: "Grid view"), String(localized: "All participants equal size")),
        (String(localized: "Gallery view"), String(localized: "Horizontal scrolling")),
    ]

    var body: some View {
        VStack(spacing: 0) {
            BackHeader(title: "Video") { store.screen = .settings }
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Camera preview").sectionLabelStyle().padding(.leading, 4)
                        ZStack {
                            LinearGradient(colors: [Palette.cardGradientStart, Palette.cardGradientEnd], startPoint: .topLeading, endPoint: .bottomTrailing)
                            RadialGradient(colors: [.white.opacity(0.1), .clear], center: .center, startRadius: 0, endRadius: 180)
                            Image(systemName: "video.fill")
                                .font(.system(size: 40))
                                .foregroundStyle(.white.opacity(0.2))
                        }
                        .frame(height: 200)
                        .clipShape(RoundedRectangle(cornerRadius: 20))
                        .overlay(alignment: .bottomTrailing) {
                            Button { frontCamera.toggle() } label: {
                                Image(systemName: "arrow.triangle.2.circlepath.camera.fill")
                                    .foregroundStyle(.white)
                                    .frame(width: 40, height: 40)
                                    .background(Circle().fill(.white.opacity(0.2)))
                            }
                            .padding(12)
                            .accessibilityLabel("Flip camera")
                        }
                    }
                    GroupSection(title: "Camera") {
                        Button { frontCamera.toggle() } label: {
                            IconRow(icon: "video.fill", tint: Palette.accentOrange, tileBackground: Palette.orangeLight,
                                    title: "Camera device", subtitle: frontCamera ? String(localized: "Front camera") : String(localized: "Rear camera")) {
                                Image(systemName: "arrow.triangle.2.circlepath.camera").foregroundStyle(Palette.inactiveGray)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                    GroupSection(title: "Resolution") {
                        FlowLayout(spacing: 8) {
                            ForEach(resolutions.indices, id: \.self) { index in
                                let active = resolution == index
                                Button { resolution = index } label: {
                                    Text(resolutions[index])
                                        .font(.system(size: 13, weight: .semibold))
                                        .foregroundStyle(active ? Palette.accentBlue : Palette.textSecondary)
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 9)
                                        .background(Capsule().fill(active ? Palette.primaryLight : Palette.backgroundGray))
                                        .overlay(Capsule().stroke(active ? Palette.accentBlue : Palette.borderLight, lineWidth: 1))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(16)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    GroupSection(title: "Layout") {
                        ForEach(layouts.indices, id: \.self) { index in
                            if index > 0 { RowDivider(leading: 20) }
                            Button { layout = index } label: {
                                HStack(spacing: 12) {
                                    RadioDot(selected: layout == index)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(layouts[index].0)
                                            .font(.system(size: 15, weight: .medium))
                                            .foregroundStyle(Palette.textPrimary)
                                        Text(layouts[index].1)
                                            .font(.system(size: 12))
                                            .foregroundStyle(Palette.inactiveGray)
                                    }
                                    Spacer()
                                }
                                .padding(.horizontal, 20)
                                .padding(.vertical, 14)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 24)
                .padding(.bottom, 32)
            }
        }
        .background(Palette.backgroundGray)
    }
}

private struct RadioDot: View {
    let selected: Bool

    var body: some View {
        Circle()
            .fill(selected ? Palette.accentBlue : .clear)
            .overlay(Circle().stroke(selected ? Palette.accentBlue : Color(hex: 0xCBD5E1), lineWidth: 2))
            .overlay { if selected { Circle().fill(.white).frame(width: 8, height: 8) } }
            .frame(width: 20, height: 20)
    }
}

/// Wrapping row layout, standing in for Compose's FlowRow.
private struct FlowLayout: Layout {
    var spacing: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = arrange(subviews, width: proposal.width ?? .infinity)
        let height = rows.map(\.height).reduce(0, +) + spacing * CGFloat(max(rows.count - 1, 0))
        return CGSize(width: proposal.width ?? rows.map(\.width).max() ?? 0, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in arrange(subviews, width: bounds.width) {
            var x = bounds.minX
            for index in row.indices {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
                x += size.width + spacing
            }
            y += row.height + spacing
        }
    }

    private func arrange(_ subviews: Subviews, width: CGFloat) -> [(indices: [Int], width: CGFloat, height: CGFloat)] {
        var rows: [(indices: [Int], width: CGFloat, height: CGFloat)] = []
        var current: (indices: [Int], width: CGFloat, height: CGFloat) = ([], 0, 0)
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.unspecified)
            let needed = current.indices.isEmpty ? size.width : current.width + spacing + size.width
            if needed > width, !current.indices.isEmpty {
                rows.append(current)
                current = ([index], size.width, size.height)
            } else {
                current = (current.indices + [index], needed, max(current.height, size.height))
            }
        }
        if !current.indices.isEmpty { rows.append(current) }
        return rows
    }
}

// MARK: Incoming calls

struct IncomingCallsSettingsScreen: View {
    @Environment(AppStore.self) private var store
    @AppStorage("ringtone") private var ringtone = "Default"
    @State private var showForwarding = false

    var body: some View {
        let accountId = store.primaryAccount?.id
        let prefs = Binding(
            get: { accountId.map(store.preferences(for:)) ?? AccountPreferences() },
            set: { value in if let accountId { store.setPreferences(value, for: accountId) } }
        )
        VStack(spacing: 0) {
            BackHeader(title: "Incoming Calls") { store.screen = .settings }
            ScrollView {
                VStack(spacing: 20) {
                    GroupSection(title: "General") {
                        ToggleRow(icon: "bell.fill", tint: Palette.accentBlue, tileBackground: Palette.primaryLight,
                                  title: "Allow Incoming Calls", subtitle: "Receive calls on this device", isOn: prefs.allowIncoming)
                        RowDivider()
                        Button { store.screen = .audioSettings } label: {
                            IconRow(icon: "music.note", tint: Palette.accentOrange, tileBackground: Palette.orangeLight,
                                    title: "Ringtone", subtitle: Ringtone.label(ringtone))
                        }
                        .buttonStyle(.plain)
                        RowDivider()
                        ToggleRow(icon: "iphone.radiowaves.left.and.right", tint: Palette.purple, tileBackground: Palette.purpleLight,
                                  title: "Vibrate", subtitle: "Vibrate when receiving calls", isOn: prefs.vibrate)
                    }
                    GroupSection(title: "Call Handling") {
                        ToggleRow(icon: "phone.badge.waveform.fill", tint: Palette.accentBlue, tileBackground: Palette.primaryLight,
                                  title: "Call Waiting", subtitle: "Notify when another call comes in", isOn: prefs.callWaiting)
                        RowDivider()
                        Button { showForwarding = true } label: {
                            IconRow(icon: "phone.arrow.right.fill", tint: Palette.accentBlue, tileBackground: Palette.primaryLight,
                                    title: "Call Forwarding",
                                    subtitle: store.forwardingDestination.map { String(localized: "Forwarding to \($0)") }
                                        ?? String(localized: "Forward calls to another number"))
                        }
                        .buttonStyle(.plain)
                        RowDivider()
                        ToggleRow(icon: "moon.fill", tint: Palette.pink, tileBackground: Palette.pinkLight,
                                  title: "Do Not Disturb", subtitle: "Silence incoming calls and messages", isOn: prefs.doNotDisturb)
                    }
                    GroupSection(title: "Advanced") {
                        ToggleRow(icon: "iphone.gen3", tint: Palette.purple, tileBackground: Palette.purpleLight,
                                  title: "Flip to Mute", subtitle: "Mute ringing by flipping device", isOn: prefs.flipToMute)
                        RowDivider()
                        ToggleRow(icon: "speaker.wave.2.bubble.fill", tint: Palette.accentBlue, tileBackground: Palette.primaryLight,
                                  title: "Call Announce", subtitle: "Speak caller name when ringing", isOn: prefs.announceCaller)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 24)
                .padding(.bottom, 32)
            }
        }
        .background(Palette.backgroundGray)
        .sheet(isPresented: $showForwarding) {
            ForwardingSheet(current: store.forwardingDestination) { store.forwardingDestination = $0 }
        }
    }
}

private struct ForwardingSheet: View {
    let current: String?
    let onSave: (String?) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var enabled = false
    @State private var number = ""
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Toggle("Enable call forwarding", isOn: $enabled).tint(Palette.accentGreen)
                    TextField("Forward to number", text: $number)
                        .keyboardType(.phonePad)
                        .disabled(!enabled)
                } footer: {
                    if let error { Text(error).foregroundStyle(Palette.accentRed) }
                }
            }
            .navigationTitle("Call Forwarding")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        let trimmed = number.trimmingCharacters(in: .whitespaces)
                        let valid = trimmed.count >= 2 && trimmed.allSatisfy { $0.isNumber || $0 == "+" || $0 == "*" || $0 == "#" }
                        if enabled && !valid {
                            error = String(localized: "Enter a valid phone number or extension")
                            return
                        }
                        onSave(enabled ? trimmed : nil)
                        dismiss()
                    }
                    .fontWeight(.semibold)
                }
            }
        }
        .tint(Palette.accentBlue)
        .presentationDetents([.medium])
        .onAppear {
            enabled = current != nil
            number = current ?? ""
        }
    }
}

// MARK: Language

private struct AppLanguage: Identifiable {
    let tag: String
    let label: String
    let flag: String
    var id: String { tag }
}

private var appLanguages: [AppLanguage] {
    [
        AppLanguage(tag: "en-US", label: String(localized: "English (US)"), flag: "🇺🇸"),
        AppLanguage(tag: "en-GB", label: String(localized: "English (UK)"), flag: "🇬🇧"),
        AppLanguage(tag: "es", label: String(localized: "Español"), flag: "🇪🇸"),
        AppLanguage(tag: "fr", label: String(localized: "Français"), flag: "🇫🇷"),
        AppLanguage(tag: "de", label: String(localized: "Deutsch"), flag: "🇩🇪"),
    ]
}

/// The app's language, matching the localization iOS chose for this launch.
private enum AppLanguagePreference {
    static var current: String {
        let preferred = Bundle.main.preferredLocalizations.first ?? "en"
        return preferred == "en" ? "en-US" : preferred
    }

    /// iOS reads this when the app starts, so the new language shows on the next launch.
    static func set(_ tag: String) {
        UserDefaults.standard.set([tag], forKey: "AppleLanguages")
    }
}

struct LanguageSettingsScreen: View {
    @Environment(AppStore.self) private var store
    @State private var languageTag = AppLanguagePreference.current
    @State private var restartNeeded = false
    @AppStorage("autoTranslate") private var autoTranslate = true
    @AppStorage("voiceAccent") private var accentTag = "en-US"
    @State private var expanded = false
    @State private var showAccents = false

    var body: some View {
        let selected = appLanguages.first { $0.tag == languageTag } ?? appLanguages[0]
        VStack(spacing: 0) {
            BackHeader(title: "Language") { store.screen = .settings }
            ScrollView {
                VStack(spacing: 22) {
                    GroupSection(title: "App Language", bordered: true) {
                        Button { withAnimation { expanded.toggle() } } label: {
                            languageRow(selected, trailing: expanded ? "chevron.up" : "chevron.down", tint: Palette.inactiveGray)
                                .padding(.vertical, 2)
                        }
                        .buttonStyle(.plain)
                        if expanded {
                            ForEach(appLanguages) { language in
                                RowDivider(leading: 20)
                                Button {
                                    if language.tag != languageTag {
                                        languageTag = language.tag
                                        AppLanguagePreference.set(language.tag)
                                        restartNeeded = language.tag != AppLanguagePreference.current
                                    }
                                    withAnimation { expanded = false }
                                } label: {
                                    languageRow(language, trailing: language.tag == languageTag ? "checkmark" : nil, tint: Color(hex: 0x22C55E))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    if restartNeeded {
                        Text("Restart YeyoFone to use the new language.")
                            .font(.system(size: 13))
                            .foregroundStyle(Palette.textSecondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 4)
                            .padding(.top, -12)
                    }
                    GroupSection(title: "Voice & Translation", bordered: true) {
                        ToggleRow(icon: "globe", tint: Palette.purple, tileBackground: Palette.purpleLight,
                                  title: "Auto-Translate", subtitle: "Translate incoming messages", isOn: $autoTranslate)
                        RowDivider(leading: 20)
                        Button { showAccents = true } label: {
                            IconRow(icon: "mic.fill", tint: Palette.pink, tileBackground: Palette.pinkLight,
                                    title: "Voice Accent", subtitle: accentLabel)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 24)
                .padding(.bottom, 32)
            }
        }
        .background(Palette.backgroundGray)
        .confirmationDialog("Voice Accent", isPresented: $showAccents, titleVisibility: .visible) {
            ForEach(appLanguages) { language in
                Button(language.tag == accentTag ? "\(language.label) ✓" : language.label) { accentTag = language.tag }
            }
        }
    }

    private var accentLabel: String {
        let american = String(localized: "American English")
        return accentTag == "en-US" ? american : appLanguages.first { $0.tag == accentTag }?.label ?? american
    }

    private func languageRow(_ language: AppLanguage, trailing: String?, tint: Color) -> some View {
        HStack(spacing: 12) {
            Text(language.flag).font(.system(size: 20))
            Text(language.label)
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(Palette.textPrimary)
            Spacer()
            if let trailing {
                Image(systemName: trailing)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(tint)
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 14)
        .contentShape(Rectangle())
    }
}

// MARK: Recordings

struct RecordingsScreen: View {
    @Environment(AppStore.self) private var store
    @State private var query = ""

    var body: some View {
        VStack(spacing: 0) {
            BackHeader(title: "Recordings") { store.screen = .settings }
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    GroupSection(title: "Storage") {
                        VStack(alignment: .leading, spacing: 0) {
                            HStack {
                                Text("Storage Used")
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundStyle(Palette.textPrimary)
                                Spacer()
                                Text(verbatim: "0 B / \(totalStorage)")
                                    .font(.system(size: 13))
                                    .foregroundStyle(Palette.textSecondary)
                            }
                            Capsule().fill(Palette.borderLight).frame(height: 8).padding(.top, 12)
                            Text("\(0) recordings saved")
                                .font(.system(size: 12))
                                .foregroundStyle(Palette.inactiveGray)
                                .padding(.top, 8)
                        }
                        .padding(20)
                    }
                    SearchField(placeholder: "Search recordings…", text: $query, background: Palette.cardWhite)
                        .clipShape(Capsule())
                    Text("Recent Recordings").sectionLabelStyle().padding(.leading, 4)
                    VStack(spacing: 8) {
                        Image(systemName: "mic.fill")
                            .font(.system(size: 28))
                            .foregroundStyle(Palette.inactiveGray)
                        (query.isEmpty ? Text("No recordings yet") : Text("No matches"))
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(Palette.textPrimary)
                        (query.isEmpty ? Text("Saved recordings on this device will appear here.") : Text("Try another name."))
                            .font(.system(size: 13))
                            .foregroundStyle(Palette.textSecondary)
                    }
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 36)
                    .background(RoundedRectangle(cornerRadius: 20).fill(Palette.cardWhite))
                }
                .padding(.horizontal, 20)
                .padding(.top, 24)
                .padding(.bottom, 32)
            }
        }
        .background(Palette.backgroundGray)
    }

    private var totalStorage: String {
        let bytes = (try? URL.documentsDirectory.resourceValues(forKeys: [.volumeTotalCapacityKey]).volumeTotalCapacity) ?? 0
        return ByteCountFormatter.string(fromByteCount: Int64(bytes), countStyle: .file)
    }
}
