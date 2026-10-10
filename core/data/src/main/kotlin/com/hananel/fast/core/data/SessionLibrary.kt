package com.hananel.fast.core.data

import com.hananel.fast.core.model.Speed

/**
 * All the sessions on a device, read the way the player should see them: with
 * football speed bands set by the top speed those same sessions show.
 *
 * That is circular — the bands depend on the top speed, and the top speed comes
 * from the summaries — but only in appearance: a session's fastest three
 * seconds do not depend on the bands. So the summaries are read once with the
 * last known top speed, and read again only if they reveal a different one.
 */
class SessionLibrary(private val store: SessionStore) {

    class Snapshot(
        /** Newest first. */
        val summaries: List<SessionSummary>,
        /** Null until some session shows a real top speed. */
        val topSpeed: Speed?,
        /** What every summary here was read with; use it to open a single session the same way. */
        val context: AnalysisContext,
    )

    /**
     * @param lastKnownTopSpeed whatever [Snapshot.topSpeed] was last time. It only saves
     *   work — the cached summaries were made with it — and never changes the result.
     */
    fun load(fallbackMaxBpm: Int? = null, lastKnownTopSpeed: Speed? = null): Snapshot {
        var context = AnalysisContext(lastKnownTopSpeed, fallbackMaxBpm)
        var summaries = store.summaries(context)
        val topSpeed = SessionInsights.personalTopSpeed(summaries)
        if (AnalysisContext(topSpeed, fallbackMaxBpm).key != context.key) {
            context = AnalysisContext(topSpeed, fallbackMaxBpm)
            summaries = store.summaries(context)
        }
        return Snapshot(summaries, topSpeed, context)
    }
}
