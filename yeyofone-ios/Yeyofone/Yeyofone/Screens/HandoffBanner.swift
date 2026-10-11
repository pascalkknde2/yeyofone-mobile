//
//  HandoffBanner.swift
//  Yeyofone
//
//  "Continue call on this device?": offers a call that is live on another device of the same
//  account. One tap moves it here; the other party stays connected without noticing.
//

import SwiftUI

struct HandoffBanner: View {
    @Environment(AppStore.self) private var store
    let offer: HandoffOffer

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                Image(systemName: "laptopcomputer.and.iphone")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(Color(hex: 0x93C5FD))
                    .frame(width: 40, height: 40)
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color(hex: 0x1E3A5F)))
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Continue call on this device?")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(.white)
                    Text(verbatim: "\(offer.remoteName ?? offer.remote) · \(String(localized: "On your \(offer.deviceName)"))")
                        .font(.system(size: 13))
                        .foregroundStyle(Color(hex: 0xCBD5E1))
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
            }
            HStack(spacing: 10) {
                Button { store.dismissHandoff() } label: {
                    Text("Not now")
                        .frame(maxWidth: .infinity, minHeight: 40)
                        .foregroundStyle(Color(hex: 0xE2E8F0))
                        .background(Capsule().fill(Color(hex: 0x26282E)))
                }
                Button { store.continueHandoff() } label: {
                    Text("Continue here")
                        .frame(maxWidth: .infinity, minHeight: 40)
                        .foregroundStyle(.white)
                        .background(Capsule().fill(Color(hex: 0x10B981)))
                }
            }
            .font(.system(size: 14, weight: .semibold))
            .buttonStyle(PressScaleStyle())
        }
        .padding(14)
        // A dark card in both appearances, like the desktop app's banner.
        .background(RoundedRectangle(cornerRadius: 20).fill(Color(hex: 0x18191D)))
        .shadow(color: .black.opacity(0.3), radius: 16, y: 8)
        .padding(.horizontal, 16)
        .accessibilityElement(children: .contain)
    }
}
