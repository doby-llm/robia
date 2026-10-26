package com.gusanitolabs.robia.suggestions

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.GarmentTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Collator
import java.util.Locale

class ReviewChangesTest {
    private val dresses = tag("category-dresses", "category", "Dresses")
    private val skirts = tag("category-skirts", "category", "Skirts")
    private val knitwear = tag("category-knitwear", "category", "Knitwear")
    private val formal = tag("occasion-formal", "occasion", "Formal")
    private val everyday = tag("occasion-everyday", "occasion", "Everyday")
    private val business = tag("occasion-business", "occasion", "Business")
    private val casual = tag("occasion-casual", "occasion", "casual")
    private val evening = tag("occasion-evening", "occasion", "Evening")
    private val exito = tag("occasion-exito", "occasion", "Éxito")
    private val twinA = tag("occasion-a", "occasion", "Brunch")
    private val twinB = tag("occasion-b", "occasion", "Brunch")
    private val red = tag("color-red", "color", "Red")

    private val allTags = listOf(dresses, skirts, knitwear, formal, everyday, business, casual, evening, exito, twinA, twinB, red)
    private val existing = idsOf(*allTags.toTypedArray())
    private val categoryIds = idsOf(dresses, skirts, knitwear)
    private val occasionIds = idsOf(formal, everyday, business, casual, evening, exito, twinA, twinB)
    private val ghostOccasion = "occasion-ghost"
    private val ghostCategory = "category-ghost"
    private val collator = Collator.getInstance(Locale.ROOT).apply { strength = Collator.PRIMARY }
    private val labels = allTags.associate { it.id to it.name }
    private val now = 99L

    // ---- reviewChangesFor: what a proposal means inside its group (V-REV-MODEL-1)

    @Test
    fun addedOccasionGivesAdd() {
        val changes = changesOf(before = setOf(everyday.id), proposed = setOf(everyday.id, formal.id))
        assertEquals(listOf(ReviewChange.Add(formal.id)), changes.changes)
        assertFalse(changes.needsChoice)
    }

    @Test
    fun addedCategoryWithCurrentGivesUnlockedSwap() {
        val changes = changesOf(before = setOf(dresses.id), proposed = setOf(skirts.id), group = categoryIds, isCategory = true)
        assertEquals(listOf(ReviewChange.Swap(dresses.id, skirts.id, fromLocked = false)), changes.changes)
    }

    @Test
    fun garmentWithoutCategoryGivesAdd() {
        val changes = changesOf(before = emptySet(), proposed = setOf(skirts.id), group = categoryIds, isCategory = true)
        assertEquals(listOf(ReviewChange.Add(skirts.id)), changes.changes)
    }

    @Test
    fun deletedOccasionGivesLockedRemove() {
        val changes = changesOf(
            before = setOf(formal.id, everyday.id),
            proposed = setOf(everyday.id),
            existingIds = existing - formal.id,
        )
        assertEquals(listOf(ReviewChange.Remove(formal.id, locked = true)), changes.changes)
    }

    @Test
    fun deletedCategoryWithWinnerGivesSwapWithLockedFrom() {
        val changes = changesOf(
            before = setOf(dresses.id),
            proposed = setOf(skirts.id),
            group = categoryIds,
            isCategory = true,
            existingIds = existing - dresses.id,
        )
        assertEquals(listOf(ReviewChange.Swap(dresses.id, skirts.id, fromLocked = true)), changes.changes)
        assertFalse(changes.needsChoice)
    }

    @Test
    fun chooseManuallyGivesLockedRemoveAndNeedsChoice() {
        val changes = changesOf(
            before = setOf(dresses.id),
            proposed = emptySet(),
            kind = ProposalKind.ChooseManually,
            group = categoryIds,
            isCategory = true,
            existingIds = existing - dresses.id,
        )
        assertEquals(listOf(ReviewChange.Remove(dresses.id, locked = true)), changes.changes)
        assertTrue(changes.needsChoice)
    }

    @Test
    fun tagsOfOtherGroupsAreIgnored() {
        val changes = changesOf(before = setOf(dresses.id, red.id), proposed = setOf(skirts.id), group = categoryIds, isCategory = true)
        assertEquals(listOf(ReviewChange.Swap(dresses.id, skirts.id, fromLocked = false)), changes.changes)
    }

    @Test
    fun addOfTagThatDoesNotExistIsDropped() {
        val occasion = changesOf(before = setOf(everyday.id), proposed = setOf(everyday.id, ghostOccasion), group = occasionIds + ghostOccasion)
        assertEquals(emptyList<ReviewChange>(), occasion.changes)
        // The dropped add is decided before the swap: a category change to a missing tag is a plain removal.
        val category = changesOf(
            before = setOf(dresses.id),
            proposed = setOf(ghostCategory),
            group = categoryIds + ghostCategory,
            isCategory = true,
        )
        assertEquals(listOf(ReviewChange.Remove(dresses.id, locked = false)), category.changes)
    }

    // ---- order and labels (V-REV-MODEL-2)

    @Test
    fun removalsComeBeforeAdditions() {
        val changes = changesOf(before = setOf(everyday.id, business.id), proposed = setOf(formal.id))
        assertEquals(
            listOf("rem:occasion-business", "rem:occasion-everyday", "add:occasion-formal"),
            changes.changes.map(ReviewChange::key),
        )
    }

    @Test
    fun labelsSortIgnoringCaseAndAccents() {
        val changes = changesOf(before = emptySet(), proposed = setOf(exito.id, evening.id, casual.id, business.id, formal.id))
        // "casual" sorts between "Business" and "Evening" (case ignored); "Éxito" sorts after "Evening" (accent ignored).
        assertEquals(
            listOf(business.id, casual.id, evening.id, exito.id, formal.id),
            changes.changes.map { (it as ReviewChange.Add).tagId },
        )
    }

    @Test
    fun equalLabelsAreTiedById() {
        val changes = changesOf(before = emptySet(), proposed = setOf(twinB.id, twinA.id))
        assertEquals(listOf(twinA.id, twinB.id), changes.changes.map { (it as ReviewChange.Add).tagId })
    }

    @Test
    fun orderIsTheSameWhateverIsLeftOut() {
        // reviewChangesFor takes no skip input: the visible order cannot change when chips are left out.
        val changes = changesOf(before = setOf(business.id, everyday.id), proposed = setOf(formal.id, casual.id))
        assertEquals(
            listOf("rem:occasion-business", "rem:occasion-everyday", "add:occasion-casual", "add:occasion-formal"),
            changes.changes.map(ReviewChange::key),
        )
    }

    // ---- primary button and the quiet note (V-REV-MODEL-3)

    @Test
    fun sixActiveUnitsGiveApplySix() {
        val changes = ReviewChanges(addsOf("a", "b", "c", "d", "e", "f"), needsChoice = false)
        assertEquals(ReviewButton.Apply(6), primaryButtonFor(changes, skipped = emptySet(), chosenTagId = null))
    }

    @Test
    fun swapAndFourAddsGiveApplyFive() {
        val changes = ReviewChanges(
            listOf<ReviewChange>(ReviewChange.Swap("x", "y", fromLocked = false)) + addsOf("a", "b", "c", "d"),
            needsChoice = false,
        )
        assertEquals(ReviewButton.Apply(5), primaryButtonFor(changes, emptySet(), null))
    }

    @Test
    fun fourOfSixOnGiveApplyPartial() {
        val changes = ReviewChanges(addsOf("a", "b", "c", "d", "e", "f"), needsChoice = false)
        val skipped = setOf("add:e", "add:f")
        assertEquals(ReviewButton.ApplyPartial(4, 6), primaryButtonFor(changes, skipped, null))
    }

    @Test
    fun allLeftOutGivesKeepAsIs() {
        val changes = ReviewChanges(addsOf("a", "b"), needsChoice = false)
        assertEquals(ReviewButton.KeepAsIs, primaryButtonFor(changes, setOf("add:a", "add:b"), null))
    }

    @Test
    fun onlyLockedChipsGiveGotIt() {
        val changes = ReviewChanges(listOf(ReviewChange.Remove("x", locked = true)), needsChoice = false)
        assertEquals(ReviewButton.GotIt, primaryButtonFor(changes, emptySet(), null))
    }

    @Test
    fun needsChoiceWithoutChoiceGivesKeepAsIs() {
        val changes = ReviewChanges(listOf(ReviewChange.Remove("x", locked = true)), needsChoice = true)
        assertEquals(ReviewButton.KeepAsIs, primaryButtonFor(changes, emptySet(), null))
    }

    @Test
    fun needsChoiceWithChoiceGivesApplyOne() {
        val changes = ReviewChanges(listOf(ReviewChange.Remove("x", locked = true)), needsChoice = true)
        assertEquals(ReviewButton.Apply(1), primaryButtonFor(changes, emptySet(), chosenTagId = "skirts"))
    }

    @Test
    fun lockedRemovalIsNeverCounted() {
        val changes = ReviewChanges(listOf(ReviewChange.Remove("x", locked = true), ReviewChange.Add("a")), needsChoice = false)
        assertEquals(ReviewButton.Apply(1), primaryButtonFor(changes, emptySet(), null))
    }

    @Test
    fun rejectIsOfferedOnlyWithApplyButtons() {
        assertTrue(ReviewButton.Apply(2).showsReject)
        assertTrue(ReviewButton.ApplyPartial(1, 2).showsReject)
        assertFalse(ReviewButton.KeepAsIs.showsReject)
        assertFalse(ReviewButton.GotIt.showsReject)
    }

    @Test
    fun noTagsLeftIsTrueWhenTheOccasionGroupEmptiesAfterActiveUnits() {
        val changes = ReviewChanges(listOf(ReviewChange.Remove(everyday.id, locked = false)), needsChoice = false)
        assertTrue(noTagsLeft(true, setOf(everyday.id), changes, emptySet(), null))
        // Left out: the removal does not happen, so the garment keeps its occasion.
        assertFalse(noTagsLeft(true, setOf(everyday.id), changes, setOf("rem:${everyday.id}"), null))
    }

    @Test
    fun noTagsLeftCountsLockedRemovalAsRemoved() {
        val changes = ReviewChanges(listOf(ReviewChange.Remove(formal.id, locked = true)), needsChoice = false)
        assertTrue(noTagsLeft(true, setOf(formal.id), changes, emptySet(), null))
    }

    @Test
    fun noTagsLeftIsFalseOutsideTheOccasionGroupOrWithoutOccasions() {
        val changes = ReviewChanges(listOf(ReviewChange.Remove(dresses.id, locked = false)), needsChoice = false)
        assertFalse(noTagsLeft(false, setOf(dresses.id), changes, emptySet(), null))
        assertFalse(noTagsLeft(true, emptySet(), changes, emptySet(), null))
    }

    @Test
    fun noTagsLeftCountsTheChosenTag() {
        val changes = ReviewChanges(listOf(ReviewChange.Remove(formal.id, locked = true)), needsChoice = true)
        assertFalse(noTagsLeft(true, setOf(formal.id), changes, emptySet(), chosenTagId = business.id))
        assertTrue(noTagsLeft(true, setOf(formal.id), changes, emptySet(), chosenTagId = null))
    }

    // ---- deltaFor: what the active units write

    @Test
    fun deltaOfSwapIsRemoveAndAddUnlessTheFromIsLocked() {
        val unlocked = ReviewChanges(listOf(ReviewChange.Swap(dresses.id, skirts.id, fromLocked = false)), needsChoice = false)
        assertEquals(TagDelta("g", setOf(dresses.id), setOf(skirts.id)), deltaFor("g", unlocked, emptySet(), null))
        val locked = ReviewChanges(listOf(ReviewChange.Swap(dresses.id, skirts.id, fromLocked = true)), needsChoice = false)
        assertEquals(TagDelta("g", emptySet(), setOf(skirts.id)), deltaFor("g", locked, emptySet(), null))
    }

    @Test
    fun deltaNeverContainsLockedRemovalsAndSkippedUnitsAreLeftOut() {
        val changes = ReviewChanges(
            listOf(
                ReviewChange.Remove(formal.id, locked = true),
                ReviewChange.Remove(everyday.id, locked = false),
                ReviewChange.Add(business.id),
            ),
            needsChoice = false,
        )
        assertEquals(
            TagDelta("g", setOf(everyday.id), setOf(business.id)),
            deltaFor("g", changes, emptySet(), null),
        )
        assertEquals(
            TagDelta("g", emptySet(), emptySet()),
            deltaFor("g", changes, setOf("rem:${everyday.id}", "add:${business.id}"), null),
        )
    }

    @Test
    fun deltaOfDeletedOccasionCardIsEmpty() {
        val changes = ReviewChanges(listOf(ReviewChange.Remove(formal.id, locked = true)), needsChoice = false)
        assertTrue(deltaFor("g", changes, emptySet(), null).isEmpty)
    }

    @Test
    fun deltaAddsTheChosenTagOnlyWhenItIsOn() {
        val changes = ReviewChanges(listOf(ReviewChange.Remove(dresses.id, locked = true)), needsChoice = true)
        assertEquals(TagDelta("g", emptySet(), setOf(skirts.id)), deltaFor("g", changes, emptySet(), skirts.id))
        assertTrue(deltaFor("g", changes, setOf("add:${skirts.id}"), skirts.id).isEmpty)
    }

    // ---- applyDelta and revertDelta (V-REV-MODEL-4, -5, -6)

    @Test
    fun applyChangesOnlyTheReviewedGroupAndNeverTheTagsOutsideTheDelta() {
        val live = garment(red, everyday, business)
        val result = applyDelta(live, TagDelta("g", setOf(everyday.id), setOf(formal.id)), allTags, "occasion", now)
        val item = result.item!!
        assertEquals(idsOf(red, business, formal), tagIds(item))
        assertEquals(now, item.updatedAtEpochMillis)
        assertEquals(live.copy(tags = item.tags, updatedAtEpochMillis = now), item)
        assertEquals(AppliedDelta("g", setOf(everyday.id), setOf(formal.id)), result.applied)
    }

    @Test
    fun addsOnlyTagsOfTheReviewedGroup() {
        val result = applyDelta(garment(everyday), TagDelta("g", emptySet(), setOf(red.id)), allTags, "occasion", now)
        assertNull(result.item)
    }

    @Test
    fun addsOnlyTagsThatExistNow() {
        val result = applyDelta(garment(everyday), TagDelta("g", emptySet(), setOf(ghostOccasion)), allTags, "occasion", now)
        assertNull(result.item)
        assertEquals(AppliedDelta("g", emptySet(), emptySet()), result.applied)
    }

    @Test
    fun archivedGarmentIsLeftAlone() {
        val result = applyDelta(garment(everyday, archived = true), TagDelta("g", setOf(everyday.id), setOf(formal.id)), allTags, "occasion", now)
        assertNull(result.item)
        assertEquals(AppliedDelta("g", emptySet(), emptySet()), result.applied)
    }

    @Test
    fun missingGarmentIsLeftAlone() {
        val result = applyDelta(null, TagDelta("g", setOf(everyday.id), setOf(formal.id)), allTags, "occasion", now)
        assertNull(result.item)
        assertEquals(AppliedDelta("g", emptySet(), emptySet()), result.applied)
    }

    @Test
    fun nothingEffectiveGivesNull() {
        // everyday is not on the garment any more, formal is already on it.
        val result = applyDelta(garment(formal), TagDelta("g", setOf(everyday.id), setOf(formal.id)), allTags, "occasion", now)
        assertNull(result.item)
    }

    @Test
    fun applyingTheSameDeltaTwiceChangesNothingTheSecondTime() {
        val delta = TagDelta("g", setOf(everyday.id), setOf(formal.id))
        val first = applyDelta(garment(everyday), delta, allTags, "occasion", now)
        val second = applyDelta(first.item, delta, allTags, "occasion", now + 1)
        assertNull(second.item)
        assertEquals(AppliedDelta("g", emptySet(), emptySet()), second.applied)
    }

    @Test
    fun swapWhoseFromIsNoLongerLiveAppliesNothing() {
        val result = applyDelta(garment(knitwear), TagDelta("g", setOf(dresses.id), setOf(skirts.id)), allTags, "category", now)
        assertNull(result.item)
    }

    @Test
    fun swapWithLockedFromAddsOnlyWhenTheGarmentHasNoCategory() {
        val withoutCategory = applyDelta(garment(everyday), TagDelta("g", emptySet(), setOf(skirts.id)), allTags, "category", now)
        assertEquals(idsOf(everyday, skirts), tagIds(withoutCategory.item!!))
        val withCategory = applyDelta(garment(dresses), TagDelta("g", emptySet(), setOf(skirts.id)), allTags, "category", now)
        assertNull(withCategory.item)
    }

    @Test
    fun categoryGarmentNeverEndsWithTwoCategories() {
        val result = applyDelta(garment(dresses), TagDelta("g", emptySet(), setOf(skirts.id)), allTags, "category", now)
        assertNull(result.item)
    }

    @Test
    fun categoryRemovalAppliesOnlyTogetherWithItsAdd() {
        // The add is not an existing tag, so it is dropped and the removal must not go through alone.
        val withMissingAdd = applyDelta(garment(dresses), TagDelta("g", setOf(dresses.id), setOf(ghostCategory)), allTags, "category", now)
        assertNull(withMissingAdd.item)
        // A delta with no add is a plain category removal.
        val removalOnly = applyDelta(garment(dresses), TagDelta("g", setOf(dresses.id), emptySet()), allTags, "category", now)
        assertEquals(emptySet<String>(), tagIds(removalOnly.item!!))
    }

    @Test
    fun occasionAddTheGarmentAlreadyHasIsNotEffective() {
        val result = applyDelta(garment(everyday, formal), TagDelta("g", emptySet(), setOf(formal.id, business.id)), allTags, "occasion", now)
        assertEquals(AppliedDelta("g", emptySet(), setOf(business.id)), result.applied)
        assertEquals(idsOf(everyday, formal, business), tagIds(result.item!!))
    }

    @Test
    fun revertRestoresTheOriginalTags() {
        val original = garment(dresses, everyday)
        val applied = applyDelta(original, TagDelta("g", setOf(dresses.id), setOf(skirts.id)), allTags, "category", now)
        val reverted = revertDelta(applied.item, applied.applied, allTags, "category", now + 1)
        assertEquals(tagIds(original), tagIds(reverted.item!!))
        assertEquals(AppliedDelta("g", setOf(skirts.id), setOf(dresses.id)), reverted.applied)
    }

    @Test
    fun revertLeavesOtherFieldsUntouched() {
        val original = garment(dresses, everyday)
        val applied = applyDelta(original, TagDelta("g", setOf(dresses.id), setOf(skirts.id)), allTags, "category", now)
        val reverted = revertDelta(applied.item, applied.applied, allTags, "category", now + 1).item!!
        assertEquals(original.copy(tags = reverted.tags, updatedAtEpochMillis = now + 1), reverted)
    }

    @Test
    fun revertKeepsATagTheUserAddedInBetween() {
        val original = garment(everyday)
        val applied = applyDelta(original, TagDelta("g", setOf(everyday.id), setOf(formal.id)), allTags, "occasion", now)
        val userAdded = applied.item!!.let { it.copy(tags = it.tags + business) }
        val reverted = revertDelta(userAdded, applied.applied, allTags, "occasion", now + 1)
        assertEquals(idsOf(everyday, business), tagIds(reverted.item!!))
    }

    @Test
    fun revertDoesNotOverwriteADifferentCategory() {
        val applied = applyDelta(garment(dresses), TagDelta("g", setOf(dresses.id), setOf(skirts.id)), allTags, "category", now)
        val userChanged = garment(knitwear)
        assertNull(revertDelta(userChanged, applied.applied, allTags, "category", now + 1).item)
    }

    @Test
    fun revertDoesNotRestoreAfterTheUserRemovedTheReplacement() {
        val applied = applyDelta(garment(dresses), TagDelta("g", setOf(dresses.id), setOf(skirts.id)), allTags, "category", now)
        val userRemoved = applied.item!!.copy(tags = emptyList())
        assertNull(revertDelta(userRemoved, applied.applied, allTags, "category", now + 1).item)
    }

    @Test
    fun revertOfNothingChangesNothing() {
        val result = revertDelta(garment(everyday), AppliedDelta("g", emptySet(), emptySet()), allTags, "occasion", now)
        assertNull(result.item)
    }

    // ---- helpers

    private fun tag(id: String, categoryId: String, name: String) = GarmentTag(id = id, categoryId = categoryId, name = name, sortOrder = 0)

    private fun idsOf(vararg tags: GarmentTag): Set<String> = tags.mapTo(mutableSetOf(), GarmentTag::id)

    private fun tagIds(item: ClothingItem): Set<String> = item.tags.mapTo(mutableSetOf(), GarmentTag::id)

    private fun garment(vararg tags: GarmentTag, archived: Boolean = false) = ClothingItem(
        id = "g",
        name = "Shirt",
        tags = tags.toList(),
        isArchived = archived,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )

    private fun addsOf(vararg tagIds: String): List<ReviewChange> = tagIds.map { ReviewChange.Add(it) }

    private fun changesOf(
        before: Set<String>,
        proposed: Set<String>,
        kind: ProposalKind = ProposalKind.Suggested,
        group: Set<String> = occasionIds,
        isCategory: Boolean = false,
        existingIds: Set<String> = existing,
    ): ReviewChanges = reviewChangesFor(
        proposal = ReviewProposal("g", before, proposed, kind),
        groupTagIds = group,
        isCategoryGroup = isCategory,
        existingTagIds = existingIds,
        label = { labels[it] ?: it },
        collator = collator,
    )
}
