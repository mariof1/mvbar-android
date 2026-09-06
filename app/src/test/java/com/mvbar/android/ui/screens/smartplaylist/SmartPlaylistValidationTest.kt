package com.mvbar.android.ui.screens.smartplaylist

import org.junit.Assert.*
import org.junit.Test

class SmartPlaylistValidationTest {
    @Test fun selectedRockIsHiddenButOtherRockGenresRemain() {
        assertFalse(isSmartSuggestionAvailable("Rock", listOf("Rock")))
        assertTrue(isSmartSuggestionAvailable("Hard Rock", listOf("Rock")))
        assertTrue(isSmartSuggestionAvailable("Rock", emptyList()))
    }
    @Test fun validatesBoundsBeforeConvertingInputs() {
        assertNull(validateSmartPlaylistInput("0", "86400", "0", "400", "", ""))
        assertNull(validateSmartPlaylistInput("", "", "", "", "", ""))
        assertNotNull(validateSmartPlaylistInput("999999999999999999", "", "", "", "", ""))
        assertNotNull(validateSmartPlaylistInput("0", "86401", "", "", "", ""))
        assertNotNull(validateSmartPlaylistInput("", "", "401", "", "", ""))
        assertNotNull(validateSmartPlaylistInput("-1", "", "", "", "", ""))
    }
    @Test fun rejectsInvalidCalendarDates() {
        assertNotNull(validateSmartPlaylistInput("", "", "", "", "2026-02-30", ""))
        assertNotNull(validateSmartPlaylistInput("", "", "", "", "2026-9-1", ""))
        assertNull(validateSmartPlaylistInput("", "", "", "", "2024-02-29", ""))
    }
    @Test fun rejectsReversedRangesAndDates() {
        assertNotNull(validateSmartPlaylistInput("60", "30", "", "", "", ""))
        assertNotNull(validateSmartPlaylistInput("", "", "120", "90", "", ""))
        assertNotNull(validateSmartPlaylistInput("", "", "", "", "2026-09-06", "2026-09-01"))
        assertNull(validateSmartPlaylistInput("60", "60", "120", "120", "2026-09-01", "2026-09-06"))
    }
}
