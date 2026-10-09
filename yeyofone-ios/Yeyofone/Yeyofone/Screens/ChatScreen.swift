//
//  ChatScreen.swift
//  Yeyofone
//
//  Port of yeyofone-android ui/chat/ChatScreen.kt and components/ChatComponents.kt.
//  Chat isn't implemented on Android either; this is the same demo conversation.
//

import SwiftUI

struct ChatScreen: View {
    @Environment(AppStore.self) private var store
    @State private var input = ""
    @State private var toast: String?

    var body: some View {
        VStack(spacing: 0) {
            header
            Divider().overlay(Palette.borderLight)
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 4) {
                        Text("TODAY")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(Palette.textSecondary)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 4)
                            .background(Capsule().fill(Palette.borderLight))
                            .padding(.vertical, 8)
                        ForEach(store.chatMessages) { message in
                            MessageBubble(message: message) { toast = String(localized: "Coming soon") }
                                .id(message.id)
                        }
                        if store.contactTyping {
                            TypingIndicator().id("typing")
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 18)
                }
                .defaultScrollAnchor(.bottom)
                .scrollDismissesKeyboard(.interactively)
                .onChange(of: store.chatMessages.count) {
                    withAnimation { proxy.scrollTo(store.chatMessages.last?.id, anchor: .bottom) }
                }
            }
            .background(Palette.backgroundGray)
            Divider().overlay(Palette.borderLight)
            inputBar
        }
        .toast($toast)
    }

    private var header: some View {
        HStack(spacing: 10) {
            Button { store.screen = .home } label: {
                Image(systemName: "arrow.left").frame(width: 40, height: 40)
            }
            .accessibilityLabel("Back")
            InitialsAvatar(name: store.chatContactName, size: 40, fontSize: 14)
                .overlay(alignment: .bottomTrailing) {
                    if store.chatContactOnline {
                        Circle().fill(Palette.accentGreen).frame(width: 12, height: 12)
                            .overlay(Circle().stroke(.white, lineWidth: 2))
                    }
                }
            VStack(alignment: .leading, spacing: 1) {
                Text(store.chatContactName)
                    .font(.system(size: 16, weight: .semibold))
                    .lineLimit(1)
                (store.chatContactOnline ? Text("Online") : Text("Offline"))
                    .font(.system(size: 12))
                    .foregroundStyle(store.chatContactOnline ? Palette.accentGreen : Palette.textSecondary)
            }
            Spacer()
            Button { store.startCall(to: store.contacts.first { $0.displayName == store.chatContactName }?.number ?? store.chatContactName) } label: {
                Image(systemName: "phone.fill").frame(width: 40, height: 40)
            }
            .accessibilityLabel("Voice call")
            Button { toast = String(localized: "Coming soon") } label: {
                Image(systemName: "video.fill").frame(width: 40, height: 40)
            }
            .accessibilityLabel("Video call")
        }
        .font(.system(size: 17, weight: .medium))
        .foregroundStyle(Palette.textPrimary)
        .padding(.horizontal, 8)
        .padding(.vertical, 8)
        .background(Color.white)
    }

    private var inputBar: some View {
        let canSend = !input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        return HStack(alignment: .bottom, spacing: 10) {
            Button { toast = String(localized: "Coming soon") } label: {
                Image(systemName: "paperclip")
                    .foregroundStyle(Palette.textSecondary)
                    .frame(width: 40, height: 40)
                    .background(Circle().fill(Palette.backgroundGray))
            }
            .accessibilityLabel("Attach file")
            TextField("Type a message…", text: $input, axis: .vertical)
                .font(.system(size: 15))
                .lineLimit(1...5)
                .tint(Palette.accentBlue)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(RoundedRectangle(cornerRadius: 22).fill(Palette.backgroundGray))
                .onSubmit(send)
            Button(action: send) {
                Image(systemName: "paperplane.fill")
                    .foregroundStyle(.white)
                    .frame(width: 40, height: 40)
                    .background(Circle().fill(canSend ? Palette.accentBlue : Palette.inactiveGray))
            }
            .disabled(!canSend)
            .accessibilityLabel("Send message")
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(Color.white)
    }

    private func send() {
        store.sendChatMessage(input)
        input = ""
    }
}

private struct MessageBubble: View {
    let message: ChatMessage
    let onPlayVoice: () -> Void

    var body: some View {
        let outgoing = message.isOutgoing
        let foreground: Color = outgoing ? .white : Palette.textPrimary
        let meta: Color = outgoing ? .white.opacity(0.78) : Palette.inactiveGray
        let shape = UnevenRoundedRectangle(
            topLeadingRadius: 18,
            bottomLeadingRadius: outgoing ? 18 : 4,
            bottomTrailingRadius: outgoing ? 4 : 18,
            topTrailingRadius: 18
        )
        HStack {
            if outgoing { Spacer(minLength: 60) }
            VStack(alignment: .trailing, spacing: 4) {
                if message.isVoice {
                    voiceMessage(foreground: foreground)
                } else {
                    Text(message.content)
                        .font(.system(size: 15))
                        .foregroundStyle(foreground)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                HStack(spacing: 3) {
                    Text(message.timestamp).font(.system(size: 10))
                    if outgoing {
                        Image(systemName: message.status == .sent ? "checkmark" : "checkmark.circle.fill")
                            .font(.system(size: 10, weight: .semibold))
                            .accessibilityLabel("Message status")
                    }
                }
                .foregroundStyle(meta)
            }
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(shape.fill(outgoing ? Palette.accentBlue : .white))
            .shadow(color: .black.opacity(outgoing ? 0.12 : 0.06), radius: outgoing ? 4 : 2, y: 1)
            .frame(maxWidth: 280, alignment: outgoing ? .trailing : .leading)
            if !outgoing { Spacer(minLength: 60) }
        }
        .padding(.vertical, 2)
    }

    private func voiceMessage(foreground: Color) -> some View {
        HStack(spacing: 10) {
            Button(action: onPlayVoice) {
                Image(systemName: "play.fill")
                    .font(.system(size: 12))
                    .foregroundStyle(message.isOutgoing ? .white : Palette.accentBlue)
                    .frame(width: 32, height: 32)
                    .background(Circle().fill(message.isOutgoing ? .white.opacity(0.22) : Palette.accentBlue.opacity(0.14)))
            }
            .accessibilityLabel("Play voice message")
            HStack(spacing: 2) {
                ForEach(Array(message.waveformHeights.enumerated()), id: \.offset) { _, height in
                    RoundedRectangle(cornerRadius: 2)
                        .fill(message.isOutgoing ? .white.opacity(0.55) : Palette.accentBlue.opacity(0.45))
                        .frame(width: 3, height: height)
                }
            }
            .frame(height: 20)
            Text(message.content)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(foreground)
        }
        .frame(minWidth: 190)
    }
}

private struct TypingIndicator: View {
    var body: some View {
        HStack {
            TimelineView(.animation) { context in
                let time = context.date.timeIntervalSinceReferenceDate
                HStack(spacing: 4) {
                    ForEach(0..<3) { index in
                        Circle()
                            .fill(Palette.inactiveGray)
                            .frame(width: 7, height: 7)
                            .offset(y: offset(time: time, delay: Double(index) * 0.18))
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(UnevenRoundedRectangle(topLeadingRadius: 18, bottomLeadingRadius: 4, bottomTrailingRadius: 18, topTrailingRadius: 18).fill(.white))
            .shadow(color: .black.opacity(0.06), radius: 2, y: 1)
            Spacer()
        }
        .padding(.vertical, 4)
    }

    /// Each dot hops up 5pt over the first 0.5s of a 1.2s cycle, like Android's keyframes.
    private func offset(time: Double, delay: Double) -> CGFloat {
        let phase = (time - delay).truncatingRemainder(dividingBy: 1.2)
        let t = phase < 0 ? phase + 1.2 : phase
        guard t < 0.5 else { return 0 }
        return t < 0.25 ? -5 * (t / 0.25) : -5 * (1 - (t - 0.25) / 0.25)
    }
}
