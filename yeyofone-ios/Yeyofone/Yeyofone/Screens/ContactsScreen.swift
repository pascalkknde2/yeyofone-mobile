//
//  ContactsScreen.swift
//  Yeyofone
//
//  Port of yeyofone-android ui/contacts/ContactsScreen.kt.
//

import SwiftUI

struct ContactsScreen: View {
    @Environment(AppStore.self) private var store
    @State private var query = ""
    @State private var editing: Contact?
    @State private var adding = false

    private var filtered: [Contact] {
        let trimmed = query.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return store.contacts }
        return store.contacts.filter {
            $0.displayName.localizedCaseInsensitiveContains(trimmed) || $0.number.localizedCaseInsensitiveContains(trimmed)
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            VStack(spacing: 4) {
                TabHeader(title: "Contacts") {
                    Button { adding = true } label: {
                        Image(systemName: "plus")
                            .font(.system(size: 20, weight: .medium))
                            .foregroundStyle(Palette.textPrimary)
                            .frame(width: 44, height: 44)
                    }
                    .accessibilityLabel("Add contact")
                }
                SearchField(placeholder: "Search contacts", text: $query)
                    .padding(.horizontal, 24)
                    .padding(.bottom, 16)
            }
            .padding(.top, 6)
            .background(Palette.cardWhite)

            if store.contacts.isEmpty {
                EmptyStateView(title: "No contacts yet", subtitle: "Tap + to add your first contact")
            } else if filtered.isEmpty {
                EmptyStateView(title: "No matches")
            } else {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        ForEach(filtered) { contact in
                            ContactCard(
                                contact: contact,
                                onCall: { store.startCall(to: contact.number) },
                                onToggleFavorite: { store.toggleFavorite(contact.id) },
                                onEdit: { editing = contact }
                            )
                        }
                    }
                    .padding(24)
                }
            }
        }
        .background(Palette.backgroundGray)
        .sheet(isPresented: $adding) {
            ContactEditSheet(contact: nil) { store.saveContact($0) }
        }
        .sheet(item: $editing) { contact in
            ContactEditSheet(contact: contact, onSave: { store.saveContact($0) }, onDelete: { store.deleteContact(contact.id) })
        }
    }
}

private struct ContactCard: View {
    let contact: Contact
    let onCall: () -> Void
    let onToggleFavorite: () -> Void
    let onEdit: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            InitialsAvatar(name: contact.displayName)
            VStack(alignment: .leading, spacing: 2) {
                Text(contact.displayName)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Palette.textPrimary)
                    .lineLimit(1)
                Text(contact.number)
                    .font(.system(size: 13))
                    .foregroundStyle(Palette.textSecondary)
            }
            .padding(.leading, 4)
            Spacer(minLength: 4)
            Button(action: onToggleFavorite) {
                Image(systemName: contact.favorite ? "star.fill" : "star")
                    .foregroundStyle(contact.favorite ? Palette.accentBlue : Palette.inactiveGray)
                    .frame(width: 36, height: 36)
            }
            .accessibilityLabel("Favorite")
            Button(action: onEdit) {
                Image(systemName: "pencil")
                    .foregroundStyle(Palette.inactiveGray)
                    .frame(width: 36, height: 36)
            }
            .accessibilityLabel("Edit contact")
            Button(action: onCall) {
                Image(systemName: "phone.fill")
                    .font(.system(size: 14))
                    .foregroundStyle(Palette.accentGreen)
                    .frame(width: 40, height: 40)
                    .background(Circle().fill(Palette.successLight))
            }
            .accessibilityLabel("Call")
        }
        .buttonStyle(.plain)
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 16).fill(Palette.cardWhite))
        .shadow(color: .black.opacity(0.05), radius: 3, y: 1)
    }
}

private struct ContactEditSheet: View {
    let contact: Contact?
    let onSave: (Contact) -> Void
    var onDelete: (() -> Void)?

    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var number = ""
    @State private var favorite = false
    @State private var error: String?
    @State private var confirmDelete = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Name", text: $name)
                        .textContentType(.name)
                    TextField("Number", text: $number)
                        .keyboardType(.phonePad)
                    Toggle("Favorite", isOn: $favorite)
                        .tint(Palette.accentGreen)
                } footer: {
                    if let error { Text(error).foregroundStyle(Palette.accentRed) }
                }
                if onDelete != nil {
                    Section {
                        Button("Delete", role: .destructive) { confirmDelete = true }
                    }
                }
            }
            .navigationTitle(contact == nil ? Text("Add Contact") : Text("Edit Contact"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        let trimmedName = name.trimmingCharacters(in: .whitespaces)
                        let trimmedNumber = number.trimmingCharacters(in: .whitespaces)
                        guard !trimmedName.isEmpty, !trimmedNumber.isEmpty else {
                            error = String(localized: "Name and number are required")
                            return
                        }
                        onSave(Contact(id: contact?.id ?? UUID(), displayName: trimmedName, number: trimmedNumber, favorite: favorite))
                        dismiss()
                    }
                    .fontWeight(.semibold)
                }
            }
            .alert("Delete this contact?", isPresented: $confirmDelete) {
                Button("Delete", role: .destructive) { onDelete?(); dismiss() }
                Button("Cancel", role: .cancel) {}
            }
        }
        .tint(Palette.accentBlue)
        .presentationDetents([.medium, .large])
        .onAppear {
            name = contact?.displayName ?? ""
            number = contact?.number ?? ""
            favorite = contact?.favorite ?? false
        }
    }
}
