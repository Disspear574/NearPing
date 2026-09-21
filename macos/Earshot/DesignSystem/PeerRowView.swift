import SwiftUI

/// Строка человека в списке. Имя произвольной длины не должно выдавливать
/// точку статуса и кнопку «!» за край — отсюда приоритет раскладки и обрезка.
struct PeerRowView: View {
    let row: PeerRow
    let onCall: () -> Void
    let onUrgent: () -> Void

    var body: some View {
        HStack(spacing: 6) {
            Button(action: onCall) {
                HStack(spacing: 10) {
                    Circle()
                        .fill(Color.primary.opacity(0.08))
                        .overlay(
                            Text(initials)
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(Tokens.Palette.primary)
                        )
                        .frame(width: Tokens.Metric.avatar, height: Tokens.Metric.avatar)

                    VStack(alignment: .leading, spacing: 1) {
                        Text(row.displayName)
                            .font(Tokens.Font.name)
                            .foregroundStyle(Tokens.Palette.primary)
                            .lineLimit(1)
                            .truncationMode(.tail)
                        Text(subtitle)
                            .font(Tokens.Font.caption)
                            .foregroundStyle(subtitleColor)
                            .lineLimit(1)
                            .truncationMode(.tail)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .layoutPriority(1)

                    statusDot
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(!canCall)

            HoldButton(
                isEnabled: canUrgent,
                accessibilityText: String(localized: "Urgent call to \(row.displayName), hold to send"),
                onFire: onUrgent
            )
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 2)
    }

    private var canCall: Bool { row.isTrusted && row.isOnline && row.pausedUntil == nil }
    private var canUrgent: Bool { canCall }

    private var subtitle: String {
        if let status = row.status { return status }
        if let until = row.pausedUntil {
            return String(localized: "paused until \(until.formatted(date: .omitted, time: .shortened))")
        }
        if !row.isTrusted { return row.fingerprint }
        return row.isOnline ? String(localized: "online") : String(localized: "offline")
    }

    private var subtitleColor: Color {
        if let status = row.status {
            switch row.statusKind {
            case .good: return Tokens.Palette.seen
            case .paused: return Tokens.Palette.paused
            case .failed: return Tokens.Palette.urgent
            case .neutral: return Tokens.Palette.secondary
            }
        }
        return row.pausedUntil == nil ? Tokens.Palette.secondary : Tokens.Palette.paused
    }

    /// Онлайн и офлайн различаются формой, а не только цветом: в оттенках серого
    /// залитый кружок и полый всё ещё различимы.
    @ViewBuilder private var statusDot: some View {
        if row.pausedUntil != nil {
            Circle().fill(Tokens.Palette.paused)
                .frame(width: Tokens.Metric.dot, height: Tokens.Metric.dot)
        } else if row.isOnline {
            Circle().fill(Tokens.Palette.online)
                .frame(width: Tokens.Metric.dot, height: Tokens.Metric.dot)
        } else {
            Circle().stroke(Tokens.Palette.offline, lineWidth: 1.5)
                .frame(width: Tokens.Metric.dot, height: Tokens.Metric.dot)
        }
    }

    private var initials: String {
        let parts = row.displayName.split(separator: " ").prefix(2)
        let letters = parts.compactMap { $0.first }.map(String.init).joined()
        return letters.isEmpty ? "?" : letters.uppercased()
    }
}
