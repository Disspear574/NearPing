import AppKit
import SwiftUI
import OSLog

/// Входящий вызов: своё окно поверх всего и управляемый звук.
///
/// Намеренно НЕ через систему уведомлений — её глушит «Не беспокоить». Окно и звук
/// к уведомлениям отношения не имеют, поэтому Focus им не помеха, а звук уходит
/// в текущее устройство вывода, то есть в те самые наушники.
@MainActor
final class UrgentAlert {
    private let log = Logger(subsystem: "com.disspear574.earshot", category: "urgent")
    private var bannerPanels: [NSPanel] = []
    private var edgePanels: [NSPanel] = []
    private var dismissTimer: Timer?
    private var sound: NSSound?
    private var onStopped: (@MainActor () -> Void)?

    static let autoDismissSeconds: TimeInterval = 8

    func show(callerName: String, isUrgent: Bool, onStopped: @escaping @MainActor () -> Void) {
        dismiss(acknowledge: true)
        self.onStopped = onStopped

        if isUrgent { showEdges() }
        // У menu-bar приложения NSScreen.main может отсутствовать, а первый
        // NSPanel тогда остаётся в позиции по умолчанию за пределами видимой
        // области. Отдельная плашка на каждом активном экране это исключает.
        let screens = NSScreen.screens.isEmpty ? [NSScreen.main].compactMap { $0 } : NSScreen.screens
        bannerPanels = screens.map { screen in
            makeBannerPanel(on: screen, callerName: callerName, isUrgent: isUrgent)
        }
        bannerPanels.forEach { $0.orderFrontRegardless() }
        play(urgent: isUrgent)
        dismissTimer = Timer.scheduledTimer(withTimeInterval: Self.autoDismissSeconds, repeats: false) { _ in
            MainActor.assumeIsolated {
                self.dismiss(acknowledge: true)
            }
        }
        log.notice("call alert shown for \(callerName, privacy: .public), urgent=\(isUrgent)")
    }

    func dismiss(acknowledge: Bool) {
        dismissTimer?.invalidate()
        dismissTimer = nil
        sound?.stop()
        sound = nil
        edgePanels.forEach { $0.orderOut(nil) }
        edgePanels.removeAll()
        bannerPanels.forEach { panel in
            panel.orderOut(nil)
            panel.contentViewController = nil
        }
        bannerPanels.removeAll()
        let callback = onStopped
        onStopped = nil
        if acknowledge { callback?() }
    }

    private func makeBannerPanel(on screen: NSScreen, callerName: String, isUrgent: Bool) -> NSPanel {
        let size = NSSize(width: 460, height: 104)
        let frame = screen.visibleFrame
        let origin = NSPoint(
                x: frame.midX - size.width / 2,
                y: frame.maxY - size.height - 24
        )
        let hosting = NSHostingController(
            rootView: UrgentBannerView(
                callerName: callerName,
                isUrgent: isUrgent,
                totalSeconds: Self.autoDismissSeconds,
                onStop: { [weak self] in self?.dismiss(acknowledge: true) }
            )
        )
        let panel = NSPanel(
            contentRect: NSRect(origin: origin, size: size),
            styleMask: [.nonactivatingPanel, .fullSizeContentView, .borderless],
            backing: .buffered,
            defer: false,
            screen: screen
        )
        panel.contentViewController = hosting
        panel.isFloatingPanel = true
        panel.level = NSWindow.Level(rawValue: NSWindow.Level.screenSaver.rawValue + 1)
        panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary, .ignoresCycle, .stationary]
        panel.backgroundColor = .clear
        panel.isOpaque = false
        panel.hasShadow = true
        panel.hidesOnDeactivate = false
        panel.canHide = false
        panel.isReleasedWhenClosed = false
        panel.ignoresMouseEvents = false
        panel.setFrame(NSRect(origin: origin, size: size), display: true)
        log.notice("banner frame=\(String(describing: panel.frame), privacy: .public)")
        return panel
    }

    private func showEdges() {
        for screen in NSScreen.screens {
            let edge = NSPanel(contentRect: screen.frame, styleMask: [.borderless, .nonactivatingPanel],
                               backing: .buffered, defer: false)
            edge.backgroundColor = .clear
            edge.isOpaque = false
            edge.hasShadow = false
            edge.ignoresMouseEvents = true
            edge.level = NSWindow.Level(rawValue: NSWindow.Level.screenSaver.rawValue - 1)
            edge.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary, .ignoresCycle]
            edge.contentView = NSHostingView(rootView: UrgentScreenEdges())
            edge.setFrame(screen.frame, display: true)
            edge.orderFrontRegardless()
            edgePanels.append(edge)
        }
    }

    private func play(urgent: Bool) {
        let sound: NSSound?
        if urgent,
           let url = Bundle.main.url(forResource: "urgent-siren", withExtension: "wav") {
            sound = NSSound(contentsOf: url, byReference: false)
        } else {
            sound = NSSound(named: NSSound.Name("Ping"))
        }
        guard let sound else { return }
        sound.volume = urgent ? 0.8 : 0.65
        sound.loops = urgent
        sound.play()
        self.sound = sound
    }
}

private struct UrgentScreenEdges: View {
    private let red = Color(red: 0.85, green: 0.08, blue: 0.06)
    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                LinearGradient(colors: [red.opacity(0.65), .clear], startPoint: .top, endPoint: .bottom).frame(height: 36)
                Spacer(minLength: 0)
                LinearGradient(colors: [.clear, red.opacity(0.65)], startPoint: .top, endPoint: .bottom).frame(height: 36)
            }
            HStack(spacing: 0) {
                LinearGradient(colors: [red.opacity(0.65), .clear], startPoint: .leading, endPoint: .trailing).frame(width: 36)
                Spacer(minLength: 0)
                LinearGradient(colors: [.clear, red.opacity(0.65)], startPoint: .leading, endPoint: .trailing).frame(width: 36)
            }
            Rectangle().strokeBorder(red.opacity(0.8), lineWidth: 2)
        }.ignoresSafeArea().allowsHitTesting(false)
    }
}
