import SwiftUI
import AppKit

/// Токены попапа. Значения из спеки `.claude/specs/2026-09-22-nearping-design.md`.
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
        static let accent = dynamic(light: 0x4E5A40, dark: 0xB5BEA3)
        static let surface = dynamic(light: 0xE4E7DC, dark: 0x293225)
        static let background = dynamic(light: 0xF3F2ED, dark: 0x141A14)
        static let primary = dynamic(light: 0x232B22, dark: 0xF0EEE5)
        static let secondary = dynamic(light: 0x5A6254, dark: 0xB7BDB0)
        static let paused = dynamic(light: 0x785917, dark: 0xD5BD80)
        static let seen = dynamic(light: 0x4E5A40, dark: 0xB5BEA3)
        static let online = dynamic(light: 0x4E5A40, dark: 0xB5BEA3)
        static let offline = dynamic(light: 0x6E6E78, dark: 0xA0A0AA)

        /// Системным акцентом не подменяется никогда: у человека с зелёным акцентом
        /// срочный вызов обязан остаться красным.
        static let urgent = dynamic(light: 0xA53F32, dark: 0xE9A092)

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
