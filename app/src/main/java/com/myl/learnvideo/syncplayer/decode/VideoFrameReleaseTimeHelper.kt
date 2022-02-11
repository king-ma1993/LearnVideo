package com.myl.learnvideo.syncplayer.decode

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.view.Choreographer
import android.view.WindowManager
import com.myl.learnvideo.utils.TimeUtils
import kotlin.math.abs

class VideoFrameReleaseTimeHelper private constructor(defaultDisplayRefreshRate: Double) {

    companion object {
        private const val DISPLAY_REFRESH_RATE_UNKNOWN = -1.0

        private fun getDefaultDisplayRefreshRate(context: Context): Double {
            val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            return if (manager.defaultDisplay != null) manager.defaultDisplay.refreshRate
                .toDouble() else DISPLAY_REFRESH_RATE_UNKNOWN
        }

        private const val CHOREOGRAPHER_SAMPLE_DELAY_MILLIS: Long = 500

        private const val VSYNC_OFFSET_PERCENTAGE: Long = 80

        private const val MIN_FRAMES_FOR_ADJUSTMENT = 6

        private const val MAX_ALLOWED_DRIFT_NS: Long = 20 * 1000 * 1000
    }

    constructor(context: Context) : this(getDefaultDisplayRefreshRate(context))

    constructor() : this(DISPLAY_REFRESH_RATE_UNKNOWN)

    private var haveSync = false

    /**
     * Enables the helper.
     */
    fun enable() {
        haveSync = false
        if (useDefaultDisplayVsync) {
            vsyncSampler?.addObserver()
        }
    }

    /**
     * Disables the helper.
     */
    fun disable() {
        if (useDefaultDisplayVsync) {
            vsyncSampler?.removeObserver()
        }
    }

    private var useDefaultDisplayVsync = false
    private var vsyncSampler: VSyncSampler? = null
    var vsyncDurationNs: Long = 0
    private var vsyncOffsetNs: Long = 0
    private var lastFramePresentationTimeUs: Long = 0
    private var frameCount: Long = 0
    private var adjustedLastFrameTimeNs: Long = 0
    private var pendingAdjustedFrameTimeNs: Long = 0
    private var syncFramePresentationTimeNs: Long = 0
    private var syncUnadjustedReleaseTimeNs: Long = 0


    init {
        useDefaultDisplayVsync = defaultDisplayRefreshRate != DISPLAY_REFRESH_RATE_UNKNOWN
        if (useDefaultDisplayVsync) {
            vsyncSampler = VSyncSampler()
            vsyncDurationNs = (TimeUtils.sToNs(1) / defaultDisplayRefreshRate).toLong()
            vsyncOffsetNs = vsyncDurationNs * VSYNC_OFFSET_PERCENTAGE / 100
        } else {
            vsyncSampler = null
            vsyncDurationNs = -1 // Value unused.
            vsyncOffsetNs = -1 // Value unused.
        }
    }

    /**
     * Adjusts a frame release timestamp.
     *
     * @param framePresentationTimeUs The frame's presentation time, in microseconds.
     * @param unadjustedReleaseTimeNs The frame's unadjusted release time, in nanoseconds and in
     * the same time base as [System.nanoTime].
     * @return The adjusted frame release timestamp, in nanoseconds and in the same time base as
     * [System.nanoTime].
     */
    fun adjustReleaseTime(
        framePresentationTimeUs: Long,
        unadjustedReleaseTimeNs: Long
    ): Long {
        val framePresentationTimeNs = TimeUtils.usToNs(framePresentationTimeUs)
        // Until we know better, the adjustment will be a no-op.
        var adjustedFrameTimeNs = framePresentationTimeNs
        var adjustedReleaseTimeNs = unadjustedReleaseTimeNs
        if (haveSync) {
            // See if we've advanced to the next frame.
            if (framePresentationTimeUs != lastFramePresentationTimeUs) {
                frameCount++
                adjustedLastFrameTimeNs = pendingAdjustedFrameTimeNs
            }
        }
        if (frameCount >= MIN_FRAMES_FOR_ADJUSTMENT) {
            // We're synced and have waited the required number of frames to apply an adjustment.
            // Calculate the average frame time across all the frames we've seen since the last sync.
            // This will typically give us a frame rate at a finer granularity than the frame times
            // themselves (which often only have millisecond granularity).
            val averageFrameDurationNs =
                ((framePresentationTimeNs - syncFramePresentationTimeNs)
                        / frameCount)
            // Project the adjusted frame time forward using the average.
            val candidateAdjustedFrameTimeNs =
                adjustedLastFrameTimeNs + averageFrameDurationNs
            if (isDriftTooLarge(candidateAdjustedFrameTimeNs, unadjustedReleaseTimeNs)) {
                haveSync = false
            } else {
                adjustedFrameTimeNs = candidateAdjustedFrameTimeNs
                adjustedReleaseTimeNs = (syncUnadjustedReleaseTimeNs + adjustedFrameTimeNs
                        - syncFramePresentationTimeNs)
            }
        } else {
            // We're synced but haven't waited the required number of frames to apply an adjustment.
            // Check drift anyway.
            if (isDriftTooLarge(framePresentationTimeNs, unadjustedReleaseTimeNs)) {
                haveSync = false
            }
        }

        // If we need to sync, do so now.
        if (!haveSync) {
            syncFramePresentationTimeNs = framePresentationTimeNs
            syncUnadjustedReleaseTimeNs = unadjustedReleaseTimeNs
            frameCount = 0
            haveSync = true
            onSynced()
        }
        lastFramePresentationTimeUs = framePresentationTimeUs
        pendingAdjustedFrameTimeNs = adjustedFrameTimeNs
        return if (vsyncSampler == null || vsyncSampler?.sampledVsyncTimeNs == 0L) {
            //if we forgot to enable VideoFrameReleaseTimeHelper, then it will go into here
            adjustedReleaseTimeNs
        } else closestVsync(
            adjustedReleaseTimeNs,
            vsyncSampler?.sampledVsyncTimeNs ?: 0, vsyncDurationNs
        )

        // Find the timestamp of the closest vsync. This is the vsync that we're targeting.
        // Apply an offset so that we release before the target vsync, but after the previous one.
        // - vsyncOffsetNs;
    }

    private fun onSynced() {
        // Do nothing.
    }

    private fun isDriftTooLarge(frameTimeNs: Long, releaseTimeNs: Long): Boolean {
        val elapsedFrameTimeNs = frameTimeNs - syncFramePresentationTimeNs
        val elapsedReleaseTimeNs = releaseTimeNs - syncUnadjustedReleaseTimeNs
        return abs(elapsedReleaseTimeNs - elapsedFrameTimeNs) > MAX_ALLOWED_DRIFT_NS
    }

    private fun closestVsync(
        releaseTime: Long,
        sampledVsyncTime: Long,
        vsyncDuration: Long
    ): Long {
        val vsyncCount = (releaseTime - sampledVsyncTime) / vsyncDuration
        val snappedTimeNs = sampledVsyncTime + vsyncDuration * vsyncCount
        val snappedBeforeNs: Long
        val snappedAfterNs: Long
        if (releaseTime <= snappedTimeNs) {
            snappedBeforeNs = snappedTimeNs - vsyncDuration
            snappedAfterNs = snappedTimeNs
        } else {
            snappedBeforeNs = snappedTimeNs
            snappedAfterNs = snappedTimeNs + vsyncDuration
        }
        val snappedAfterDiff = snappedAfterNs - releaseTime
        val snappedBeforeDiff = releaseTime - snappedBeforeNs
        return if (snappedAfterDiff < snappedBeforeDiff) snappedAfterNs else snappedBeforeNs
    }

    /**
     * Samples display vsync timestamps. A single instance using a single [Choreographer] is
     * shared by all [VideoFrameReleaseTimeHelper] instances. This is done to avoid a resource
     * leak in the platform on API levels prior to 23. See [Internal: b/12455729].
     */
    private class VSyncSampler : Choreographer.FrameCallback,
        Handler.Callback {

        companion object {
            private const val HANDLER_THREAD_NAME = "ChoreographerOwner:Handler"
            private const val CREATE_CHOREOGRAPHER = 0
            private const val MSG_ADD_OBSERVER = 1
            private const val MSG_REMOVE_OBSERVER = 2
        }

        private val choreographerOwnerThread: HandlerThread =
            HandlerThread(HANDLER_THREAD_NAME)
        private val handler: Handler = Handler(choreographerOwnerThread.looper, this)
        private val vSyncSampler: VSyncSampler = VSyncSampler()
        private var choreographer: Choreographer? = null
        private var observerCount = 0

        @Volatile
        var sampledVsyncTimeNs: Long = 0

        init {
            choreographerOwnerThread.start()
            handler.sendEmptyMessage(CREATE_CHOREOGRAPHER)
        }

        fun getInstance(): VSyncSampler {
            return vSyncSampler
        }

        /**
         * Notifies the sampler that a [VideoFrameReleaseTimeHelper] is observing
         * [.sampledVsyncTimeNs], and hence that the value should be periodically updated.
         */
        fun addObserver() {
            handler.sendEmptyMessage(MSG_ADD_OBSERVER)
        }

        /**
         * Notifies the sampler that a [VideoFrameReleaseTimeHelper] is no longer observing
         * [.sampledVsyncTimeNs].
         */
        fun removeObserver() {
            handler.sendEmptyMessage(MSG_REMOVE_OBSERVER)
        }

        override fun doFrame(vsyncTimeNs: Long) {
            sampledVsyncTimeNs = vsyncTimeNs
            choreographer?.postFrameCallbackDelayed(this, CHOREOGRAPHER_SAMPLE_DELAY_MILLIS)
        }

        private fun createChoreographerInstanceInternal() {
            choreographer = Choreographer.getInstance()
        }


        private fun addObserverInternal() {
            observerCount++
            if (observerCount == 1) {
                choreographer?.postFrameCallback(this)
            }
        }


        override fun handleMessage(message: Message): Boolean {
            return when (message.what) {
                CREATE_CHOREOGRAPHER -> {
                    createChoreographerInstanceInternal()
                    true
                }
                MSG_ADD_OBSERVER -> {
                    addObserverInternal()
                    true
                }
                MSG_REMOVE_OBSERVER -> {
                    removeObserverInternal()
                    true
                }
                else -> {
                    false
                }
            }
        }

        private fun removeObserverInternal() {
            observerCount--
            if (observerCount == 0) {
                choreographer?.removeFrameCallback(this)
                sampledVsyncTimeNs = 0
            }
        }
    }
}