import Foundation
import CryptoKit
@preconcurrency import core

/// Криптография на CryptoKit. Без состояния, поэтому безопасна для любых потоков.
final class AppleCrypto: Crypto, @unchecked Sendable {
    func randomBytes(count: Int32) -> KotlinByteArray {
        var bytes = [UInt8](repeating: 0, count: Int(count))
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        return Data(bytes).asKotlinByteArray
    }

    func createEphemeralKeyPair() -> EphemeralKeyPair {
        let key = Curve25519.KeyAgreement.PrivateKey()
        return EphemeralKeyPair(
            privateKey: key.rawRepresentation.asKotlinByteArray,
            publicKey: key.publicKey.rawRepresentation.asKotlinByteArray
        )
    }

    func sharedSecret(privateKey: KotlinByteArray, peerPublicKey: KotlinByteArray) -> KotlinByteArray {
        guard let mine = try? Curve25519.KeyAgreement.PrivateKey(rawRepresentation: privateKey.asData),
              let theirs = try? Curve25519.KeyAgreement.PublicKey(rawRepresentation: peerPublicKey.asData),
              let shared = try? mine.sharedSecretFromKeyAgreement(with: theirs) else {
            return Data().asKotlinByteArray
        }
        return shared.withUnsafeBytes { Data($0) }.asKotlinByteArray
    }

    func deriveSessionKey(sharedSecret: KotlinByteArray, salt: KotlinByteArray) -> KotlinByteArray {
        let key = HKDF<SHA256>.deriveKey(
            inputKeyMaterial: SymmetricKey(data: sharedSecret.asData),
            salt: salt.asData,
            info: Data("earshot-session-v1".utf8),
            outputByteCount: 32
        )
        return key.withUnsafeBytes { Data($0) }.asKotlinByteArray
    }

    func seal(key: KotlinByteArray, plaintext: KotlinByteArray) -> KotlinByteArray {
        let symmetric = SymmetricKey(data: key.asData)
        guard let box = try? ChaChaPoly.seal(plaintext.asData, using: symmetric) else {
            return Data().asKotlinByteArray
        }
        return box.combined.asKotlinByteArray
    }

    func open(key: KotlinByteArray, sealed: KotlinByteArray) -> KotlinByteArray? {
        let symmetric = SymmetricKey(data: key.asData)
        guard let box = try? ChaChaPoly.SealedBox(combined: sealed.asData),
              let plaintext = try? ChaChaPoly.open(box, using: symmetric) else {
            return nil
        }
        return plaintext.asKotlinByteArray
    }
}

/// Системные часы. Пауза сравнивается с ними, поэтому они вынесены интерфейсом:
/// в тестах ядра время двигают руками.
final class SystemClock: Clock, @unchecked Sendable {
    func nowEpochSeconds() -> Int64 { Int64(Date().timeIntervalSince1970) }
}
