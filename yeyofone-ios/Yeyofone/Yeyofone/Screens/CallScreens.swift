//
//  CallScreens.swift
//  Yeyofone
//
//  Ports of yeyofone-android ui/call (OutgoingCallScreen, CallOptionsScreen, TransferCallScreen),
//  ui/incomingcall/IncomingCallScreen.kt and ui/callended/CallEndedScreen.kt.
//

import SwiftUI

// MARK: Active / outgoing call

struct OutgoingCallScreen: View {
    @Environment(AppStore.self) private var store
    let session: CallSession

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button { store.endCall() } label: {
                    Image(systemName: "arrow.left").frame(width: 48, height: 48)
                }
                .accessibilityLabel("Back")
                .hidden()
                Spacer()
                Button { store.setHeld(!session.held) } label: {
                    Image(systemName: session.held ? "play.fill" : "pause.fill")
                        .foregroundStyle(session.held ? Palette.accentBlue : Palette.textPrimary)
                        .frame(width: 48, height: 48)
                }
                .accessibilityLabel(session.held ? Text("Resume") : Text("Hold"))
            }
            .appFont(size: 18, weight: .semibold)
            .foregroundStyle(Palette.textPrimary)
            .padding(.horizontal, 16)
            .padding(.vertical, 6)

            ScrollView {
                VStack(spacing: 24) {
                    TimelineView(.periodic(from: .now, by: 1)) { context in
                        CallerIdentity(name: session.remoteName, status: status(at: context.date))
                    }
                    if session.recording {
                        HStack(spacing: 8) {
                            Circle().fill(Palette.accentRed).frame(width: 8, height: 8)
                            Text("Recording")
                                .appFont(size: 12, weight: .bold)
                                .foregroundStyle(Palette.accentRed)
                        }
                        .padding(.horizontal, 14).padding(.vertical, 6)
                        .background(Capsule().fill(Palette.accentRed.opacity(0.12)))
                    }
                    if session.assistant {
                        HStack(spacing: 8) {
                            Image(systemName: "sparkles").appFont(size: 11)
                            Text("AI notes on").appFont(size: 12, weight: .bold)
                        }
                        .foregroundStyle(Palette.accentBlue)
                        .padding(.horizontal, 14).padding(.vertical, 6)
                        .background(Capsule().fill(Palette.accentBlue.opacity(0.12)))
                    }
                }
                .padding(.horizontal, 24)
                .padding(.vertical, 16)
                .frame(maxWidth: .infinity)
            }
            .scrollBounceBehavior(.basedOnSize)
            .defaultScrollAnchor(.center)

            CallTray {
                VStack(spacing: 24) {
                    HStack {
                        CallActionButton(icon: "speaker.wave.2.fill", label: "Speaker", active: store.selectedRoute == .speaker) {
                            store.selectedRoute = store.selectedRoute == .speaker ? .earpiece : .speaker
                        }
                        CallActionButton(icon: "circle.grid.3x3.fill", label: "Keypad") {
                            store.callPage = .keypad
                        }
                        CallActionButton(icon: session.muted ? "mic.slash.fill" : "mic.fill", label: "Mute",
                                         active: session.muted, enabled: !session.held) {
                            store.setMuted(!session.muted)
                        }
                    }
                    HStack {
                        CallActionButton(icon: "arrow.left.arrow.right", label: "Transfer", enabled: !session.held) {
                            store.callPage = .transfer
                        }
                        CallActionButton(icon: "phone.down.fill", label: "Hang up", destructive: true) {
                            store.endCall()
                        }
                        CallActionButton(icon: "ellipsis", label: "More", active: session.held) {
                            store.callPage = .options
                        }
                    }
                }
            }
        }
    }

    private func status(at date: Date) -> String {
        if session.held { return CallState.held.label }
        guard let connectedAt = session.connectedAt else { return session.state.label }
        return DurationFormat.clock(date.timeIntervalSince(connectedAt))
    }
}

private struct CallerIdentity: View {
    let name: String
    let status: String
    @State private var pulse = false

    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                Circle()
                    .fill(Palette.accentGreen.opacity(pulse ? 0.1 : 0.3))
                    .frame(width: 136, height: 136)
                    .scaleEffect(pulse ? 1.1 : 1)
                Text(name.initials)
                    .appFont(size: 48, weight: .semibold)
                    .foregroundStyle(Palette.textPrimary)
                    .frame(width: 120, height: 120)
                    .background(Circle().fill(Palette.glass))
                    .overlay(Circle().stroke(Palette.accentGreen.opacity(0.2), lineWidth: 1))
            }
            .frame(width: 150, height: 150)
            .onAppear {
                withAnimation(.easeInOut(duration: 1).repeatForever(autoreverses: true)) { pulse = true }
            }
            Text(name)
                .appFont(size: 32, weight: .bold)
                .tracking(-0.5)
                .foregroundStyle(Palette.textPrimary)
                .multilineTextAlignment(.center)
                .padding(.top, 18)
            HStack(spacing: 8) {
                Circle()
                    .fill(Palette.accentGreen)
                    .frame(width: 8, height: 8)
                    .shadow(color: Palette.accentGreen, radius: 3)
                Text(status)
                    .appFont(size: 16, weight: .medium)
                    .monospacedDigit()
                    .foregroundStyle(Palette.textSecondary)
            }
            .padding(.top, 8)
        }
    }
}

private struct CallActionButton: View {
    let icon: String
    let label: LocalizedStringKey
    var active = false
    var destructive = false
    var enabled = true
    let action: () -> Void

    var body: some View {
        let background: Color = destructive ? Palette.accentRed : (active ? Palette.accentBlue : Palette.glass)
        let border: Color = destructive ? Palette.accentRed : (active ? Palette.accentBlue : Palette.borderLight)
        let tint: Color = destructive || active ? .white : Palette.textPrimary
        Button(action: action) {
            VStack(spacing: 12) {
                Image(systemName: icon)
                    .appFont(size: 22)
                    .foregroundStyle(tint.opacity(enabled || destructive ? 1 : 0.4))
                    .frame(width: 64, height: 64)
                    .background(Circle().fill(background.opacity(enabled ? 1 : 0.45)))
                    .overlay(Circle().stroke(border, lineWidth: 1))
                    .shadow(color: destructive ? Palette.accentRed.opacity(0.4) : .black.opacity(0.05),
                            radius: destructive ? 10 : 3, y: destructive ? 4 : 1)
                Text(label)
                    .appFont(size: 12, weight: .semibold)
                    .tracking(0.5)
                    .textCase(.uppercase)
                    .foregroundStyle(active ? Palette.accentBlue : Palette.textSecondary)
            }
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(PressScaleStyle())
        .disabled(!enabled)
        .accessibilityAddTraits(active ? .isSelected : [])
    }
}

// MARK: Incoming call

struct IncomingCallScreen: View {
    @Environment(AppStore.self) private var store
    @State private var ring = false

    var body: some View {
        let caller = store.incomingCall
        let name = caller.flatMap { store.contactName(for: $0.remoteNumber) } ?? caller?.remoteName ?? ""
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                Image(systemName: "phone.fill").appFont(size: 13)
                Text("INCOMING CALL")
                    .appFont(size: 13, weight: .bold)
                    .tracking(1)
            }
            .foregroundStyle(Palette.accentGreen)
            .padding(.horizontal, 20)
            .padding(.vertical, 8)
            .background(Capsule().fill(Palette.successLight))
            .overlay(Capsule().stroke(Palette.accentGreen.opacity(0.2), lineWidth: 1))
            .shadow(color: .black.opacity(0.06), radius: 2, y: 1)
            .padding(.vertical, 16)
            .frame(maxWidth: .infinity)

            VStack(spacing: 0) {
                Spacer()
                ZStack {
                    Circle()
                        .fill(Palette.accentGreen.opacity(ring ? 0 : 0.2))
                        .frame(width: 172, height: 172)
                        .scaleEffect(ring ? 1.15 : 1)
                    Text(name.initials)
                        .appFont(size: 56, weight: .bold)
                        .foregroundStyle(Palette.textPrimary)
                        .frame(width: 140, height: 140)
                        .background(Circle().fill(Palette.glass))
                        .overlay(Circle().stroke(Palette.accentBlue.opacity(0.1), lineWidth: 1))
                        .padding(8)
                        .background(Circle().fill(Palette.primaryLight))
                }
                .onAppear {
                    withAnimation(.linear(duration: 2).repeatForever(autoreverses: false)) { ring = true }
                }
                Text(name)
                    .appFont(size: 36, weight: .bold)
                    .tracking(-0.5)
                    .foregroundStyle(Palette.textPrimary)
                    .multilineTextAlignment(.center)
                    .padding(.top, 32)
                Text("Ext. \(caller?.remoteNumber ?? "")")
                    .appFont(size: 18, weight: .medium)
                    .foregroundStyle(Palette.textSecondary)
                    .padding(.top, 8)
                Spacer()
            }
            .padding(.horizontal, 24)
            .frame(maxWidth: .infinity)

            CallTray(background: Palette.glass) {
                HStack {
                    PrimaryCallActionButton(icon: "phone.down.fill", label: "Decline", color: Palette.accentRed) {
                        store.declineIncoming()
                    }
                    .frame(maxWidth: .infinity)
                    PrimaryCallActionButton(icon: "phone.fill", label: "Accept", color: Palette.accentGreen) {
                        store.acceptIncoming()
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
    }
}

// MARK: Call ended

/// The AI assistant's notes on the call that just ended.
private struct NotesCard: View {
    let state: NotesState
    @State private var showTranscript = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "sparkles").appFont(size: 14).foregroundStyle(Palette.accentBlue)
                Text("AI notes").appFont(size: 15, weight: .bold).foregroundStyle(Palette.textPrimary)
            }
            switch state {
            case .working:
                Text("Writing up the call on this iPhone…")
                    .appFont(size: 13).foregroundStyle(Palette.textSecondary)
            case .failed(let reason):
                Text("Notes could not be made: \(reason)")
                    .appFont(size: 13).foregroundStyle(Palette.accentRed)
            case .ready(let notes):
                if notes.isEmpty {
                    Text("No speech was picked up in this call.")
                        .appFont(size: 13).foregroundStyle(Palette.textSecondary)
                } else {
                    if !notes.summary.isEmpty {
                        heading("Summary")
                        Text(notes.summary).appFont(size: 14).foregroundStyle(Palette.textPrimary)
                    }
                    if !notes.actionItems.isEmpty {
                        heading("Follow-ups")
                        ForEach(notes.actionItems, id: \.self) { item in
                            Text(verbatim: "\u{2022} \(item)").appFont(size: 14).foregroundStyle(Palette.textPrimary)
                        }
                    }
                    Button { showTranscript.toggle() } label: {
                        (showTranscript ? Text("Hide transcript") : Text("Show transcript"))
                            .appFont(size: 13, weight: .semibold).foregroundStyle(Palette.accentBlue)
                    }
                    if showTranscript {
                        Text(notes.transcript).appFont(size: 13).foregroundStyle(Palette.textSecondary)
                    }
                    Text("Made automatically on this iPhone. It can contain mistakes.")
                        .appFont(size: 11).foregroundStyle(Palette.textSecondary)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(18)
        .background(RoundedRectangle(cornerRadius: 20).fill(Palette.glass))
        .overlay(RoundedRectangle(cornerRadius: 20).stroke(Palette.borderLight, lineWidth: 1))
    }

    private func heading(_ title: LocalizedStringKey) -> some View {
        Text(title).appFont(size: 11, weight: .semibold).foregroundStyle(Palette.textSecondary).textCase(.uppercase)
    }
}

struct CallEndedScreen: View {
    @Environment(AppStore.self) private var store
    let summary: CallSummary

    var body: some View {
        let accent = summary.missed ? Palette.accentRed : Palette.accentGreen
        VStack(spacing: 0) {
            HStack {
                Button { store.dismissSummary() } label: {
                    Image(systemName: "xmark")
                        .appFont(size: 18, weight: .semibold)
                        .foregroundStyle(Palette.textPrimary)
                        .frame(width: 48, height: 48)
                }
                .accessibilityLabel("Close")
                Spacer()
                HStack(spacing: 6) {
                    Image(systemName: "phone.down.fill").appFont(size: 12)
                    (summary.missed ? Text("Missed") : Text("CALL ENDED"))
                        .appFont(size: 12, weight: .bold)
                }
                .foregroundStyle(accent)
                .padding(.horizontal, 14)
                .padding(.vertical, 6)
                .background(Capsule().fill(accent.opacity(0.1)))
                Spacer()
                Color.clear.frame(width: 48, height: 48)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)

            ScrollView {
                VStack(spacing: 0) {
                    Text(summary.callerName.initials)
                        .appFont(size: 48, weight: .bold)
                        .foregroundStyle(Palette.textPrimary)
                        .frame(width: 136, height: 136)
                        .background(Circle().fill(Palette.glass))
                        .overlay(Circle().stroke(accent.opacity(0.25), lineWidth: 2))
                    Text(summary.callerName)
                        .appFont(size: 32, weight: .bold)
                        .tracking(-0.5)
                        .foregroundStyle(Palette.textPrimary)
                        .multilineTextAlignment(.center)
                        .padding(.top, 24)
                    if !summary.callerNumber.isEmpty, summary.callerNumber != summary.callerName {
                        Text(summary.callerNumber)
                            .appFont(size: 16, weight: .medium)
                            .foregroundStyle(Palette.textSecondary)
                            .padding(.top, 4)
                    }
                    HStack(spacing: 8) {
                        Circle().fill(accent).frame(width: 8, height: 8)
                        Text(summary.missed ? CallType.missed.label : (summary.direction == .incoming ? CallType.incoming.label : CallType.outgoing.label))
                            .appFont(size: 16, weight: .medium)
                            .foregroundStyle(Palette.textSecondary)
                    }
                    .padding(.top, 10)

                    HStack(spacing: 28) {
                        detail("Duration", DurationFormat.clock(summary.duration))
                        Rectangle().fill(Palette.borderLight).frame(width: 1, height: 32)
                        detail("Time", summary.endedAt.formatted(.dateTime.hour(.twoDigits(amPM: .omitted)).minute(.twoDigits)))
                    }
                    .padding(.horizontal, 28)
                    .padding(.vertical, 16)
                    .background(RoundedRectangle(cornerRadius: 20).fill(Palette.glass))
                    .overlay(RoundedRectangle(cornerRadius: 20).stroke(Palette.borderLight, lineWidth: 1))
                    .padding(.top, 28)

                    if let notes = store.endedNotes {
                        NotesCard(state: notes).padding(.top, 24)
                    }
                }
                .padding(24)
                .frame(maxWidth: .infinity)
            }
            .scrollBounceBehavior(.basedOnSize)
            .defaultScrollAnchor(.center)

            CallTray(background: Palette.glass) {
                PrimaryCallActionButton(icon: "phone.fill", label: "Call Again", color: Palette.accentGreen) {
                    store.callAgain()
                }
                #if DEBUG
                SecondaryCallActionButton(icon: "bubble.left", label: "Send message") {
                    store.chatContactName = summary.callerName
                    store.chatContactOnline = false
                    store.chatMessages = []
                    store.contactTyping = false
                    store.endedSummary = nil
                    store.screen = .chat
                }
                .padding(.top, 28)
                #endif
            }
        }
    }

    private func detail(_ label: LocalizedStringKey, _ value: String) -> some View {
        VStack(spacing: 4) {
            Text(label)
                .appFont(size: 11, weight: .semibold)
                .tracking(0.5)
                .textCase(.uppercase)
                .foregroundStyle(Palette.textSecondary)
            Text(value)
                .appFont(size: 15, weight: .semibold, design: .monospaced)
                .foregroundStyle(Palette.textPrimary)
        }
    }
}

// MARK: Call options ("More")

struct CallOptionsScreen: View {
    @Environment(AppStore.self) private var store
    @State private var enteringDestination = false
    @State private var destination = ""
    @State private var showRoutePicker = false

    var body: some View {
        let held = store.activeCall?.held ?? false
        VStack(spacing: 0) {
            CenteredBackHeader(title: headerTitle) {
                if enteringDestination { enteringDestination = false; store.setHeld(false) }
                store.callPage = .main
            }

            if let consultation = store.consultationCall {
                consultationCenter(consultation)
                CallTray {
                    HStack {
                        Spacer()
                        PrimaryCallActionButton(icon: "arrow.triangle.merge", label: "Merge", color: Palette.accentBlue,
                                                enabled: consultation.state == .connected) {
                            store.mergeCalls()
                        }
                        Spacer()
                        PrimaryCallActionButton(icon: "checkmark", label: "Complete transfer", color: Palette.accentGreen,
                                                enabled: consultation.state == .connected) {
                            store.completeTransfer()
                        }
                        Spacer()
                    }
                    SecondaryCallActionButton(icon: "arrow.left", label: "Return to caller") {
                        store.returnToCaller()
                    }
                    .padding(.top, 28)
                }
            } else if enteringDestination {
                ScrollView {
                    VStack(spacing: 16) {
                        Text("Consultation destination")
                            .appFont(size: 15, weight: .medium)
                            .foregroundStyle(Palette.textSecondary)
                            .padding(.bottom, 8)
                        DialedNumberDisplay(number: destination) { destination = String(destination.dropLast()) }
                        if !held {
                            Text("Waiting for the original call to be placed on hold…")
                                .appFont(size: 12)
                                .foregroundStyle(Palette.textSecondary)
                        }
                        DialPad(rowSpacing: 16) { destination += $0 }
                            .frame(maxWidth: 288)
                    }
                    .padding(.horizontal, 32)
                    .frame(maxWidth: .infinity)
                }
                .scrollBounceBehavior(.basedOnSize)
                .defaultScrollAnchor(.center)
                CallTray {
                    PrimaryCallActionButton(icon: "person.badge.plus", label: "Start consultation", color: Palette.accentGreen,
                                            enabled: !destination.trimmingCharacters(in: .whitespaces).isEmpty && held) {
                        store.startConsultation(to: destination)
                        enteringDestination = false
                        destination = ""
                    }
                }
            } else {
                ScrollView {
                    VStack(spacing: 0) {
                        OptionRow(icon: held ? "play.fill" : "pause.fill", title: held ? "Resume" : "Hold",
                                  subtitle: held ? String(localized: "Resume the held call") : String(localized: "Put this call on hold")) {
                            store.setHeld(!held)
                        }
                        RowDivider(leading: 18)
                        OptionRow(icon: "person.badge.plus", title: "Consult transfer",
                                  subtitle: String(localized: "Call someone else privately, then complete or return")) {
                            if !held { store.setHeld(true) }
                            enteringDestination = true
                        }
                        RowDivider(leading: 18)
                        OptionRow(icon: "phone.badge.plus", title: "Add call",
                                  subtitle: String(localized: "Bring someone else into this conversation")) {
                            // Nobody is put on hold: the new person is dialled while the call carries on.
                            store.callPage = .addPerson
                        }
                        RowDivider(leading: 18)
                        let assistant = store.activeCall?.assistant ?? false
                        OptionRow(icon: "sparkles", title: assistant ? "Stop AI assistant" : "AI assistant",
                                  subtitle: assistant ? String(localized: "Notes will be ready when the call ends")
                                                      : String(localized: "Transcript, summary and follow-ups after the call"),
                                  iconTint: assistant ? Palette.accentGreen : Palette.accentBlue) {
                            store.setAssistant(!assistant)
                        }
                        RowDivider(leading: 18)
                        let recording = store.activeCall?.recording ?? false
                        OptionRow(icon: "record.circle", title: recording ? "Stop recording" : "Record",
                                  subtitle: recording ? String(localized: "This call is being recorded")
                                                      : String(localized: "Save this call as a recording"),
                                  iconTint: recording ? Palette.accentRed : Palette.accentBlue) {
                            store.setRecording(!recording)
                        }
                        if store.availableRoutes.count > 1 {
                            RowDivider(leading: 18)
                            OptionRow(icon: "speaker.wave.2.fill", title: "Output device", subtitle: store.selectedRoute.label) {
                                showRoutePicker = true
                            }
                        }
                    }
                    .background(RoundedRectangle(cornerRadius: 20).fill(Palette.glass))
                    .padding(.horizontal, 24)
                    .padding(.vertical, 16)
                }
            }
        }
        .confirmationDialog("Output device", isPresented: $showRoutePicker, titleVisibility: .visible) {
            ForEach(store.availableRoutes) { route in
                Button(route == store.selectedRoute ? "\(route.label) ✓" : route.label) { store.selectedRoute = route }
            }
        }
    }

    private var headerTitle: LocalizedStringKey {
        guard store.consultationCall != nil || enteringDestination else { return "More options" }
        return "Consult transfer"
    }

    private func consultationCenter(_ session: CallSession) -> some View {
        VStack(spacing: 8) {
            Spacer()
            // A bare extension has no separate display name; don't repeat it as "1002 · Ext. 1002".
            (session.remoteName == session.remoteNumber
                ? Text("Consultation: \(session.remoteName)")
                : Text("Consultation: \(session.remoteName) · Ext. \(session.remoteNumber)"))
                .appFont(size: 22, weight: .bold)
                .foregroundStyle(Palette.textPrimary)
            Text(session.state.label)
                .appFont(size: 15)
                .foregroundStyle(Palette.textSecondary)
            Text("\(store.activeCall?.remoteName ?? "") is on hold")
                .appFont(size: 13)
                .foregroundStyle(Palette.textSecondary)
                .padding(.top, 16)
            Spacer()
        }
        .multilineTextAlignment(.center)
        .padding(.horizontal, 32)
        .frame(maxWidth: .infinity)
    }
}

private struct OptionRow: View {
    let icon: String
    let title: LocalizedStringKey
    let subtitle: String
    var iconTint: Color = Palette.accentBlue
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                IconTile(icon: icon, tint: iconTint, background: Palette.primaryLight)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .appFont(size: 15, weight: .semibold)
                        .foregroundStyle(Palette.textPrimary)
                    Text(subtitle)
                        .appFont(size: 13)
                        .foregroundStyle(Palette.textSecondary)
                        .multilineTextAlignment(.leading)
                }
                Spacer(minLength: 8)
                Chevron()
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 16)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

// MARK: In-call keypad

/// The in-call keypad as its own page, laid out like [TransferCallScreen], so the dial pad no
/// longer squeezes in between the caller identity and the call controls. Cancel returns to the
/// call screen; the call keeps running.
struct InCallKeypadScreen: View {
    @Environment(AppStore.self) private var store
    let session: CallSession
    @State private var tones = ""

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button { store.callPage = .main } label: {
                    Image(systemName: "arrow.left")
                        .appFont(size: 18, weight: .semibold)
                        .foregroundStyle(Palette.textPrimary)
                        .frame(width: 48, height: 48)
                }
                .accessibilityLabel("Cancel")
                Spacer()
                VStack(spacing: 2) {
                    Text(session.remoteName)
                        .appFont(size: 18, weight: .bold)
                        .foregroundStyle(Palette.textPrimary)
                    TimelineView(.periodic(from: .now, by: 1)) { context in
                        Text(status(at: context.date))
                            .appFont(size: 13, weight: .medium)
                            .monospacedDigit()
                            .foregroundStyle(Palette.textSecondary)
                    }
                }
                Spacer()
                Color.clear.frame(width: 48, height: 48)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)

            ScrollView {
                VStack(spacing: 16) {
                    Text(tones)
                        .appFont(size: 32, weight: .medium)
                        .tracking(1)
                        .foregroundStyle(Palette.textPrimary)
                        .lineLimit(1)
                        .frame(height: 44)
                    if session.held {
                        Text("Resume the call to send keypad tones")
                            .appFont(size: 12)
                            .foregroundStyle(Palette.accentRed)
                            .multilineTextAlignment(.center)
                    }
                    DialPad(rowSpacing: 16) { key in
                        guard !session.held else { return }
                        tones = String((tones + key).suffix(24))
                        store.sendDtmf(key)
                    }
                    .frame(maxWidth: 288)
                }
                .padding(.horizontal, 32)
                .frame(maxWidth: .infinity)
            }
            .scrollBounceBehavior(.basedOnSize)
            .defaultScrollAnchor(.center)

            CallTray {
                HStack {
                    PrimaryCallActionButton(icon: "xmark", label: "Cancel", color: Palette.textSecondary) {
                        store.callPage = .main
                    }
                    .frame(maxWidth: .infinity)
                    PrimaryCallActionButton(icon: "phone.down.fill", label: "Hang up", color: Palette.accentRed) {
                        store.endCall()
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
    }

    private func status(at date: Date) -> String {
        if session.held { return CallState.held.label }
        guard let connectedAt = session.connectedAt else { return session.state.label }
        return DurationFormat.clock(date.timeIntervalSince(connectedAt))
    }
}

// MARK: Blind transfer

struct TransferCallScreen: View {
    @Environment(AppStore.self) private var store
    let callerName: String
    @State private var destination = ""

    var body: some View {
        VStack(spacing: 0) {
            CenteredBackHeader(title: "Transfer") { store.callPage = .main }
            ScrollView {
                VStack(spacing: 16) {
                    Text("Transferring call with \(callerName)")
                        .appFont(size: 15, weight: .medium)
                        .foregroundStyle(Palette.textSecondary)
                        .multilineTextAlignment(.center)
                        .padding(.bottom, 8)
                    DialedNumberDisplay(number: destination, placeholder: "Transfer destination") {
                        destination = String(destination.dropLast())
                    }
                    DialPad(rowSpacing: 16) { destination += $0 }
                        .frame(maxWidth: 288)
                }
                .padding(.horizontal, 32)
                .frame(maxWidth: .infinity)
            }
            .scrollBounceBehavior(.basedOnSize)
            .defaultScrollAnchor(.center)
            CallTray {
                HStack {
                    PrimaryCallActionButton(icon: "xmark", label: "Cancel", color: Palette.textSecondary) {
                        store.callPage = .main
                    }
                    .frame(maxWidth: .infinity)
                    PrimaryCallActionButton(icon: "phone.arrow.right.fill", label: "Transfer now", color: Palette.accentBlue,
                                            enabled: !destination.trimmingCharacters(in: .whitespaces).isEmpty) {
                        store.blindTransfer(to: destination)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
    }
}
