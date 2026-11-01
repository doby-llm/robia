package com.gusanitolabs.robia.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionMembershipTest {
    @Test
    fun addKeepsOnlyNonMembers() {
        val plan = planTagMembership(currentMembers = setOf("g1"), add = setOf("g1", "g2"), remove = emptySet())

        assertEquals(listOf("g2"), plan.add)
        assertEquals(emptyList<String>(), plan.remove)
    }

    @Test
    fun removeKeepsOnlyMembers() {
        val plan = planTagMembership(currentMembers = setOf("g1"), add = emptySet(), remove = setOf("g1", "g3"))

        assertEquals(emptyList<String>(), plan.add)
        assertEquals(listOf("g1"), plan.remove)
    }

    @Test
    fun anIdInBothInputsIsIgnoredWhetherItIsAMemberOrNot() {
        val plan = planTagMembership(
            currentMembers = setOf("member"),
            add = setOf("member", "fresh", "newcomer"),
            remove = setOf("member", "fresh"),
        )

        // "member" and "fresh" are in both inputs, so neither is changed; only "newcomer" is a real addition.
        assertEquals(listOf("newcomer"), plan.add)
        assertEquals(emptyList<String>(), plan.remove)
        assertEquals(listOf("newcomer"), plan.changed)
    }

    @Test
    fun emptyInputsGiveAnEmptyPlan() {
        val plan = planTagMembership(currentMembers = setOf("g1"), add = emptySet(), remove = emptySet())

        assertEquals(MembershipPlan(add = emptyList(), remove = emptyList()), plan)
        assertEquals(emptyList<String>(), plan.changed)
    }

    @Test
    fun outputsAreSortedAndChangedListsAddThenRemove() {
        val plan = planTagMembership(
            currentMembers = setOf("b", "d"),
            add = setOf("z", "a", "c"),
            remove = setOf("d", "b"),
        )

        assertEquals(listOf("a", "c", "z"), plan.add)
        assertEquals(listOf("b", "d"), plan.remove)
        assertEquals(listOf("a", "c", "z", "b", "d"), plan.changed)
    }
}
