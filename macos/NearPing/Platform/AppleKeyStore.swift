import Foundation
import CryptoKit
import Security
import OSLog
@preconcurrency import core

/// Личность устройства на Apple: постоянный ключ подписи Curve25519 в Keychain.
/// Приватная часть наружу не отдаётся — подписывает `sign(data:)`, и только.
final class AppleKeyStore: KeyStore, Signer, @unchecked Sendable {
    static let shared = AppleKeyStore()

    private static let keychainAccount = "com.disspear574.nearping.identity"
    private static let displayNameKey = "com.disspear574.nearping.displayName"

    private let privateKey: Curve25519.Signing.PrivateKey

    private static let log = Logger(subsystem: "com.disspear574.nearping", category: "identity")

    init() {
        let existing = Self.existingKey()
        privateKey = existing ?? Self.createKey()
        let fingerprint = Core.shared.fingerprint(
            bytes: privateKey.publicKey.rawRepresentation.asKotlinByteArray
        )
        let action = existing == nil ? "created" : "loaded"
        Self.log.notice("identity \(action, privacy: .public) \(fingerprint, privacy: .public)")
        #if DEBUG
        print("[nearping] identity \(action) \(fingerprint)")
        fflush(stdout)
        #endif
    }

    // MARK: KeyStore

    func loadOrCreate() -> Identity {
        Identity(
            publicKey: privateKey.publicKey.rawRepresentation.asKotlinByteArray,
            displayName: Self.storedDisplayName()
        )
    }

    func setDisplayName(name: String) {
        UserDefaults.standard.set(name, forKey: Self.displayNameKey)
    }

    // MARK: Signer

    func sign(data: KotlinByteArray) -> KotlinByteArray {
        let signature = try? privateKey.signature(for: data.asData)
        return (signature ?? Data()).asKotlinByteArray
    }

    func verify(data: KotlinByteArray, signature: KotlinByteArray, publicKey: KotlinByteArray) -> Bool {
        guard let key = try? Curve25519.Signing.PublicKey(rawRepresentation: publicKey.asData) else {
            return false
        }
        return key.isValidSignature(signature.asData, for: data.asData)
    }

    // MARK: Keychain

    private static func storedDisplayName() -> String {
        if let saved = UserDefaults.standard.string(forKey: displayNameKey), !saved.isEmpty {
            return saved
        }
        return Host.current().localizedName ?? ""
    }

    private static func existingKey() -> Curve25519.Signing.PrivateKey? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: keychainAccount,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data else { return nil }
        return try? Curve25519.Signing.PrivateKey(rawRepresentation: data)
    }

    private static func createKey() -> Curve25519.Signing.PrivateKey {
        let key = Curve25519.Signing.PrivateKey()
        let attributes: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: keychainAccount,
            kSecValueData as String: key.rawRepresentation,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock,
        ]
        SecItemDelete(attributes as CFDictionary)
        SecItemAdd(attributes as CFDictionary, nil)
        return key
    }
}
