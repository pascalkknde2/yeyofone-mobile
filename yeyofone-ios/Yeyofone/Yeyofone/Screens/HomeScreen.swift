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
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                header(name: account?.displayName ?? "YeyoFone", online: store.isPrimaryRegistered)

                if let destination = store.forwardingDestination {
                    ForwardingBanner(accountNumber: account?.username, destination: destination)
                        .padding(.horizontal, 20)
                }

                AccountStatusCard(account: account) {
                    if let account { store.screen = .accountDetail(account.id) } else { store.screen = .accountEditor(nil) }
                }
                .padding(.horizontal, 20)

                HStack(spacing: 12) {
                    QuickAction(icon: "circle.grid.3x3.fill", label: "Keypad", color: Palette.accentBlue) { store.select(.keypad) }
                    QuickAction(icon: "person.fill", label: "Contacts", color: Palette.accentGreen) { store.select(.contacts) }
                    QuickAction(icon: "clock.arrow.circlepath", label: "Recent", color: Palette.accentOrange) { store.select(.calls) }
                }
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

    private func header(name: String, online: Bool) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("Welcome back")
                    .font(.system(size: 12, weight: .semibold))
                    .tracking(0.5)
                    .textCase(.uppercase)
                    .foregroundStyle(Palette.textSecondary)
                Text(name)
                    .font(.system(size: 26, weight: .bold))
                    .tracking(-0.5)
                    .foregroundStyle(Palette.textPrimary)
                    .lineLimit(1)
            }
            Spacer(minLength: 16)
            InitialsAvatar(name: name, size: 44, fontSize: 18, foreground: Palette.accentBlue)
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

    private var favoritesStrip: some View {
        ScrollView(.horizontal) {
            HStack(alignment: .top, spacing: 16) {
                ForEach(store.contacts.filter(\.favorite)) { contact in
                    Button { store.startCall(to: contact.number) } label: {
                        VStack(spacing: 7) {
                            InitialsAvatar(name: contact.displayName, size: 56, fontSize: 20, foreground: Palette.accentBlue)
                            Text(contact.displayName)
                                .font(.system(size: 12, weight: .medium))
                                .foregroundStyle(Palette.textSecondary)
                                .lineLimit(1)
                        }
                        .frame(width: 72)
                    }
                    .buttonStyle(PressScaleStyle())
                }
                Button { store.select(.contacts) } label: {
                    VStack(spacing: 7) {
                        Image(systemName: "plus")
                            .font(.system(size: 18))
                            .foregroundStyle(Palette.textSecondary)
                            .frame(width: 56, height: 56)
                            .background(Circle().fill(Palette.backgroundGray))
                            .overlay(Circle().stroke(Palette.borderLight, lineWidth: 1))
                        Text("Add")
                            .font(.system(size: 12))
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
            VStack(spacing: 0) {
                ForEach(Array(calls.enumerated()), id: \.element.id) { index, call in
                    RecentCallRow(call: call) { store.startCall(to: call.number) }
                    if index < calls.count - 1 {
                        Rectangle().fill(Palette.borderLight.opacity(0.5)).frame(height: 1)
                    }
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
                .font(.system(size: 15))
            Text(accountNumber.map { "Calls to \($0) are being forwarded to \(destination)" } ?? "Forwarding to \(destination)")
                .font(.system(size: 13, weight: .medium))
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
    let onManage: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("Primary account")
                    .font(.system(size: 11, weight: .semibold))
                    .tracking(1.5)
                    .textCase(.uppercase)
                    .foregroundStyle(.white.opacity(0.6))
                Spacer()
                // SIM-style chip.
                RoundedRectangle(cornerRadius: 4)
                    .fill(LinearGradient(colors: [Color(hex: 0xFBBF24), Color(hex: 0xD97706)], startPoint: .topLeading, endPoint: .bottomTrailing))
                    .frame(width: 32, height: 24)
                    .overlay(RoundedRectangle(cornerRadius: 2).stroke(.black.opacity(0.2), lineWidth: 1).frame(width: 20, height: 16))
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(account?.displayName ?? "No SIP accounts configured")
                    .font(.system(size: 22, weight: .semibold))
                    .tracking(-0.5)
                    .foregroundStyle(.white)
                    .lineLimit(2)
                if let account {
                    Text("Extension \(account.username)")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(.white.opacity(0.7))
                }
            }
            .padding(.vertical, 20)
            Rectangle().fill(.white.opacity(0.1)).frame(height: 1)
            Button(action: onManage) {
                HStack(spacing: 4) {
                    Text(account == nil ? "Add account" : "Manage Account")
                        .font(.system(size: 13))
                    Image(systemName: "chevron.right")
                        .font(.system(size: 11, weight: .semibold))
                }
                .foregroundStyle(.white.opacity(0.7))
                .padding(.top, 14)
            }
        }
        .padding(24)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background {
            ZStack(alignment: .topTrailing) {
                LinearGradient(colors: [Palette.cardGradientStart, Palette.cardGradientEnd], startPoint: .topLeading, endPoint: .bottomTrailing)
                RadialGradient(colors: [Palette.cardAccent.opacity(0.22), .clear], center: .topTrailing, startRadius: 0, endRadius: 150)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 24))
        .shadow(color: .black.opacity(0.18), radius: 12, y: 6)
    }
}

private struct QuickAction: View {
    let icon: String
    let label: String
    let color: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 10) {
                Image(systemName: icon)
                    .font(.system(size: 24))
                    .foregroundStyle(color)
                    .frame(width: 60, height: 60)
                    .background(Circle().fill(color.opacity(0.13)))
                Text(label)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Palette.textPrimary)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
        }
        .buttonStyle(PressScaleStyle())
    }
}

private struct SectionHeader: View {
    let title: String
    let action: String
    let onAction: () -> Void

    var body: some View {
        HStack {
            Text(title)
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(Palette.textPrimary)
            Spacer()
            Button(action, action: onAction)
                .font(.system(size: 13, weight: .semibold))
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
        HStack(spacing: 12) {
            InitialsAvatar(
                name: call.contactName, size: 44, fontSize: 15,
                background: missed ? Palette.dangerLight : Palette.backgroundGray,
                foreground: missed ? Palette.accentRed : Palette.textSecondary
            )
            VStack(alignment: .leading, spacing: 4) {
                Text(call.contactName)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Palette.textPrimary)
                    .lineLimit(1)
                HStack(spacing: 4) {
                    Image(systemName: call.callType.arrowIcon)
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(missed ? Palette.accentRed : Palette.accentGreen)
                    Text("\(call.callType.label) • \(call.timestamp.formatted(.dateTime.hour(.twoDigits(amPM: .omitted)).minute(.twoDigits)))")
                        .font(.system(size: 13))
                        .foregroundStyle(missed ? Palette.accentRed : Palette.textSecondary)
                }
            }
            Spacer(minLength: 0)
            Button(action: onCall) {
                Image(systemName: "phone.fill")
                    .font(.system(size: 16))
                    .foregroundStyle(Palette.accentGreen)
                    .frame(width: 48, height: 48)
                    .background(Circle().fill(Palette.successLight))
            }
            .buttonStyle(PressScaleStyle())
            .accessibilityLabel("Call back")
        }
        .padding(.vertical, 12)
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
