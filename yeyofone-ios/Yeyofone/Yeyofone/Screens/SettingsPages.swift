//
//  SettingsPages.swift
//  Yeyofone
//
//  Settings pages that replaced popups (confirmation dialogs, sheets and "Coming soon" toasts),
//  matching yeyofone-android's ui/settings/SettingsPages.kt.
//

import SwiftUI

// MARK: Shared pieces

/// A page with a back button and scrolling content, like the other settings pages.
struct SettingsPageLayout<Content: View>: View {
    let title: LocalizedStringKey
    let onBack: () -> Void
    @ViewBuilder let content: Content

    var body: some View {
        VStack(spacing: 0) {
            BackHeader(title: title, onBack: onBack)
            ScrollView {
                VStack(spacing: 20) { content }
                    .padding(.horizontal, 20)
                    .padding(.top, 24)
                    .padding(.bottom, 32)
            }
        }
        .background(Palette.backgroundGray)
    }
}

/// One choice in a list; the chosen one shows a check mark.
struct ChoiceRow: View {
    let title: String
    var subtitle: String?
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: title)
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(Palette.textPrimary)
                    if let subtitle {
                        Text(verbatim: subtitle)
                            .font(.system(size: 13))
                            .foregroundStyle(Palette.textSecondary)
                    }
                }
                Spacer(minLength: 0)
                if selected {
                    Image(systemName: "checkmark")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Palette.accentBlue)
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 15)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? [.isSelected, .isButton] : .isButton)
    }
}

/// A short explanatory paragraph under or between sections.
struct PageNote: View {
    let text: LocalizedStringKey

    var body: some View {
        Text(text)
            .font(.system(size: 13))
            .foregroundStyle(Palette.textSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 4)
    }
}

/// The page's main action, full width.
struct PageButton: View {
    let title: LocalizedStringKey
    var color = Palette.accentBlue
    var enabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity, minHeight: 52)
                .background(RoundedRectangle(cornerRadius: 16).fill(color.opacity(enabled ? 1 : 0.4)))
        }
        .buttonStyle(PressScaleStyle(scale: 0.98))
        .disabled(!enabled)
    }
}

private struct HeroCard: View {
    let icon: String
    let title: LocalizedStringKey
    let subtitle: LocalizedStringKey

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 48, height: 48)
                .background(RoundedRectangle(cornerRadius: 14).fill(.white.opacity(0.18)))
            Text(title)
                .font(.system(size: 22, weight: .bold))
                .foregroundStyle(.white)
            Text(subtitle)
                .font(.system(size: 14))
                .foregroundStyle(.white.opacity(0.9))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(22)
        .background(
            RoundedRectangle(cornerRadius: 24)
                .fill(LinearGradient(colors: [Palette.cardGradientStart, Palette.cardGradientEnd],
                                     startPoint: .topLeading, endPoint: .bottomTrailing))
        )
    }
}

private struct Feature {
    let icon: String
    let tint: Color
    let background: Color
    let title: LocalizedStringKey
    let subtitle: String
}

private struct FeatureList: View {
    let title: LocalizedStringKey
    let features: [Feature]

    var body: some View {
        GroupSection(title: title) {
            ForEach(Array(features.enumerated()), id: \.offset) { index, feature in
                if index > 0 { RowDivider() }
                IconRow(icon: feature.icon, tint: feature.tint, tileBackground: feature.background,
                        title: feature.title, subtitle: feature.subtitle) { EmptyView() }
            }
        }
    }
}

// MARK: Appearance

struct AppearanceScreen: View {
    @Environment(AppStore.self) private var store
    @AppStorage("appearance") private var appearance = Appearance.system

    private func subtitle(_ option: Appearance) -> String {
        switch option {
        case .system: String(localized: "Match your phone's setting")
        case .light: String(localized: "Light backgrounds, dark text")
        case .dark: String(localized: "Dark backgrounds, easier at night")
        }
    }

    var body: some View {
        SettingsPageLayout(title: "Appearance", onBack: { store.screen = .settings }) {
            GroupSection(title: "Theme") {
                ForEach(Array(Appearance.allCases.enumerated()), id: \.element) { index, option in
                    if index > 0 { RowDivider(leading: 20) }
                    ChoiceRow(title: option.title, subtitle: subtitle(option), selected: option == appearance) {
                        appearance = option
                    }
                }
            }
            PageNote(text: "The change applies straight away to every screen.")
        }
    }
}

// MARK: Premium

struct PremiumScreen: View {
    @Environment(AppStore.self) private var store

    var body: some View {
        SettingsPageLayout(title: "Premium Features", onBack: { store.screen = .settings }) {
            HeroCard(icon: "star.fill", title: "YeyoFone Premium", subtitle: "Better calls and smart tools for busy teams.")
            FeatureList(title: "What's included", features: [
                Feature(icon: "waveform", tint: Palette.accentBlue, background: Palette.primaryLight,
                        title: "HD voice", subtitle: String(localized: "Wideband audio on supported calls")),
                Feature(icon: "record.circle", tint: Palette.accentRed, background: Palette.accentRed.opacity(0.12),
                        title: "Unlimited recording", subtitle: String(localized: "Record every call and keep it backed up")),
                Feature(icon: "sparkles", tint: Palette.purple, background: Palette.purpleLight,
                        title: "AI call notes", subtitle: String(localized: "Transcripts, summaries and follow-ups")),
                Feature(icon: "video.fill", tint: Palette.accentGreen, background: Palette.accentGreen.opacity(0.12),
                        title: "Team video rooms", subtitle: String(localized: "Meet face to face from any device")),
                Feature(icon: "person.crop.circle.badge.questionmark", tint: Palette.accentOrange, background: Palette.orangeLight,
                        title: "Priority support", subtitle: String(localized: "Faster answers from our team")),
            ])
            GroupSection(title: "Your plan") {
                HStack {
                    Text("Current plan")
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(Palette.textPrimary)
                    Spacer()
                    Text("Free")
                        .font(.system(size: 15))
                        .foregroundStyle(Palette.textSecondary)
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 14)
            }
            PageButton(title: "Coming soon", enabled: false) {}
            PageNote(text: "Premium isn't available to buy yet. We'll let you know in the app when it is.")
        }
    }
}

// MARK: Enterprise

struct EnterpriseScreen: View {
    @Environment(AppStore.self) private var store

    var body: some View {
        SettingsPageLayout(title: "Enterprise Sign In", onBack: { store.screen = .settings }) {
            HeroCard(icon: "building.2.fill", title: "Sign in with your work account",
                     subtitle: "Your company's phone settings, set up for you in one step.")
            FeatureList(title: "How it works", features: [
                Feature(icon: "envelope.fill", tint: Palette.accentBlue, background: Palette.primaryLight,
                        title: "Enter your work email", subtitle: String(localized: "We find your company's phone system")),
                Feature(icon: "lock.fill", tint: Palette.purple, background: Palette.purpleLight,
                        title: "Sign in with your company", subtitle: String(localized: "The same login you use for work")),
                Feature(icon: "checkmark.seal.fill", tint: Palette.accentGreen, background: Palette.accentGreen.opacity(0.12),
                        title: "You're ready to call", subtitle: String(localized: "Your extension and settings are added")),
            ])
            PageNote(text: "Work-account sign-in isn't available yet. Until then, ask your administrator for your SIP account details and add them yourself.")
            PageButton(title: "Add SIP account") { store.screen = .accountEditor(nil) }
        }
    }
}

// MARK: Translate

struct TranslateScreen: View {
    @Environment(AppStore.self) private var store

    var body: some View {
        SettingsPageLayout(title: "Translate", onBack: { store.screen = .settings }) {
            HeroCard(icon: "character.bubble.fill", title: "Talk across languages",
                     subtitle: "Understand callers who speak another language, live during the call.")
            FeatureList(title: "How it will work", features: [
                Feature(icon: "captions.bubble.fill", tint: Palette.accentBlue, background: Palette.primaryLight,
                        title: "Live captions", subtitle: String(localized: "See what the caller says in your language")),
                Feature(icon: "speaker.wave.2.bubble.fill", tint: Palette.purple, background: Palette.purpleLight,
                        title: "Spoken translation", subtitle: String(localized: "Hear a translation as they speak")),
                Feature(icon: "doc.text.fill", tint: Palette.accentGreen, background: Palette.accentGreen.opacity(0.12),
                        title: "Translated notes", subtitle: String(localized: "Summaries in the language you choose")),
            ])
            GroupSection(title: "Status") {
                IconRow(icon: "hourglass", tint: Palette.accentOrange, tileBackground: Palette.orangeLight,
                        title: "Coming soon",
                        subtitle: String(localized: "Live translation is being built and isn't available yet.")) { EmptyView() }
            }
            PageButton(title: "Language settings") { store.screen = .languageSettings }
        }
    }
}

// MARK: Not available yet

/// A setting release builds don't offer yet (video, call recording).
struct FeatureUnavailableScreen: View {
    @Environment(AppStore.self) private var store
    let title: String

    var body: some View {
        SettingsPageLayout(title: LocalizedStringKey(title), onBack: { store.screen = .settings }) {
            GroupSection(title: "Status") {
                IconRow(icon: "hourglass", tint: Palette.accentOrange, tileBackground: Palette.orangeLight,
                        title: "Coming soon",
                        subtitle: String(localized: "This isn't available in this version yet.")) { EmptyView() }
            }
            PageNote(text: "It will appear here in a future update.")
        }
    }
}
