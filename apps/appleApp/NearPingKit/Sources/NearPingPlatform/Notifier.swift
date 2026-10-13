import Foundation
import AppKit
import UserNotifications
import OSLog
import Observation

/// Normal call: system notification and a soft sound; respects Do Not Disturb on purpose, only urgent may break focus.
@MainActor
@Observable
package final class Notifier {
    package init() {}

    private let log = Logger(subsystem: "com.disspear574.nearping", category: "notify")
    private let presentation = NotificationPresentation()
    private var centerAvailable = false
    package private(set) var needsPermission = false
    package func refreshPermission() async {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        centerAvailable = settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional
        needsPermission = !centerAvailable || settings.alertSetting == .disabled
    }
    package func enableNotifications() {
        Task {
            let settings = await UNUserNotificationCenter.current().notificationSettings()
            if settings.authorizationStatus == .notDetermined { requestPermission() }
            else if let url = URL(string: "x-apple.systempreferences:com.apple.Notifications-Settings.extension") {
                NSWorkspace.shared.open(url)
            }
        }
    }

    package func requestPermission() {
        guard Bundle.main.bundleIdentifier != nil else { return }
        UNUserNotificationCenter.current().delegate = presentation
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { [weak self] granted, error in
            Task { @MainActor in
                self?.centerAvailable = granted
                await self?.refreshPermission()
                if let error { self?.log.error("notification permission: \(error.localizedDescription, privacy: .public)") }
            }
        }
    }

    package func ordinaryCall(from name: String, playSound: Bool = true) {
        Task {
            await refreshPermission()
            if playSound { NSSound(named: NSSound.Name("Ping"))?.play() }
            guard centerAvailable else { return }
            let content = UNMutableNotificationContent()
            content.title = name
            content.body = String(localized: "wants your attention")
            content.sound = playSound ? .default : nil
            let request = UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil)
            try? await UNUserNotificationCenter.current().add(request)
        }
    }

    package func pairingRequest(from name: String) {
        Task {
            await refreshPermission()
            guard centerAvailable else { return }
            let content = UNMutableNotificationContent()
            content.title = String(localized: "Pairing request")
            content.body = String(localized: "\(name) wants to add you")
            let request = UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil)
            try? await UNUserNotificationCenter.current().add(request)
        }
    }
}

/// Keep the system banner visible while the menu is open too.
private final class NotificationPresentation: NSObject, UNUserNotificationCenterDelegate, @unchecked Sendable {
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .list, .sound])
    }
}
