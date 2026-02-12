import Foundation

@MainActor
class AutoReconnectManager: ObservableObject {

    enum ReconnectState: Equatable {
        case idle
        case waiting(attempt: Int, nextRetryDate: Date)
        case attempting(attempt: Int)
    }

    @Published var state: ReconnectState = .idle

    private var reconnectTask: Task<Void, Never>?
    private static let backoffDelays: [TimeInterval] = [2, 4, 8, 16, 30]

    nonisolated init() {}

    func startReconnecting(address: String, connectAction: @escaping (String) async -> Void) {
        stop()

        reconnectTask = Task { [weak self] in
            var attempt = 0
            while !Task.isCancelled {
                attempt += 1
                let delay = Self.backoffDelays[min(attempt - 1, Self.backoffDelays.count - 1)]
                let nextRetry = Date().addingTimeInterval(delay)

                self?.state = .waiting(attempt: attempt, nextRetryDate: nextRetry)

                do {
                    try await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
                } catch {
                    return // Cancelled
                }

                guard !Task.isCancelled else { return }

                self?.state = .attempting(attempt: attempt)
                await connectAction(address)

                // Wait a bit to see if connection succeeds
                do {
                    try await Task.sleep(nanoseconds: 3_000_000_000)
                } catch {
                    return
                }

                // If we're still running, connection didn't trigger onConnectionEstablished
                // so we loop again
            }
        }
    }

    func stop() {
        reconnectTask?.cancel()
        reconnectTask = nil
        state = .idle
    }

    func onConnectionEstablished() {
        stop()
    }
}
