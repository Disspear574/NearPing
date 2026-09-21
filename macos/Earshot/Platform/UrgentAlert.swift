import AppKit
import SwiftUI
import OSLog

/// Срочный вызов: своё окно поверх всего и свой звук.
///
/// Намеренно НЕ через систему уведомлений — её глушит «Не беспокоить». Окно и звук
/// к уведомлениям отношения не имеют, поэтому Focus им не помеха, а звук уходит
/// в текущее устройство вывода, то есть в те самые наушники.
@MainActor
final class UrgentAlert {
    private let log = Logger(subsystem: "app.earshot.Earshot", category: "urgent")
    private var panel: NSPanel?
    private var dismissTimer: Timer?
    private var sound: NSSound?

    static let autoDismissSeconds: TimeInterval = 8

    func show(callerName: String, onSeen: @escaping @MainActor () -> Void) {
        dismiss(notify: false)

        let hosting = NSHostingController(
            rootView: UrgentBannerView(
                callerName: callerName,
                totalSeconds: Self.autoDismissSeconds,
                onSeen: { [weak self] in
                    self?.dismiss(notify: false)
                    onSeen()
                }
            )
        )
        let panel = NSPanel(contentViewController: hosting)
        panel.styleMask = [.nonactivatingPanel, .fullSizeContentView, .borderless]
        panel.isFloatingPanel = true
        panel.level = .screenSaver
        panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary, .ignoresCycle]
        panel.backgroundColor = .clear
        panel.isOpaque = false
        panel.hasShadow = true
        panel.isMovableByWindowBackground = true
        panel.setContentSize(NSSize(width: 460, height: 104))
        position(panel)
        panel.orderFrontRegardless()
        self.panel = panel

        play()
        dismissTimer = Timer.scheduledTimer(withTimeInterval: Self.autoDismissSeconds, repeats: false) { _ in
            MainActor.assumeIsolated {
                self.dismiss(notify: true)
                onSeen()
            }
        }
        log.notice("urgent alert shown for \(callerName, privacy: .public)")
    }

    func dismiss(notify: Bool) {
        dismissTimer?.invalidate()
        dismissTimer = nil
        sound?.stop()
        sound = nil
        panel?.orderOut(nil)
        panel = nil
    }

    private func position(_ panel: NSPanel) {
        guard let screen = NSScreen.main else { return }
        let frame = screen.visibleFrame
        let size = panel.frame.size
        panel.setFrameOrigin(
            NSPoint(
                x: frame.midX - size.width / 2,
                y: frame.maxY - size.height - 24
            )
        )
    }

    private func play() {
        // Системный звук как временный: своя записанная нота появится вместе
        // с оформлением. Важно, что он идёт прямо в устройство вывода.
        guard let sound = NSSound(named: NSSound.Name("Submarine")) else { return }
        sound.volume = 1.0
        sound.loops = true
        sound.play()
        self.sound = sound
    }
}
