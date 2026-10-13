import SwiftUI

/// One person row: ping is the main action, urgent is in the menu and needs a hold.
struct PeerRowView: View {
    let row: PeerRow
    let onCall: () -> Void
    let onUrgent: () -> Void
    let onRemove: () -> Void

    @State private var hovered = false
    @State private var showingUrgent = false
    private var accent: Color { Tokens.Palette.accent }

    var body: some View {
        VStack(alignment: .leading, spacing: Tokens.Space.xs) {
        HStack(spacing: Tokens.Space.s) {
            RoundedRectangle(cornerRadius: 8)
                .fill(Tokens.Palette.surface)
                .overlay(
                    Text(initials)
                        .font(Tokens.Font.section)
                        .foregroundStyle(Tokens.Palette.primary)
                )
                .frame(width: Tokens.Metric.avatar, height: Tokens.Metric.avatar)

            // A name of any length must not push the dot and buttons off the edge.
            VStack(alignment: .leading, spacing: 1) {
                Text(row.displayName)
                    .font(Tokens.Font.name)
                    .foregroundStyle(Tokens.Palette.primary)
                    .lineLimit(1)
                    .truncationMode(.tail)
                HStack(spacing: 5) {
                    statusDot
                    Text(subtitle)
                    .font(Tokens.Font.caption)
                    .foregroundStyle(subtitleColor)
                    .lineLimit(1)
                    .truncationMode(.tail)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .layoutPriority(1)


            Menu {
                Button(row.urgentCooldown > 0 ? String(localized: "Urgent Ping") + " · \(row.urgentCooldown)s" : String(localized: "Urgent Ping")) { showingUrgent = true }
                    .disabled(!canUrgent)
                Divider()
                Button(String(localized: "Remove contact"), role: .destructive, action: onRemove)
            } label: { Image(systemName: "ellipsis").fontWeight(.semibold).frame(width: 28, height: 28).contentShape(Rectangle()) }
            .menuStyle(.borderlessButton).menuIndicator(.hidden).fixedSize()
            .accessibilityLabel(String(localized: "Contact actions"))
            .popover(isPresented: $showingUrgent) {
                VStack(alignment: .leading, spacing: Tokens.Space.m) {
                    Text(String(localized: "Urgent Ping")).font(Tokens.Font.nameStrong)
                    Text(row.displayName).font(Tokens.Font.name).lineLimit(2).truncationMode(.tail)
                    HStack(spacing: Tokens.Space.m) {
                        HoldButton(
                            isEnabled: canUrgent,
                            accessibilityText: String(localized: "Urgent call to \(row.displayName), hold to send")
                        ) {
                            guard canUrgent else { return }
                            onUrgent()
                            showingUrgent = false
                        }
                        Text(String(localized: "Hold ! for an urgent call"))
                            .font(Tokens.Font.caption).fixedSize(horizontal: false, vertical: true)
                    }
                }
                .padding(Tokens.Space.edge).frame(width: 240)
                .foregroundStyle(Tokens.Palette.primary).background(Tokens.Palette.background)
            }

            Button(action: onCall) {
                // A minimum width keeps the button steady while the countdown replaces the word.
                Text(row.cooldown > 0 ? "\(row.cooldown)s" : String(localized: "Ping"))
                    .frame(minWidth: 40)
            }
            .buttonStyle(.borderedProminent)
            .tint(accent)
            .controlSize(.regular)
            .fixedSize()
            .disabled(!canCall)
            .help(String(localized: "Send a normal call"))
        }
        if row.timeoutHint {
            Text(String(localized: "Too many pings. If urgent, use Urgent Ping in the contact menu."))
                .font(Tokens.Font.caption).foregroundStyle(Tokens.Palette.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        }
        .padding(.horizontal, Tokens.Space.s)
        .padding(.vertical, Tokens.Space.xs)
        .background(
            RoundedRectangle(cornerRadius: Tokens.Metric.rowRadius, style: .continuous)
                // Tinted with a hint of accent rather than gray, so the row reads as live and belonging to this app.
                .fill(hovered ? accent.opacity(0.14) : Color.clear)
        )
        .onHover { hovered = $0 }
    }

    private var reachable: Bool { row.isTrusted && row.isOnline && row.pausedUntil == nil }
    private var canCall: Bool { reachable && row.cooldown == 0 }
    private var canUrgent: Bool { reachable && row.urgentCooldown == 0 }

    private var subtitle: String {
        if let status = row.status { return status }
        if let until = row.pausedUntil {
            return String(localized: "paused until \(until.formatted(date: .omitted, time: .shortened))")
        }
        if !row.isTrusted { return row.fingerprint }
        return row.isOnline ? String(localized: "online") : String(localized: "offline")
    }

    private var subtitleColor: Color {
        if row.status != nil {
            switch row.statusKind {
            case .good: return Tokens.Palette.seen
            case .paused: return Tokens.Palette.paused
            case .failed: return Tokens.Palette.urgent
            case .neutral: return Tokens.Palette.secondary
            }
        }
        return row.pausedUntil == nil ? Tokens.Palette.secondary : Tokens.Palette.paused
    }

    // Online and offline differ by shape, not only colour: filled versus hollow stays visible in grayscale.
    @ViewBuilder private var statusDot: some View {
        if row.pausedUntil != nil {
            Image(systemName: "pause.fill").font(.system(size: 8)).foregroundStyle(Tokens.Palette.paused)
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

#Preview("Rows") {
    VStack(spacing: 0) {
        PeerRowView(row: PeerRow(id: "1", displayName: "Anna Petrova", fingerprint: "AB12 CD34", isTrusted: true, isOnline: true, pausedUntil: nil), onCall: {}, onUrgent: {}, onRemove: {})
        PeerRowView(row: PeerRow(id: "2", displayName: "A very long display name that must truncate", fingerprint: "EF56 7890", isTrusted: true, isOnline: true, pausedUntil: nil, cooldown: 12, status: "delivered", statusKind: .good), onCall: {}, onUrgent: {}, onRemove: {})
        PeerRowView(row: PeerRow(id: "3", displayName: "Ivan", fingerprint: "1122 3344", isTrusted: true, isOnline: true, pausedUntil: .now.addingTimeInterval(3600)), onCall: {}, onUrgent: {}, onRemove: {})
        PeerRowView(row: PeerRow(id: "4", displayName: "Offline Mac", fingerprint: "5566 7788", isTrusted: true, isOnline: false, pausedUntil: nil, timeoutHint: true), onCall: {}, onUrgent: {}, onRemove: {})
    }
    .padding(.horizontal, Tokens.Space.s)
    .frame(width: Tokens.Metric.popoverWidth)
    .background(Tokens.Palette.background)
}
