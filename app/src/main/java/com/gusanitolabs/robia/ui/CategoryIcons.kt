package com.gusanitolabs.robia.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Shared presentation-only icon mapping for wardrobe metadata categories.
 * Keep this out of domain/storage models so icon choices can evolve without migrations.
 */
fun categoryIconFor(categoryId: String): ImageVector = when (categoryId) {
    "category" -> Icons.Outlined.Checkroom
    "season" -> Icons.Outlined.CalendarMonth
    "occasion" -> Icons.Outlined.Event
    "location" -> Icons.Outlined.FolderOpen
    else -> Icons.Outlined.Checkroom
}
