package com.gusanitolabs.robia.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** V-U04-1: the Manage pager opens on the selected tab and falls back to page 0 for a stale id. */
class ManageTabPagerTest {
    private val tabs = listOf("category", "season", "occasion", "colors")

    @Test
    fun pageFor_returnsIndexOfKnownTab() {
        assertEquals(2, pageFor(tabs, "occasion"))
        assertEquals(3, pageFor(tabs, "colors"))
    }

    @Test
    fun pageFor_fallsBackToFirstPageForUnknownTab() {
        assertEquals(0, pageFor(tabs, "deleted-category"))
    }
}
