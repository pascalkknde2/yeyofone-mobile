//
//  MoreSettingsScreens.swift
//  Yeyofone
//
//  Settings › More: Advanced (diagnostics), Social (invite and share) and About (version and licenses).
//

import Network
import SwiftUI
import UIKit

// MARK: Advanced

struct AdvancedSettingsScreen: View {
    @Environment(AppStore.self) private var store
    @State private var network = NetworkMonitor()
    @AppStorage(EngineLog.settingKey) private var debugLog = false
    @State private var logSize: Int64 = 0

    var body: some View {
        VStack(spacing: 0) {
            BackHeader(title: "Advanced") { store.screen = .settings }
            ScrollView {
                VStack(spacing: 20) {
                    GroupSection(title: "Network") {
                        IconRow(icon: network.icon, tint: Palette.accentBlue, tileBackground: Palette.primaryLight,
                                title: "Connection", subtitle: network.label) { EmptyView() }
                    }
                    GroupSection(title: "Accounts") {
                        if store.accounts.isEmpty {
                            InfoRow(title: String(localized: "No accounts"), detail: String(localized: "Add a SIP account in Settings › Accounts"))
                        }
                        ForEach(Array(store.accounts.enumerated()), id: \.element.id) { index, account in
                            let (label, color) = account.status(store.registration[account.id] ?? .notRegistered)
                            if index > 0 { RowDivider(leading: 16) }
                            InfoRow(title: account.displayName, detail: "sip:\(account.username)@\(account.domain)") {
                                StatusBadge(label: label, color: color)
                            }
                        }
                    }
                    GroupSection(title: "Audio codecs") {
                        ForEach(Array(Codec.supported.enumerated()), id: \.offset) { index, codec in
                            if index > 0 { RowDivider(leading: 16) }
                            InfoRow(title: codec.name, detail: codec.detail)
                        }
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        GroupSection(title: "Debugging") {
                            ToggleRow(icon: "ladybug.fill", tint: Palette.accentOrange, tileBackground: Palette.orangeLight,
                                      title: "Debug log", subtitle: "Record calling engine activity", isOn: $debugLog)
                            if logSize > 0 {
                                RowDivider(leading: 72)
                                ShareLink(item: EngineLog.url) {
                                    IconRow(icon: "square.and.arrow.up", tint: Palette.accentBlue, tileBackground: Palette.primaryLight,
                                            title: "Share debug log",
                                            subtitle: logSize.formatted(.byteCount(style: .file)))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        Text("The log can contain phone numbers and server addresses. It stays on this device unless you share it.")
                            .font(.system(size: 12))
                            .foregroundStyle(Palette.textSecondary)
                            .padding(.horizontal, 4)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 24)
                .padding(.bottom, 32)
            }
        }
        .background(Palette.backgroundGray)
        .onChange(of: debugLog) { _, on in
            if on { EngineLog.start() } else { EngineLog.stop() }
            refreshLogSize()
        }
        .task {
            // The log grows during calls; keep the size current while the page is open.
            while !Task.isCancelled {
                refreshLogSize()
                try? await Task.sleep(for: .seconds(2))
            }
        }
    }

    private func refreshLogSize() {
        let attributes = try? FileManager.default.attributesOfItem(atPath: EngineLog.url.path())
        logSize = attributes?[.size] as? Int64 ?? 0
    }
}

/// The audio codecs compiled into the calling engine (see tools/build-pjsip-ios.sh).
private struct Codec {
    let name: String
    let detail: String

    static let supported = [
        Codec(name: "G.722", detail: String(localized: "Wideband, 16 kHz")),
        Codec(name: "G.711 μ-law (PCMU)", detail: String(localized: "Narrowband, 8 kHz")),
        Codec(name: "G.711 A-law (PCMA)", detail: String(localized: "Narrowband, 8 kHz")),
    ]
}

/// Current connection type, from the system's network path.
@Observable
private final class NetworkMonitor {
    private(set) var path: NWPath?
    @ObservationIgnored private let monitor = NWPathMonitor()

    init() {
        monitor.pathUpdateHandler = { [weak self] path in
            MainActor.assumeIsolated { self?.path = path }
        }
        monitor.start(queue: .main)
    }

    deinit { monitor.cancel() }

    var label: String {
        guard let path, path.status == .satisfied else { return String(localized: "Offline") }
        if path.usesInterfaceType(.wifi) { return String(localized: "Wi-Fi") }
        if path.usesInterfaceType(.cellular) { return String(localized: "Mobile data") }
        if path.usesInterfaceType(.wiredEthernet) { return String(localized: "Ethernet") }
        return String(localized: "Connected")
    }

    var icon: String {
        guard let path, path.status == .satisfied else { return "wifi.slash" }
        return path.usesInterfaceType(.cellular) ? "antenna.radiowaves.left.and.right" : "wifi"
    }
}

private struct StatusBadge: View {
    let label: String
    let color: Color

    var body: some View {
        HStack(spacing: 6) {
            Circle().fill(color).frame(width: 8, height: 8)
            Text(label)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(color)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(Capsule().fill(color.opacity(0.12)))
    }
}

/// Title and detail text with an optional trailing view; for values that aren't localized keys.
private struct InfoRow<Trailing: View>: View {
    let title: String
    let detail: String
    @ViewBuilder var trailing: Trailing

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: title)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Palette.textPrimary)
                Text(verbatim: detail)
                    .font(.system(size: 13))
                    .foregroundStyle(Palette.textSecondary)
                    .lineLimit(1)
                    .truncationMode(.middle)
            }
            Spacer(minLength: 8)
            trailing
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
    }
}

extension InfoRow where Trailing == EmptyView {
    init(title: String, detail: String) {
        self.init(title: title, detail: detail) { EmptyView() }
    }
}

// MARK: Social

struct SocialScreen: View {
    @Environment(AppStore.self) private var store

    var body: some View {
        VStack(spacing: 0) {
            BackHeader(title: "Social") { store.screen = .settings }
            ScrollView {
                VStack(spacing: 20) {
                    GroupSection(title: "Share") {
                        ShareLink(item: inviteMessage) {
                            IconRow(icon: "person.badge.plus", tint: Palette.accentGreen, tileBackground: Palette.successLight,
                                    title: "Invite a friend", subtitle: String(localized: "Tell people how to reach you on YeyoFone"))
                        }
                        .buttonStyle(.plain)
                        RowDivider()
                        if let card = contactCard {
                            ShareLink(item: card) {
                                IconRow(icon: "person.crop.rectangle", tint: Palette.accentBlue, tileBackground: Palette.primaryLight,
                                        title: "Share my contact", subtitle: String(localized: "Your name, extension and SIP address"))
                            }
                            .buttonStyle(.plain)
                        } else {
                            IconRow(icon: "person.crop.rectangle", tint: Palette.inactiveGray, tileBackground: Palette.backgroundGray,
                                    title: "Share my contact", subtitle: String(localized: "Add a SIP account first")) { EmptyView() }
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

    private var inviteMessage: String {
        if let account = store.primaryAccount {
            String(localized: "I'm using YeyoFone for calls. You can reach me on extension \(account.username).")
        } else {
            String(localized: "I'm using YeyoFone for calls.")
        }
    }

    private var contactCard: String? {
        store.primaryAccount.map { account in
            [account.displayName, String(localized: "Extension \(account.username)"), "sip:\(account.username)@\(account.domain)"]
                .joined(separator: "\n")
        }
    }
}

// MARK: About

struct AboutScreen: View {
    @Environment(AppStore.self) private var store

    var body: some View {
        VStack(spacing: 0) {
            BackHeader(title: "About") { store.screen = .settings }
            ScrollView {
                VStack(spacing: 20) {
                    VStack(spacing: 10) {
                        Image(systemName: "phone.fill")
                            .font(.system(size: 34, weight: .semibold))
                            .foregroundStyle(.white)
                            .frame(width: 76, height: 76)
                            .background(RoundedRectangle(cornerRadius: 20).fill(Palette.accentBlue))
                        Text(verbatim: "YeyoFone")
                            .font(.system(size: 22, weight: .bold))
                            .foregroundStyle(Palette.textPrimary)
                        Text("Version \(AppInfo.version) (\(AppInfo.build))")
                            .font(.system(size: 14))
                            .foregroundStyle(Palette.textSecondary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)

                    GroupSection(title: "App") {
                        ValueRow(title: "Version", value: AppInfo.version)
                        RowDivider(leading: 16)
                        ValueRow(title: "Build", value: AppInfo.build)
                        RowDivider(leading: 16)
                        ValueRow(title: "iOS", value: UIDevice.current.systemVersion)
                    }
                    GroupSection(title: "Legal") {
                        Button { store.screen = .licenses } label: {
                            IconRow(icon: "doc.text.fill", tint: Palette.accentBlue, tileBackground: Palette.primaryLight,
                                    title: "Open-source licenses", subtitle: String(localized: "Software YeyoFone is built with"))
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
    }
}

private struct ValueRow: View {
    let title: LocalizedStringKey
    let value: String

    var body: some View {
        HStack {
            Text(title)
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(Palette.textPrimary)
            Spacer()
            Text(verbatim: value)
                .font(.system(size: 15))
                .foregroundStyle(Palette.textSecondary)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
    }
}

struct LicensesScreen: View {
    @Environment(AppStore.self) private var store

    var body: some View {
        VStack(spacing: 0) {
            BackHeader(title: "Open-source licenses") { store.screen = .about }
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    GroupSection(title: "Calling engine") {
                        Link(destination: URL(string: "https://www.pjsip.org")!) {
                            VStack(alignment: .leading, spacing: 4) {
                                HStack {
                                    Text(verbatim: "PJSIP")
                                        .font(.system(size: 15, weight: .semibold))
                                        .foregroundStyle(Palette.textPrimary)
                                    Spacer()
                                    Text(verbatim: "GNU GPL v2")
                                        .font(.system(size: 13, weight: .medium))
                                        .foregroundStyle(Palette.accentBlue)
                                }
                                Text("SIP signalling and audio. Includes third-party components under their own licenses, listed at pjsip.org.")
                                    .font(.system(size: 13))
                                    .foregroundStyle(Palette.textSecondary)
                                    .multilineTextAlignment(.leading)
                            }
                            .padding(16)
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
