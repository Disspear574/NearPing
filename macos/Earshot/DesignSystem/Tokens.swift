import SwiftUI
import AppKit

/// Значения сняты с согласованного макета. Числа, которого там не было,
/// в коде появиться не должно.
enum Tokens {
    enum Metric {
        static let popoverWidth: CGFloat = 320
        static let rowRadius: CGFloat = 13
        static let avatar: CGFloat = 28
        static let dot: CGFloat = 8
        static let urgentButton: CGFloat = 30
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
        static let primary = dynamic(light: 0x15151A, dark: 0xF3F3F6)
        static let secondary = dynamic(light: 0x6B6B74, dark: 0xA4A4AD)
        static let paused = dynamic(light: 0x7F5C00, dark: 0xE8B33D)
        static let seen = dynamic(light: 0x1B6F40, dark: 0x5BD98A)
        static let online = dynamic(light: 0x2C9D65, dark: 0x40D486)
        static let offline = dynamic(light: 0x83838C, dark: 0x8B8B95)
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
