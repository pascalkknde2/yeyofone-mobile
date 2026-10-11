//
//  Theme.swift
//  Yeyofone
//
//  Palette and shared styling, matching yeyofone-android's ui/theme (Color.kt, Theme.kt).
//

import SwiftUI

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
    }
}

extension Color {
    /// A color that follows the current light/dark appearance.
    init(light: UInt32, dark: UInt32) {
        self.init(UIColor { traits in
            let hex = traits.userInterfaceStyle == .dark ? dark : light
            return UIColor(
                red: CGFloat((hex >> 16) & 0xFF) / 255,
                green: CGFloat((hex >> 8) & 0xFF) / 255,
                blue: CGFloat(hex & 0xFF) / 255,
                alpha: 1
            )
        })
    }
}

/// The supplied light design, with a slate-based dark counterpart for each color.
enum Palette {
    static let backgroundGray = Color(light: 0xF4F7F9, dark: 0x0B1120)
    static let keypadBackground = Color(light: 0xF8FAFC, dark: 0x0F172A)
    static let cardWhite = Color(light: 0xFFFFFF, dark: 0x1E293B)
    static let textPrimary = Color(light: 0x0F172A, dark: 0xF1F5F9)
    /// Text on a `textPrimary` fill: white in light mode, dark slate in dark mode.
    static let textOnPrimary = Color(light: 0xFFFFFF, dark: 0x0F172A)
    static let textSecondary = Color(light: 0x64748B, dark: 0x94A3B8)
    static let inactiveGray = Color(light: 0x94A3B8, dark: 0x64748B)
    static let accentBlue = Color(light: 0x2563EB, dark: 0x3B82F6)
    static let primaryLight = Color(light: 0xEFF6FF, dark: 0x1E3A5F)
    static let primaryBorder = Color(light: 0xBFDBFE, dark: 0x1D4ED8)
    static let accentGreen = Color(light: 0x10B981, dark: 0x34D399)
    static let successLight = Color(light: 0xECFDF5, dark: 0x064E3B)
    static let accentRed = Color(light: 0xEF4444, dark: 0xF87171)
    static let dangerLight = Color(light: 0xFEF2F2, dark: 0x450A0A)
    static let accentOrange = Color(light: 0xF97316, dark: 0xFB923C)
    static let orangeLight = Color(light: 0xFFF7ED, dark: 0x431407)
    static let borderLight = Color(light: 0xE2E8F0, dark: 0x334155)
    static let radioBorder = Color(light: 0xCBD5E1, dark: 0x475569)
    static let cardGradientStart = Color(light: 0x1E293B, dark: 0x334155)
    static let cardGradientEnd = Color(light: 0x0F172A, dark: 0x1E293B)
    static let cardAccent = Color(hex: 0x3B82F6)
    /// Unselected bottom navigation icons: gray in light mode, white in dark mode.
    static let navInactive = Color(light: 0x94A3B8, dark: 0xFFFFFF)
    static let purple = Color(light: 0x8B5CF6, dark: 0xA78BFA)
    static let purpleLight = Color(light: 0xF5F3FF, dark: 0x2E1065)
    static let pink = Color(light: 0xDB2777, dark: 0xF472B6)
    static let pinkLight = Color(light: 0xFDF2F8, dark: 0x500724)
}

/// The user's light/dark choice, stored under `appearance` in UserDefaults.
enum Appearance: String, CaseIterable, Identifiable {
    case system, light, dark

    var id: String { rawValue }

    var colorScheme: ColorScheme? {
        switch self {
        case .system: nil
        case .light: .light
        case .dark: .dark
        }
    }

    var title: String {
        switch self {
        case .system: String(localized: "System")
        case .light: String(localized: "Light")
        case .dark: String(localized: "Dark")
        }
    }
}

extension String {
    /// Up to two uppercase initials, or "?" when there's nothing to show.
    var initials: String {
        let words = split(whereSeparator: { $0 == " " || $0 == "." || $0 == "-" || $0 == "_" })
        let letters = words.prefix(2).compactMap(\.first).map { String($0).uppercased() }.joined()
        return letters.isEmpty ? "?" : letters
    }
}

extension View {
    /// Uppercased small caption used for section labels throughout the app.
    func sectionLabelStyle(color: Color = Palette.inactiveGray, size: CGFloat = 12, tracking: CGFloat = 1) -> some View {
        font(.system(size: size, weight: .bold))
            .tracking(tracking)
            .foregroundStyle(color)
            .textCase(.uppercase)
    }
}

/// Rounded top-only tray used at the bottom of the call screens.
struct TopRoundedTray: Shape {
    var radius: CGFloat = 32

    func path(in rect: CGRect) -> Path {
        Path(
            roundedRect: rect,
            cornerRadii: RectangleCornerRadii(topLeading: radius, bottomLeading: 0, bottomTrailing: 0, topTrailing: radius)
        )
    }
}

/// Plain press feedback without the default highlight, for custom round buttons.
struct PressScaleStyle: ButtonStyle {
    var scale: CGFloat = 0.96

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? scale : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}

nonisolated enum AppInfo {
    static var version: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0"
    }

    static var build: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "1"
    }
}
