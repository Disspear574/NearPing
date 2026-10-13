import SwiftUI
import AppKit

/// Settings in their own window, as on macOS; every change applies at once.
struct SettingsView: View {
    @Bindable var runtime: NearPingRuntime
    @State private var nameDraft = ""
    @State private var launchAtLogin = LoginItem.isEnabled
    @AppStorage("nearping.appearance") private var appearance = "system"

    var body: some View {
        Form {
            Section {
                TextField(String(localized: "Your name"), text: $nameDraft)
                    .onSubmit(saveName)
            } footer: {
                Text(String(localized: "Shown to people on your local network."))
                    .font(Tokens.Font.caption)
                    .foregroundStyle(.secondary)
            }
            Section {
                Picker(String(localized: "Appearance"), selection: $appearance) {
                    Text(String(localized: "System")).tag("system")
                    Text(String(localized: "Light")).tag("light")
                    Text(String(localized: "Dark")).tag("dark")
                }
                .pickerStyle(.segmented)
                Toggle(String(localized: "Open at login"), isOn: $launchAtLogin)
            }
            Section {
                LabeledContent(String(localized: "Fingerprint")) {
                    Text(verbatim: runtime.myFingerprint)
                        .font(Tokens.Font.caption.monospaced())
                        .textSelection(.enabled)
                }
            }
        }
        .formStyle(.grouped)
        .frame(width: 420)
        .fixedSize(horizontal: false, vertical: true)
        .tint(Tokens.Palette.accent)
        .onAppear {
            nameDraft = runtime.myDisplayName
            launchAtLogin = LoginItem.isEnabled
            // A menu bar app is not active, so without this the window opens behind other apps.
            NSApp.activate()
        }
        // Closing the window keeps the typed name, as other settings already apply at once.
        .onDisappear(perform: saveName)
        .onChange(of: appearance) { _, value in Appearance.apply(value) }
        .onChange(of: launchAtLogin) { _, enabled in
            LoginItem.set(enabled)
            launchAtLogin = LoginItem.isEnabled
        }
    }

    private func saveName() {
        runtime.rename(to: nameDraft)
    }
}

enum Appearance {
    // MenuBarExtra is an AppKit window whose NSColor palette ignores preferredColorScheme; nil restores system tracking.
    @MainActor static func apply(_ value: String) {
        NSApp.appearance = value == "dark" ? NSAppearance(named: .darkAqua)
            : value == "light" ? NSAppearance(named: .aqua) : nil
    }
}
