import SwiftUI
import AppKit

/// Токены попапа. Значения из спеки `.claude/specs/2026-09-22-earshot-legibility-design.md`.
///
/// Фон сплошной, а не материал: на прозрачном слое контраст зависит от обоев под
/// окном, и любой зашитый оттенок рано или поздно оказывается нечитаемым. Когда фон
/// известен, контраст можно посчитать и гарантировать.
enum Tokens {
    enum Metric {
        static let popoverWidth: CGFloat = 320
        static let rowRadius: CGFloat = 10
        static let avatar: CGFloat = 28
        static let dot: CGFloat = 8
        static let urgentButton: CGFloat = 28
        static let holdSeconds: TimeInterval = 0.9
    }

    enum Font {
        static let name = SwiftUI.Font.system(size: 13, weight: .medium)
        static let nameStrong = SwiftUI.Font.system(size: 13, weight: .semibold)
        static let caption = SwiftUI.Font.system(size: 11)
        static let section = SwiftUI.Font.system(size: 10, weight: .semibold)
        static let footer = SwiftUI.Font.system(size: 12)
    }

    enum Palette {
        static let background = dynamic(light: 0xF6F6F8, dark: 0x232326)
        static let primary = dynamic(light: 0x15151A, dark: 0xF2F2F5)
        static let secondary = dynamic(light: 0x5E5E66, dark: 0xA8A8B2)
        static let paused = dynamic(light: 0x8A6300, dark: 0xF0C14B)
        static let seen = dynamic(light: 0x18703F, dark: 0x5FE39A)
        static let online = dynamic(light: 0x1E8E55, dark: 0x4ADE8A)
        static let offline = dynamic(light: 0x6E6E78, dark: 0xA0A0AA)

        /// Системным акцентом не подменяется никогда: у человека с зелёным акцентом
        /// срочный вызов обязан остаться красным.
        static let urgent = dynamic(light: 0xC42D18, dark: 0xFF7A5A)

        static let separator = Color.primary.opacity(0.09)
        static let rowHover = Color.primary.opacity(0.06)

        private static func dynamic(light: Int, dark: Int) -> Color {
            Color(nsColor: NSColor(name: nil) { appearance in
                let isDark = appearance.bestMatch(from: [.aqua, .darkAqua]) == .darkAqua
                return NSColor(hex: isDark ? dark : light)
            })
        }
    }
}

/// Системный акцент, обновляемый на лету: человек меняет его в настройках,
/// и приложение обязано перекраситься без перезапуска.
@MainActor
@Observable
final class SystemAccent {
    static let shared = SystemAccent()

    private(set) var color: Color = Color(nsColor: .controlAccentColor)

    private init() {
        NotificationCenter.default.addObserver(
            forName: NSNotification.Name("AppleColorPreferencesChangedNotification"),
            object: nil,
            queue: .main
        ) { [weak self] _ in
            MainActor.assumeIsolated {
                self?.color = Color(nsColor: .controlAccentColor)
            }
        }
    }
}

private extension NSColor {
    convenience init(hex: Int) {
        self.init(
            srgbRed: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1
        )
    }
}
