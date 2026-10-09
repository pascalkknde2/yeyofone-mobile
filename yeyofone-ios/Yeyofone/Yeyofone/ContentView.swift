//
//  ContentView.swift
//  Yeyofone
//
//  Created by Pascal Kanyama Kankonde on 05/10/2026.
//

import SwiftUI

#Preview("Home") {
    RootView().environment(AppStore(demo: true))
}

#Preview("Incoming call") {
    let store = AppStore(demo: true)
    store.openForDebugging("incoming")
    return RootView().environment(store)
}
