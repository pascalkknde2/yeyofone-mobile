//
//  DeviceStorage.swift
//  Yeyofone
//
//  On-device persistence: SIP passwords in the Keychain, everything else as JSON files.
//  The iOS counterparts of Android's Keystore-backed secret store and Room tables.
//

import Foundation
import Security

enum PasswordStore {
    private static let service = "com.yeyofone.app.sip-password"

    static func password(for account: UUID) -> String? {
        var query = baseQuery(account)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess, let data = result as? Data else {
            return nil
        }
        return String(data: data, encoding: .utf8)
    }

    @discardableResult
    static func setPassword(_ password: String, for account: UUID) -> Bool {
        let data = Data(password.utf8)
        let update = [kSecValueData as String: data]
        let status = SecItemUpdate(baseQuery(account) as CFDictionary, update as CFDictionary)
        if status == errSecItemNotFound {
            var insert = baseQuery(account)
            insert[kSecValueData as String] = data
            // Needed for re-registering in the background after the first unlock; never synced off the device.
            insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            return SecItemAdd(insert as CFDictionary, nil) == errSecSuccess
        }
        return status == errSecSuccess
    }

    static func deletePassword(for account: UUID) {
        SecItemDelete(baseQuery(account) as CFDictionary)
    }

    private static func baseQuery(_ account: UUID) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account.uuidString,
        ]
    }
}

enum LocalStore {
    static func load<Value: Decodable>(_ type: Value.Type, from name: String) -> Value? {
        guard let data = try? Data(contentsOf: url(name)) else { return nil }
        return try? JSONDecoder().decode(type, from: data)
    }

    static func save<Value: Encodable>(_ value: Value, to name: String) {
        guard let data = try? JSONEncoder().encode(value) else { return }
        try? data.write(to: url(name), options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }

    #if DEBUG
    /// Deletes all saved data and stored passwords, so UI tests start from a clean install.
    static func resetForTesting() {
        for account in load([SipAccount].self, from: "accounts") ?? [] {
            PasswordStore.deletePassword(for: account.id)
        }
        for name in ["accounts", "preferences", "contacts", "history"] {
            try? FileManager.default.removeItem(at: url(name))
        }
    }
    #endif

    private static func url(_ name: String) -> URL {
        let directory = URL.applicationSupportDirectory.appending(path: "YeyoFone", directoryHint: .isDirectory)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory.appending(path: name + ".json")
    }
}
