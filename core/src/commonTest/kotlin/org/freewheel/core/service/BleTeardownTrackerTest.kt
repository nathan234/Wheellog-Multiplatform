package org.freewheel.core.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BleTeardownTrackerTest {

    @Test
    fun `pendingTeardownDeferredFor returns null when none in flight`() {
        val tracker = BleTeardownTracker()
        assertNull(tracker.pendingTeardownDeferredFor("AA:BB"))
    }

    @Test
    fun `startTeardown registers a pending deferred`() {
        val tracker = BleTeardownTracker()
        val d = tracker.startTeardown("AA:BB")
        assertSame(d, tracker.pendingTeardownDeferredFor("AA:BB"))
        assertFalse(d.isCompleted)
        assertEquals(1, tracker.size())
    }

    @Test
    fun `completeTeardown releases the deferred with DRAINED and removes the entry`() = runTest {
        val tracker = BleTeardownTracker()
        val d = tracker.startTeardown("AA:BB")
        tracker.completeTeardown("AA:BB")
        assertTrue(d.isCompleted)
        assertEquals(TeardownDrainResult.DRAINED, d.await())
        assertNull(tracker.pendingTeardownDeferredFor("AA:BB"))
        assertEquals(0, tracker.size())
    }

    @Test
    fun `repeated startTeardown reuses the same deferred`() {
        // Codex's idempotency requirement: a redundant cancelConnection call
        // for the same address must not replace the deferred and prematurely
        // release waiters who were blocking on the original drain.
        val tracker = BleTeardownTracker()
        val d1 = tracker.startTeardown("AA:BB")
        val d2 = tracker.startTeardown("AA:BB")
        assertSame(d1, d2, "Repeated startTeardown for the same address must reuse the deferred")
        assertFalse(d1.isCompleted)
        assertEquals(1, tracker.size())
    }

    @Test
    fun `completeTeardown for unknown address is a no-op`() {
        val tracker = BleTeardownTracker()
        tracker.completeTeardown("AA:BB") // does not throw
        assertEquals(0, tracker.size())
    }

    @Test
    fun `completeTeardown does not release other addresses`() {
        val tracker = BleTeardownTracker()
        val a = tracker.startTeardown("AA:AA")
        val b = tracker.startTeardown("BB:BB")
        tracker.completeTeardown("BB:BB")
        assertTrue(b.isCompleted)
        assertFalse(a.isCompleted)
        assertNotNull(tracker.pendingTeardownDeferredFor("AA:AA"))
        assertNull(tracker.pendingTeardownDeferredFor("BB:BB"))
    }

    @Test
    fun `await on pending teardown blocks until completeTeardown fires`() = runTest {
        val tracker = BleTeardownTracker()
        tracker.startTeardown("AA:BB")
        val deferred = tracker.pendingTeardownDeferredFor("AA:BB")!!

        var completed = false
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            deferred.await()
            completed = true
        }
        runCurrent()
        assertFalse(completed, "await must block until completeTeardown fires")

        tracker.completeTeardown("AA:BB")
        runCurrent()
        assertTrue(completed)

        job.join()
    }

    @Test
    fun `repeated startTeardown then completeTeardown releases all waiters once`() = runTest {
        // Two coroutines awaiting the (idempotent) deferred for the same
        // address. completeTeardown releases both.
        val tracker = BleTeardownTracker()
        tracker.startTeardown("AA:BB")
        tracker.startTeardown("AA:BB") // idempotent
        val deferred = tracker.pendingTeardownDeferredFor("AA:BB")!!

        var firstDone = false
        var secondDone = false
        launch(UnconfinedTestDispatcher(testScheduler)) {
            deferred.await(); firstDone = true
        }
        launch(UnconfinedTestDispatcher(testScheduler)) {
            deferred.await(); secondDone = true
        }
        runCurrent()
        assertFalse(firstDone)
        assertFalse(secondDone)

        tracker.completeTeardown("AA:BB")
        runCurrent()
        assertTrue(firstDone)
        assertTrue(secondDone)
    }

    @Test
    fun `completeTeardown after timeout-style abandonment removes the entry`() {
        // Simulates the timeout path in BleManager.connect: caller gave up
        // waiting; calls completeTeardown to clear the slot so future connects
        // don't keep hitting the same stale entry.
        val tracker = BleTeardownTracker()
        val d = tracker.startTeardown("AA:BB")
        tracker.completeTeardown("AA:BB")
        assertTrue(d.isCompleted)
        assertNull(tracker.pendingTeardownDeferredFor("AA:BB"))
        assertEquals(0, tracker.size())
    }

    // ----- Quarantine semantics on timeout (Codex round-9 P1) -----

    @Test
    fun `pending deferred persists across a same-address re-startTeardown`() {
        // Idempotency means a second startTeardown for the same address keeps
        // the existing deferred rather than creating a new one. So if the
        // platform's await timed out (caller treats this as a connect
        // failure) and a future cancelActiveSession runs again for the same
        // address, the SAME deferred remains — it can only be released by an
        // authoritative completeTeardown (or [reset]).
        val tracker = BleTeardownTracker()
        val d1 = tracker.startTeardown("AA:BB")
        // Caller times out and bails (does NOT complete or abandon — the
        // deferred stays pending until OS catches up).
        // A subsequent cancelActiveSession for the same address does another
        // startTeardown — must reuse the same deferred.
        val d1Again = tracker.startTeardown("AA:BB")
        assertSame(d1, d1Again)
        assertFalse(d1.isCompleted)
        assertEquals(1, tracker.size())
    }

    @Test
    fun `late OS callback after a connect-time timeout completes the original deferred`() {
        // Connect 1 timed out waiting for d1. The deferred sits in the
        // tracker. When the OS finally delivers the drain, completeTeardown
        // releases d1 and clears the slot — the next connect proceeds
        // normally.
        val tracker = BleTeardownTracker()
        val d = tracker.startTeardown("AA:BB")
        // ... caller's await on d times out ... no action on tracker ...
        // OS catches up:
        tracker.completeTeardown("AA:BB")
        assertTrue(d.isCompleted)
        assertNull(tracker.pendingTeardownDeferredFor("AA:BB"))
    }

    @Test
    fun `reset completes all pending deferreds with INVALIDATED and clears the tracker`() = runTest {
        // Used when the BLE manager is reinitialized or the adapter cycles —
        // any lingering teardown signals from prior sessions are no longer
        // meaningful. Awaiters must be told the stack was invalidated so
        // they refuse to proceed (vs. treating reset as an authoritative
        // drain — Codex round-10 P1).
        val tracker = BleTeardownTracker()
        val a = tracker.startTeardown("AA:AA")
        val b = tracker.startTeardown("BB:BB")
        tracker.reset()
        assertEquals(TeardownDrainResult.INVALIDATED, a.await())
        assertEquals(TeardownDrainResult.INVALIDATED, b.await())
        assertEquals(0, tracker.size())
        assertNull(tracker.pendingTeardownDeferredFor("AA:AA"))
        assertNull(tracker.pendingTeardownDeferredFor("BB:BB"))
    }

    @Test
    fun `reset on empty tracker is a no-op`() {
        val tracker = BleTeardownTracker()
        tracker.reset()
        assertEquals(0, tracker.size())
    }

    @Test
    fun `completeTeardown after reset is a no-op for already-invalidated deferreds`() = runTest {
        // Race: reset fires first, then a stale OS callback delivers
        // completeTeardown. The second completion is dropped (CompletableDeferred
        // first-completer-wins) and the awaiter sees INVALIDATED — which is
        // the right answer because the stack was reset before the drain
        // arrived, so the drain signal is no longer meaningful.
        val tracker = BleTeardownTracker()
        val d = tracker.startTeardown("AA:BB")
        tracker.reset()
        assertEquals(TeardownDrainResult.INVALIDATED, d.await())
        // Late stale callback — tracker has no entry; no-op.
        tracker.completeTeardown("AA:BB")
        assertEquals(TeardownDrainResult.INVALIDATED, d.await())
    }

    @Test
    fun `reset after completeTeardown does not change the result`() = runTest {
        // Reverse race: drain arrives, then reset. Already-completed deferred
        // keeps its DRAINED result; reset only acts on still-pending entries
        // (and the entry was removed by completeTeardown).
        val tracker = BleTeardownTracker()
        val d = tracker.startTeardown("AA:BB")
        tracker.completeTeardown("AA:BB")
        tracker.reset()
        assertEquals(TeardownDrainResult.DRAINED, d.await())
    }
}
