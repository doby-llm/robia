package com.gusanitolabs.robia.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.core.model.TagDisplayPolicy

@Composable
internal fun GarmentTag.localizedTagLabel(): String {
    if (TagDisplayPolicy.shouldUseStoredName(this)) return name
    return defaultTagLabelRes(id)?.let { resId -> stringResource(resId) } ?: name
}

@StringRes
private fun defaultTagLabelRes(tagId: String): Int? = when (tagId) {
    "category-shorts" -> R.string.tag_shorts
    "category-jackets" -> R.string.tag_jackets
    "category-jumpsuits" -> R.string.tag_jumpsuits
    "category-blouses" -> R.string.tag_blouses
    "category-dresses" -> R.string.tag_dresses
    "category-skirts" -> R.string.tag_skirts
    "category-blazers" -> R.string.tag_blazers
    "category-cardigans" -> R.string.tag_cardigans
    "category-bags" -> R.string.tag_bags
    "category-tops" -> R.string.tag_tops
    "category-knitwear" -> R.string.tag_knitwear
    "category-trousers" -> R.string.tag_trousers
    "category-sweaters" -> R.string.tag_sweaters
    "category-shoes" -> R.string.tag_shoes
    "category-shirts" -> R.string.tag_shirts
    "category-vests" -> R.string.tag_vests
    "category-jewelry" -> R.string.tag_jewelry
    "category-accessories" -> R.string.tag_accessories
    "category-coats" -> R.string.tag_coats
    "season-spring" -> R.string.tag_spring
    "season-summer" -> R.string.tag_summer
    "season-fall" -> R.string.tag_fall
    "season-winter" -> R.string.tag_winter
    "occasion-active" -> R.string.tag_active
    "occasion-statement" -> R.string.tag_statement
    "occasion-dressed-up" -> R.string.tag_dressed_up
    "occasion-formal" -> R.string.tag_formal
    "occasion-everyday" -> R.string.tag_everyday
    "occasion-business" -> R.string.tag_business
    "location-main-closet" -> R.string.tag_main_closet
    else -> null
}
