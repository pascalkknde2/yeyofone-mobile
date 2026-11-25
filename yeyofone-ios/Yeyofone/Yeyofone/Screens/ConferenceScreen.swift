import SwiftUI

//  The conference call screen and its add-people picker. Port of yeyofone-android ui/call/ConferenceScreen:
//  frameless avatars and one dark toolbar. The calling engine joins two other people to you, so a
//  conference here is three-way.

private struct ConferencePerson: Identifiable {
    let id: String
    let name: String
    let isSelf: Bool
    let muted: Bool
    let status: LocalizedStringKey?
    /// True while the person is still being called, so their avatar is dimmed.
    let connecting: Bool
    let removable: Bool
}

struct ConferenceScreen: View {
    @Environment(AppStore.self) private var store
    let session: CallSession
    @State private var recordingStart: Date?

    var body: some View {
        let people = participants()
        VStack(spacing: 0) {
            Text("Conference call").appFont(size: 20, weight: .bold).foregroundStyle(Palette.textPrimary).padding(.top, 20)
            Text("\(people.count) people on this call")
                .appFont(size: 14).foregroundStyle(Palette.textSecondary).padding(.top, 4)
            ScrollView {
                VStack(spacing: 24) {
                    // With more than two people the first other person is the spotlight.
                    if people.count > 2, let spotlight = people.first(where: { store.speaking.contains($0.id) && !$0.isSelf }) ?? people.first(where: { !$0.isSelf }) {
                        tile(spotlight, large: true)
                        HStack(alignment: .top, spacing: 12) {
                            ForEach(people.filter { $0.id != spotlight.id }) { tile($0, large: false) }
                        }
                    } else {
                        HStack(alignment: .top, spacing: 12) {
                            ForEach(people) { tile($0, large: false) }
                        }
                    }
                }
                .padding(.horizontal, 16).padding(.vertical, 24)
                .frame(maxWidth: .infinity)
            }
            .scrollBounceBehavior(.basedOnSize)
            .defaultScrollAnchor(.center)
            toolbar(count: people.count)
        }
        .onChange(of: session.recording) { _, on in recordingStart = on ? Date() : nil }
        .onAppear { if session.recording, recordingStart == nil { recordingStart = Date() } }
    }

    /// Set when someone else organised the conference; this phone shows their roster and can't add or remove people.
    private var remote: ConferenceRoster? {
        guard let roster = store.remoteRoster, roster.host == session.remoteNumber else { return nil }
        return roster
    }

    private func participants() -> [ConferencePerson] {
        if let remote {
            let myExtension = store.selfExtension
            var list = [ConferencePerson(id: "you", name: String(localized: "You"), isSelf: true, muted: session.muted, status: nil, connecting: false, removable: false)]
            for member in remote.members where member.ext != myExtension {
                let name = store.contactName(for: member.ext) ?? member.name
                list.append(ConferencePerson(id: member.ext, name: name, isSelf: false, muted: false,
                                             status: member.calling ? "Calling…" : "Active", connecting: member.calling, removable: false))
            }
            return list
        }
        // Nobody in a conference is on hold: someone who has answered is simply "Active".
        var list = [ConferencePerson(id: "you", name: String(localized: "You"), isSelf: true, muted: session.muted, status: nil, connecting: false, removable: false),
                    ConferencePerson(id: "first", name: session.remoteName, isSelf: false, muted: false, status: "Active", connecting: false, removable: false)]
        if let other = store.consultationCall {
            let active = store.conferenceMerged || other.state == .connected || other.state == .held
            list.append(ConferencePerson(id: "second", name: other.remoteName, isSelf: false, muted: false,
                                         status: active ? "Active" : LocalizedStringKey(other.state.label), connecting: !active, removable: true))
        }
        return list
    }

    private func tile(_ p: ConferencePerson, large: Bool) -> some View {
        let size: CGFloat = large ? 112 : 72
        let initials = p.name.split(whereSeparator: { " .-_".contains($0) }).prefix(2).compactMap { $0.first }.map(String.init).joined().uppercased()
        return VStack(spacing: 10) {
            ZStack {
                Text(initials.isEmpty ? "?" : initials)
                    .appFont(size: large ? 36 : 24, weight: .bold)
                    .foregroundStyle(Palette.textPrimary)
                    .frame(width: size, height: size)
                    .background(Circle().fill(Palette.primaryLight))
                    .overlay(Circle().stroke(Palette.accentBlue, lineWidth: store.speaking.contains(p.id) ? 3 : 0).padding(-6))
                    .opacity(p.connecting ? 0.55 : 1)
                if p.muted {
                    Image(systemName: "mic.slash.fill").font(.system(size: 12, weight: .bold)).foregroundStyle(.white)
                        .frame(width: 24, height: 24).background(Circle().fill(Palette.accentRed))
                        .offset(x: size / 2 - 12, y: size / 2 - 12)
                }
                if p.removable {
                    Button { store.returnToCaller() } label: {
                        Image(systemName: "xmark").font(.system(size: 11, weight: .bold)).foregroundStyle(.white)
                            .frame(width: 24, height: 24).background(Circle().fill(Color(red: 0.28, green: 0.33, blue: 0.41)))
                    }
                    .accessibilityLabel("Remove from call")
                    .offset(x: size / 2 - 12, y: -(size / 2 - 12))
                }
            }
            Text(p.name).appFont(size: large ? 17 : 14, weight: .semibold).foregroundStyle(Palette.textPrimary).lineLimit(1)
            if let status = p.status {
                Text(status).appFont(size: 12).foregroundStyle(Palette.textSecondary)
            }
        }
        .frame(width: large ? 160 : 96)
    }

    private func toolbar(count: Int) -> some View {
        let toolbarBackground = Color(red: 0.11, green: 0.115, blue: 0.13)
        let canAdd = remote == nil && !store.conferenceMerged && store.consultationCall == nil
        return HStack {
            toolbarButton(session.muted ? "mic.slash.fill" : "mic.fill", tint: session.muted ? Palette.accentRed : .white, label: session.muted ? "Unmute" : "Mute") {
                if remote != nil { store.setMuted(!session.muted) } else { store.setConferenceMuted(!session.muted) }
            }
            Spacer(minLength: 0)
            toolbarButton("speaker.wave.2.fill", tint: store.selectedRoute == .speaker ? Palette.accentBlue : .white, label: "Speaker") {
                store.selectedRoute = store.selectedRoute == .speaker ? .earpiece : .speaker
            }
            Spacer(minLength: 0)
            HStack(spacing: 6) {
                Image(systemName: "person.2.fill").font(.system(size: 18))
                Text("\(count)").appFont(size: 15, weight: .medium)
            }
            .foregroundStyle(.white).frame(height: 44)
            Spacer(minLength: 0)
            if remote == nil {
                toolbarButton("person.badge.plus", tint: canAdd ? .white : .white.opacity(0.3), label: "Add call") {
                    if canAdd { store.callPage = .addPerson }
                }
                Spacer(minLength: 0)
            }
            Button { store.setRecording(!session.recording) } label: {
                HStack(spacing: 6) {
                    Image(systemName: "circle.fill").font(.system(size: 14)).foregroundStyle(session.recording ? Palette.accentRed : .white)
                    if session.recording, let start = recordingStart {
                        TimelineView(.periodic(from: .now, by: 1)) { context in
                            let seconds = max(0, Int(context.date.timeIntervalSince(start)))
                            Text(String(format: "%02d:%02d", seconds / 60, seconds % 60))
                                .appFont(size: 14, weight: .medium).foregroundStyle(.white).monospacedDigit()
                        }
                    }
                }
                .padding(.horizontal, session.recording ? 12 : 8).frame(height: 40)
                .background(Capsule().fill(session.recording ? Color(red: 0.2, green: 0.21, blue: 0.24) : .clear))
            }
            .accessibilityLabel(session.recording ? Text("Stop recording") : Text("Record"))
            Spacer(minLength: 0)
            Button { store.endCall() } label: {
                Image(systemName: "phone.down.fill").font(.system(size: 18)).foregroundStyle(.white)
                    .frame(width: 52, height: 44).background(Capsule().fill(Palette.accentRed))
            }
            .accessibilityLabel("Hang up")
        }
        .padding(.horizontal, 8).padding(.vertical, 8)
        .background(Capsule().fill(toolbarBackground))
        .padding(.horizontal, 12).padding(.bottom, 16)
    }

    private func toolbarButton(_ icon: String, tint: Color, label: LocalizedStringKey, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: 18)).foregroundStyle(tint).frame(width: 44, height: 44)
        }
        .accessibilityLabel(Text(label))
    }
}

/// Choose who to add to the call. Only saved contacts can be added; people already on the call are left out.
struct ContactPickerScreen: View {
    @Environment(AppStore.self) private var store
    @State private var query = ""

    var body: some View {
        let inCall: Set<String> = Set([store.activeCall?.remoteNumber, store.consultationCall?.remoteNumber].compactMap { $0 })
        let available = store.contacts.filter { !inCall.contains($0.number) }
        let shown = available.filter {
            query.isEmpty || $0.displayName.localizedCaseInsensitiveContains(query) || $0.number.contains(query)
        }
        VStack(spacing: 0) {
            CenteredBackHeader(title: "Add people") { store.callPage = .main }
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass").foregroundStyle(Palette.inactiveGray)
                TextField("Search contacts", text: $query).textInputAutocapitalization(.never).autocorrectionDisabled()
            }
            .padding(12)
            .background(RoundedRectangle(cornerRadius: 12).fill(Palette.glass))
            .padding(.horizontal, 24).padding(.bottom, 12)
            if shown.isEmpty {
                Spacer()
                Text(available.isEmpty ? "No contacts to add. Save people in Contacts first, then add them here." : "No matches")
                    .appFont(size: 15).foregroundStyle(Palette.textSecondary)
                    .multilineTextAlignment(.center).padding(40)
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: 10) {
                        ForEach(shown) { contact in
                            Button { store.addPerson(contact) } label: {
                                HStack(spacing: 14) {
                                    Text(initials(contact.displayName)).appFont(size: 15, weight: .bold)
                                        .foregroundStyle(Palette.textPrimary).frame(width: 44, height: 44)
                                        .background(Circle().fill(Palette.primaryLight))
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(contact.displayName).appFont(size: 15, weight: .semibold).foregroundStyle(Palette.textPrimary)
                                        Text(contact.number).appFont(size: 13).foregroundStyle(Palette.textSecondary)
                                    }
                                    Spacer()
                                    Image(systemName: "phone.fill").foregroundStyle(Palette.accentGreen)
                                        .frame(width: 36, height: 36).background(Circle().fill(Palette.successLight))
                                }
                                .padding(14)
                                .background(RoundedRectangle(cornerRadius: 16).fill(Palette.glass))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 24).padding(.vertical, 8)
                }
            }
        }
    }

    private func initials(_ name: String) -> String {
        let value = name.split(whereSeparator: { " .-_".contains($0) }).prefix(2).compactMap { $0.first }.map(String.init).joined().uppercased()
        return value.isEmpty ? "?" : value
    }
}
