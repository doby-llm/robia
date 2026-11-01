package com.gusanitolabs.robia.ui

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the members route, its save path and its isolation from Browse state as source text (no Compose UI tests in CI). */
class CollectionMembersWiringContractTest {
    private val app = source("app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt")
    private val screen = source("app/src/main/java/com/gusanitolabs/robia/ui/CollectionMembersScreen.kt")

    @Test
    fun v_c05_1_routeFeedsWardrobeModelsHidesShellChromeAndIsOpenedFromManage() {
        assertTrue(app.contains("data class CollectionMembers(val tagId: String) : RobiaRoute {\n        override val titleRes = R.string.manage_tab_collections"))

        val needsModels = app.substringAfter("val needsWardrobeUiModels =").substringBefore("\n")
        assertTrue(needsModels.contains("currentRoute is RobiaRoute.CollectionMembers"))
        assertFalse(app.contains("RobiaRoute.CollectionMembers in WardrobeUiModelRoutes"))

        assertTrue(app.contains("currentRoute != RobiaRoute.AdvancedFilters && currentRoute !is RobiaRoute.CollectionMembers) CenterAlignedTopAppBar("))
        val bottomBarGate = app.substringAfter("currentRoute != RobiaRoute.ItemDetail &&").substringBefore(") {")
        assertTrue(bottomBarGate.contains("currentRoute !is RobiaRoute.CollectionMembers"))

        assertTrue(app.contains("onEditMembers = { tag -> onRouteSelected(RobiaRoute.CollectionMembers(tag.id)) }"))
        val branch = app.substringAfter("is RobiaRoute.CollectionMembers -> ").substringBefore("RobiaRoute.ColorReview -> ")
        assertTrue(branch.contains("CollectionMembersScreen("))
        assertTrue(branch.contains("onClose = onBack"))
        assertTrue(branch.contains("?: LaunchedEffect(Unit)"))
    }

    @Test
    fun v_c05_2_saveGoesThroughSetTagMembershipInsideLaunchReviewWriteAndNeverClearsTags() {
        val save = app.substringAfter("onSaveCollectionMembership = { tagId, add, remove ->").substringBefore("onTagReviewWrite = ::writeTagReview")
        val launch = save.indexOf("launchReviewWrite {")
        val write = save.indexOf("tagRepository.setTagMembership(tagId, add, remove, System.currentTimeMillis())")
        assertTrue(launch >= 0)
        assertTrue(write > launch)
        assertTrue(save.contains("(add + remove).map { id -> WardrobeSyncOperation.UpsertItem(id) }"))

        assertFalse(screen.contains("clearTags"))
        assertTrue(screen.contains("fun save() {\n        if (saving) return\n        saving = true\n        onSave(session.added, session.removed)\n        onClose()\n    }"))
        assertTrue(screen.contains("onSave = { save() }"))
    }

    @Test
    fun v_c05_3_screenOwnsItsFilterSearchAndDragArmAndNeverTouchesBrowseState() {
        assertFalse(screen.contains("browseFilters"))
        assertFalse(screen.contains("selectedBrowseItemIds"))
        assertFalse(screen.contains("onBrowseColumnsChange"))
        assertFalse(screen.contains("DragSelectArm"))
        assertFalse(screen.contains("dragSelect("))

        assertTrue(screen.contains("var filters by remember { mutableStateOf(BrowseFilterState()) }"))
        assertTrue(screen.contains("SearchController("))
        assertTrue(screen.contains("remember { MembersDragArm() }"))
        assertTrue(screen.contains("DragPaint(anchorId, arm.before)"))

        val branch = app.substringAfter("is RobiaRoute.CollectionMembers -> ").substringBefore("RobiaRoute.ColorReview -> ")
        assertFalse(branch.contains("browseFilters"))
        assertFalse(branch.contains("selectedBrowseItemIds"))
    }

    @Test
    fun ft1_1_changeLabelsStayOnOneLineAndTheRemovedLabelIsStruckThrough() {
        val label = screen.substringAfter("private fun ChangeLabel(").substringBefore("@OptIn")
        assertTrue(label.contains("maxLines = 1"))
        assertTrue(label.contains("softWrap = false"))
        assertTrue(label.contains("overflow = TextOverflow.Ellipsis"))
        assertTrue(label.contains("style = MaterialTheme.typography.labelSmall"))
        assertTrue(label.contains("if (strikeThrough) TextDecoration.LineThrough else null"))

        val badge = screen.substringAfter("private fun MemberChangeBadge(").substringBefore("@Composable\nprivate fun ChangeLabel(")
        assertTrue(badge.contains("text = \"+ \" + stringResource(R.string.collection_members_added_badge)"))
        assertTrue(badge.contains("text = stringResource(R.string.collection_members_removed_badge)"))
        assertTrue(badge.contains("strikeThrough = true"))
    }

    @Test
    fun grid_cardsAreReusedButBrowseBehaviourIsUnchanged() {
        val browseGrid = source("app/src/main/java/com/gusanitolabs/robia/ui/BrowseGrid.kt")
        assertTrue(browseGrid.contains("internal fun GarmentPhotoTile("))
        assertTrue(browseGrid.contains("internal fun GarmentGridCard("))
        assertFalse(browseGrid.contains("private fun GarmentPhotoTile("))
        assertFalse(browseGrid.contains("private fun GarmentGridCard("))
    }

    private fun source(relativePath: String): String {
        val candidates = listOf(
            Path.of(relativePath),
            Path.of("..").resolve(relativePath),
        )
        return candidates.first { Files.exists(it) }.toFile().readText()
    }
}
