import Foundation
import Network
import OSLog
@preconcurrency import core

/// Куда сеть отдаёт события. Реализуется средой выполнения на главном потоке.
@MainActor
protocol NetworkDelegate: AnyObject {
    func networkFoundPeer(_ peer: Peer, endpointKey: String)
    func networkLostPeer(keyHex: String)
    func networkConnected(connectionId: String)
    func networkReceived(connectionId: String, data: Data)
    func networkDisconnected(connectionId: String)
    func networkLocalNetworkDenied()
}

/// Bonjour и TCP на Network.framework.
///
/// Свой последовательный поток на всё состояние: Network.framework зовёт обратно
/// со своей очереди, а ядро трогается только с главного потока.
final class EarshotNetwork: Transport, @unchecked Sendable {
    private let log = Logger(subsystem: "app.earshot.Earshot", category: "network")
    private let queue = DispatchQueue(label: "app.earshot.network")

    private var listener: NWListener?
    private var browser: NWBrowser?
    private var connections: [String: NWConnection] = [:]
    private var endpoints: [String: NWEndpoint] = [:]
    private var incomingCounter = 0

    weak var delegate: NetworkDelegate?
    private var identity: Identity?

    // MARK: запуск

    private func debug(_ text: String) {
        #if DEBUG
        print("[earshot/net] \(text)")
        fflush(stdout)
        #endif
        log.notice("\(text, privacy: .public)")
    }

    func start(identity: Identity) {
        debug("start requested")
        queue.async { [self] in
            self.identity = identity
            startListener(identity: identity)
            startBrowser()
        }
    }

    func stop() {
        queue.async { [self] in
            listener?.cancel(); listener = nil
            browser?.cancel(); browser = nil
            connections.values.forEach { $0.cancel() }
            connections.removeAll()
        }
    }

    private func startListener(identity: Identity) {
        guard let listener = try? NWListener(using: .tcp) else {
            debug("listener could not be created")
            return
        }
        let fields = TxtRecord.shared.of(identity: identity)
        var txt = NWTXTRecord()
        for (key, value) in fields {
            txt[key] = value
        }
        let type = TxtRecord.shared.SERVICE_TYPE
        debug("service type=\(type) fields=\(fields.keys.sorted())")
        listener.service = NWListener.Service(
            name: nil,
            type: type,
            domain: nil,
            txtRecord: txt
        )
        listener.newConnectionHandler = { [weak self] connection in
            self?.queue.async {
                guard let self else { return }
                self.incomingCounter += 1
                let id = "in:\(self.incomingCounter)"
                self.adopt(connection, as: id)
            }
        }
        listener.stateUpdateHandler = { [weak self] state in
            self?.debug("listener state: \(state)")
            self?.reportIfUnauthorised(state)
        }
        listener.serviceRegistrationUpdateHandler = { [weak self] change in
            self?.debug("service registration: \(change)")
        }
        listener.start(queue: queue)
        self.listener = listener
        debug("advertising \(TxtRecord.shared.SERVICE_TYPE)")
    }

    private func startBrowser() {
        let descriptor = NWBrowser.Descriptor.bonjourWithTXTRecord(
            type: TxtRecord.shared.SERVICE_TYPE,
            domain: nil
        )
        let browser = NWBrowser(for: descriptor, using: .tcp)
        browser.browseResultsChangedHandler = { [weak self] results, changes in
            self?.queue.async { self?.handle(results: results, changes: changes) }
        }
        browser.stateUpdateHandler = { [weak self] state in
            self?.debug("browser state: \(state)")
            self?.reportIfUnauthorised(state)
        }
        browser.start(queue: queue)
        self.browser = browser
    }

    /// Без разрешения на локальную сеть mDNS не работает, а сокет открывается —
    /// приложение выглядит рабочим и молчит. Отказ надо показывать человеку.
    private func reportIfUnauthorised(_ state: NWListener.State) {
        if case .failed(let error) = state, isNoAuth(error) { announceDenial() }
    }

    private func reportIfUnauthorised(_ state: NWBrowser.State) {
        if case .failed(let error) = state, isNoAuth(error) { announceDenial() }
    }

    private func isNoAuth(_ error: NWError) -> Bool {
        if case .dns(let code) = error { return code == DNSServiceErrorType(kDNSServiceErr_NoAuth) }
        return false
    }

    private func announceDenial() {
        DispatchQueue.main.async { [weak self] in
            self?.delegate?.networkLocalNetworkDenied()
        }
    }

    private func handle(results: Set<NWBrowser.Result>, changes: Set<NWBrowser.Result.Change>) {
        for change in changes {
            switch change {
            case .added(let result), .changed(_, let result, _):
                guard case .bonjour(let txt) = result.metadata else { continue }
                var dictionary: [String: String] = [:]
                for key in ["v", "pk", "n"] {
                    if let value = txt[key] { dictionary[key] = value }
                }
                guard let peer = TxtRecord.shared.parse(txt: dictionary) else { continue }
                endpoints[peer.keyHex] = result.endpoint
                let found = peer
                DispatchQueue.main.async { [weak self] in
                    self?.delegate?.networkFoundPeer(found, endpointKey: found.keyHex)
                }
            case .removed(let result):
                guard case .bonjour(let txt) = result.metadata else { continue }
                var dictionary: [String: String] = [:]
                for key in ["v", "pk", "n"] {
                    if let value = txt[key] { dictionary[key] = value }
                }
                guard let peer = TxtRecord.shared.parse(txt: dictionary) else { continue }
                let lost = peer.keyHex
                DispatchQueue.main.async { [weak self] in
                    self?.delegate?.networkLostPeer(keyHex: lost)
                }
            default:
                continue
            }
        }
    }

    // MARK: Transport

    func connect(connectionId: String, endpoint: String) {
        queue.async { [self] in
            // Идентификатор исходящего соединения несёт в себе ключ пира — по нему
            // и находится адрес, найденный обозревателем.
            let keyHex = connectionId.hasPrefix("out:")
                ? String(connectionId.dropFirst(4))
                : connectionId
            guard let target = endpoints[keyHex] else {
                log.error("no endpoint for \(keyHex, privacy: .public)")
                return
            }
            guard connections[connectionId] == nil else { return }
            adopt(NWConnection(to: target, using: .tcp), as: connectionId)
        }
    }

    func send(connectionId: String, bytes: KotlinByteArray) {
        let data = bytes.asData
        queue.async { [self] in
            connections[connectionId]?.send(content: data, completion: .contentProcessed { _ in })
        }
    }

    func disconnect(connectionId: String) {
        queue.async { [self] in
            connections.removeValue(forKey: connectionId)?.cancel()
        }
    }

    // MARK: соединения

    private func adopt(_ connection: NWConnection, as id: String) {
        connections[id] = connection
        connection.stateUpdateHandler = { [weak self] state in
            guard let self else { return }
            queue.async {
                switch state {
                case .ready:
                    DispatchQueue.main.async { self.delegate?.networkConnected(connectionId: id) }
                    self.receive(on: connection, id: id)
                case .failed, .cancelled:
                    self.connections.removeValue(forKey: id)
                    DispatchQueue.main.async { self.delegate?.networkDisconnected(connectionId: id) }
                default:
                    break
                }
            }
        }
        connection.start(queue: queue)
    }

    private func receive(on connection: NWConnection, id: String) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 64 * 1024) { [weak self] data, _, isComplete, error in
            guard let self else { return }
            if let data, !data.isEmpty {
                DispatchQueue.main.async { self.delegate?.networkReceived(connectionId: id, data: data) }
            }
            if isComplete || error != nil {
                queue.async {
                    self.connections.removeValue(forKey: id)?.cancel()
                    DispatchQueue.main.async { self.delegate?.networkDisconnected(connectionId: id) }
                }
                return
            }
            self.receive(on: connection, id: id)
        }
    }
}
