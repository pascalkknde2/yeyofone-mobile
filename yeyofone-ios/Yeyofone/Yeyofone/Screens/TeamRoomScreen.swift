//
//  TeamRoomScreen.swift
//  Yeyofone
//
//  The Team room: a video conference preview ported from the desktop app's ConferenceRoom.tsx.
//  Your camera and microphone are real local captures shown only on this device; the other
//  participants and the chat are sample data, and nothing you share is sent to anyone.
//  On a phone the desktop's side panel becomes a sheet, and screen sharing is left out.
//

import SwiftUI

/// A sample participant, as in the desktop room.
struct RoomPerson: Identifiable, Equatable {
    let id: String
    let name: String
    let initials: String
    let role: LocalizedStringResource
    let color: UInt32

    static func == (a: RoomPerson, b: RoomPerson) -> Bool { a.id == b.id }

    static let attendees: [RoomPerson] = [
        RoomPerson(id: "you", name: "Sarah Ndio", initials: "SN", role: "Host", color: 0x567A9D),
        RoomPerson(id: "alex", name: "Alex Morgan", initials: "AM", role: "Product design", color: 0x6B709D),
        RoomPerson(id: "maya", name: "Maya Wilson", initials: "MW", role: "Engineering", color: 0x946E78),
        RoomPerson(id: "daniel", name: "Daniel Evans", initials: "DE", role: "Customer experience", color: 0x5A847E),
        RoomPerson(id: "elena", name: "Elena Carter", initials: "EC", role: "Operations", color: 0x8D8064),
        RoomPerson(id: "james", name: "James Brooks", initials: "JB", role: "Sales", color: 0x697D9A),
    ]
}

struct RoomMessage: Identifiable {
    let id = UUID()
    let from: String
    /// Sample messages are translated; your own are shown as typed.
    let sample: LocalizedStringResource?
    let text: String
}

@Observable
final class TeamRoom {
    enum Stage { case meeting, notes }
    enum Panel { case chat, participants }

    static let topics: [LocalizedStringResource] = ["Team check-in", "Product priorities", "Decisions & next steps"]

    var joined = ["you", "alex", "maya", "daniel"]
    var focused = "alex"
    var stage = Stage.meeting
    var panel = Panel.participants
    var panelOpen = false
    var query = ""
    // Camera and mic start off so iOS only asks when you turn them on.
    var muted = true
    var camera = false
    var talking = false
    var raised = false
    var sharingAgenda = false
    var mediaError: LocalizedStringResource?
    var seconds = 0
    var topic = 0
    var notes = ""
    var messages = [
        RoomMessage(from: "alex", sample: "Morning all! I added the release notes to the agenda.", text: ""),
        RoomMessage(from: "maya", sample: "Thanks, I’ll walk through the API changes.", text: ""),
    ]
    var draft = ""

    @ObservationIgnored let cameraCapture = RoomCamera()
    @ObservationIgnored private let micMeter = RoomMicMeter()

    var participants: [RoomPerson] { RoomPerson.attendees.filter { joined.contains($0.id) } }
    var invited: [RoomPerson] { RoomPerson.attendees.filter { !joined.contains($0.id) } }
    var speaker: RoomPerson { participants.first { $0.id == focused } ?? participants[0] }
    var time: String { String(format: "%02d:%02d", seconds / 60, seconds % 60) }

    func matches(_ person: RoomPerson) -> Bool {
        let text = query.trimmingCharacters(in: .whitespaces)
        return text.isEmpty || person.name.localizedCaseInsensitiveContains(text)
    }

    func speaking(_ person: RoomPerson) -> Bool {
        person.id == "you" ? !muted && talking : person.id == speaker.id && !sharingAgenda
    }

    func handUp(_ person: RoomPerson) -> Bool { person.id == "you" && raised }

    func showOnStage(_ id: String) {
        focused = id
        stage = .meeting
        sharingAgenda = false
    }

    func toggleShare() {
        mediaError = nil
        sharingAgenda.toggle()
        if sharingAgenda { stage = .meeting }
    }

    func toggleCamera() async {
        if camera {
            cameraCapture.stop()
            camera = false
            return
        }
        if let error = await cameraCapture.start() {
            mediaError = error
        } else {
            camera = true
            mediaError = nil
        }
    }

    func toggleMic() async {
        if !muted {
            stopMic()
            return
        }
        if let error = await micMeter.start(onTalking: { [weak self] in self?.talking = $0 }) {
            stopMic()
            mediaError = error
        } else {
            muted = false
            mediaError = nil
        }
    }

    func sendMessage() {
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        messages.append(RoomMessage(from: "you", sample: nil, text: String(text.prefix(500))))
        draft = ""
    }

    /// Releases every capture when the room closes.
    func close() {
        cameraCapture.stop()
        camera = false
        stopMic()
    }

    private func stopMic() {
        micMeter.stop()
        talking = false
        muted = true
    }
}

/// Fixed room colors from the desktop's conference-room.css; the meeting area is dark in both
/// appearances, and the panel, notes and shared agenda follow the system appearance.
private enum RoomColor {
    static let background = Color(hex: 0x15171C)
    static let surface = Color(hex: 0x22252C)
    static let speaker = Color(hex: 0x1D2026)
    static let tile = Color(hex: 0x262A31)
    static let control = Color(hex: 0x3A3F4A)
    static let offControl = Color(hex: 0xF1F5F9)
    static let offInk = Color(hex: 0x0F172A)
    static let blue = Color(hex: 0x2563EB)
    static let selectedBorder = Color(hex: 0x60A5FA)
    static let red = Color(hex: 0xDC2626)
    static let handBackground = Color(hex: 0xFBBF24)
    static let handInk = Color(hex: 0x78350F)
    static let subtle = Color(hex: 0x94A3B8)
    static let note = Color(hex: 0x64748B)
    static let label = Color(hex: 0x0F172A, alpha: 0.6)

    static let paper = Color(light: 0xFFFFFF, dark: 0x1E2026)
    static let paper2 = Color(light: 0xF8FAFC, dark: 0x1A1C21)
    static let paperMuted = Color(light: 0xF1F5F9, dark: 0x2A2D34)
    static let border = Color(light: 0xE2E8F0, dark: 0x343842)
    static let ink = Color(light: 0x0F172A, dark: 0xF1F5F9)
    static let ink3 = Color(light: 0x475569, dark: 0xB4BCC8)
    static let inkMuted = Color(light: 0x64748B, dark: 0x949CAB)
    static let tint = Color(light: 0xEFF6FF, dark: 0x1C2A44)
    static let accent = Color(light: 0x2563EB, dark: 0x3B82F6)
    static let errorBackground = Color(light: 0xFEF2F2, dark: 0x3A1D22)
    static let errorInk = Color(light: 0xB91C1C, dark: 0xFCA5A5)
}

struct TeamRoomScreen: View {
    @Environment(AppStore.self) private var store
    @State private var room = TeamRoom()

    var body: some View {
        VStack(spacing: 12) {
            header
            stageTabs
            if room.stage == .notes {
                RoomNotes(room: room)
            } else {
                RoomStage(room: room)
                RoomStrip(room: room)
            }
            Text("Conference preview · Sample participants · Nothing you share is sent to anyone")
                .font(.system(size: 12))
                .foregroundStyle(RoomColor.note)
                .multilineTextAlignment(.center)
            controls
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .padding(.bottom, 12)
        .background(RoomColor.background.ignoresSafeArea())
        .sheet(isPresented: $room.panelOpen) {
            RoomPanel(room: room)
                .presentationDetents([.medium, .large])
                .presentationBackground(RoomColor.paper)
        }
        .task {
            // Elena joins a few seconds in, as on the desktop.
            Task {
                try? await Task.sleep(for: .seconds(4.5))
                if !room.joined.contains("elena") { room.joined.append("elena") }
            }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(1))
                room.seconds += 1
            }
        }
        .onDisappear { room.close() }
    }

    private var header: some View {
        HStack(alignment: .center, spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: "\(String(localized: "Weekly catch-up")) · \(room.time)")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(RoomColor.subtle)
                    .monospacedDigit()
                Text("Team room")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(.white)
                    .accessibilityAddTraits(.isHeader)
            }
            Spacer(minLength: 0)
            headerButton(room.panelOpen ? "Hide room panel" : "Show room panel", systemImage: "person.2.fill", selected: room.panelOpen) {
                room.panelOpen.toggle()
            }
            headerButton("Close conference preview", systemImage: "xmark", selected: false) {
                store.teamRoomOpen = false
            }
        }
    }

    private func headerButton(_ label: LocalizedStringResource, systemImage: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 40, height: 40)
                .background(Circle().fill(selected ? RoomColor.blue : RoomColor.surface))
        }
        .buttonStyle(PressScaleStyle())
        .accessibilityLabel(Text(label))
    }

    private var stageTabs: some View {
        HStack(spacing: 4) {
            tab("Notes", stage: .notes)
            tab("Meeting", stage: .meeting)
        }
        .padding(4)
        .background(Capsule().fill(RoomColor.surface))
    }

    private func tab(_ title: LocalizedStringResource, stage: TeamRoom.Stage) -> some View {
        let selected = room.stage == stage
        return Button { room.stage = stage } label: {
            Text(title)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(selected ? .white : RoomColor.subtle)
                .frame(maxWidth: .infinity, minHeight: 34)
                .background(Capsule().fill(selected ? RoomColor.control : .clear))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private var controls: some View {
        HStack(spacing: 14) {
            control(room.camera ? "Camera off" : "Camera on", systemImage: room.camera ? "video.fill" : "video.slash.fill",
                    style: room.camera ? .normal : .off) { Task { await room.toggleCamera() } }
            control(room.muted ? "Unmute" : "Mute", systemImage: room.muted ? "mic.slash.fill" : "mic.fill",
                    style: room.muted ? .off : .normal) { Task { await room.toggleMic() } }
            control(room.sharingAgenda ? "Stop sharing" : "Share agenda", systemImage: "square.and.arrow.up",
                    style: room.sharingAgenda ? .on : .normal) { room.toggleShare() }
            control(room.raised ? "Lower hand" : "Raise hand", systemImage: "hand.raised.fill",
                    style: room.raised ? .on : .normal) { room.raised.toggle() }
            control("Leave", systemImage: "phone.down.fill", style: .leave) { store.teamRoomOpen = false }
        }
    }

    private enum ControlStyle { case normal, off, on, leave }

    private func control(_ label: LocalizedStringResource, systemImage: String, style: ControlStyle, action: @escaping () -> Void) -> some View {
        let (fill, ink): (Color, Color) = switch style {
        case .normal: (RoomColor.control, RoomColor.offControl)
        case .off: (RoomColor.offControl, RoomColor.offInk)
        case .on: (RoomColor.blue, .white)
        case .leave: (RoomColor.red, .white)
        }
        return Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 19, weight: .semibold))
                .foregroundStyle(ink)
                .frame(width: 52, height: 52)
                .background(Circle().fill(fill))
        }
        .buttonStyle(PressScaleStyle())
        .accessibilityLabel(Text(label))
    }
}

private struct RoomAvatar: View {
    let person: RoomPerson
    let size: CGFloat

    var body: some View {
        Text(verbatim: person.initials)
            .font(.system(size: size * 0.36, weight: .semibold))
            .foregroundStyle(.white)
            .frame(width: size, height: size)
            .background(Circle().fill(Color(hex: person.color)))
            .accessibilityHidden(true)
    }
}

private struct RoomBadge: View {
    let systemImage: String
    var fill = RoomColor.blue
    var ink = Color.white

    var body: some View {
        Image(systemName: systemImage)
            .font(.system(size: 11, weight: .bold))
            .foregroundStyle(ink)
            .frame(width: 24, height: 24)
            .background(Circle().fill(fill))
    }
}

private struct RoomStage: View {
    @Environment(AppStore.self) private var store
    let room: TeamRoom

    var body: some View {
        ZStack(alignment: .top) {
            if room.sharingAgenda {
                sharedAgenda
            } else {
                speaker
            }
            if let error = room.mediaError {
                HStack(spacing: 8) {
                    Text(error).font(.system(size: 14))
                    Button { room.mediaError = nil } label: {
                        Image(systemName: "xmark").font(.system(size: 12, weight: .bold)).frame(width: 26, height: 26)
                    }
                    .accessibilityLabel(Text("Dismiss notification"))
                }
                .foregroundStyle(RoomColor.errorInk)
                .padding(.leading, 14)
                .padding(.trailing, 6)
                .padding(.vertical, 6)
                .background(RoundedRectangle(cornerRadius: 10).fill(RoomColor.errorBackground))
                .padding(.top, 14)
                .padding(.horizontal, 14)
                .accessibilityAddTraits(.isStaticText)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(RoomColor.surface)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    private var speaker: some View {
        let person = room.speaker
        return ZStack(alignment: .bottomLeading) {
            RadialGradient(colors: [Color(hex: person.color, alpha: 0.55), .clear], center: UnitPoint(x: 0.5, y: 0.42),
                           startRadius: 0, endRadius: 260)
                .background(RoomColor.speaker)
            if person.id == "you" && room.camera {
                RoomCameraView(session: room.cameraCapture.session)
                    .accessibilityLabel(Text("Your camera"))
            } else {
                RoomAvatar(person: person, size: 120)
                    .overlay(Circle().stroke(.white.opacity(0.06), lineWidth: 10).padding(-5))
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            Text(verbatim: person.id == "you" ? "\(person.name) (\(String(localized: "You")))" : person.name)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(RoundedRectangle(cornerRadius: 8).fill(RoomColor.label))
                .padding(16)
        }
    }

    private var sharedAgenda: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Label { Text("Sarah’s agenda · Share preview") } icon: { Image(systemName: "square.and.arrow.up") }
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(RoomColor.accent)
                VStack(alignment: .leading, spacing: 0) {
                    Text("A little focus.")
                    Text("A better conversation.")
                }
                .font(.system(size: 26, weight: .bold))
                .foregroundStyle(RoomColor.ink)
                .padding(.vertical, 20)
                VStack(alignment: .leading, spacing: 10) {
                    ForEach(Array(TeamRoom.topics.enumerated()), id: \.offset) { index, title in
                        HStack(alignment: .firstTextBaseline, spacing: 8) {
                            Text(verbatim: "\(index + 1).")
                            Text(title)
                        }
                        .font(.system(size: 17, weight: room.topic == index ? .semibold : .regular))
                        .foregroundStyle(room.topic == index ? RoomColor.accent : RoomColor.ink3)
                    }
                }
            }
            .padding(24)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(RoomColor.paper2)
    }
}

private struct RoomStrip: View {
    let room: TeamRoom

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(room.participants) { person in tile(person) }
            }
        }
        .frame(height: 112)
        .accessibilityElement(children: .contain)
        .accessibilityLabel(Text("Participants"))
    }

    private func tile(_ person: RoomPerson) -> some View {
        let you = person.id == "you"
        let selected = person.id == room.speaker.id
        return Button { room.showOnStage(person.id) } label: {
            ZStack(alignment: .bottom) {
                RadialGradient(colors: [Color(hex: person.color, alpha: 0.45), .clear], center: UnitPoint(x: 0.5, y: 0.4),
                               startRadius: 0, endRadius: 90)
                    .background(RoomColor.tile)
                // The camera shows in one place: on the stage when you're there, else in your tile.
                if you && room.camera && room.speaker.id != "you" {
                    RoomCameraView(session: room.cameraCapture.session)
                } else {
                    RoomAvatar(person: person, size: 48)
                        .padding(.bottom, 14)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                HStack(spacing: 4) {
                    Text(verbatim: you ? String(localized: "You") : person.name)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                        .shadow(color: .black.opacity(0.5), radius: 1, y: 1)
                    Spacer(minLength: 4)
                    if room.handUp(person) {
                        RoomBadge(systemImage: "hand.raised.fill", fill: RoomColor.handBackground, ink: RoomColor.handInk)
                    }
                    if you && room.muted {
                        RoomBadge(systemImage: "mic.slash.fill", fill: RoomColor.red)
                    } else if room.speaking(person) {
                        RoomBadge(systemImage: "waveform")
                    }
                }
                .padding(.horizontal, 8)
                .padding(.bottom, 6)
            }
            .frame(width: 140, height: 112)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(selected ? RoomColor.selectedBorder : .clear, lineWidth: 2))
        }
        .buttonStyle(PressScaleStyle())
        .accessibilityLabel(Text(verbatim: "\(String(localized: "Show on stage")): \(person.name)"))
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

private struct RoomNotes: View {
    @Bindable var room: TeamRoom

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Agenda")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(RoomColor.ink)
                ForEach(Array(TeamRoom.topics.enumerated()), id: \.offset) { index, title in
                    let current = room.topic == index
                    Button { room.topic = index } label: {
                        HStack(spacing: 12) {
                            Text(verbatim: "\(index + 1)")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundStyle(current ? .white : RoomColor.inkMuted)
                                .frame(width: 26, height: 26)
                                .background(Circle().fill(current ? RoomColor.accent : RoomColor.paperMuted))
                            Text(title)
                                .font(.system(size: 15, weight: current ? .semibold : .regular))
                                .foregroundStyle(current ? RoomColor.accent : RoomColor.ink)
                            Spacer(minLength: 0)
                        }
                        .padding(10)
                        .background(RoundedRectangle(cornerRadius: 10).fill(current ? RoomColor.tint : .clear))
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(current ? .isSelected : [])
                }
                Text("Decisions & next steps")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(RoomColor.ink)
                    .padding(.top, 8)
                ZStack(alignment: .topLeading) {
                    if room.notes.isEmpty {
                        Text("Capture a decision or an action…")
                            .foregroundStyle(RoomColor.inkMuted)
                            .padding(.horizontal, 13)
                            .padding(.vertical, 16)
                    }
                    TextEditor(text: $room.notes)
                        .scrollContentBackground(.hidden)
                        .foregroundStyle(RoomColor.ink)
                        .padding(8)
                        .frame(minHeight: 140)
                        .onChange(of: room.notes) { _, value in
                            if value.count > 2000 { room.notes = String(value.prefix(2000)) }
                        }
                        .accessibilityLabel(Text("Decisions & next steps"))
                }
                .font(.system(size: 15))
                .background(RoundedRectangle(cornerRadius: 10).fill(RoomColor.paper2))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(RoomColor.border))
                Text("Private preview notes · Cleared when you leave")
                    .font(.system(size: 12))
                    .foregroundStyle(RoomColor.inkMuted)
            }
            .padding(18)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(RoomColor.paper)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}

private struct RoomPanel: View {
    @Bindable var room: TeamRoom

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 6) {
                panelTab(.chat) { Text("Chat") }
                panelTab(.participants) {
                    HStack(spacing: 6) {
                        Text("Participants")
                        Text(verbatim: "\(room.participants.count)")
                            .font(.system(size: 12, weight: .bold))
                            .padding(.horizontal, 7)
                            .padding(.vertical, 1)
                            .background(Capsule().fill(RoomColor.paperMuted))
                    }
                }
            }
            .padding(16)
            if room.panel == .participants { participants } else { chat }
        }
        .foregroundStyle(RoomColor.ink)
    }

    private func panelTab(_ panel: TeamRoom.Panel, @ViewBuilder label: () -> some View) -> some View {
        let selected = room.panel == panel
        return Button { room.panel = panel } label: {
            label()
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(selected ? RoomColor.accent : RoomColor.inkMuted)
                .frame(maxWidth: .infinity, minHeight: 38)
                .background(RoundedRectangle(cornerRadius: 10).fill(selected ? RoomColor.tint : .clear))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? [.isSelected, .isButton] : .isButton)
    }

    private var participants: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 8) {
                    Image(systemName: "magnifyingglass").foregroundStyle(RoomColor.inkMuted)
                    TextField(String(localized: "Search for people"), text: $room.query)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }
                .padding(.horizontal, 12)
                .frame(height: 42)
                .background(RoundedRectangle(cornerRadius: 10).fill(RoomColor.paperMuted))
                sectionTitle(Text("On the call"))
                ForEach(room.participants.filter(room.matches)) { person in
                    rosterRow(person) {
                        if room.speaking(person) {
                            Image(systemName: "waveform")
                                .foregroundStyle(RoomColor.accent)
                                .frame(width: 36, height: 36)
                                .accessibilityLabel(Text("Speaking"))
                        } else {
                            Button { room.showOnStage(person.id); room.panelOpen = false } label: {
                                Image(systemName: "pin.fill")
                                    .foregroundStyle(RoomColor.inkMuted)
                                    .frame(width: 36, height: 36)
                            }
                            .accessibilityLabel(Text(verbatim: "\(String(localized: "Show on stage")): \(person.name)"))
                        }
                    }
                }
                if !room.invited.isEmpty {
                    sectionTitle(Text(verbatim: "\(String(localized: "Invited")) \(room.invited.count)"))
                    ForEach(room.invited.filter(room.matches)) { person in
                        rosterRow(person) {
                            Button { room.joined.append(person.id) } label: {
                                Text("Admit")
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundStyle(.white)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 7)
                                    .background(Capsule().fill(RoomColor.blue))
                            }
                            .buttonStyle(PressScaleStyle())
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 16)
        }
    }

    private func sectionTitle(_ text: Text) -> some View {
        text
            .font(.system(size: 12, weight: .semibold))
            .textCase(.uppercase)
            .foregroundStyle(RoomColor.inkMuted)
            .padding(.top, 8)
    }

    private func rosterRow(_ person: RoomPerson, @ViewBuilder trailing: () -> some View) -> some View {
        HStack(spacing: 12) {
            RoomAvatar(person: person, size: 36)
            VStack(alignment: .leading, spacing: 1) {
                Text(verbatim: person.id == "you" ? "\(person.name) (\(String(localized: "You")))" : person.name)
                    .font(.system(size: 15, weight: .semibold))
                    .lineLimit(1)
                Group {
                    if room.handUp(person) { Text("Hand raised") } else { Text(person.role) }
                }
                .font(.system(size: 13))
                .foregroundStyle(RoomColor.inkMuted)
            }
            Spacer(minLength: 0)
            trailing()
        }
    }

    private var chat: some View {
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        ForEach(room.messages) { message in
                            let person = RoomPerson.attendees.first { $0.id == message.from }!
                            HStack(alignment: .top, spacing: 10) {
                                RoomAvatar(person: person, size: 32)
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(verbatim: message.from == "you" ? String(localized: "You") : person.name)
                                        .font(.system(size: 13, weight: .semibold))
                                    Group {
                                        if let sample = message.sample { Text(sample) } else { Text(verbatim: message.text) }
                                    }
                                    .font(.system(size: 15))
                                    .foregroundStyle(RoomColor.ink3)
                                }
                                Spacer(minLength: 0)
                            }
                            .id(message.id)
                        }
                    }
                    .padding(.horizontal, 16)
                }
                .onChange(of: room.messages.count) {
                    if let last = room.messages.last { withAnimation { proxy.scrollTo(last.id, anchor: .bottom) } }
                }
            }
            HStack(spacing: 8) {
                TextField(String(localized: "Message everyone"), text: $room.draft)
                    .submitLabel(.send)
                    .onSubmit { room.sendMessage() }
                    .onChange(of: room.draft) { _, value in
                        if value.count > 500 { room.draft = String(value.prefix(500)) }
                    }
                    .padding(.horizontal, 12)
                    .frame(height: 42)
                    .background(RoundedRectangle(cornerRadius: 10).fill(RoomColor.paperMuted))
                Button { room.sendMessage() } label: {
                    Image(systemName: "paperplane.fill")
                        .foregroundStyle(.white)
                        .frame(width: 42, height: 42)
                        .background(Circle().fill(RoomColor.blue))
                }
                .disabled(room.draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                .opacity(room.draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? 0.5 : 1)
                .accessibilityLabel(Text("Send"))
            }
            .padding(.horizontal, 16)
            .padding(.top, 10)
            Text("Preview chat · Messages stay on this device")
                .font(.system(size: 12))
                .foregroundStyle(RoomColor.inkMuted)
                .padding(.vertical, 8)
        }
    }
}
