import Foundation
import ServiceManagement

/// Launch at login through the main app service.
public enum LoginItem {
    private static let offeredKey = "nearping.loginItemOffered"

    public static var isEnabled: Bool { SMAppService.mainApp.status == .enabled }

    public static func set(_ enabled: Bool) {
        do {
            if enabled { try SMAppService.mainApp.register() } else { try SMAppService.mainApp.unregister() }
        } catch {
            NSLog("NearPing login item change failed: %@", error.localizedDescription)
        }
    }

    // Turns it on once for an installed copy; after that the choice stands.
    public static func enableOnFirstLaunch() {
        let defaults = UserDefaults.standard
        guard !defaults.bool(forKey: offeredKey), Bundle.main.bundlePath.hasPrefix("/Applications/") else { return }
        defaults.set(true, forKey: offeredKey)
        set(true)
    }
}
