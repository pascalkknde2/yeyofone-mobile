//
//  RootView.swift
//  Yeyofone
//
//  Screen switching, the iOS counterpart of Android's AccountsApp in MainActivity.kt.
//  Like Android, an incoming call takes over the whole screen, then the active call,
//  then the call-ended summary; otherwise the selected screen shows.
//

import SwiftUI

struct RootView: View {
    @Environment(AppStore.self) private var store

    var body: some View {
        Group {
            if store.incomingCall != nil {
                IncomingCallScreen()
            } else if let call = store.activeCall {
                switch store.callPage {
                case .main: OutgoingCallScreen(session: call)
                case .options: CallOptionsScreen()
                case .transfer: TransferCallScreen(callerName: call.remoteName)
                }
            } else if let summary = store.endedSummary {
                CallEndedScreen(summary: summary)
            } else {
                mainScreen
            }
        }
        .animation(.easeInOut(duration: 0.2), value: store.incomingCall?.id)
        .animation(.easeInOut(duration: 0.2), value: store.activeCall?.id)
        .animation(.easeInOut(duration: 0.2), value: store.endedSummary)
    }

    private var mainScreen: some View {
        VStack(spacing: 0) {
            Group {
                switch store.screen {
                case .home: HomeScreen()
                case .dial(let destination): DialPadScreen(initialNumber: destination).id(destination)
                case .contacts: ContactsScreen()
                case .history: CallHistoryScreen()
                case .chat: ChatScreen()
                case .settings: SettingsScreen()
                case .accounts: AccountsScreen()
                case .accountDetail(let id): AccountDetailScreen(accountId: id)
                case .accountEditor(let id): AccountEditorScreen(existing: store.accounts.first { $0.id == id })
                case .audioSettings: AudioSettingsScreen()
                case .videoSettings: VideoSettingsScreen()
                case .incomingCallsSettings: IncomingCallsSettingsScreen()
                case .languageSettings: LanguageSettingsScreen()
                case .recordings: RecordingsScreen()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            if showsTabBar {
                BottomNavigationBar(selected: store.selectedTab) { store.select($0) }
                    .background(tabBarBackground.ignoresSafeArea(edges: .bottom))
            }
        }
    }

    /// The keypad, account pages and settings sub-pages are full-screen on Android too.
    private var showsTabBar: Bool {
        switch store.screen {
        case .home, .contacts, .history, .chat, .settings, .accounts: true
        default: false
        }
    }

    private var tabBarBackground: Color {
        store.screen == .home ? Palette.cardWhite : Palette.backgroundGray
    }
}
