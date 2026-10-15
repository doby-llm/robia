package com.gusanitolabs.robia.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagNameRulesTest {
    @Test
    fun normalize_ignoresAccentsCaseAndExtraSpaces() {
        assertEquals("vestidos", TagNameRules.normalize("  Vestídos "))
        assertEquals("main closet", TagNameRules.normalize("Main    Closet"))
        assertEquals("dressed-up", TagNameRules.normalize("Dressed-up"))
    }

    @Test
    fun conflicts_matchesNormalisedStoredNameAndLocalisedLabel() {
        assertTrue(TagNameRules.conflicts("dresses", listOf("Dresses")))
        assertTrue(TagNameRules.conflicts("Vestidos", listOf("Dresses", "Vestidos")))
        assertTrue(TagNameRules.conflicts("Pantalón", listOf("Pantalon")))
    }

    @Test
    fun conflicts_doesNotMatchDifferentNamesOrBlankCandidates() {
        assertFalse(TagNameRules.conflicts("Skirts", listOf("Dresses")))
        assertFalse(TagNameRules.conflicts("   ", listOf("")))
    }
}
