import SwiftUI
import AppKit

/// Токены попапа.
///
/// Текст намеренно идёт системными семантическими цветами, а не фиксированными
/// оттенками из макета: попап стоит на системном материале, прозрачность которого
/// зависит от обоев и темы, и любой зашитый серый на нём рано или поздно плывёт.
/// Свои цвета остаются только там, где они несут смысл.
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
        /// Системные: сами держат контраст на любом материале.
        static let primary = Color.primary
        static let secondary = Color.secondary

        static let paused = dynamic(light: 0x8A6300, dark: 0xF0C14B)
        static let seen = dynamic(light: 0x18703F, dark: 0x5FE39A)
        static let online = dynamic(light: 0x1E8E55, dark: 0x4ADE8A)
        static let offline = dynamic(light: 0x6E6E78, dark: 0xA0A0AA)
        /// Системным акцентом не подменяется никогда: иначе у человека с зелёным
        /// акцентом срочный вызов станет зелёным.
        static let urgent = dynamic(light: 0xC42D18, dark: 0xFF7A5A)

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
