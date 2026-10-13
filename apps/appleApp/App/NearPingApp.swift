import SwiftUI
import AppKit
import CoreServices
import NearPingPlatform
import NearPingUI

@main
struct NearPingApp: App {
    @State private var runtime = NearPingRuntime.shared

    // Starts at app launch, not first click: MenuBarExtra content is not created until the menu opens, so nothing would be announced.
    init() {
        // Refresh bundle metadata after replacing a test build; Notification Center resolves the sender icon via Launch Services.
        let status = LSRegisterURL(Bundle.main.bundleURL as CFURL, true)
        if status != noErr { NSLog("NearPing application registration failed: %d", status) }
        if let url = Bundle.main.url(forResource: "AppIcon", withExtension: "icns"),
           let icon = NSImage(contentsOf: url) {
            NSApplication.shared.applicationIconImage = icon
        }
        NearPingRuntime.shared.start()
        LoginItem.enableOnFirstLaunch()
    }

    // MenuBarExtra uses the NSImage size, not the SwiftUI label frame; a separate copy keeps the mark size for the popup title.
    private static let menuBarIcon: NSImage = {
        let source = NSImage(named: "NearPingMark")
        let icon = (source?.copy() as? NSImage) ?? NSImage(size: NSSize(width: 18, height: 18))
        icon.size = NSSize(width: 18, height: 18)
        icon.isTemplate = true
        return icon
    }()

    var body: some Scene {
        MenuBarExtra {
            PopoverView(runtime: runtime)
        } label: {
            if runtime.pausedUntil == nil {
                Image(nsImage: Self.menuBarIcon)
                    .accessibilityLabel("NearPing")
            } else {
                Image(systemName: "pause.circle").accessibilityLabel(String(localized: "Paused"))
            }
        }
        .menuBarExtraStyle(.window)

        Settings {
            SettingsView(runtime: runtime)
        }
    }
}
