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

/// The supplied design uses one consistent light palette on every screen.
enum Palette {
    static let backgroundGray = Color(hex: 0xF4F7F9)
    static let keypadBackground = Color(hex: 0xF8FAFC)
    static let cardWhite = Color.white
    static let textPrimary = Color(hex: 0x0F172A)
    static let textSecondary = Color(hex: 0x64748B)
    static let inactiveGray = Color(hex: 0x94A3B8)
    static let accentBlue = Color(hex: 0x2563EB)
    static let primaryLight = Color(hex: 0xEFF6FF)
    static let accentGreen = Color(hex: 0x10B981)
    static let successLight = Color(hex: 0xECFDF5)
    static let accentRed = Color(hex: 0xEF4444)
    static let dangerLight = Color(hex: 0xFEF2F2)
    static let accentOrange = Color(hex: 0xF97316)
    static let orangeLight = Color(hex: 0xFFF7ED)
    static let borderLight = Color(hex: 0xE2E8F0)
    static let cardGradientStart = Color(hex: 0x1E293B)
    static let cardGradientEnd = Color(hex: 0x0F172A)
    static let cardAccent = Color(hex: 0x3B82F6)
    static let purple = Color(hex: 0x8B5CF6)
    static let purpleLight = Color(hex: 0xF5F3FF)
    static let pink = Color(hex: 0xDB2777)
    static let pinkLight = Color(hex: 0xFDF2F8)
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

enum AppInfo {
    static var version: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0"
    }
}
