package com.gusanitolabs.robia.core.model

object TagDisplayPolicy {
    private val canonicalDefaultNamesById: Map<String, String> = DefaultTags.tags.associate { tag -> tag.id to tag.name }

    fun canonicalDefaultNameForTagId(tagId: String): String? = canonicalDefaultNamesById[tagId]

    fun shouldUseStoredName(tag: GarmentTag): Boolean {
        val canonicalName = canonicalDefaultNameForTagId(tag.id) ?: return true
        return !tag.name.equals(canonicalName, ignoreCase = false)
    }
}
