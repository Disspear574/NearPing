import SwiftUI
import AppKit

@main
struct EarshotApp: App {
    @State private var runtime = EarshotRuntime.shared

    /// Запуск при старте приложения, а не по первому клику: содержимое MenuBarExtra
    /// не создаётся, пока меню не открыли, и анонс в сети не начался бы вовсе.
    init() {
        EarshotRuntime.shared.start()
    }

    var body: some Scene {
        MenuBarExtra("Earshot", systemImage: runtime.pausedUntil == nil ? "bell.badge" : "bell.slash") {
            PopoverView(runtime: runtime)
        }
        .menuBarExtraStyle(.window)
    }
}

private struct PopoverView: View {
    @Bindable var runtime: EarshotRuntime
    @State private var nameDraft: String = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            header
            Divider().padding(.horizontal, 12)

            if runtime.localNetworkDenied {
                localNetworkWarning
            }

            if !runtime.pendingRequests.isEmpty {
                section(String(localized: "PAIRING REQUESTS"))
                ForEach(runtime.pendingRequests) { row in
                    requestRow(row)
                }
            }

            if !contacts.isEmpty {
                section(String(localized: "CONTACTS"))
                ForEach(contacts) { row in
                    PeerRowView(
                        row: row,
                        onCall: { runtime.call(row, urgent: false) },
                        onUrgent: { runtime.call(row, urgent: true) }
                    )
                    .padding(.horizontal, 8)
                }
            }

            section(String(localized: "NEARBY"))
            if nearby.isEmpty {
                Text(String(localized: "Nobody else on this network yet. Open Earshot on another Mac."))
                    .font(Tokens.Font.caption)
                    .foregroundStyle(Tokens.Palette.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 6)
            } else {
                ForEach(nearby) { row in
                    nearbyRow(row)
                }
            }

            Divider().padding(.horizontal, 12).padding(.top, 8)
            footer
        }
        .frame(width: Tokens.Metric.popoverWidth)
        .onAppear { nameDraft = runtime.myDisplayName }
    }

    private var contacts: [PeerRow] { runtime.peers.filter(\.isTrusted) }
    private var nearby: [PeerRow] {
        let pendingIds = Set(runtime.pendingRequests.map(\.id))
        return runtime.peers.filter { !$0.isTrusted && !pendingIds.contains($0.id) }
    }

    private var localNetworkWarning: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(String(localized: "No local network access"))
                .font(Tokens.Font.name)
                .foregroundStyle(Tokens.Palette.urgent)
            Text(String(localized: "Earshot cannot see anyone until you allow it in System Settings → Privacy & Security → Local Network."))
                .font(Tokens.Font.caption)
                .foregroundStyle(Tokens.Palette.secondary)
                .fixedSize(horizontal: false, vertical: true)
            Button(String(localized: "Open Settings")) {
                if let url = URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_LocalNetwork") {
                    NSWorkspace.shared.open(url)
                }
            }
            .controlSize(.small)
            .padding(.top, 2)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }

    private var header: some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                TextField(String(localized: "Your name"), text: $nameDraft)
                    .textFieldStyle(.plain)
                    .font(Tokens.Font.nameStrong)
                    .onSubmit { runtime.rename(to: nameDraft) }
                Text(verbatim: runtime.myFingerprint)
                    .font(Tokens.Font.caption)
                    .foregroundStyle(Tokens.Palette.secondary)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .layoutPriority(1)

            Menu {
                Button(String(localized: "15 minutes")) { runtime.pause(minutes: 15) }
                Button(String(localized: "1 hour")) { runtime.pause(minutes: 60) }
                Button(String(localized: "Until tomorrow")) { runtime.pause(minutes: 60 * 12) }
                Divider()
                Button(String(localized: "Resume")) { runtime.pause(minutes: nil) }
            } label: {
                Text(runtime.pausedUntil == nil
                     ? String(localized: "Pause")
                     : String(localized: "Paused"))
                    .font(Tokens.Font.caption)
            }
            .menuStyle(.borderlessButton)
            .fixedSize()
        }
        .padding(.horizontal, 14)
        .padding(.top, 12)
        .padding(.bottom, 10)
    }

    private func section(_ title: String) -> some View {
        Text(title)
            .font(Tokens.Font.section)
            .kerning(0.5)
            .foregroundStyle(Tokens.Palette.secondary)
            .padding(.horizontal, 16)
            .padding(.top, 11)
            .padding(.bottom, 4)
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func requestRow(_ row: PeerRow) -> some View {
        HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 1) {
                Text(row.displayName)
                    .font(Tokens.Font.name)
                    .lineLimit(1)
                    .truncationMode(.tail)
                Text(verbatim: row.fingerprint)
                    .font(Tokens.Font.caption)
                    .foregroundStyle(Tokens.Palette.secondary)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .layoutPriority(1)

            Button(String(localized: "Accept")) { runtime.acceptPairing(row) }
                .controlSize(.small)
            Button(String(localized: "Decline")) { runtime.rejectPairing(row) }
                .controlSize(.small)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 3)
    }

    private func nearbyRow(_ row: PeerRow) -> some View {
        HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 1) {
                Text(row.displayName)
                    .font(Tokens.Font.name)
                    .lineLimit(1)
                    .truncationMode(.tail)
                Text(verbatim: row.status ?? row.fingerprint)
                    .font(Tokens.Font.caption)
                    .foregroundStyle(Tokens.Palette.secondary)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .layoutPriority(1)

            Button(String(localized: "Add")) { runtime.requestPairing(row) }
                .controlSize(.small)
                .disabled(!row.isOnline)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 3)
    }

    private var footer: some View {
        HStack {
            Spacer()
            Button(String(localized: "Quit")) { NSApplication.shared.terminate(nil) }
                .buttonStyle(.plain)
                .font(Tokens.Font.footer)
                .foregroundStyle(Tokens.Palette.secondary)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
    }
}
