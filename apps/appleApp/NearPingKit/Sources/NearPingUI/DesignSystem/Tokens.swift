import SwiftUI
import AppKit

/// Popup tokens; the background is solid because contrast over a transparent layer depends on the wallpaper.
enum Tokens {
    enum Metric {
        static let popoverWidth: CGFloat = 320
        static let rowRadius: CGFloat = 10
        static let avatar: CGFloat = 28
        static let dot: CGFloat = 8
        static let urgentButton: CGFloat = 28
        static let holdSeconds: TimeInterval = 0.9
    }

    /// One spacing scale; `edge` is the single left and right margin of every screen.
    enum Space {
        static let xs: CGFloat = 4
        static let s: CGFloat = 8
        static let m: CGFloat = 12
        static let edge: CGFloat = 16
    }

    // System text styles, so weight and leading follow the platform.
    enum Font {
        static let name = SwiftUI.Font.body.weight(.medium)
        static let nameStrong = SwiftUI.Font.headline
        static let caption = SwiftUI.Font.subheadline
        static let section = SwiftUI.Font.subheadline.weight(.semibold)
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

        // Never follows the system accent; `urgent` is text and outline, `urgentFill` sits under white content, both >= 4.5:1.
        static let urgent = dynamic(light: 0xA53F32, dark: 0xFF8A70)
        static let urgentFill = dynamic(light: 0xB23A2C, dark: 0xD63A2A)

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
