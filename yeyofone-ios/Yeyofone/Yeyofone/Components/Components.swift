//
//  Components.swift
//  Yeyofone
//
//  Shared building blocks, ported from yeyofone-android's ui/components.
//

import SwiftUI

// MARK: Bottom navigation

struct BottomNavigationBar: View {
    let selected: Tab?
    let onSelect: (Tab) -> Void

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Tab.visible, id: \.self) { tab in
                let isSelected = tab == selected
                Button { onSelect(tab) } label: {
                    Image(systemName: isSelected ? tab.selectedIcon : tab.icon)
                        .font(.system(size: 19, weight: .medium))
                        .foregroundStyle(isSelected ? Color.white : Palette.inactiveGray)
                        .frame(width: 44, height: 44)
                        .background(Circle().fill(isSelected ? Palette.accentBlue : .clear))
                        .shadow(color: isSelected ? Palette.accentBlue.opacity(0.3) : .clear, radius: 4, y: 2)
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .contentShape(Rectangle())
                }
                .buttonStyle(PressScaleStyle())
                .accessibilityLabel(tab.label)
                .accessibilityAddTraits(isSelected ? .isSelected : [])
            }
        }
        .padding(.vertical, 8)
        .padding(.horizontal, 6)
        .background(Capsule().fill(Palette.cardWhite))
        .overlay(Capsule().stroke(Palette.borderLight.opacity(0.6), lineWidth: 1))
        .shadow(color: .black.opacity(0.08), radius: 10, y: 4)
        .padding(.horizontal, 20)
        .padding(.top, 12)
        .padding(.bottom, 8)
        .animation(.easeInOut(duration: 0.2), value: selected)
    }
}

extension Tab {
    var label: String {
        switch self {
        case .home: String(localized: "Home")
        case .keypad: String(localized: "Keypad")
        case .contacts: String(localized: "Contacts")
        case .calls: String(localized: "Calls")
        case .chat: String(localized: "Chat")
        case .settings: String(localized: "Settings")
        }
    }

    var icon: String {
        switch self {
        case .home: "house"
        case .keypad: "circle.grid.3x3"
        case .contacts: "person"
        case .calls: "phone"
        case .chat: "bubble.left"
        case .settings: "gearshape"
        }
    }

    var selectedIcon: String {
        switch self {
        case .keypad: "circle.grid.3x3.fill"
        default: icon + ".fill"
        }
    }
}

// MARK: Dial pad

private struct DialKey: Hashable {
    let number: String
    var letters: String?
}

private let dialKeys: [[DialKey]] = [
    [DialKey(number: "1"), DialKey(number: "2", letters: "ABC"), DialKey(number: "3", letters: "DEF")],
    [DialKey(number: "4", letters: "GHI"), DialKey(number: "5", letters: "JKL"), DialKey(number: "6", letters: "MNO")],
    [DialKey(number: "7", letters: "PQRS"), DialKey(number: "8", letters: "TUV"), DialKey(number: "9", letters: "WXYZ")],
    [DialKey(number: "*"), DialKey(number: "0", letters: "+"), DialKey(number: "#")],
]

struct DialPad: View {
    var rowSpacing: CGFloat = 12
    let onKey: (String) -> Void

    var body: some View {
        VStack(spacing: rowSpacing) {
            ForEach(dialKeys, id: \.self) { row in
                HStack {
                    ForEach(row, id: \.self) { key in
                        DialKeyButton(key: key) { onKey(key.number) }
                            .frame(maxWidth: .infinity)
                    }
                }
            }
        }
    }
}

private struct DialKeyButton: View {
    let key: DialKey
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 2) {
                Text(key.number)
                    .font(.system(size: ["*", "0", "#"].contains(key.number) ? 28 : 24, weight: .medium))
                    .foregroundStyle(Palette.textPrimary)
                if let letters = key.letters {
                    Text(letters)
                        .font(.system(size: key.number == "0" ? 10 : 9, weight: .semibold))
                        .tracking(1)
                        .foregroundStyle(Palette.inactiveGray)
                }
            }
            .frame(width: 72, height: 72)
        }
        .buttonStyle(DialKeyStyle())
        .accessibilityLabel(key.number)
    }
}

private struct DialKeyStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(Circle().fill(configuration.isPressed ? Palette.keypadBackground : Palette.cardWhite))
            .overlay(Circle().stroke(Palette.borderLight.opacity(0.4), lineWidth: 1))
            .shadow(color: .black.opacity(configuration.isPressed ? 0.04 : 0.08), radius: configuration.isPressed ? 1 : 3, y: 1)
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .animation(.easeOut(duration: 0.1), value: configuration.isPressed)
    }
}

/// The large typed number with its backspace button, shared by the dial, transfer and consult screens.
struct DialedNumberDisplay: View {
    let number: String
    var placeholder: LocalizedStringKey = "Enter number"
    let onBackspace: () -> Void

    var body: some View {
        VStack(spacing: 8) {
            (number.isEmpty ? Text(placeholder) : Text(verbatim: number))
                .font(.system(size: number.isEmpty ? 16 : 32, weight: number.isEmpty ? .regular : .medium))
                .tracking(number.isEmpty ? 0 : 1)
                .foregroundStyle(number.isEmpty ? Palette.inactiveGray : Palette.textPrimary)
                .lineLimit(2)
                .multilineTextAlignment(.center)
                .minimumScaleFactor(0.6)
            Button(action: onBackspace) {
                Image(systemName: "delete.left")
                    .font(.system(size: 24))
                    .foregroundStyle(Palette.textSecondary)
                    .frame(width: 48, height: 48)
            }
            .opacity(number.isEmpty ? 0 : 1)
            .disabled(number.isEmpty)
            .accessibilityLabel("Backspace")
        }
    }
}

// MARK: Call action buttons

/// Large filled circle with an uppercase caption: Accept, Decline, Call Again, Transfer now…
struct PrimaryCallActionButton: View {
    let icon: String
    let label: LocalizedStringKey
    let color: Color
    var enabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 16) {
                Image(systemName: icon)
                    .font(.system(size: 28, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 72, height: 72)
                    .background(Circle().fill(color))
                    .shadow(color: color.opacity(0.3), radius: 8, y: 4)
                Text(label)
                    .font(.system(size: 14, weight: .semibold))
                    .tracking(0.5)
                    .textCase(.uppercase)
                    .foregroundStyle(Palette.textSecondary)
            }
        }
        .buttonStyle(PressScaleStyle())
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.45)
    }
}

/// Small white circle with a caption underneath: Message, Remind Me, Return to caller…
struct SecondaryCallActionButton: View {
    let icon: String
    let label: LocalizedStringKey
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.system(size: 20))
                    .foregroundStyle(Palette.textPrimary)
                    .frame(width: 56, height: 56)
                    .background(Circle().fill(Color.white))
                    .shadow(color: .black.opacity(0.1), radius: 4, y: 2)
                Text(label)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(Palette.textSecondary)
            }
        }
        .buttonStyle(PressScaleStyle())
    }
}

/// White tray with rounded top corners holding a call screen's main actions.
struct CallTray<Content: View>: View {
    var background: Color = Palette.backgroundGray
    @ViewBuilder let content: Content

    var body: some View {
        VStack(spacing: 0) { content }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 24)
            .padding(.top, 32)
            .padding(.bottom, 24)
            .background(
                TopRoundedTray()
                    .fill(background)
                    .shadow(color: .black.opacity(0.08), radius: 4, y: -2)
                    .ignoresSafeArea(edges: .bottom)
            )
    }
}

// MARK: Headers and avatars

/// Back arrow + bold title, used by settings sub-pages.
struct BackHeader: View {
    let title: LocalizedStringKey
    let onBack: () -> Void

    var body: some View {
        HStack(spacing: 4) {
            Button(action: onBack) {
                Image(systemName: "arrow.left")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(Palette.textPrimary)
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel("Back")
            Text(title)
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(Palette.textPrimary)
            Spacer()
        }
        .padding(.leading, 8)
        .padding(.trailing, 20)
        .padding(.vertical, 6)
        .background(Palette.cardWhite)
    }
}

/// Back arrow, centered title and an empty balance slot, used by the in-call sub-screens.
struct CenteredBackHeader: View {
    let title: LocalizedStringKey
    var icon = "arrow.left"
    let onBack: () -> Void

    var body: some View {
        HStack {
            Button(action: onBack) {
                Image(systemName: icon)
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(Palette.textPrimary)
                    .frame(width: 48, height: 48)
            }
            Spacer()
            Text(title)
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(Palette.textPrimary)
            Spacer()
            Color.clear.frame(width: 48, height: 48)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
    }
}

/// Large bold title with an optional trailing icon button, used by the tab screens.
struct TabHeader<Trailing: View>: View {
    let title: LocalizedStringKey
    @ViewBuilder var trailing: Trailing

    var body: some View {
        HStack {
            Text(title)
                .font(.system(size: 24, weight: .bold))
                .tracking(-0.5)
                .foregroundStyle(Palette.textPrimary)
            Spacer()
            trailing
        }
        .padding(.leading, 24)
        .padding(.trailing, 12)
        .padding(.vertical, 10)
    }
}

struct InitialsAvatar: View {
    let name: String
    var size: CGFloat = 48
    var fontSize: CGFloat = 16
    var background: Color = Palette.primaryLight
    var foreground: Color = Palette.textPrimary

    var body: some View {
        Text(name.initials)
            .font(.system(size: fontSize, weight: .bold))
            .foregroundStyle(foreground)
            .frame(width: size, height: size)
            .background(Circle().fill(background))
    }
}

struct IconTile: View {
    let icon: String
    let tint: Color
    var background: Color?
    var size: CGFloat = 40
    var corner: CGFloat = 12

    var body: some View {
        Image(systemName: icon)
            .font(.system(size: size * 0.45, weight: .medium))
            .foregroundStyle(tint)
            .frame(width: size, height: size)
            .background(RoundedRectangle(cornerRadius: corner).fill(background ?? tint.opacity(0.08)))
    }
}

// MARK: Grouped rows (settings-style)

struct GroupSection<Content: View>: View {
    let title: LocalizedStringKey
    var bordered = false
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .sectionLabelStyle()
                .padding(.leading, 4)
            VStack(spacing: 0) { content }
                .background(RoundedRectangle(cornerRadius: 20).fill(Palette.cardWhite))
                .overlay {
                    if bordered {
                        RoundedRectangle(cornerRadius: 20).stroke(Palette.borderLight.opacity(0.55), lineWidth: 1)
                    }
                }
        }
    }
}

struct RowDivider: View {
    var leading: CGFloat = 72

    var body: some View {
        Rectangle()
            .fill(Palette.borderLight.opacity(0.55))
            .frame(height: 1)
            .padding(.leading, leading)
    }
}

/// Icon tile, title and subtitle, with a trailing chevron or custom accessory.
struct IconRow<Accessory: View>: View {
    let icon: String
    let tint: Color
    var tileBackground: Color?
    let title: LocalizedStringKey
    let subtitle: String
    @ViewBuilder var accessory: Accessory

    var body: some View {
        HStack(spacing: 16) {
            IconTile(icon: icon, tint: tint, background: tileBackground)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Palette.textPrimary)
                Text(subtitle)
                    .font(.system(size: 13))
                    .foregroundStyle(Palette.textSecondary)
            }
            Spacer(minLength: 8)
            accessory
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .contentShape(Rectangle())
    }
}

extension IconRow where Accessory == Chevron {
    init(icon: String, tint: Color, tileBackground: Color? = nil, title: LocalizedStringKey, subtitle: String) {
        self.init(icon: icon, tint: tint, tileBackground: tileBackground, title: title, subtitle: subtitle) { Chevron() }
    }
}

struct Chevron: View {
    var body: some View {
        Image(systemName: "chevron.right")
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(Palette.inactiveGray)
    }
}

/// Icon row with a green switch, used by incoming-call and language settings.
struct ToggleRow: View {
    let icon: String
    let tint: Color
    let tileBackground: Color
    let title: LocalizedStringKey
    let subtitle: LocalizedStringResource
    @Binding var isOn: Bool

    var body: some View {
        IconRow(icon: icon, tint: tint, tileBackground: tileBackground, title: title, subtitle: String(localized: subtitle)) {
            Toggle("", isOn: $isOn)
                .labelsHidden()
                .tint(Palette.accentGreen)
        }
    }
}

/// Segmented control styled like the Android tab chips (Recents, Accounts).
struct ChipTabs: View {
    let titles: [String]
    @Binding var selection: Int
    var fontSize: CGFloat = 14

    var body: some View {
        HStack(spacing: 4) {
            ForEach(titles.indices, id: \.self) { index in
                let selected = selection == index
                Button { selection = index } label: {
                    Text(titles[index])
                        .font(.system(size: fontSize, weight: .semibold))
                        .foregroundStyle(selected ? Palette.accentBlue : Palette.textSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(RoundedRectangle(cornerRadius: 8).fill(selected ? Palette.primaryLight : .clear))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(4)
        .background(RoundedRectangle(cornerRadius: 12).fill(Palette.backgroundGray))
        .animation(.easeInOut(duration: 0.15), value: selection)
    }
}

struct SearchField: View {
    let placeholder: LocalizedStringKey
    @Binding var text: String
    var background: Color = Palette.backgroundGray

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass").foregroundStyle(Palette.inactiveGray)
            TextField(placeholder, text: $text)
                .font(.system(size: 15))
                .foregroundStyle(Palette.textPrimary)
                .autocorrectionDisabled()
            if !text.isEmpty {
                Button { text = "" } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(Palette.inactiveGray)
                }
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(RoundedRectangle(cornerRadius: 14).fill(background))
    }
}

struct EmptyStateView: View {
    let title: LocalizedStringKey
    var subtitle: LocalizedStringKey?

    var body: some View {
        VStack(spacing: 8) {
            Text(title)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Palette.textSecondary)
            if let subtitle {
                Text(subtitle)
                    .font(.system(size: 14))
                    .foregroundStyle(Palette.inactiveGray)
            }
        }
        .multilineTextAlignment(.center)
        .padding(40)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// Short-lived message at the bottom of the screen, standing in for Android's Snackbar.
struct ToastModifier: ViewModifier {
    @Binding var message: String?

    func body(content: Content) -> some View {
        content.overlay(alignment: .bottom) {
            if let message {
                Text(message)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 12)
                    .background(Capsule().fill(Palette.textPrimary.opacity(0.92)))
                    .padding(.bottom, 100)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .task(id: message) {
                        try? await Task.sleep(for: .seconds(2))
                        withAnimation { self.message = nil }
                    }
            }
        }
        .animation(.spring(duration: 0.3), value: message)
    }
}

extension View {
    func toast(_ message: Binding<String?>) -> some View { modifier(ToastModifier(message: message)) }
}
