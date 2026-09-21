import SwiftUI
import AppKit
import core

@main
struct EarshotApp: App {
    /// Личность нужна при запуске, а не по первому клику: с неё начинается
    /// анонс в Bonjour, и ждать, пока человек откроет меню, нельзя.
    init() {
        _ = AppleKeyStore.shared.loadOrCreate()
    }

    var body: some Scene {
        MenuBarExtra("Earshot", systemImage: "bell.badge") {
            IdentityView()
        }
        .menuBarExtraStyle(.window)
    }
}

/// Временный экран: своя личность и её отпечаток.
/// Список контактов появится в Task 5, оформление — в Task 8.
private struct IdentityView: View {
    @State private var displayName: String = ""
    @State private var fingerprint: String = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(verbatim: "Earshot")
                .font(.system(size: 13, weight: .semibold))

            TextField("Your name", text: $displayName)
                .textFieldStyle(.roundedBorder)
                .font(.system(size: 12))
                .onSubmit { AppleKeyStore.shared.setDisplayName(name: displayName) }

            HStack(spacing: 6) {
                Text(verbatim: fingerprint)
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .truncationMode(.tail)
                Spacer(minLength: 0)
                Text(verbatim: "v\(Core.shared.protocolVersion)")
                    .font(.system(size: 11))
                    .foregroundStyle(.tertiary)
            }

            Divider()

            Button("Quit") { NSApplication.shared.terminate(nil) }
                .buttonStyle(.plain)
                .font(.system(size: 12))
        }
        .padding(12)
        .frame(width: 240, alignment: .leading)
        .task {
            let identity = AppleKeyStore.shared.loadOrCreate()
            displayName = identity.displayName
            fingerprint = identity.fingerprint
        }
    }
}
