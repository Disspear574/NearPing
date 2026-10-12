import AppKit

// Draws the DMG window background (640x420 pt) at the given scale: logo, title, arrow to Applications.
let out = CommandLine.arguments[1]
let scale = CGFloat(Double(CommandLine.arguments[2]) ?? 1)
let iconPath = CommandLine.arguments[3]
let size = NSSize(width: 640, height: 420)

let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: Int(size.width * scale), pixelsHigh: Int(size.height * scale),
                           bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
                           colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
rep.size = size
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)

func rgb(_ hex: Int) -> NSColor {
    NSColor(red: CGFloat(hex >> 16 & 0xFF) / 255, green: CGFloat(hex >> 8 & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
}

let full = NSRect(origin: .zero, size: size)
NSGradient(colors: [rgb(0xF3F2ED), rgb(0xE3E6DA)])!.draw(in: full, angle: -90)

// Logo and title; y grows upwards in AppKit.
if let icon = NSImage(contentsOfFile: iconPath) {
    icon.draw(in: NSRect(x: 320 - 36, y: 420 - 28 - 72, width: 72, height: 72))
}
func text(_ s: String, size: CGFloat, weight: NSFont.Weight, color: NSColor, y: CGFloat) {
    let p = NSMutableParagraphStyle(); p.alignment = .center
    let a: [NSAttributedString.Key: Any] = [.font: NSFont.systemFont(ofSize: size, weight: weight), .foregroundColor: color, .paragraphStyle: p]
    NSAttributedString(string: s, attributes: a).draw(in: NSRect(x: 0, y: y, width: 640, height: size * 1.4))
}
text("NearPing", size: 24, weight: .bold, color: NSColor(white: 0.12, alpha: 1), y: 420 - 136)
text("Call a colleague who is wearing headphones", size: 13, weight: .regular, color: NSColor(white: 0.4, alpha: 1), y: 420 - 160)

// Arrow between the app (x 170) and Applications (x 470) icons.
let y = 420 - 255.0
let accent = rgb(0x4E5A40)
accent.setStroke()
for points in [[NSPoint(x: 248, y: y), NSPoint(x: 384, y: y)],
               [NSPoint(x: 376, y: y + 12), NSPoint(x: 394, y: y), NSPoint(x: 376, y: y - 12)]] {
    let path = NSBezierPath()
    path.move(to: points[0])
    points.dropFirst().forEach { path.line(to: $0) }
    path.lineWidth = 4
    path.lineCapStyle = .round
    path.lineJoinStyle = .round
    path.stroke()
}

text("Drag to Applications", size: 12, weight: .medium, color: NSColor(white: 0.45, alpha: 1), y: 420 - 380)

NSGraphicsContext.restoreGraphicsState()
try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: out))
