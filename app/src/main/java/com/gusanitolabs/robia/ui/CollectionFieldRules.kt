package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.COLLECTION_CATEGORY_ID
import com.gusanitolabs.robia.core.model.GarmentTag

/**
 * Collections in the garment editor, item detail and filters (R4-D06). Pure: no Compose, no Android types.
 */

/** Occasions, seasons and collections take any number of tags; every other category takes one. */
fun isMultiSelectCategory(categoryId: String): Boolean =
    categoryId == "occasion" || categoryId == "season" || categoryId == COLLECTION_CATEGORY_ID

/** The editor shows the Collections row when a collection exists to choose or the garment already has one. */
fun showsCollectionRow(availableCollectionCount: Int, garmentCollectionCount: Int): Boolean =
    availableCollectionCount > 0 || garmentCollectionCount > 0

/** Item detail shows the Collections row only for a garment that belongs to at least one collection. */
fun showsCollectionDetailRow(garmentCollectionCount: Int): Boolean = garmentCollectionCount > 0

/** Filters show the Collections card only when at least one collection exists. */
fun showsCollectionFilterCard(availableCollectionCount: Int): Boolean = availableCollectionCount > 0

/**
 * Tags a saved garment carries: every available tag it has selected, whatever its category. Collection memberships
 * ride along here, so an edit cannot drop them.
 */
fun savedGarmentTags(availableTags: List<GarmentTag>, selectedTagIds: List<String>): List<GarmentTag> =
    availableTags.filter { tag -> tag.id in selectedTagIds }
