import Foundation
@preconcurrency import core

/// Trusted peers as JSON next to the app settings; keys are hex because ByteArray is costly across the bridge.
final class FilePeerStorage: PeerStorage, @unchecked Sendable {
    private struct Stored: Codable {
        let publicKeyHex: String
        let displayName: String
    }

    private let url: URL
    private let lock = NSLock()

    init() {
        let base = FileManager.default
            .urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("NearPing", isDirectory: true)
        try? FileManager.default.createDirectory(at: base, withIntermediateDirectories: true)
        url = base.appendingPathComponent("peers.json")
    }

    func load() -> [Peer] {
        lock.lock(); defer { lock.unlock() }
        guard let data = try? Data(contentsOf: url),
              let stored = try? JSONDecoder().decode([Stored].self, from: data) else { return [] }
        return stored.compactMap { entry in
            guard let key = Data(hex: entry.publicKeyHex) else { return nil }
            return Peer(publicKey: key.asKotlinByteArray, displayName: entry.displayName)
        }
    }

    func save(peers: [Peer]) {
        lock.lock(); defer { lock.unlock() }
        let stored = peers.map { Stored(publicKeyHex: $0.keyHex, displayName: $0.displayName) }
        guard let data = try? JSONEncoder().encode(stored) else { return }
        try? data.write(to: url, options: .atomic)
    }
}

extension Data {
    init?(hex: String) {
        guard hex.count % 2 == 0 else { return nil }
        var bytes = [UInt8]()
        bytes.reserveCapacity(hex.count / 2)
        var index = hex.startIndex
        while index < hex.endIndex {
            let next = hex.index(index, offsetBy: 2)
            guard let byte = UInt8(hex[index..<next], radix: 16) else { return nil }
            bytes.append(byte)
            index = next
        }
        self = Data(bytes)
    }
}
