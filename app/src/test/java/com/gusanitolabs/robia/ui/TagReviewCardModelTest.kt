package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.suggestions.ProposalKind
import com.gusanitolabs.robia.suggestions.ReviewChange
import com.gusanitolabs.robia.suggestions.ReviewChanges
import com.gusanitolabs.robia.suggestions.ReviewProposal
import com.gusanitolabs.robia.suggestions.TagChangeSet
import com.gusanitolabs.robia.suggestions.TagDelta
import com.gusanitolabs.robia.suggestions.TagOperation
import com.gusanitolabs.robia.suggestions.applyDelta
import java.nio.file.Files
import java.nio.file.Path
import java.text.Collator
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** V-REV-CARD-1 and V-REV-CARD-2 (R3-DEC-052 to R3-DEC-054): the card model, the header state, the commit and the strings. */
class TagReviewCardModelTest {
    private val collator: Collator = Collator.getInstance(Locale.ROOT)
    private val occasions = listOf(
        tag("occ-everyday", "occasion"),
        tag("occ-old", "occasion"),
        tag("occ-new", "occasion"),
        tag("occ-formal", "occasion"),
    )
    private val occasionIds = occasions.mapTo(mutableSetOf(), GarmentTag::id)
    private val categories = listOf(tag("category-coats", "category"), tag("category-deleted", "category"))
    private val allTags = occasions + categories

    @Test
    fun chipVisualStatesMatchTheTable() {
        assertEquals(ChipVisual(ChipKind.Add, filledSign = true, struck = false, dashedOutline = false, switch = true), chipVisual(ReviewChange.Add("a"), skipped = false))
        assertEquals(ChipVisual(ChipKind.Add, filledSign = false, struck = false, dashedOutline = true, switch = true), chipVisual(ReviewChange.Add("a"), skipped = true))
        assertEquals(ChipVisual(ChipKind.Remove, filledSign = true, struck = true, dashedOutline = false, switch = true), chipVisual(ReviewChange.Remove("a", locked = false), skipped = false))
        assertEquals(ChipVisual(ChipKind.Remove, filledSign = false, struck = false, dashedOutline = false, switch = true), chipVisual(ReviewChange.Remove("a", locked = false), skipped = true))
        // A locked removal is filled and struck in both states, and is never a switch.
        assertEquals(ChipVisual(ChipKind.Remove, filledSign = true, struck = true, dashedOutline = false, switch = false), chipVisual(ReviewChange.Remove("a", locked = true), skipped = false))
        assertEquals(ChipVisual(ChipKind.Remove, filledSign = true, struck = true, dashedOutline = false, switch = false), chipVisual(ReviewChange.Remove("a", locked = true), skipped = true))
        assertEquals(ChipVisual(ChipKind.Swap, filledSign = true, struck = false, dashedOutline = false, switch = true), chipVisual(ReviewChange.Swap("a", "b", fromLocked = false), skipped = false))
        assertEquals(ChipVisual(ChipKind.Swap, filledSign = false, struck = false, dashedOutline = true, switch = true), chipVisual(ReviewChange.Swap("a", "b", fromLocked = false), skipped = true))
        assertEquals(ChipVisual(ChipKind.Swap, filledSign = true, struck = false, dashedOutline = false, switch = false), chipVisual(ReviewChange.Swap("a", "b", fromLocked = true), skipped = false))
    }

    @Test
    fun chipDescriptionsFollowTheState() {
        assertEquals(ChipDescription.Add, chipDescription(ReviewChange.Add("a"), skipped = false))
        assertEquals(ChipDescription.AddOff, chipDescription(ReviewChange.Add("a"), skipped = true))
        assertEquals(ChipDescription.Remove, chipDescription(ReviewChange.Remove("a", locked = false), skipped = false))
        assertEquals(ChipDescription.RemoveOff, chipDescription(ReviewChange.Remove("a", locked = false), skipped = true))
        assertEquals(ChipDescription.Removed, chipDescription(ReviewChange.Remove("a", locked = true), skipped = false))
        assertEquals(ChipDescription.Swap, chipDescription(ReviewChange.Swap("a", "b", fromLocked = false), skipped = false))
        assertEquals(ChipDescription.SwapOff, chipDescription(ReviewChange.Swap("a", "b", fromLocked = false), skipped = true))
        assertEquals(ChipDescription.Replace, chipDescription(ReviewChange.Add("a"), skipped = false, chosen = true))
        assertEquals(ChipDescription.ReplaceOff, chipDescription(ReviewChange.Add("a"), skipped = true, chosen = true))
        assertEquals("review_cd_add", ChipDescription.Add.resName)
        assertEquals("review_cd_replace_off", ChipDescription.ReplaceOff.resName)
    }

    @Test
    fun identityLineIsCategoryAndColourWithoutAnySeparatorWhenTheColourIsUnknown() {
        assertEquals("Coats · Blue", identityLine("Coats", "Blue", noCategoryLabel = "No category"))
        assertEquals("Coats", identityLine("Coats", null, noCategoryLabel = "No category"))
        assertEquals("No category · Blue", identityLine(null, "Blue", noCategoryLabel = "No category"))
        assertEquals("No category", identityLine(" ", null, noCategoryLabel = "No category"))
    }

    @Test
    fun nowLineListsTheOccasionsBeforeTheChangeOrNone() {
        assertEquals("Casual, Date night", nowLine(listOf("Casual", "Date night"), noneLabel = "none"))
        assertEquals("none", nowLine(emptyList(), noneLabel = "none"))
    }

    @Test
    fun headerShowsTheScanWhileScanningThenTheLeftCountWithProgressUpToOne() {
        assertTrue(reviewHeaderState(isScanning = true, isPreparing = false, leftCount = 0, decided = 0, found = 0).showsScan)
        assertTrue(reviewHeaderState(isScanning = false, isPreparing = true, leftCount = 0, decided = 0, found = 0).showsScan)

        val afterScan = reviewHeaderState(isScanning = false, isPreparing = false, leftCount = 3, decided = 1, found = 4)
        assertFalse(afterScan.showsScan)
        assertEquals(3, afterScan.leftCount)
        assertEquals(0.25f, afterScan.progress, 0f)

        assertEquals(0f, reviewHeaderState(isScanning = false, isPreparing = false, leftCount = 0, decided = 0, found = 0).progress, 0f)
        assertEquals(1f, reviewHeaderState(isScanning = false, isPreparing = false, leftCount = 0, decided = 5, found = 4).progress, 0f)
    }

    @Test
    fun offeredTagsAreTheGroupsExistingTagsTheGarmentDoesNotHave() {
        val card = occasionCard(garment("g1", "occ-everyday", "occ-old"), occasionProposal())

        assertEquals(setOf("occ-new", "occ-formal"), card.offeredTagIds)
        assertEquals(setOf("occ-everyday", "occ-old"), card.liveGroupTagIds)
    }

    @Test
    fun aChipLeftOutIsNeverWrittenAndTheRestIs() {
        val item = garment("g1", "occ-everyday", "occ-old")
        val session = TagReviewSession().withCard(occasionCard(item, occasionProposal())).toggle("g1", "add:occ-new")

        val (decided, delta) = session.apply("g1", occasionIds)

        assertEquals(TagDelta("g1", remove = setOf("occ-old"), add = setOf("occ-formal")), delta)
        assertEquals(setOf("occ-everyday", "occ-formal"), commitTags(item, decided, occasions))
    }

    @Test
    fun aKeptRemovalKeepsItsTagAndTheAddsAreWritten() {
        val item = garment("g1", "occ-everyday", "occ-old")
        val session = TagReviewSession().withCard(occasionCard(item, occasionProposal())).toggle("g1", "rem:occ-old")

        val (decided, delta) = session.apply("g1", occasionIds)

        assertEquals(TagDelta("g1", remove = emptySet(), add = setOf("occ-formal", "occ-new")), delta)
        assertEquals(setOf("occ-everyday", "occ-old", "occ-formal", "occ-new"), commitTags(item, decided, occasions))
    }

    @Test
    fun aGarmentWithEveryChipLeftOutIsNotWritten() {
        val item = garment("g1", "occ-everyday", "occ-old")
        val session = TagReviewSession()
            .withCard(occasionCard(item, occasionProposal()))
            .toggle("g1", "rem:occ-old")
            .toggle("g1", "add:occ-formal")
            .toggle("g1", "add:occ-new")

        val (decided, _) = session.apply("g1", occasionIds)

        assertNull(commitDeltaFor(decided.cards.single(), occasionIds))
        assertEquals(setOf("occ-everyday", "occ-old"), commitTags(item, decided, occasions))
    }

    @Test
    fun acceptAllWritesThePendingChipsAsTheyAre() {
        val item = garment("g1", "occ-everyday", "occ-old")
        val session = TagReviewSession().withCard(occasionCard(item, occasionProposal()))

        assertEquals(
            TagDelta("g1", remove = setOf("occ-old"), add = setOf("occ-formal", "occ-new")),
            commitDeltaFor(session.cards.single(), occasionIds),
        )
    }

    @Test
    fun aChosenCategoryIsWrittenOnceAndADeletedChoiceIsNoChoice() {
        val item = garment("g2")
        val proposal = ReviewProposal("g2", before = setOf("category-deleted"), proposed = emptySet(), kind = ProposalKind.ChooseManually)
        val card = reviewCardStateFor(
            proposal = proposal,
            liveGroupTagIds = emptySet(),
            groupTagIds = setOf("category-deleted", "category-coats"),
            isCategoryGroup = true,
            existingTagIds = setOf("category-coats"),
            label = { tagId -> tagId },
            collator = collator,
        )
        val session = TagReviewSession().withCard(card)

        assertTrue(card.changes.needsChoice)
        assertEquals(setOf("category-coats"), card.offeredTagIds)
        assertEquals(session, session.choose("g2", "category-gone"))
        assertNull(chosenTagOf(card.copy(chosenTagId = "category-gone"), setOf("category-coats")))

        val chosen = session.choose("g2", "category-coats")
        val (decided, _) = chosen.apply("g2", setOf("category-coats"))

        assertEquals(setOf("category-coats"), commitTags(item, decided, categories))
    }

    @Test
    fun aGarmentWhoseOnlyChangesAreLockedIsNotOffered() {
        val item = garment("g3", "occ-everyday")
        // The only change is the removal of a deleted occasion: occ-old is no longer an existing tag, so the removal is locked.
        val proposal = ReviewProposal("g3", before = setOf("occ-everyday", "occ-old"), proposed = setOf("occ-everyday"), kind = ProposalKind.Suggested)
        val session = TagReviewSession().withCard(
            reviewCardStateFor(
                proposal = proposal,
                liveGroupTagIds = setOf("occ-everyday"),
                groupTagIds = occasionIds,
                isCategoryGroup = false,
                existingTagIds = setOf("occ-everyday"),
                label = { tagId -> tagId },
                collator = collator,
            ),
        )

        assertFalse(session.pending.any { card -> card.garmentId == item.id })
        assertTrue(session.leftCount == 0)
    }

    @Test
    fun aDeletedCategoryIsReplacedOnTheLiveGarmentAndTheReplacementIsWritten() {
        val changeSet = deletionOf("category-deleted", "category", categories)
        val live = liveViewOf(garment("g2", "category-deleted"), changeSet)
        val proposal = ReviewProposal("g2", before = setOf("category-deleted"), proposed = setOf("category-coats"), kind = ProposalKind.Suggested)
        val session = TagReviewSession().withCard(cardFor(live, proposal, changeSet))
        val card = session.cards.single()

        // The "+" half is shown: a swap whose "−" half is locked, read as "Set" rather than a removal.
        assertEquals(listOf("g2"), session.pending.map(ReviewCardState::garmentId))
        assertEquals(listOf(ReviewChange.Swap("category-deleted", "category-coats", fromLocked = true)), card.changes.changes)
        assertEquals(ChipDescription.Replace, chipDescription(card.changes.changes.single(), skipped = false))

        val delta = commitDeltaFor(card, setOf("category-coats"))
        assertEquals(TagDelta("g2", remove = emptySet(), add = setOf("category-coats")), delta)
        val written = applyDelta(live = live, delta = delta!!, existingTags = changeSet.afterTags, categoryId = "category", now = 1L).item!!
        assertEquals(listOf("category-coats"), written.tags.map(GarmentTag::id))
        assertEquals(1, written.tags.count { tag -> tag.categoryId == "category" })
    }

    @Test
    fun aDeletedCategoryWithNoReplacementShowsTheChooserAndWritesTheChosenTag() {
        val changeSet = deletionOf("category-deleted", "category", categories)
        val live = liveViewOf(garment("g2", "category-deleted"), changeSet)
        val proposal = ReviewProposal("g2", before = setOf("category-deleted"), proposed = emptySet(), kind = ProposalKind.ChooseManually)
        val session = TagReviewSession().withCard(cardFor(live, proposal, changeSet))
        val card = session.cards.single()

        assertEquals(listOf("g2"), session.pending.map(ReviewCardState::garmentId))
        assertTrue(card.changes.needsChoice)
        assertEquals(setOf("category-coats"), card.offeredTagIds)
        assertNull(commitDeltaFor(card, setOf("category-coats")))

        val chosen = session.choose("g2", "category-coats")
        val delta = commitDeltaFor(chosen.cards.single(), setOf("category-coats"))
        assertEquals(TagDelta("g2", remove = emptySet(), add = setOf("category-coats")), delta)
        val written = applyDelta(live = live, delta = delta!!, existingTags = changeSet.afterTags, categoryId = "category", now = 1L).item!!
        assertEquals(listOf("category-coats"), written.tags.map(GarmentTag::id))
        assertEquals(1, written.tags.count { tag -> tag.categoryId == "category" })
    }

    @Test
    fun aDeletedOccasionIsGoneFromTheLiveGarmentAndNothingIsWritten() {
        val changeSet = deletionOf("occ-old", "occasion", occasions)
        val live = liveViewOf(garment("g3", "occ-everyday", "occ-old"), changeSet)
        val proposal = ReviewProposal("g3", before = setOf("occ-everyday", "occ-old"), proposed = setOf("occ-everyday"), kind = ProposalKind.Suggested)
        val session = TagReviewSession().withCard(cardFor(live, proposal, changeSet))
        val card = session.cards.single()

        assertEquals(setOf("occ-everyday"), live.tags.mapTo(mutableSetOf(), GarmentTag::id))
        assertEquals(setOf("occ-everyday"), card.liveGroupTagIds)
        assertTrue(isHiddenOnScan(session, "g3"))
        val delta = commitDeltaFor(card, occasionIds - "occ-old")
        assertTrue(delta!!.isEmpty)
        assertNull(applyDelta(live = live, delta = delta, existingTags = changeSet.afterTags, categoryId = "occasion", now = 1L).item)
    }

    @Test
    fun aNonDeleteChangeLeavesTheGarmentAsItIs() {
        val item = garment("g1", "occ-everyday", "occ-old")
        val renamed = TagChangeSet(
            id = "rename-occ-old",
            operation = TagOperation.Renamed,
            categoryId = "occasion",
            touchedTagIds = setOf("occ-old"),
            beforeTags = occasions,
            afterTags = occasions,
        )

        assertEquals(item, liveViewOf(item, renamed))
        val session = TagReviewSession().withCard(occasionCard(liveViewOf(item, renamed), occasionProposal()))
        assertEquals(setOf("occ-everyday", "occ-old"), session.cards.single().liveGroupTagIds)
    }

    @Test
    fun aChooserWritesOnlyAValidatedChoiceAndNothingWithoutOne() {
        val changeSet = deletionOf("category-deleted", "category", categories)
        val live = liveViewOf(garment("g2", "category-deleted"), changeSet)
        val proposal = ReviewProposal("g2", before = setOf("category-deleted"), proposed = emptySet(), kind = ProposalKind.ChooseManually)
        val card = cardFor(live, proposal, changeSet)
        val existing = setOf("category-coats")

        // A chooser with no choice writes nothing.
        assertTrue(card.changes.needsChoice)
        assertNull(commitDeltaFor(card, existing))
        // A choice that no longer exists is not written.
        assertNull(commitDeltaFor(card.copy(chosenTagId = "category-gone"), existing))
        assertEquals(
            TagDelta("g2", remove = emptySet(), add = setOf("category-coats")),
            commitDeltaFor(card.copy(chosenTagId = "category-coats"), existing),
        )
    }

    @Test
    fun aGarmentWithOnlyLockedChangesIsHiddenOnScanAndAnUnlockedOneIsNot() {
        val changeSet = deletionOf("occ-old", "occasion", occasions)
        val locked = liveViewOf(garment("g3", "occ-everyday", "occ-old"), changeSet)
        val lockedProposal = ReviewProposal("g3", before = setOf("occ-everyday", "occ-old"), proposed = setOf("occ-everyday"), kind = ProposalKind.Suggested)
        val lockedSession = TagReviewSession().withCard(cardFor(locked, lockedProposal, changeSet))

        assertTrue(isHiddenOnScan(lockedSession, "g3"))
        assertFalse(isHiddenOnScan(TagReviewSession().withCard(occasionCard(garment("g1", "occ-everyday", "occ-old"), occasionProposal())), "g1"))
    }

    @Test
    fun allChangesSkippedNeedsSomethingToLeaveOut() {
        val card = occasionCard(garment("g1", "occ-everyday", "occ-old"), occasionProposal())
        val allLeftOut = card.copy(skipped = setOf("rem:occ-old", "add:occ-formal", "add:occ-new"))

        assertTrue(allChangesSkipped(allLeftOut, chosenTagId = null))
        assertFalse(allChangesSkipped(card, chosenTagId = null))
    }

    // V-FB3-CHIP-PICK-3: which chips take a long press, and what the picker offers (R3-DEC-060)

    @Test
    fun replaceableChipsAreTheAddsAndSwapHalvesOfAPendingCard() {
        val card = occasionCard(garment("g1", "occ-everyday", "occ-old"), occasionProposal())
        assertFalse(replaceableChip(card, ReviewChange.Remove("occ-old", locked = false)))
        assertFalse(replaceableChip(card, ReviewChange.Remove("occ-gone", locked = true)))
        assertTrue(replaceableChip(card, ReviewChange.Add("occ-new")))
        assertTrue(replaceableChip(card, ReviewChange.Swap("category-coats", "category-deleted", fromLocked = true)))
        val applied = card.copy(decision = ReviewDecision.Applied(TagDelta("g1", emptySet(), setOf("occ-new")), null))
        assertFalse(replaceableChip(applied, ReviewChange.Add("occ-new")))
        assertFalse(replaceableChip(card.copy(decision = ReviewDecision.Rejected()), ReviewChange.Add("occ-new")))
    }

    @Test
    fun pickerOffersTheOfferedTagsMinusTheOtherChipsAndKeepsThePressedTag() {
        val card = occasionCard(garment("g1", "occ-everyday", "occ-old"), occasionProposal())
        assertEquals(setOf("occ-new", "occ-formal"), card.offeredTagIds)
        assertEquals(setOf("occ-new"), replacementOptions(card, chosenTagId = null, key = ReviewChange.Add("occ-new").key))
        assertEquals(setOf("occ-formal"), replacementOptions(card, chosenTagId = null, key = ReviewChange.Add("occ-formal").key))
    }

    @Test
    fun pickerOfAChooserOffersItsOfferedTagsAndTheChosenOneStaysIn() {
        val chooser = ReviewCardState(
            garmentId = "g2",
            changes = ReviewChanges(listOf(ReviewChange.Remove("category-deleted", locked = true)), needsChoice = true),
            liveGroupTagIds = emptySet(),
            offeredTagIds = setOf("category-coats"),
        )
        assertEquals(setOf("category-coats"), replacementOptions(chooser, chosenTagId = null, key = null))
        assertEquals(setOf("category-coats"), replacementOptions(chooser, chosenTagId = "category-coats", key = null))
    }

    @Test
    fun theLongPressLabelIsInEveryLocale() {
        val en = resource("app/src/main/res/values/strings.xml")
        val es = resource("app/src/main/res/values-es/strings.xml")
        val de = resource("app/src/main/res/values-de/strings.xml")
        assertEquals("Choose another tag", stringValue(en, "review_chip_choose_another"))
        assertEquals("Elegir otra etiqueta", stringValue(es, "review_chip_choose_another"))
        assertEquals("Anderes Tag wählen", stringValue(de, "review_chip_choose_another"))
    }

    @Test
    fun theStringsHaveTheSameArgumentsInEveryLocale() {
        val en = resource("app/src/main/res/values/strings.xml")
        val es = resource("app/src/main/res/values-es/strings.xml")
        val de = resource("app/src/main/res/values-de/strings.xml")
        val keys = ChipDescription.entries.map(ChipDescription::resName) + listOf(
            "review_reject",
            "review_apply_n_of_m",
            "review_keep_as_is",
            "review_got_it",
            "review_all_skipped",
            "review_none_left_occ",
            "review_now",
            "review_none",
        )

        for (key in keys) {
            val expected = formatArguments(stringValue(en, key))
            assertEquals(key, expected, formatArguments(stringValue(es, key)))
            assertEquals(key, expected, formatArguments(stringValue(de, key)))
        }
        for (plural in listOf("review_apply_n", "review_left")) {
            for (xml in listOf(en, es, de)) {
                val block = xml.substringAfter("<plurals name=\"$plural\">", missingDelimiterValue = "").substringBefore("</plurals>")
                assertTrue(plural, block.contains("quantity=\"one\"") && block.contains("quantity=\"other\""))
            }
        }
    }

    /** A Deleted change set for [deletedId]: the tags before the deletion, and the same tags without it. */
    private fun deletionOf(deletedId: String, categoryId: String, before: List<GarmentTag>) = TagChangeSet(
        id = "delete-$deletedId",
        operation = TagOperation.Deleted,
        categoryId = categoryId,
        touchedTagIds = setOf(deletedId),
        beforeTags = before,
        afterTags = before.filterNot { tag -> tag.id == deletedId },
    )

    /** A card built the way the screen builds it: from the garment's live view and the change set's tags of its kind. */
    private fun cardFor(live: ClothingItem, proposal: ReviewProposal, changeSet: TagChangeSet): ReviewCardState = reviewCardStateFor(
        proposal = proposal,
        liveGroupTagIds = live.tags.filter { tag -> tag.categoryId == changeSet.categoryId }.mapTo(mutableSetOf(), GarmentTag::id),
        groupTagIds = (changeSet.beforeTags + changeSet.afterTags)
            .filter { tag -> tag.categoryId == changeSet.categoryId }
            .mapTo(mutableSetOf(), GarmentTag::id),
        isCategoryGroup = changeSet.categoryId == "category",
        existingTagIds = changeSet.afterTags.mapTo(mutableSetOf(), GarmentTag::id),
        label = { tagId -> tagId },
        collator = collator,
    )

    private fun tag(id: String, categoryId: String) = GarmentTag(id = id, categoryId = categoryId, name = id, sortOrder = 0)

    private fun garment(id: String, vararg tagIds: String) = ClothingItem(
        id = id,
        name = id,
        tags = allTags.filter { tag -> tag.id in tagIds },
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
    )

    private fun occasionProposal() = ReviewProposal(
        garmentId = "g1",
        before = setOf("occ-everyday", "occ-old"),
        proposed = setOf("occ-everyday", "occ-new", "occ-formal"),
        kind = ProposalKind.Suggested,
    )

    private fun occasionCard(item: ClothingItem, proposal: ReviewProposal): ReviewCardState = reviewCardStateFor(
        proposal = proposal,
        liveGroupTagIds = item.tags.filter { tag -> tag.categoryId == "occasion" }.mapTo(mutableSetOf(), GarmentTag::id),
        groupTagIds = occasionIds,
        isCategoryGroup = false,
        existingTagIds = occasionIds,
        label = { tagId -> tagId },
        collator = collator,
    )

    /** The tags the garment is written with on commit, for the decided session of one card. */
    private fun commitTags(item: ClothingItem, session: TagReviewSession, existingTags: List<GarmentTag>): Set<String> {
        val existingIds = existingTags.mapTo(mutableSetOf(), GarmentTag::id)
        val delta = commitDeltaFor(session.cards.single(), existingIds) ?: return item.tags.mapTo(mutableSetOf(), GarmentTag::id)
        val written = applyDelta(live = item, delta = delta, existingTags = existingTags, categoryId = existingTags.first().categoryId, now = 1L).item
        return (written ?: item).tags.mapTo(mutableSetOf(), GarmentTag::id)
    }

    private fun formatArguments(value: String): List<String> =
        Regex("""%(\d)\$[sd]""").findAll(value).map { match -> match.groupValues[1] }.toList()

    private fun stringValue(xml: String, name: String): String =
        Regex("""<string name="$name">([^<]*)</string>""").find(xml)?.groupValues?.get(1)
            ?: error("$name is missing")

    private fun resource(relativePath: String): String {
        val candidates = listOf(
            Path.of(relativePath),
            Path.of("..").resolve(relativePath),
        )
        return candidates.first { Files.exists(it) }.toFile().readText()
    }
}
