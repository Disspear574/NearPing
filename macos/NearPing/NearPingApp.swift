import SwiftUI
import AppKit
import CoreServices

@main
struct NearPingApp: App {
    @State private var runtime = NearPingRuntime.shared

    // Starts at app launch, not first click: MenuBarExtra content is not created until the menu opens, so nothing would be announced.
    init() {
        // Refresh bundle metadata after replacing a test build; Notification Center resolves the sender icon via Launch Services.
        let status = LSRegisterURL(Bundle.main.bundleURL as CFURL, true)
        if status != noErr { NSLog("NearPing application registration failed: %d", status) }
        if let url = Bundle.main.url(forResource: "AppIcon", withExtension: "icns"),
           let icon = NSImage(contentsOf: url) {
            NSApplication.shared.applicationIconImage = icon
        }
        NearPingRuntime.shared.start()
        LoginItem.enableOnFirstLaunch()
    }

    // MenuBarExtra uses the NSImage size, not the SwiftUI label frame; a separate copy keeps the mark size for the popup title.
    private static let menuBarIcon: NSImage = {
        let source = NSImage(named: "NearPingMark")
        let icon = (source?.copy() as? NSImage) ?? NSImage(size: NSSize(width: 18, height: 18))
        icon.size = NSSize(width: 18, height: 18)
        icon.isTemplate = true
        return icon
    }()

    var body: some Scene {
        MenuBarExtra {
            PopoverView(runtime: runtime)
        } label: {
            if runtime.pausedUntil == nil {
                Image(nsImage: Self.menuBarIcon)
                    .accessibilityLabel("NearPing")
            } else {
                Image(systemName: "pause.circle").accessibilityLabel(String(localized: "Paused"))
            }
        }
        .menuBarExtraStyle(.window)

        Settings {
            SettingsView(runtime: runtime)
        }
    }
}

private struct PopoverView: View {
    @Bindable var runtime: NearPingRuntime
    @State private var contactToRemove: PeerRow?
    @AppStorage("nearping.appearance") private var appearance = "system"
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var selectedScheme: ColorScheme? {
        appearance == "dark" ? .dark : appearance == "light" ? .light : nil
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            if let row = contactToRemove {
                removeContactScreen(row)
                    .transition(.opacity)
            } else {
                contactsScreen
            }
        }
        .frame(width: Tokens.Metric.popoverWidth)
        .fixedSize(horizontal: false, vertical: true)
        .background(Tokens.Palette.background)
        .foregroundStyle(Tokens.Palette.primary)
        .tint(Tokens.Palette.accent)
        .preferredColorScheme(selectedScheme)
        .onAppear { Appearance.apply(appearance); runtime.refreshPermissions() }
        .onReceive(NotificationCenter.default.publisher(for: NSApplication.didBecomeActiveNotification)) { _ in runtime.refreshPermissions() }
        .onChange(of: appearance) { _, value in Appearance.apply(value) }
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.15), value: contactToRemove?.id)
        .onDisappear { contactToRemove = nil }
    }

    // Same MenuBarExtra window: a separate confirmation panel can dismiss its parent.
    private func removeContactScreen(_ row: PeerRow) -> some View {
        VStack(alignment: .leading, spacing: Tokens.Space.m) {
            Text(String(localized: "Remove contact?")).font(Tokens.Font.nameStrong)
            Text(row.displayName).font(Tokens.Font.name)
                .lineLimit(2).truncationMode(.tail)
            Text(String(localized: "You will need to pair again to send pings."))
                .font(Tokens.Font.caption)
                .foregroundStyle(Tokens.Palette.secondary)
                .fixedSize(horizontal: false, vertical: true)
            // macOS order: both buttons on the right; the destructive one is never the default.
            HStack(spacing: Tokens.Space.s) {
                Spacer(minLength: 0)
                Button(String(localized: "Cancel")) { contactToRemove = nil }
                    .keyboardShortcut(.cancelAction)
                Button(String(localized: "Remove contact"), role: .destructive) {
                    runtime.forget(row)
                    contactToRemove = nil
                }
                // The popup sets a primary foreground for everything, which hides the destructive red.
                .foregroundStyle(Tokens.Palette.urgent)
                .fixedSize()
            }
            .padding(.top, Tokens.Space.xs)
        }
        .padding(Tokens.Space.edge)
    }

    private var contactsScreen: some View {
        VStack(alignment: .leading, spacing: 0) {
            header
            Divider().padding(.horizontal, Tokens.Space.edge)

            if !runtime.pendingRequests.isEmpty {
                section(String(localized: "Pairing requests"))
                ForEach(runtime.pendingRequests) { row in
                    requestRow(row)
                }
            }

            if !contacts.isEmpty {
                section(String(localized: "Contacts"))
                ForEach(contacts) { row in
                    PeerRowView(
                        row: row,
                        onCall: { runtime.call(row, urgent: false) },
                        onUrgent: { runtime.call(row, urgent: true) },
                        onRemove: { contactToRemove = row }
                    )
                    .padding(.horizontal, Tokens.Space.s)
                }
            }

            section(String(localized: "Nearby"))
            if nearby.isEmpty {
                Text(String(localized: "Nobody else on this network yet. Open NearPing on another device."))
                    .font(Tokens.Font.caption)
                    .foregroundStyle(Tokens.Palette.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, Tokens.Space.edge)
                    .padding(.bottom, Tokens.Space.xs)
            } else {
                ForEach(nearby) { row in
                    nearbyRow(row)
                }
            }

            Divider().padding(.horizontal, Tokens.Space.edge).padding(.top, Tokens.Space.s)
            if runtime.localNetworkDenied { localNetworkWarning }
            if runtime.notifier.needsPermission {
                VStack(alignment: .leading, spacing: Tokens.Space.xs) {
                    Text(String(localized: "Notifications are off")).font(Tokens.Font.name)
                    Button(String(localized: "Enable notifications")) { runtime.notifier.enableNotifications() }
                        .padding(.top, Tokens.Space.xs)
                }
                .padding(.horizontal, Tokens.Space.edge)
                .padding(.vertical, Tokens.Space.s)
            }
            footer
        }
        .frame(width: Tokens.Metric.popoverWidth)
        // Solid background: on a material, contrast depends on the wallpaper.
        .background(Tokens.Palette.background)
    }

    private var contacts: [PeerRow] { runtime.peers.filter(\.isTrusted) }

    // Unpaired and disconnected peers are hidden: they cannot be added and would look like a duplicate of the live contact.
    private var nearby: [PeerRow] {
        let pendingIds = Set(runtime.pendingRequests.map(\.id))
        return runtime.peers.filter { !$0.isTrusted && $0.isOnline && !pendingIds.contains($0.id) }
    }

    private var localNetworkWarning: some View {
        VStack(alignment: .leading, spacing: Tokens.Space.xs) {
            Text(String(localized: "No local network access"))
                .font(Tokens.Font.name)
                .foregroundStyle(Tokens.Palette.urgent)
            Text(String(localized: "NearPing cannot see anyone until you allow it in System Settings → Privacy & Security → Local Network."))
                .font(Tokens.Font.caption)
                .foregroundStyle(Tokens.Palette.secondary)
                .fixedSize(horizontal: false, vertical: true)
            Button(String(localized: "Open Settings")) {
                if let url = URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_LocalNetwork") {
                    NSWorkspace.shared.open(url)
                }
            }
            .padding(.top, Tokens.Space.xs)
        }
        .padding(.horizontal, Tokens.Space.edge)
        .padding(.vertical, Tokens.Space.s)
    }

    private var header: some View {
        VStack(spacing: Tokens.Space.m) {
            HStack(spacing: Tokens.Space.s) {
                Image("NearPingMark").resizable().scaledToFit().frame(width: 22, height: 22)
                    .foregroundStyle(Tokens.Palette.accent)
                Text(verbatim: "NearPing").font(.title3.weight(.semibold))
                Spacer(minLength: Tokens.Space.s)
                Text(runtime.myDisplayName).font(Tokens.Font.caption)
                    .foregroundStyle(Tokens.Palette.secondary).lineLimit(1).truncationMode(.tail)
                // Settings and Quit live in a menu, as in system menu bar extras.
                Menu {
                    SettingsLink { Text(String(localized: "Settings…")) }
                        .keyboardShortcut(",", modifiers: .command)
                    Divider()
                    Button(String(localized: "Quit NearPing")) { NSApplication.shared.terminate(nil) }
                        .keyboardShortcut("q", modifiers: .command)
                } label: {
                    Image(systemName: "gearshape").frame(width: 28, height: 28).contentShape(Rectangle())
                }
                .menuStyle(.borderlessButton).menuIndicator(.hidden).fixedSize()
                .accessibilityLabel(String(localized: "Settings"))
            }
            HStack {
                Label(runtime.pausedUntil == nil ? String(localized: "Available") : String(localized: "Paused"), systemImage: runtime.pausedUntil == nil ? "circle.fill" : "pause.fill")
                    .font(Tokens.Font.caption).foregroundStyle(Tokens.Palette.secondary)
                Spacer()
                pauseControl
            }
        }
        .padding(.horizontal, Tokens.Space.edge)
        .padding(.vertical, Tokens.Space.m)
    }

    // Presets as in Focus: a free minutes field asked the person to do arithmetic.
    @ViewBuilder private var pauseControl: some View {
        if runtime.pausedUntil != nil {
            Button { runtime.pause(minutes: nil) } label: {
                Label(String(localized: "Resume"), systemImage: "play.fill")
                    .font(Tokens.Font.caption)
                    .padding(.vertical, Tokens.Space.xs)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain).fixedSize()
        } else {
            Menu {
                Button(String(localized: "15 minutes")) { runtime.pause(minutes: 15) }
                Button(String(localized: "1 hour")) { runtime.pause(minutes: 60) }
                Button(String(localized: "Until tomorrow")) { runtime.pause(minutes: Self.minutesUntilTomorrowMorning()) }
            } label: {
                Label(String(localized: "Pause"), systemImage: "pause.fill")
            }
            // A borderless menu ignores the label font; the control size sets its text size.
            .menuStyle(.borderlessButton).menuIndicator(.hidden).controlSize(.small).fixedSize()
        }
    }

    private static func minutesUntilTomorrowMorning(now: Date = .now) -> Int {
        let calendar = Calendar.current
        let tomorrow = calendar.date(byAdding: .day, value: 1, to: calendar.startOfDay(for: now)) ?? now.addingTimeInterval(86_400)
        let morning = calendar.date(bySettingHour: 9, minute: 0, second: 0, of: tomorrow) ?? tomorrow
        return max(1, Int(morning.timeIntervalSince(now) / 60))
    }

    private func section(_ title: String) -> some View {
        Text(title)
            .font(Tokens.Font.section)
            .foregroundStyle(Tokens.Palette.secondary)
            .padding(.horizontal, Tokens.Space.edge)
            .padding(.top, Tokens.Space.m)
            .padding(.bottom, Tokens.Space.xs)
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func requestRow(_ row: PeerRow) -> some View {
        HStack(spacing: Tokens.Space.s) {
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
            .layoutPriority(0)

            Button(String(localized: "Accept")) { runtime.acceptPairing(row) }
                .buttonStyle(.borderedProminent)
                .tint(Tokens.Palette.accent)
                .fixedSize(horizontal: true, vertical: true)
                .layoutPriority(1)
            Button(String(localized: "Decline")) { runtime.rejectPairing(row) }
                .buttonStyle(.bordered)
                .fixedSize(horizontal: true, vertical: true)
                .layoutPriority(1)
        }
        .padding(.horizontal, Tokens.Space.edge)
        .padding(.vertical, Tokens.Space.xs)
    }

    private func nearbyRow(_ row: PeerRow) -> some View {
        HStack(spacing: Tokens.Space.s) {
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
            .layoutPriority(0)

            if !runtime.requestedPairings.contains(row.id) {
                Button(String(localized: "Add")) { runtime.requestPairing(row) }
                    .buttonStyle(.borderedProminent)
                    .tint(Tokens.Palette.accent)
                    .fixedSize(horizontal: true, vertical: true)
                    .layoutPriority(1)
                    .disabled(!row.isOnline)
            }
        }
        .padding(.horizontal, Tokens.Space.edge)
        .padding(.vertical, Tokens.Space.xs)
    }

    private var footer: some View {
        // The hold gesture is not discoverable, so say it in words.
        Text(String(localized: "Urgent Ping is in the contact menu"))
            .font(Tokens.Font.caption)
            .foregroundStyle(Tokens.Palette.secondary)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, Tokens.Space.edge)
            .padding(.vertical, Tokens.Space.s)
    }
}
