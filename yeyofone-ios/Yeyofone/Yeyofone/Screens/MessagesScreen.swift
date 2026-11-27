//
//  MessagesScreen.swift
//  Yeyofone
//
//  The Chat tab: pick a contact to open (or carry on) a conversation with them.
//  Port of yeyofone-android ui/chat/MessagesScreen.kt.
//

import SwiftUI

struct MessagesScreen: View {
    @Environment(AppStore.self) private var store
    @State private var query = ""

    /// People you've written to come first, then everyone else by name.
    private var sorted: [Contact] {
        store.contacts.sorted { a, b in
            let aTalked = lastMessage(for: a) != nil
            let bTalked = lastMessage(for: b) != nil
            if aTalked != bTalked { return aTalked }
            return a.displayName.localizedCaseInsensitiveCompare(b.displayName) == .orderedAscending
        }
    }

    private var filtered: [Contact] {
        let trimmed = query.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return sorted }
        return sorted.filter {
            $0.displayName.localizedCaseInsensitiveContains(trimmed) || $0.number.localizedCaseInsensitiveContains(trimmed)
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            VStack(spacing: 4) {
                TabHeader(title: "Chat") { EmptyView() }
                    .frame(minHeight: 44)
                SearchField(placeholder: "Search contacts", text: $query)
                    .padding(.horizontal, 24)
                    .padding(.bottom, 8)
            }
            .padding(.top, 6)

            if store.contacts.isEmpty {
                EmptyStateView(title: "No contacts yet", subtitle: "Add people in Contacts to start a conversation")
            } else if filtered.isEmpty {
                EmptyStateView(title: "No matches")
            } else {
                ScrollView {
                    LazyVStack(spacing: 10) {
                        ForEach(filtered) { contact in
                            ConversationRow(contact: contact, lastMessage: lastMessage(for: contact)) {
                                store.openChat(number: contact.number, name: contact.displayName)
                            }
                        }
                    }
                    .padding(.horizontal, 24)
                    .padding(.vertical, 16)
                }
            }
        }
    }

    private func lastMessage(for contact: Contact) -> ChatMessage? {
        store.chatConversations[contact.number.trimmingCharacters(in: .whitespaces)]?.last
    }
}

private struct ConversationRow: View {
    let contact: Contact
    let lastMessage: ChatMessage?
    let onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) {
            HStack(spacing: 14) {
                InitialsAvatar(name: contact.displayName)
                VStack(alignment: .leading, spacing: 2) {
                    Text(contact.displayName)
                        .appFont(size: 15, weight: .semibold)
                        .foregroundStyle(Palette.textPrimary)
                        .lineLimit(1)
                    Text(lastMessage?.content ?? contact.number)
                        .appFont(size: 13)
                        .foregroundStyle(Palette.textSecondary)
                        .lineLimit(1)
                }
                Spacer(minLength: 4)
                if let lastMessage {
                    Text(lastMessage.timestamp)
                        .appFont(size: 12)
                        .foregroundStyle(Palette.inactiveGray)
                } else {
                    Image(systemName: "bubble.left.fill")
                        .appFont(size: 16)
                        .foregroundStyle(Palette.accentBlue)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(RoundedRectangle(cornerRadius: 16).fill(Palette.glass))
            .contentShape(RoundedRectangle(cornerRadius: 16))
            .shadow(color: .black.opacity(0.05), radius: 3, y: 1)
        }
        .buttonStyle(.plain)
        .accessibilityHint("Message contact")
    }
}
