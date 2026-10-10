package com.hananel.fast.core.diagnostics

/** One titled block of the device report, e.g. "GNSS". */
data class ReportSection(val title: String, val rows: List<ReportRow>)

data class ReportRow(
    val label: String,
    val value: String,
    val status: RowStatus = RowStatus.INFO,
)

enum class RowStatus {
    /** The capability is present and helps accuracy. */
    GOOD,

    /** Missing or unreported — accuracy or a feature depends on it. */
    WARN,

    /** Plain fact, neither good nor bad. */
    INFO,
}

/** Plain-text form for Logcat, so a report can be pulled off a watch over adb. */
fun List<ReportSection>.toLogText(): String = buildString {
    for (section in this@toLogText) {
        appendLine("== ${section.title} ==")
        for (row in section.rows) {
            val mark = when (row.status) {
                RowStatus.GOOD -> "+"
                RowStatus.WARN -> "!"
                RowStatus.INFO -> " "
            }
            appendLine("$mark ${row.label}: ${row.value}")
        }
    }
}
