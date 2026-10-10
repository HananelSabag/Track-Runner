package com.hananel.fast.core.model

/**
 * Which sensor produced a raw sample. Every stored sample carries its source,
 * so a session can be re-processed later with a better algorithm and the
 * contribution of each device stays visible.
 */
enum class SampleSource {
    /** The watch's own GNSS chip, read directly. */
    WATCH_GNSS,

    /** The watch vendor's fused exercise pipeline (Health Services). */
    WATCH_HEALTH_SERVICES,

    /** The phone's GNSS chip. */
    PHONE_GNSS,
}
