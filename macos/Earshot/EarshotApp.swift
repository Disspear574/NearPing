import SwiftUI
import AppKit
import core

@main
struct EarshotApp: App {
    var body: some Scene {
        MenuBarExtra("Earshot", systemImage: "bell.badge") {
            BridgeCheckView()
        }
        .menuBarExtraStyle(.window)
    }
}

/// Временный экран: подтверждает, что ядро на Kotlin доехало до Swift.
/// Уезжает в Task 5, когда появится настоящий список контактов.
private struct BridgeCheckView: View {
    private let sampleFingerprint = Core.shared.fingerprint(
        bytes: Data([0xA3, 0xF9, 0x2B, 0x71]).asKotlinByteArray
    )

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(verbatim: "Earshot")
                .font(.system(size: 13, weight: .semibold))
            Text(verbatim: "protocol v\(Core.shared.protocolVersion)")
                .font(.system(size: 11))
                .foregroundStyle(.secondary)
            Text(verbatim: sampleFingerprint)
                .font(.system(size: 11, design: .monospaced))
                .foregroundStyle(.secondary)
            Divider()
            Button("Quit") { NSApplication.shared.terminate(nil) }
                .buttonStyle(.plain)
                .font(.system(size: 12))
        }
        .padding(12)
        .frame(width: 220, alignment: .leading)
    }
}

extension Data {
    /// Kotlin/Native ждёт KotlinByteArray там, где в Kotlin объявлен ByteArray.
    var asKotlinByteArray: KotlinByteArray {
        let out = KotlinByteArray(size: Int32(count))
        for (index, byte) in enumerated() {
            out.set(index: Int32(index), value: Int8(bitPattern: byte))
        }
        return out
    }
}
