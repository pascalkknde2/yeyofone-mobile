//
//  ContentView.swift
//  Yeyofone
//
//  Created by Pascal Kanyama Kankonde on 05/10/2026.
//

import SwiftUI

#Preview("Home") {
    RootView().environment(AppStore())
}

#Preview("Incoming call") {
    let store = AppStore()
    store.simulateIncomingCall()
    return RootView().environment(store)
}
