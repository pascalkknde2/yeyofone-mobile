//
//  DialPadScreen.swift
//  Yeyofone
//
//  Port of yeyofone-android ui/dialpad/DialPadScreen.kt.
//

import SwiftUI

struct DialPadScreen: View {
    @Environment(AppStore.self) private var store
    @State private var number: String
    @State private var pulsing = false

    private let maxLength = 15

    init(initialNumber: String) {
        _number = State(initialValue: initialNumber)
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button { store.screen = .home } label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 20, weight: .semibold))
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel("Back")
                Spacer()
                Button { store.select(.contacts) } label: {
                    Image(systemName: "person.badge.plus")
                        .font(.system(size: 19))
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel("Add contact")
            }
            .foregroundStyle(Palette.textPrimary)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)

            Spacer(minLength: 8)
            DialedNumberDisplay(number: number) { number = String(number.dropLast()) }
                .padding(.horizontal, 32)
            Spacer(minLength: 8)

            DialPad(rowSpacing: 16) { key in
                if number.count < maxLength { number += key }
            }
            .frame(maxWidth: 288)
            .padding(.horizontal, 24)

            callButton
                .padding(.top, 28)
                .padding(.bottom, 24)
        }
        .background(Palette.keypadBackground)
    }

    private var callButton: some View {
        ZStack {
            if !number.isEmpty {
                Circle()
                    .fill(Palette.accentGreen)
                    .frame(width: 72, height: 72)
                    .scaleEffect(pulsing ? 1.3 : 1)
                    .opacity(pulsing ? 0 : 0.3)
                    .onAppear { pulsing = false; withAnimation(.easeOut(duration: 2).repeatForever(autoreverses: false)) { pulsing = true } }
            }
            Button {
                store.startCall(to: number)
                number = ""
            } label: {
                Image(systemName: "phone.fill")
                    .font(.system(size: 28))
                    .foregroundStyle(.white.opacity(number.isEmpty ? 0.65 : 1))
                    .frame(width: 72, height: 72)
                    .background(Circle().fill(Palette.accentGreen))
                    .shadow(color: Palette.accentGreen.opacity(0.3), radius: 8, y: 4)
            }
            .buttonStyle(PressScaleStyle())
            .disabled(number.isEmpty)
            .accessibilityLabel("Call")
        }
    }
}
