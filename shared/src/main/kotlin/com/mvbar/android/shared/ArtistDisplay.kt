package com.mvbar.android.shared

private val artistValueSeparator = Regex("\\s*(?:;|\\||•|\\u0000|\\uFEFF)\\s*")

/**
 * Formats repeated or multi-value artist tags consistently on phone, TV, and Wear OS.
 * Commas are deliberately preserved because they are valid inside artist names.
 */
fun formatArtistDisplay(vararg values: String?): String? {
    for (value in values) {
        val names = value
            ?.split(artistValueSeparator)
            ?.map { it.trim().replace(Regex("\\s+"), " ") }
            ?.filter { it.isNotEmpty() }
            ?.distinctBy { it.lowercase() }
            .orEmpty()
        if (names.isNotEmpty()) return names.joinToString(" • ")
    }
    return null
}
