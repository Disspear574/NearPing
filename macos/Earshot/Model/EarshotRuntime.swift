import Foundation
import Observation
import OSLog
@preconcurrency import core

/// Строка списка: плоский снимок для интерфейса.
struct PeerRow: Identifiable, Equatable {
    let id: String
    let displayName: String
    let fingerprint: String
    let isTrusted: Bool
    let isOnline: Bool
    let pausedUntil: Date?
    var cooldown: Int = 0
    var urgentCooldown: Int = 0
    var timeoutHint = false
    var status: String?
    var statusKind: StatusKind = .neutral

    enum StatusKind { case neutral, good, paused, failed }
}

/// Связывает ядро с интерфейсом. Всё ядро трогается только с главного потока.
@MainActor
@Observable
final class EarshotRuntime {
    static let shared = EarshotRuntime()

    private let log = Logger(subsystem: "com.disspear574.earshot", category: "runtime")

    private(set) var peers: [PeerRow] = []
    private(set) var pendingRequests: [PeerRow] = []
    private(set) var requestedPairings: Set<String> = []
    private(set) var myFingerprint: String = ""
    var myDisplayName: String = ""
    private(set) var pausedUntil: Date?
    /// Разрешение на локальную сеть отозвано или не выдано: без него mDNS молчит.
    private(set) var localNetworkDenied = false

    private let keyStore = AppleKeyStore.shared
    private var network = EarshotNetwork()
    let notifier = Notifier()
    private var ticker: Task<Void, Never>?
    private var timeoutPeers: Set<String> = []
    private var retryTicks = 0
    private let urgent = UrgentAlert()
    private var engine: Engine?
    // Сеть держит делегата слабо — сильная ссылка живёт здесь, иначе он умрёт сразу.
    private var networkDelegate: Delegate?
    private var callStatuses: [String: (String, PeerRow.StatusKind)] = [:]
    private var statusClears: [String: Task<Void, Never>] = [:]

    private var started = false

    func start() {
        guard !started else { return }
        started = true
        if ticker == nil {
            ticker = Task { [weak self] in
                while !Task.isCancelled {
                    try? await Task.sleep(for: .seconds(1))
                    guard let self, !Task.isCancelled else { return }
                    if let until = self.pausedUntil, until <= Date() { self.pause(minutes: nil) }
                    self.engine?.tick()
                    self.retryTicks += 1
                    if self.retryTicks % 5 == 0 { self.engine?.retryConnections() }
                    self.refresh()
                }
            }
        }
        let identity = keyStore.loadOrCreate()
        myFingerprint = identity.fingerprint
        myDisplayName = identity.displayName

        let engine = Engine(
            identity: identity,
            signer: keyStore,
            crypto: AppleCrypto(),
            registry: PeerRegistry(storage: FilePeerStorage()),
            transport: network,
            clock: SystemClock(),
            listener: Listener(owner: self)
        )
        self.engine = engine
        let delegate = Delegate(owner: self)
        networkDelegate = delegate
        network.delegate = delegate
        network.start(identity: identity)
        notifier.requestPermission()
        refresh()
        log.notice("runtime started as \(identity.fingerprint, privacy: .public)")
    }

    // MARK: действия пользователя

    func rename(to name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, trimmed != myDisplayName else { return }
        keyStore.setDisplayName(name: trimmed)
        // Имя едет в hello и в TXT-запись. Ядро само рвёт и переподнимает сессии,
        // а доверие, входящие запросы, лимиты и пауза остаются на месте —
        // пересоздавать движок и сеть незачем, раньше это теряло чужие запросы.
        engine?.rename(displayName: trimmed)
        myDisplayName = engine?.myDisplayName ?? trimmed
        network.retryDiscovery()
        refresh()
    }

    func call(_ row: PeerRow, urgent isUrgent: Bool) {
        guard (engine?.callCooldownSeconds(keyHex: row.id, urgent: isUrgent) ?? 0) == 0 else { return }
        guard let id = engine?.call(keyHex: row.id, urgent: isUrgent) else {
            setStatus(row.id, String(localized: "not delivered"), .failed)
            return
        }
        if !isUrgent, (engine?.callCooldownSeconds(keyHex: row.id, urgent: false) ?? 0) > 3 { timeoutPeers.insert(row.id) }
        setStatus(row.id, String(localized: "sent"), .neutral)
        log.notice("call \(id, privacy: .public) urgent=\(isUrgent)")
    }

    func requestPairing(_ row: PeerRow) {
        guard !requestedPairings.contains(row.id) else { return }
        requestedPairings.insert(row.id)
        engine?.requestPairing(keyHex: row.id)
        setStatus(row.id, String(localized: "request sent"), .paused)
    }

    func acceptPairing(_ row: PeerRow) { engine?.acceptPairing(keyHex: row.id); refresh() }
    func rejectPairing(_ row: PeerRow) { engine?.rejectPairing(keyHex: row.id); refresh() }
    func forget(_ row: PeerRow) { callStatuses.removeValue(forKey: row.id); requestedPairings.remove(row.id); engine?.forget(keyHex: row.id); refresh() }

    func pause(minutes: Int?) {
        guard let engine else { return }
        if let minutes {
            guard (1...10080).contains(minutes) else { return }
            let until = Date().addingTimeInterval(TimeInterval(minutes * 60))
            pausedUntil = until
            engine.pauseUntil(epochSeconds: KotlinLong(value: Int64(until.timeIntervalSince1970)))
        } else {
            pausedUntil = nil
            engine.pauseUntil(epochSeconds: nil)
        }
        refresh()
    }

    func refreshPermissions() {
        Task { await notifier.refreshPermission() }
        if localNetworkDenied { network.retryDiscovery() }
    }

    // MARK: состояние

    fileprivate func refresh() {
        guard let engine else { return }
        peers = engine.peers().map { view in
            var row = PeerRow(
                id: view.keyHex,
                displayName: view.displayName,
                fingerprint: view.fingerprint,
                isTrusted: view.isTrusted,
                isOnline: view.isOnline,
                pausedUntil: view.pausedUntilEpochSeconds.map { Date(timeIntervalSince1970: TimeInterval(truncating: $0)) }
            )
            row.cooldown = Int(engine.callCooldownSeconds(keyHex: view.keyHex, urgent: false))
            row.urgentCooldown = Int(engine.callCooldownSeconds(keyHex: view.keyHex, urgent: true))
            if row.cooldown == 0 { timeoutPeers.remove(view.keyHex) }
            row.timeoutHint = timeoutPeers.contains(view.keyHex)
            if view.isTrusted { requestedPairings.remove(view.keyHex) }
            if requestedPairings.contains(view.keyHex) {
                row.status = String(localized: "request sent")
                row.statusKind = .paused
            }
            if let status = callStatuses[view.keyHex] {
                row.status = status.0
                row.statusKind = status.1
            }
            return row
        }
        pendingRequests = engine.pendingPairingRequests().map { peer in
            PeerRow(
                id: peer.keyHex,
                displayName: peer.displayName,
                fingerprint: peer.fingerprint,
                isTrusted: false,
                isOnline: true,
                pausedUntil: nil
            )
        }
    }

    fileprivate func setStatus(_ keyHex: String, _ text: String, _ kind: PeerRow.StatusKind) {
        callStatuses[keyHex] = (text, kind)
        refresh()
        statusClears[keyHex]?.cancel()
        statusClears[keyHex] = Task { [weak self] in
            try? await Task.sleep(for: .seconds(5))
            guard !Task.isCancelled else { return }
            await MainActor.run {
                self?.callStatuses.removeValue(forKey: keyHex)
                self?.refresh()
            }
        }
    }

    fileprivate func handleIncoming(peer: Peer, callId: String, isUrgent: Bool) {
        urgent.show(callerName: peer.displayName, isUrgent: isUrgent) { [weak self] in
            self?.engine?.markSeen(keyHex: peer.keyHex, callId: callId)
        }
        // Обычный Ping остаётся в истории системных уведомлений, но его звук
        // контролирует плашка, чтобы кнопка «Стоп» останавливала всё.
        if !isUrgent { notifier.ordinaryCall(from: peer.displayName, playSound: false) }
    }

    // MARK: мосты

    /// Ядро и сеть зовут только с главного потока, поэтому мосты на нём и живут.
    /// Протоколы из Kotlin об акторах не знают — отсюда @preconcurrency.
    @MainActor
    private final class Listener: @preconcurrency EngineListener {
        private unowned let owner: EarshotRuntime
        init(owner: EarshotRuntime) { self.owner = owner }

        func onPeersChanged() {
            owner.refresh()
        }

        func onPairingRequest(peer: Peer) {
            owner.notifier.pairingRequest(from: peer.displayName)
            owner.refresh()
        }

        func onPairingRejected(peer: Peer) {
            owner.requestedPairings.remove(peer.keyHex)
            owner.setStatus(peer.keyHex, String(localized: "declined"), .failed)
        }

        func onIncomingCall(peer: Peer, callId: String, urgent: Bool) {
            owner.handleIncoming(peer: peer, callId: callId, isUrgent: urgent)
        }

        func onCallState(keyHex: String, callId: String, state: CallState) {
            switch state {
            case .delivered: owner.setStatus(keyHex, String(localized: "delivered"), .neutral)
            case .seen: owner.setStatus(keyHex, String(localized: "seen it"), .good)
            case .failed: owner.setStatus(keyHex, String(localized: "not delivered"), .failed)
            default: break
            }
        }
    }

    @MainActor
    private final class Delegate: @preconcurrency NetworkDelegate {
        private unowned let owner: EarshotRuntime
        init(owner: EarshotRuntime) { self.owner = owner }

        func networkFoundPeer(_ peer: Peer, endpointKey: String) {
            owner.engine?.onPeerFound(peer: peer, endpoint: endpointKey)
        }

        func networkLostPeer(keyHex: String) {
            owner.engine?.onPeerLost(keyHex: keyHex)
        }

        func networkConnected(connectionId: String) {
            owner.engine?.onConnected(connectionId: connectionId)
        }

        func networkReceived(connectionId: String, data: Data) {
            owner.engine?.onBytes(connectionId: connectionId, chunk: data.asKotlinByteArray)
        }

        func networkDisconnected(connectionId: String) {
            owner.engine?.onDisconnected(connectionId: connectionId)
        }

        func networkLocalNetworkReady() { owner.localNetworkDenied = false }

        func networkLocalNetworkDenied() {
            owner.localNetworkDenied = true
        }
    }
}
