package org.freewheel.core.service

import org.freewheel.core.utils.Lock
import org.freewheel.core.utils.withLock
import kotlinx.coroutines.CompletableDeferred

/**
 * Outcome of awaiting a [BleTeardownTracker] deferred.
 *
 * - [DRAINED]: the OS delivered the disconnect / connection-failed callback
 *   for the prior session. The address is safe for a new connect.
 * - [INVALIDATED]: the BLE stack itself was reset (adapter cycled, manager
 *   destroyed) and the deferred was completed by [BleTeardownTracker.reset]
 *   rather than by an authoritative drain signal. Caller must NOT proceed —
 *   the connect would issue against a stack that's no longer valid.
 */
enum class TeardownDrainResult {
    DRAINED,
    INVALIDATED,
}

/**
 * Tracks pending OS-level BLE teardowns so same-peripheral reconnects can
 * serialize until the prior session's callbacks have drained.
 *
 * ## Why this exists
 *
 * `BluetoothPeripheral` (Android, via Blessed) and `CBPeripheral` (iOS) are
 * **not** session tokens — both libraries reuse the same wrapper instance for
 * the same address across reconnects. That means a late callback from session
 * 1 to peripheral A is indistinguishable from a fresh callback to session 2's
 * peripheral A using only the peripheral reference. Peripheral identity is
 * enough for the cross-device race (A vs B), but it cannot distinguish two
 * attempts to the same A.
 *
 * The cure is to wait for the OS to confirm the prior session has drained
 * (the `onDisconnectedPeripheral` / `onConnectionFailed` callback fired)
 * before initiating a new connect to the same address. Cross-device connects
 * skip the wait entirely.
 *
 * ## Usage
 *
 * - The platform's `cancelConnection(peripheral)` is followed by
 *   [startTeardown] for that peripheral's address.
 * - The corresponding disconnect / connection-failed callback calls
 *   [completeTeardown] for that address — *before* any peripheral-identity
 *   guard, so even stale callbacks signal the drain.
 * - The platform's `connect()` consults [pendingTeardownDeferredFor] for the
 *   target address and awaits the deferred (with a bounded timeout) before
 *   issuing a new `connectPeripheral`. On timeout the platform must **fail
 *   the connect** rather than proceed (see "No timeout fallback" below).
 *
 * ## Idempotency
 *
 * [startTeardown] is idempotent: calling it twice for the same address
 * without an intervening completion returns the existing deferred instead of
 * replacing it. Replacing would prematurely complete waiters that were
 * blocking on the original drain — they'd resume thinking the OS had
 * confirmed teardown when in fact a redundant cancelConnection just fired
 * and the OS hasn't drained yet.
 *
 * ## No timeout fallback
 *
 * There is intentionally no `abandonTeardown` or stray-callback budget. Once
 * a same-address reconnect's await times out, the platform layer has no
 * authoritative way to classify later callbacks for that address — a stale
 * callback from the timed-out session is indistinguishable from a fresh
 * callback for any session that proceeded anyway. So the platform must
 * surface the timeout as a connect failure and leave the deferred in place,
 * to be cleared only by:
 *  - The real OS drain callback finally arriving — [completeTeardown]
 *    completes the deferred. The next connect to that address proceeds
 *    normally.
 *  - An explicit [reset], called when the BLE manager is reinitialized or
 *    the adapter cycles power. All pending teardowns are wiped.
 */
class BleTeardownTracker {
    private val lock = Lock()
    private val pendingTeardowns: MutableMap<String, CompletableDeferred<TeardownDrainResult>> = mutableMapOf()

    /**
     * Mark [address] as having an in-flight OS teardown. Idempotent — if a
     * teardown is already pending for this address, returns the existing
     * deferred instead of creating a new one.
     */
    fun startTeardown(address: String): CompletableDeferred<TeardownDrainResult> = lock.withLock {
        pendingTeardowns.getOrPut(address) { CompletableDeferred() }
    }

    /**
     * Signal that the OS has confirmed teardown for [address] (the platform
     * disconnect / connection-failed callback fired). Completes the current
     * deferred with [TeardownDrainResult.DRAINED] and removes the entry.
     * No-op if no entry exists.
     */
    fun completeTeardown(address: String): Unit = lock.withLock {
        pendingTeardowns.remove(address)?.complete(TeardownDrainResult.DRAINED)
    }

    /**
     * Look up the deferred for [address]'s pending teardown, or null if none
     * is in flight. Caller awaits the returned deferred to block until the
     * OS confirms drain.
     */
    fun pendingTeardownDeferredFor(address: String): CompletableDeferred<TeardownDrainResult>? = lock.withLock {
        pendingTeardowns[address]
    }

    /**
     * Wipe all pending teardowns. Use when the BLE manager is reinitialized
     * or the adapter cycles power — the OS BLE state has been reset, so any
     * lingering teardown signals from prior sessions are no longer
     * meaningful. All pending deferreds are completed with
     * [TeardownDrainResult.INVALIDATED] so awaiters can distinguish "stack
     * invalidated" from a genuine OS drain and refuse to proceed.
     * Subsequent connects find no entries and proceed normally.
     */
    fun reset(): Unit = lock.withLock {
        for ((_, deferred) in pendingTeardowns) {
            deferred.complete(TeardownDrainResult.INVALIDATED)
        }
        pendingTeardowns.clear()
    }

    /** Test-only: number of in-flight teardowns. */
    internal fun size(): Int = lock.withLock { pendingTeardowns.size }
}
