//
//  HomeScreen.swift
//  Yeyofone
//
//  Port of yeyofone-android ui/main/MainScreen.kt.
//

import SwiftUI

struct HomeScreen: View {
    @Environment(AppStore.self) private var store

    var body: some View {
        let account = store.primaryAccount
        let dnd = account.map { store.preferences(for: $0.id).doNotDisturb } ?? false
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                header(name: account?.displayName ?? "YeyoFone", extensionNumber: account?.username, online: store.isPrimaryRegistered, doNotDisturb: dnd)

                if let destination = store.forwardingDestination {
                    ForwardingBanner(accountNumber: account?.username, destination: destination)
                        .padding(.horizontal, 20)
                }

                AccountStatusCard(
                    account: account,
                    doNotDisturb: dnd,
                    onToggleDoNotDisturb: {
                        guard let account else { return }
                        var prefs = store.preferences(for: account.id)
                        prefs.doNotDisturb.toggle()
                        store.setPreferences(prefs, for: account.id)
                    }
                ) {
                    if let account { store.screen = .accountDetail(account.id) } else { store.screen = .accountEditor(nil) }
                }
                .padding(.horizontal, 20)

                SearchBar { store.select(.keypad) }
                    .padding(.horizontal, 20)

                VStack(alignment: .leading, spacing: 8) {
                    SectionHeader(title: "Favorites", action: "Edit") { store.select(.contacts) }
                    favoritesStrip
                }

                SectionHeader(title: "Recent calls", action: "See All") { store.select(.calls) }
                recentCalls
            }
            .padding(.bottom, 32)
        }
        .scrollIndicators(.hidden)
        .background(Palette.cardWhite)
    }

    private func header(name: String, extensionNumber: String?, online: Bool, doNotDisturb: Bool) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(name)
                    .appFont(size: 26, weight: .bold)
                    .tracking(-0.5)
                    .foregroundStyle(Palette.textPrimary)
                    .lineLimit(1)
                HStack(spacing: 6) {
                    Circle()
                        .fill(doNotDisturb ? Palette.accentRed : online ? Palette.accentGreen : Palette.inactiveGray)
                        .frame(width: 8, height: 8)
                    let status = doNotDisturb ? String(localized: "Do Not Disturb") : online ? String(localized: "Available") : String(localized: "Offline")
                    Text(verbatim: extensionNumber.map { "\(status) • Ext \($0)" } ?? status)
                        .appFont(size: 14, weight: .medium)
                        .foregroundStyle(Palette.textSecondary)
                        .lineLimit(1)
                }
            }
            Spacer(minLength: 16)
            InitialsAvatar(name: name, size: 52, fontSize: 16, background: Palette.cardWhite, foreground: Palette.accentBlue)
                .overlay(Circle().stroke(Palette.borderLight, lineWidth: 2))
                .overlay(alignment: .bottomTrailing) {
                    Circle()
                        .fill(online ? Palette.accentGreen : Palette.inactiveGray)
                        .frame(width: 12, height: 12)
                        .overlay(Circle().stroke(Palette.cardWhite, lineWidth: 2))
                }
        }
        .padding(.horizontal, 20)
        .padding(.top, 16)
    }

    private static let favoriteTints: [(foreground: Color, background: Color)] = [
        (Palette.accentBlue, Palette.primaryLight),
        (Palette.pink, Palette.pinkLight),
        (Palette.accentOrange, Palette.orangeLight),
        (Palette.accentGreen, Palette.successLight),
    ]

    private var favoritesStrip: some View {
        ScrollView(.horizontal) {
            HStack(alignment: .top, spacing: 16) {
                ForEach(Array(store.contacts.filter(\.favorite).enumerated()), id: \.element.id) { index, contact in
                    let tint = Self.favoriteTints[index % Self.favoriteTints.count]
                    Button { store.startCall(to: contact.number) } label: {
                        VStack(spacing: 7) {
                            InitialsAvatar(name: contact.displayName, size: 64, fontSize: 18, background: tint.background, foreground: tint.foreground)
                            Text(contact.displayName)
                                .appFont(size: 12, weight: .semibold)
                                .foregroundStyle(Palette.textPrimary)
                                .lineLimit(1)
                        }
                        .frame(width: 72)
                    }
                    .buttonStyle(PressScaleStyle())
                }
                Button { store.select(.contacts) } label: {
                    VStack(spacing: 7) {
                        Image(systemName: "plus")
                            .appFont(size: 18)
                            .foregroundStyle(Palette.textSecondary)
                            .frame(width: 64, height: 64)
                            .overlay(Circle().stroke(Palette.borderLight, lineWidth: 1.5))
                        Text("Add")
                            .appFont(size: 12)
                            .foregroundStyle(Palette.textSecondary)
                    }
                }
                .buttonStyle(PressScaleStyle())
            }
            .padding(.horizontal, 20)
        }
        .scrollIndicators(.hidden)
    }

    @ViewBuilder
    private var recentCalls: some View {
        let calls = Array(store.history.prefix(3))
        if calls.isEmpty {
            Text("No recent calls")
                .foregroundStyle(Palette.textSecondary)
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
        } else {
            VStack(spacing: 10) {
                ForEach(calls) { call in
                    RecentCallRow(call: call) { store.startCall(to: call.number) }
                }
            }
            .padding(.horizontal, 20)
        }
    }
}

private struct ForwardingBanner: View {
    let accountNumber: String?
    let destination: String

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "phone.arrow.right.fill")
                .appFont(size: 15)
            Text(accountNumber.map { String(localized: "Calls to \($0) are being forwarded to \(destination)") }
                 ?? String(localized: "Forwarding to \(destination)"))
                .appFont(size: 13, weight: .medium)
            Spacer(minLength: 0)
        }
        .foregroundStyle(Palette.accentBlue)
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(RoundedRectangle(cornerRadius: 14).fill(Palette.primaryLight))
    }
}

private struct AccountStatusCard: View {
    let account: SipAccount?
    let doNotDisturb: Bool
    let onToggleDoNotDisturb: () -> Void
    let onManage: () -> Void

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text("Primary account")
                    .appFont(size: 11, weight: .bold)
                    .tracking(1)
                    .textCase(.uppercase)
                    .foregroundStyle(.white.opacity(0.75))
                (account.map { Text("Extension \($0.username)") } ?? Text("Add account"))
                    .appFont(size: 18, weight: .bold)
                    .foregroundStyle(.white)
                    .lineLimit(1)
            }
            Spacer(minLength: 12)
            if account != nil {
                Button(action: onToggleDoNotDisturb) {
                    HStack(spacing: 8) {
                        Image(systemName: "power").appFont(size: 14, weight: .semibold)
                        Text("DND").appFont(size: 13, weight: .semibold)
                    }
                    .foregroundStyle(doNotDisturb ? Palette.accentRed : .white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(Capsule().fill(doNotDisturb ? Color.white : Color.white.opacity(0.1)))
                    .overlay(Capsule().stroke(.white.opacity(0.15), lineWidth: 1))
                }
                .buttonStyle(PressScaleStyle())
            }
        }
        .padding(.horizontal, 22)
        .padding(.vertical, 18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(LinearGradient(colors: [Color(hex: 0x3B82F6), Color(hex: 0x1D4ED8)], startPoint: .topLeading, endPoint: .bottomTrailing))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .shadow(color: Palette.accentBlue.opacity(0.35), radius: 12, y: 6)
        .contentShape(RoundedRectangle(cornerRadius: 12))
        .onTapGesture(perform: onManage)
    }
}

private struct SearchBar: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: "magnifyingglass")
                    .appFont(size: 18, weight: .semibold)
                    .foregroundStyle(Palette.accentBlue)
                Text("Search or dial a number…")
                    .appFont(size: 16, weight: .medium)
                    .foregroundStyle(Palette.inactiveGray)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 16)
            .background(RoundedRectangle(cornerRadius: 20).fill(Palette.cardWhite))
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(Palette.borderLight, lineWidth: 1))
            .shadow(color: .black.opacity(0.08), radius: 6, y: 3)
        }
        .buttonStyle(PressScaleStyle())
    }
}

private struct SectionHeader: View {
    let title: LocalizedStringKey
    let action: LocalizedStringKey
    let onAction: () -> Void

    var body: some View {
        HStack {
            Text(title)
                .appFont(size: 16, weight: .bold)
                .foregroundStyle(Palette.textPrimary)
            Spacer()
            Button(action, action: onAction)
                .appFont(size: 13, weight: .semibold)
                .foregroundStyle(Palette.accentBlue)
        }
        .padding(.horizontal, 20)
    }
}

private struct RecentCallRow: View {
    let call: CallLog
    let onCall: () -> Void

    var body: some View {
        let missed = call.callType == .missed
        let tint = missed ? Palette.accentRed : Palette.accentGreen
        Button(action: onCall) {
            HStack(spacing: 14) {
                Image(systemName: call.callType.arrowIcon)
                    .appFont(size: 18, weight: .bold)
                    .foregroundStyle(tint)
                    .frame(width: 46, height: 46)
                    .background(Circle().fill(missed ? Palette.dangerLight : Palette.successLight))
                VStack(alignment: .leading, spacing: 4) {
                    Text(call.contactName)
                        .appFont(size: 15, weight: .bold)
                        .foregroundStyle(Palette.textPrimary)
                        .lineLimit(1)
                    Text(call.callType.label)
                        .appFont(size: 13, weight: .medium)
                        .foregroundStyle(missed ? Palette.accentRed : Palette.textSecondary)
                }
                Spacer(minLength: 0)
                Text(verbatim: call.timestamp.formatted(.dateTime.hour(.twoDigits(amPM: .omitted)).minute(.twoDigits)))
                    .appFont(size: 12, weight: .semibold)
                    .foregroundStyle(Palette.textSecondary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(RoundedRectangle(cornerRadius: 10).fill(Palette.backgroundGray))
            }
            .padding(14)
            .background(RoundedRectangle(cornerRadius: 16).fill(Palette.cardWhite))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Palette.borderLight.opacity(0.6), lineWidth: 1))
        }
        .buttonStyle(PressScaleStyle())
        .accessibilityLabel("Call back")
    }
}

extension CallType {
    var arrowIcon: String {
        switch self {
        case .incoming: "arrow.down.left"
        case .outgoing: "arrow.up.right"
        case .missed: "phone.arrow.down.left"
        }
    }
}
