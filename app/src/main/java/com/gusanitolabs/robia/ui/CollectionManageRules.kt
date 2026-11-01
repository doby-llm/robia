package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.COLLECTION_CATEGORY_ID
import com.gusanitolabs.robia.core.model.GarmentTag

/**
 * Manage-screen rules for the Collections tab (R4-D05, R4-D07). Pure: no Compose, no Android types.
 */

/** The ± members entry: only collection rows, and only when the screen was given a members callback. */
fun showsMembersAction(categoryId: String, hasCallback: Boolean): Boolean =
    categoryId == COLLECTION_CATEGORY_ID && hasCallback

/** Deleting a collection removes its memberships, not garments, but it still asks first. Other tags delete as before. */
fun needsDeleteConfirmation(tag: GarmentTag): Boolean = tag.categoryId == COLLECTION_CATEGORY_ID

/** A collection has no description (it only feeds label embeddings), so the editor does not show the field. */
fun showsDescriptionField(categoryId: String): Boolean = categoryId != COLLECTION_CATEGORY_ID
