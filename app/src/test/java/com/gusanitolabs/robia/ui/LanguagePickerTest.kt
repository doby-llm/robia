package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.LanguagePreference
import org.junit.Assert.assertEquals
import org.junit.Test

/** V-U05-1: the language picker is one row per preference, System first, with exactly the stored preference selected. */
class LanguagePickerTest {
    @Test
    fun rowsFollowPreferenceOrderWithSystemFirst() {
        val rows = languagePickerRows(LanguagePreference.German)

        assertEquals(LanguagePreference.entries.size, rows.size)
        assertEquals(LanguagePreference.System, rows.first().language)
        assertEquals(LanguagePreference.entries, rows.map { it.language })
    }

    @Test
    fun exactlyTheCurrentPreferenceIsSelected() {
        LanguagePreference.entries.forEach { current ->
            val selected = languagePickerRows(current).filter { it.selected }.map { it.language }

            assertEquals(listOf(current), selected)
        }
    }
}
