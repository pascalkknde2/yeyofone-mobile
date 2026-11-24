//
//  CallHistoryScreen.swift
//  Yeyofone
//
//  Port of yeyofone-android ui/callhistory/CallHistoryScreen.kt and components/CallHistoryItem.kt.
//

import SwiftUI

struct CallHistoryScreen: View {
    @Environment(AppStore.self) private var store
    @State private var selectedTab = 0
    @State private var confirmClear = false

    private var calls: [CallLog] {
        switch selectedTab {
        case 1: store.history.filter { $0.callType == .missed }
        case 2: store.history.filter(\.hasVoicemail)
        default: store.history
        }
    }

    // Own keys: Android translates the filter tabs differently from the call-type labels (e.g. "Perdidas" vs "Perdida").
    private var filterTitles: [String] {
        [
            String(localized: "filter.all", defaultValue: "All"),
            String(localized: "filter.missed", defaultValue: "Missed"),
            String(localized: "filter.voicemail", defaultValue: "Voicemail"),
        ]
    }

    var body: some View {
        VStack(spacing: 0) {
            VStack(spacing: 16) {
                TabHeader(title: "Recents") {
                    Button { confirmClear = true } label: {
                        Image(systemName: "pencil")
                            .appFont(size: 18, weight: .medium)
                            .foregroundStyle(Palette.textPrimary)
                            .frame(width: 44, height: 44)
                    }
                    .disabled(store.history.isEmpty)
                    .accessibilityLabel("Edit call history")
                }
                ChipTabs(titles: filterTitles, selection: $selectedTab)
                    .padding(.horizontal, 24)
            }
            .padding(.top, 16)
            .padding(.bottom, 16)

            if calls.isEmpty {
                EmptyStateView(title: "No recent calls")
            } else {
                List {
                    Text("TODAY")
                        .appFont(size: 13, weight: .semibold)
                        .foregroundStyle(Palette.textSecondary)
                        .padding(.leading, 4)
                        .listRowStyle()
                    ForEach(calls) { call in
                        CallHistoryItem(call: call) { store.startCall(to: call.number) }
                            .listRowStyle()
                            .swipeActions {
                                Button(role: .destructive) {
                                    store.history.removeAll { $0.id == call.id }
                                } label: { Label("Delete", systemImage: "trash") }
                            }
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
                .contentMargins(.top, 8, for: .scrollContent)
            }
        }
        .alert("Clear all call history?", isPresented: $confirmClear) {
            Button("Clear all", role: .destructive) { store.history.removeAll() }
            Button("Cancel", role: .cancel) {}
        }
    }
}

private extension View {
    func listRowStyle() -> some View {
        listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
            .listRowInsets(EdgeInsets(top: 4, leading: 24, bottom: 4, trailing: 24))
    }
}

struct CallHistoryItem: View {
    let call: CallLog
    let onCall: () -> Void

    var body: some View {
        HStack(spacing: 14) {
            InitialsAvatar(name: call.contactName, size: 46, fontSize: 15, background: Palette.backgroundGray)
                .overlay(alignment: .bottomTrailing) {
                    Image(systemName: call.callType.arrowIcon)
                        .appFont(size: 9, weight: .bold)
                        .foregroundStyle(badgeColor)
                        .frame(width: 18, height: 18)
                        .background(Circle().fill(Palette.glass))
                }
            VStack(alignment: .leading, spacing: 2) {
                Text(call.contactName)
                    .appFont(size: 16, weight: .semibold)
                    .foregroundStyle(call.callType == .missed ? Palette.accentRed : Palette.textPrimary)
                Text(metadata)
                    .appFont(size: 13)
                    .foregroundStyle(Palette.textSecondary)
            }
            Spacer(minLength: 8)
            if call.hasVoicemail {
                roundButton(icon: "recordingtape", tint: Palette.textPrimary, background: Palette.backgroundGray) {}
                    .accessibilityLabel("Play voicemail from \(call.contactName)")
            }
            roundButton(icon: "phone.fill", tint: Palette.accentGreen, background: Palette.accentGreen.opacity(0.1), action: onCall)
                .accessibilityLabel("Call \(call.contactName)")
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 16).fill(Palette.glass))
        .shadow(color: .black.opacity(0.05), radius: 3, y: 1)
        .contentShape(Rectangle())
        .onTapGesture(perform: onCall)
    }

    private var badgeColor: Color {
        switch call.callType {
        case .incoming: Palette.accentGreen
        case .outgoing: Palette.accentBlue
        case .missed: Palette.accentRed
        }
    }

    private var metadata: String {
        let time = call.timestamp.formatted(.dateTime.month(.abbreviated).day().hour().minute())
        guard let duration = call.duration else { return "\(call.callType.label) • \(time)" }
        return "\(call.callType.label) • \(time) • \(DurationFormat.short(duration))"
    }

    private func roundButton(icon: String, tint: Color, background: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .appFont(size: 15)
                .foregroundStyle(tint)
                .frame(width: 36, height: 36)
                .background(Circle().fill(background))
        }
        .buttonStyle(.borderless)
    }
}
