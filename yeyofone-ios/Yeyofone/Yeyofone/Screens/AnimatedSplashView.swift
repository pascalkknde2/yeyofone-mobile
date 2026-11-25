import SwiftUI

/// The opening animation after the launch screen: the bird pops in, "YeyoFone" rises under it, then the
/// whole screen fades away. It blocks touches while it plays and calls `onFinished` when done.
struct AnimatedSplashView: View {
    let onFinished: () -> Void

    @State private var logoVisible = false
    @State private var textVisible = false
    @State private var fadingOut = false

    var body: some View {
        ZStack {
            Color("SplashBackground").ignoresSafeArea()
            VStack(spacing: 22) {
                Image("SplashLogo")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 128)
                    .scaleEffect(logoVisible ? 1 : 0.6)
                    .opacity(logoVisible ? 1 : 0)
                Text("YeyoFone")
                    .font(.system(size: 36, weight: .bold))
                    .tracking(1)
                    .foregroundStyle(.white)
                    .opacity(textVisible ? 1 : 0)
                    .offset(y: textVisible ? 0 : 24)
            }
        }
        .opacity(fadingOut ? 0 : 1)
        .contentShape(Rectangle())
        .task {
            withAnimation(.spring(response: 0.6, dampingFraction: 0.5)) { logoVisible = true }
            try? await Task.sleep(for: .milliseconds(380))
            withAnimation(.easeOut(duration: 0.52)) { textVisible = true }
            try? await Task.sleep(for: .milliseconds(1250))
            withAnimation(.easeIn(duration: 0.38)) { fadingOut = true }
            try? await Task.sleep(for: .milliseconds(400))
            onFinished()
        }
        .accessibilityHidden(true)
    }
}
