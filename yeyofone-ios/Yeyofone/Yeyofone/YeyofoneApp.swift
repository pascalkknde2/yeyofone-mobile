//
//  YeyofoneApp.swift
//  Yeyofone
//
//  Created by Pascal Kanyama Kankonde on 05/10/2026.
//

import SwiftUI

@main
struct YeyofoneApp: App {
    @State private var store: AppStore

    init() {
        #if DEBUG
        // `-YFScreen <name>` opens a screen directly on sample data, for screenshots and UI checks.
        if let name = UserDefaults.standard.string(forKey: "YFScreen") {
            let demo = AppStore(demo: true)
            demo.openForDebugging(name)
            _store = State(initialValue: demo)
            return
        }
        #endif
        _store = State(initialValue: AppStore())
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(store)
                .tint(Palette.accentBlue)
                // The design is light-only, like Android's YeyoFoneTheme.
                .preferredColorScheme(.light)
        }
    }
}
