package org.freewheel.core.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReplayPlaybackReducerTest {

    private fun sample(ts: Long) = TelemetrySample(
        timestampMs = ts,
        speedKmh = 0.0, voltageV = 0.0, currentA = 0.0, powerW = 0.0,
        temperatureC = 0.0, batteryPercent = 0.0, pwmPercent = 0.0
    )

    private val samples = (0L..10L).map { sample(it * 1000L) }   // 0, 1000, ..., 10000 ms
    private val idle = ReplayPlaybackState()

    // ==================== play ====================

    @Test
    fun `play on idle state starts playback`() {
        val result = ReplayPlaybackReducer.play(idle)
        assertTrue(result.isPlaying)
        assertFalse(result.isFinished)
        assertEquals(0, result.currentIndex)
    }

    @Test
    fun `play when already playing is a no-op`() {
        val playing = idle.copy(isPlaying = true, currentIndex = 5)
        assertEquals(playing, ReplayPlaybackReducer.play(playing))
    }

    @Test
    fun `play when finished resets index to 0 and clears finished`() {
        val finished = idle.copy(currentIndex = 10, isFinished = true, isPlaying = false)
        val result = ReplayPlaybackReducer.play(finished)
        assertEquals(0, result.currentIndex)
        assertTrue(result.isPlaying)
        assertFalse(result.isFinished)
    }

    // ==================== pause / togglePlayPause ====================

    @Test
    fun `pause on playing state stops playback without touching index`() {
        val playing = idle.copy(isPlaying = true, currentIndex = 3)
        val result = ReplayPlaybackReducer.pause(playing)
        assertFalse(result.isPlaying)
        assertEquals(3, result.currentIndex)
    }

    @Test
    fun `pause when not playing is a no-op`() {
        assertEquals(idle, ReplayPlaybackReducer.pause(idle))
    }

    @Test
    fun `togglePlayPause flips play state`() {
        val playing = idle.copy(isPlaying = true)
        assertFalse(ReplayPlaybackReducer.togglePlayPause(playing).isPlaying)
        assertTrue(ReplayPlaybackReducer.togglePlayPause(idle).isPlaying)
    }

    // ==================== stop ====================

    @Test
    fun `stop resets everything`() {
        val advanced = idle.copy(currentIndex = 8, isPlaying = true, isFinished = true)
        val result = ReplayPlaybackReducer.stop(advanced)
        assertEquals(0, result.currentIndex)
        assertFalse(result.isPlaying)
        assertFalse(result.isFinished)
    }

    // ==================== seekTo ====================

    @Test
    fun `seekTo maps progress to index and clamps to valid range`() {
        assertEquals(0, ReplayPlaybackReducer.seekTo(idle, 0.0f, samples).currentIndex)
        assertEquals(10, ReplayPlaybackReducer.seekTo(idle, 1.0f, samples).currentIndex)
        assertEquals(5, ReplayPlaybackReducer.seekTo(idle, 0.5f, samples).currentIndex)
    }

    @Test
    fun `seekTo clamps out-of-range progress values`() {
        assertEquals(0, ReplayPlaybackReducer.seekTo(idle, -0.5f, samples).currentIndex)
        assertEquals(10, ReplayPlaybackReducer.seekTo(idle, 1.5f, samples).currentIndex)
    }

    @Test
    fun `seekTo pauses playback and clears finished`() {
        val playing = idle.copy(isPlaying = true, isFinished = true)
        val result = ReplayPlaybackReducer.seekTo(playing, 0.5f, samples)
        assertFalse(result.isPlaying)
        assertFalse(result.isFinished)
    }

    @Test
    fun `seekTo with fewer than 2 samples returns safe state`() {
        val result = ReplayPlaybackReducer.seekTo(idle, 0.5f, listOf(sample(0L)))
        assertFalse(result.isPlaying)
        assertFalse(result.isFinished)
    }

    // ==================== skipForward / skipBackward ====================

    @Test
    fun `skipForward jumps 30s forward and lands on first sample at or after target`() {
        val start = idle.copy(currentIndex = 1)   // timestamp 1000
        val result = ReplayPlaybackReducer.skipForward(start, samples)
        // Target = 1000 + 30_000 = 31_000. No such sample, so clamp to last (idx 10).
        assertEquals(10, result.currentIndex)

        val earlier = idle.copy(currentIndex = 0) // timestamp 0
        val result2 = ReplayPlaybackReducer.skipForward(earlier, samples)
        // Target = 30_000. Same — clamp to last.
        assertEquals(10, result2.currentIndex)
    }

    @Test
    fun `skipBackward jumps 30s back and clamps to 0 when before range`() {
        val start = idle.copy(currentIndex = 5)   // timestamp 5000
        val result = ReplayPlaybackReducer.skipBackward(start, samples)
        // Target = 5000 - 30_000 = -25_000. Clamp to 0.
        assertEquals(0, result.currentIndex)
    }

    @Test
    fun `skipForward within range finds nearest sample at or after target`() {
        // Samples at 0, 1000, 2000... 10000. Use a denser list and a 3s skip.
        val dense = (0L..20L).map { sample(it * 1000L) }
        val reducer = ReplayPlaybackReducer
        val start = ReplayPlaybackState(currentIndex = 2) // ts 2000
        // Override SKIP_STEP via seekByMs indirect — not exposed, so just use default with a
        // sample set where 30s is in-range. 20 samples × 1000ms span 0..20000, 30s out of range.
        // We'll use the default 30s skip with a 40-sample list so it lands internally.
        val long = (0L..40L).map { sample(it * 1000L) }
        val fromTen = ReplayPlaybackState(currentIndex = 10) // ts 10_000, target 40_000
        val result = reducer.skipForward(fromTen, long)
        assertEquals(40, result.currentIndex)

        val fromFive = ReplayPlaybackState(currentIndex = 5) // ts 5_000, target 35_000
        val result2 = reducer.skipForward(fromFive, long)
        assertEquals(35, result2.currentIndex)

        // Silence unused-variable warnings
        assertNotNull(dense)
        assertNotNull(start)
    }

    @Test
    fun `skip on empty samples is a no-op`() {
        assertEquals(idle, ReplayPlaybackReducer.skipForward(idle, emptyList()))
        assertEquals(idle, ReplayPlaybackReducer.skipBackward(idle, emptyList()))
    }

    // ==================== setSpeed ====================

    @Test
    fun `setSpeed updates multiplier without touching other fields`() {
        val playing = idle.copy(isPlaying = true, currentIndex = 3)
        val result = ReplayPlaybackReducer.setSpeed(playing, 8f)
        assertEquals(8f, result.speedMultiplier)
        assertTrue(result.isPlaying)
        assertEquals(3, result.currentIndex)
    }

    // ==================== advanceOne ====================

    @Test
    fun `advanceOne while playing moves one index forward`() {
        val playing = idle.copy(isPlaying = true, currentIndex = 2)
        val tick = ReplayPlaybackReducer.advanceOne(playing, samples)
        assertEquals(3, tick.state.currentIndex)
        assertTrue(tick.state.isPlaying)
    }

    @Test
    fun `advanceOne delay scales by speed multiplier`() {
        val playing = idle.copy(isPlaying = true, currentIndex = 0, speedMultiplier = 2f)
        val tick = ReplayPlaybackReducer.advanceOne(playing, samples)
        // Gap is 1000ms, at 2× speed → 500ms delay.
        assertEquals(500L, tick.delayMs)
    }

    @Test
    fun `advanceOne enforces minimum 10ms delay at high speed`() {
        val playing = idle.copy(isPlaying = true, currentIndex = 0, speedMultiplier = 1000f)
        val tick = ReplayPlaybackReducer.advanceOne(playing, samples)
        // Gap 1000ms / 1000 = 1ms, clamped to MIN_TICK_MS (10ms).
        assertEquals(ReplayPlaybackReducer.MIN_TICK_MS, tick.delayMs)
    }

    @Test
    fun `advanceOne from second-to-last marks finished and stops playing`() {
        val playing = idle.copy(isPlaying = true, currentIndex = 9) // one before last
        val tick = ReplayPlaybackReducer.advanceOne(playing, samples)
        assertEquals(10, tick.state.currentIndex)
        assertFalse(tick.state.isPlaying)
        assertTrue(tick.state.isFinished)
    }

    @Test
    fun `advanceOne past last index yields finished without incrementing`() {
        val playing = idle.copy(isPlaying = true, currentIndex = 10)
        val tick = ReplayPlaybackReducer.advanceOne(playing, samples)
        assertFalse(tick.state.isPlaying)
        assertTrue(tick.state.isFinished)
    }

    @Test
    fun `advanceOne when not playing is a no-op tick`() {
        val tick = ReplayPlaybackReducer.advanceOne(idle, samples)
        assertEquals(idle, tick.state)
    }

    @Test
    fun `advanceOne on short sample list returns state unchanged`() {
        val playing = idle.copy(isPlaying = true)
        val tick = ReplayPlaybackReducer.advanceOne(playing, listOf(sample(0L)))
        assertEquals(playing, tick.state)
    }

    // ==================== derived helpers ====================

    @Test
    fun `currentSample returns sample at currentIndex or null`() {
        assertEquals(5000L, ReplayPlaybackReducer.currentSample(idle.copy(currentIndex = 5), samples)?.timestampMs)
        assertNull(ReplayPlaybackReducer.currentSample(idle.copy(currentIndex = 999), samples))
        assertNull(ReplayPlaybackReducer.currentSample(idle, emptyList()))
    }

    @Test
    fun `progress returns fractional position of currentIndex`() {
        assertEquals(0f, ReplayPlaybackReducer.progress(idle, samples))
        assertEquals(0.5f, ReplayPlaybackReducer.progress(idle.copy(currentIndex = 5), samples))
        assertEquals(1f, ReplayPlaybackReducer.progress(idle.copy(currentIndex = 10), samples))
    }

    @Test
    fun `progress returns 0 for empty or single-sample lists`() {
        assertEquals(0f, ReplayPlaybackReducer.progress(idle, emptyList()))
        assertEquals(0f, ReplayPlaybackReducer.progress(idle, listOf(sample(0L))))
    }

    @Test
    fun `totalDurationMs returns last minus first timestamp`() {
        assertEquals(10_000L, ReplayPlaybackReducer.totalDurationMs(samples))
        assertEquals(0L, ReplayPlaybackReducer.totalDurationMs(listOf(sample(0L))))
        assertEquals(0L, ReplayPlaybackReducer.totalDurationMs(emptyList()))
    }

    @Test
    fun `elapsedMs returns current minus first sample timestamp`() {
        assertEquals(0L, ReplayPlaybackReducer.elapsedMs(idle, samples))
        assertEquals(5000L, ReplayPlaybackReducer.elapsedMs(idle.copy(currentIndex = 5), samples))
    }
}
