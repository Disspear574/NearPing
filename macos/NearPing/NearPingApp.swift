import SwiftUI
import AppKit
import CoreServices

@main
struct NearPingApp: App {
    @State private var runtime = NearPingRuntime.shared

    /// Запуск при старте приложения, а не по первому клику: содержимое MenuBarExtra
    /// не создаётся, пока меню не открыли, и анонс в сети не начался бы вовсе.
    init() {
        // Refresh this bundle's metadata after replacing a test build in Applications.
        // Notification Center resolves the sender icon through Launch Services.
        let status = LSRegisterURL(Bundle.main.bundleURL as CFURL, true)
        if status != noErr { NSLog("NearPing application registration failed: %d", status) }
        if let url = Bundle.main.url(forResource: "AppIcon", withExtension: "icns"),
           let icon = NSImage(contentsOf: url) {
            NSApplication.shared.applicationIconImage = icon
        }
        NearPingRuntime.shared.start()
    }

    // MenuBarExtra использует размер NSImage, а не SwiftUI frame у label.
    // Отдельная копия сохраняет исходный размер знака для заголовка попапа.
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
    }
}

private struct PopoverView: View {
    @Bindable var runtime: NearPingRuntime
    @State private var nameDraft: String = ""
    @State private var showingSettings = false
    @State private var showingPause = false
    @State private var pauseMinutes = "30"
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
            } else if showingSettings {
                settingsScreen.transition(.asymmetric(insertion: .move(edge: .trailing).combined(with: .opacity), removal: .move(edge: .trailing).combined(with: .opacity)))
            } else {
                contactsScreen.transition(.asymmetric(insertion: .move(edge: .leading).combined(with: .opacity), removal: .move(edge: .leading).combined(with: .opacity)))
            }
        }
        .frame(width: Tokens.Metric.popoverWidth)
        .fixedSize(horizontal: false, vertical: true)
        .background(Tokens.Palette.background)
        .foregroundStyle(Tokens.Palette.primary)
        .tint(Tokens.Palette.accent)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .preferredColorScheme(selectedScheme)
        .onAppear { applyAppearance(); runtime.refreshPermissions() }
        .onReceive(NotificationCenter.default.publisher(for: NSApplication.didBecomeActiveNotification)) { _ in runtime.refreshPermissions() }
        .onChange(of: appearance) { _, _ in applyAppearance() }
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.19), value: showingSettings)
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.15), value: contactToRemove?.id)
        .onDisappear { contactToRemove = nil }
    }

    // Same MenuBarExtra window: a separate confirmation panel can dismiss its parent.
    private func removeContactScreen(_ row: PeerRow) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            Button { contactToRemove = nil } label: {
                Label(String(localized: "Back"), systemImage: "chevron.left")
            }
            .buttonStyle(.plain)
            .foregroundStyle(Tokens.Palette.accent)
            Text(String(localized: "Remove contact?")).font(Tokens.Font.nameStrong)
            Text(row.displayName).font(Tokens.Font.name)
                .lineLimit(2).truncationMode(.tail)
            Text(String(localized: "You will need to pair again to send pings."))
                .font(Tokens.Font.caption)
                .foregroundStyle(Tokens.Palette.secondary)
                .fixedSize(horizontal: false, vertical: true)
            HStack {
                Button(String(localized: "Cancel")) { contactToRemove = nil }
                    .buttonStyle(.bordered)
                Spacer(minLength: 8)
                Button(String(localized: "Remove contact"), role: .destructive) {
                    runtime.forget(row)
                    contactToRemove = nil
                }
                .buttonStyle(.borderedProminent)
                .tint(Tokens.Palette.urgentFill)
                .fixedSize()
            }
        }
        .padding(16)
        .onExitCommand { contactToRemove = nil }
    }

    private func applyAppearance() {
        // MenuBarExtra — окно AppKit: его NSColor-палитра не следует одной
        // preferredColorScheme. nil возвращает отслеживание системной темы.
        NSApp.appearance = appearance == "dark" ? NSAppearance(named: .darkAqua)
            : appearance == "light" ? NSAppearance(named: .aqua) : nil
    }

    private var contactsScreen: some View {
        VStack(alignment: .leading, spacing: 0) {
            header
            Rectangle().fill(Tokens.Palette.separator).frame(height: 1).padding(.horizontal, 12)



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
                        onUrgent: { runtime.call(row, urgent: true) },
                        onRemove: { contactToRemove = row }
                    )
                    .padding(.horizontal, 8)
                }
            }

            section(String(localized: "NEARBY"))
            if nearby.isEmpty {
                Text(String(localized: "Nobody else on this network yet. Open NearPing on another device."))
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

            Rectangle().fill(Tokens.Palette.separator).frame(height: 1).padding(.horizontal, 12).padding(.top, 8)
            if runtime.localNetworkDenied { localNetworkWarning }
            if runtime.notifier.needsPermission {
                VStack(alignment: .leading, spacing: 6) {
                    Text(String(localized: "Notifications are off")).font(Tokens.Font.name)
                    Button(String(localized: "Enable notifications")) { runtime.notifier.enableNotifications() }
                        .controlSize(.small)
                }.padding(16)
            }
            footer
        }
        .frame(width: Tokens.Metric.popoverWidth)
        // Сплошной фон: на материале контраст зависит от обоев под окном.
        .background(Tokens.Palette.background)
        .onAppear { nameDraft = runtime.myDisplayName }
    }

    private var contacts: [PeerRow] { runtime.peers.filter(\.isTrusted) }

    /// Неспаренные и отключившиеся не показываются: добавить их нельзя, а в списке
    /// они выглядят как дубль контакта, который рядом стоит живым.
    private var nearby: [PeerRow] {
        let pendingIds = Set(runtime.pendingRequests.map(\.id))
        return runtime.peers.filter { !$0.isTrusted && $0.isOnline && !pendingIds.contains($0.id) }
    }

    private var localNetworkWarning: some View {
        VStack(alignment: .leading, spacing: 4) {
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
            .controlSize(.small)
            .padding(.top, 2)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }

    private var header: some View {
        VStack(spacing: 14) {
            HStack(spacing: 8) {
                Image("NearPingMark").resizable().scaledToFit().frame(width: 22, height: 22)
                    .foregroundStyle(Tokens.Palette.accent)
                Text(verbatim: "NearPing").font(.system(size: 16, weight: .semibold))
                Spacer(minLength: 8)
                Text(runtime.myDisplayName).font(Tokens.Font.caption)
                    .foregroundStyle(Tokens.Palette.secondary).lineLimit(1).truncationMode(.tail)
                Button {
                    nameDraft = runtime.myDisplayName
                    showingSettings = true
                } label: { Image(systemName: "gearshape").frame(width: 24, height: 24) }
                .buttonStyle(.plain)
                .accessibilityLabel(String(localized: "Settings"))
            }
            HStack {
                Label(runtime.pausedUntil == nil ? String(localized: "Available") : String(localized: "Paused"), systemImage: runtime.pausedUntil == nil ? "circle.fill" : "pause.fill")
                    .font(Tokens.Font.caption).foregroundStyle(Tokens.Palette.secondary)
                Spacer()
                Button {
                    if runtime.pausedUntil != nil { runtime.pause(minutes: nil) }
                    else { showingPause = true }
                } label: {
                    Label(runtime.pausedUntil == nil ? String(localized: "Pause") : String(localized: "Resume"), systemImage: "pause.fill")
                        .font(Tokens.Font.caption)
                }
                .buttonStyle(.plain).fixedSize()
                .popover(isPresented: $showingPause) {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(String(localized: "Pause duration")).font(Tokens.Font.nameStrong)
                        HStack {
                            TextField(String(localized: "Minutes"), text: $pauseMinutes).textFieldStyle(.roundedBorder)
                            Text(String(localized: "Minutes"))
                        }
                        Text(String(localized: "From 1 minute to 7 days")).font(Tokens.Font.caption)
                        Button(String(localized: "Pause")) {
                            if let minutes = Int(pauseMinutes), (1...10080).contains(minutes) {
                                runtime.pause(minutes: minutes); showingPause = false
                            }
                        }.buttonStyle(.borderedProminent)
                            .disabled(!(Int(pauseMinutes).map { (1...10080).contains($0) } ?? false))
                    }.padding(16).frame(width: 230)
                        .foregroundStyle(Tokens.Palette.primary).background(Tokens.Palette.background)
                }
            }
        }
        .padding(14)
    }

    private var settingsScreen: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 18) {
                Button { showingSettings = false } label: {
                    Label(String(localized: "Back"), systemImage: "chevron.left")
                }.buttonStyle(.plain).foregroundStyle(Tokens.Palette.accent)
                Text(String(localized: "Settings")).font(Tokens.Font.nameStrong)
                Spacer()
            }.padding(16)
            Divider()
            VStack(alignment: .leading, spacing: 8) {
                Text(String(localized: "Your name")).font(Tokens.Font.name)
                TextField(String(localized: "Your name"), text: $nameDraft).textFieldStyle(.roundedBorder)
                Text(String(localized: "Shown to people on your local network."))
                    .font(Tokens.Font.caption).foregroundStyle(Tokens.Palette.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                Text(String(localized: "Appearance")).font(Tokens.Font.name).padding(.top, 18)
                Picker(String(localized: "Appearance"), selection: $appearance) {
                    Text(String(localized: "System")).tag("system")
                    Text(String(localized: "Light")).tag("light")
                    Text(String(localized: "Dark")).tag("dark")
                }.labelsHidden().pickerStyle(.segmented)
                Text(verbatim: runtime.myFingerprint).font(.system(size: 11, design: .monospaced))
                    .foregroundStyle(Tokens.Palette.secondary).textSelection(.enabled).padding(.top, 18)
                HStack {
                    Spacer()
                    Button(String(localized: "Save")) {
                        runtime.rename(to: nameDraft)
                        showingSettings = false
                    }.buttonStyle(.borderedProminent)
                        .disabled(nameDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }.padding(.top, 18)
            }.padding(16)
        }
        .onExitCommand { showingSettings = false }
    }

    private func section(_ title: String) -> some View {
        Text(title)
            .font(Tokens.Font.section)
            .kerning(0.5)
            .foregroundStyle(Tokens.Palette.primary.opacity(0.65))
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
            .layoutPriority(0)

            Button(String(localized: "Accept")) { runtime.acceptPairing(row) }
                .buttonStyle(.borderedProminent)
                .tint(Tokens.Palette.accent)
                .controlSize(.small)
                .fixedSize(horizontal: true, vertical: true)
                .layoutPriority(1)
            Button(String(localized: "Decline")) { runtime.rejectPairing(row) }
                .buttonStyle(.bordered)
                .controlSize(.small)
                .fixedSize(horizontal: true, vertical: true)
                .layoutPriority(1)
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
            .layoutPriority(0)

            if !runtime.requestedPairings.contains(row.id) {
                Button(String(localized: "Add")) { runtime.requestPairing(row) }
                    .buttonStyle(.borderedProminent)
                    .tint(Tokens.Palette.accent)
                    .controlSize(.small)
                .fixedSize(horizontal: true, vertical: true)
                .layoutPriority(1)
                    .disabled(!row.isOnline)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 3)
    }

    private var footer: some View {
        HStack(spacing: 8) {
            // Жест удержания ниоткуда не следует — про него надо сказать словами.
            Text(String(localized: "Urgent Ping is in the contact menu"))
                .font(Tokens.Font.caption)
                .foregroundStyle(Tokens.Palette.secondary)
                .lineLimit(1)
                .truncationMode(.tail)
                .layoutPriority(1)
            Spacer(minLength: 0)
            Button(String(localized: "Quit")) { NSApplication.shared.terminate(nil) }
                .buttonStyle(.bordered)
                .controlSize(.small)
                .fixedSize()
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
    }
}
