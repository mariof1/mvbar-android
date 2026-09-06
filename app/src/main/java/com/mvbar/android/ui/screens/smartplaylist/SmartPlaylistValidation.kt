package com.mvbar.android.ui.screens.smartplaylist

internal fun isSmartSuggestionAvailable(label: String, selected: List<String>): Boolean =
    selected.none { it == label }

internal fun validateSmartPlaylistInput(
    durationMin: String, durationMax: String, bpmMin: String, bpmMax: String,
    dateFrom: String, dateTo: String
): String? {
    fun range(min: String, max: String, ceiling: Int, label: String): String? {
        if (listOf(min, max).any { it.isNotBlank() && (it.toIntOrNull() == null || it.toInt() !in 0..ceiling) })
            return "$label must be a whole number from 0 to $ceiling"
        if (min.isNotBlank() && max.isNotBlank() && min.toInt() > max.toInt())
            return "$label minimum must not exceed maximum"
        return null
    }
    range(durationMin, durationMax, 86400, "Duration")?.let { return it }
    range(bpmMin, bpmMax, 400, "BPM")?.let { return it }
    for (date in listOf(dateFrom, dateTo).filter { it.isNotBlank() }) {
        if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(date) || runCatching { java.time.LocalDate.parse(date) }.isFailure)
            return "Date Added must be a valid date"
    }
    if (dateFrom.isNotBlank() && dateTo.isNotBlank() && dateFrom > dateTo)
        return "Start date must not be after end date"
    return null
}
