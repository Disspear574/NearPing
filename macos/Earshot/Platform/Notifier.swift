import Foundation
import AppKit
import UserNotifications
import OSLog

/// Обычный вызов: системное уведомление и мягкий звук.
/// Уважает «Не беспокоить» намеренно — ломать чужой фокус позволено только срочному.
@MainActor
final class Notifier {
    private let log = Logger(subsystem: "com.disspear574.earshot", category: "notify")
    private var centerAvailable = false

    func requestPermission() {
        guard Bundle.main.bundleIdentifier != nil else { return }
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { [weak self] granted, error in
            Task { @MainActor in
                self?.centerAvailable = granted
                if let error { self?.log.error("notification permission: \(error.localizedDescription, privacy: .public)") }
            }
        }
    }

    func ordinaryCall(from name: String) {
        NSSound(named: NSSound.Name("Ping"))?.play()
        guard centerAvailable else { return }
        let content = UNMutableNotificationContent()
        content.title = name
        content.body = String(localized: "wants your attention")
        content.sound = .default
        let request = UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request)
    }

    func pairingRequest(from name: String) {
        guard centerAvailable else { return }
        let content = UNMutableNotificationContent()
        content.title = String(localized: "Pairing request")
        content.body = String(localized: "\(name) wants to add you")
        let request = UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request)
    }
}
