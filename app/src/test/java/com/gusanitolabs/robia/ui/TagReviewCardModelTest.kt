package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.suggestions.ProposalKind
import com.gusanitolabs.robia.suggestions.ReviewChange
import com.gusanitolabs.robia.suggestions.ReviewProposal
import com.gusanitolabs.robia.suggestions.TagDelta
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
        val proposal = ReviewProposal("g3", before = setOf("occ-old"), proposed = setOf("occ-everyday"), kind = ProposalKind.Suggested)
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
    fun allChangesSkippedNeedsSomethingToLeaveOut() {
        val card = occasionCard(garment("g1", "occ-everyday", "occ-old"), occasionProposal())
        val allLeftOut = card.copy(skipped = setOf("rem:occ-old", "add:occ-formal", "add:occ-new"))

        assertTrue(allChangesSkipped(allLeftOut, chosenTagId = null))
        assertFalse(allChangesSkipped(card, chosenTagId = null))
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
            "review_hint_changes",
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
