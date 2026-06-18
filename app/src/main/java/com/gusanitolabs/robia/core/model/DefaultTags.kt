package com.gusanitolabs.robia.core.model

object DefaultTags {
    val categories = listOf(
        TagCategory(id = "category", name = "Category", sortOrder = 10, isSystem = true),
        TagCategory(id = "season", name = "Season", sortOrder = 20, isSystem = true),
        TagCategory(id = "occasion", name = "Occasion", sortOrder = 40, isSystem = true),
        TagCategory(id = "location", name = "Location", sortOrder = 50, isSystem = true),
    )

    val tags = listOf(
        GarmentTag(id = "category-t-shirt", categoryId = "category", name = "T-shirt", sortOrder = 10),
        GarmentTag(id = "category-pants", categoryId = "category", name = "Pants", sortOrder = 20),
        GarmentTag(id = "category-shirt", categoryId = "category", name = "Shirt", sortOrder = 30),
        GarmentTag(id = "category-shoes", categoryId = "category", name = "Shoes", sortOrder = 40),
        GarmentTag(id = "season-spring", categoryId = "season", name = "Spring", sortOrder = 10, isSystem = true),
        GarmentTag(id = "season-summer", categoryId = "season", name = "Summer", sortOrder = 20, isSystem = true),
        GarmentTag(id = "season-autumn", categoryId = "season", name = "Autumn", sortOrder = 30, isSystem = true),
        GarmentTag(id = "season-winter", categoryId = "season", name = "Winter", sortOrder = 40, isSystem = true),
        GarmentTag(id = "occasion-everyday", categoryId = "occasion", name = "Everyday", sortOrder = 10),
        GarmentTag(id = "occasion-work", categoryId = "occasion", name = "Work", sortOrder = 20),
        GarmentTag(id = "occasion-travel", categoryId = "occasion", name = "Travel", sortOrder = 30),
        GarmentTag(id = "location-main-closet", categoryId = "location", name = "Main closet", sortOrder = 10),
    )

    val mainColors = listOf(
        MainColor(id = "black", name = "Black", hex = "#1F1F1F", sortOrder = 10, isDefault = true),
        MainColor(id = "white", name = "White", hex = "#F8F9FA", sortOrder = 20, isDefault = true),
        MainColor(id = "gray-charcoal", name = "Gray / Charcoal", hex = "#5F6368", sortOrder = 30, isDefault = true),
        MainColor(id = "brown", name = "Brown", hex = "#8B6848", sortOrder = 40, isDefault = true),
        MainColor(id = "beige-cream", name = "Beige / Cream", hex = "#D8C3A5", sortOrder = 50, isDefault = true),
        MainColor(id = "navy-blue", name = "Navy / Blue", hex = "#315F8E", sortOrder = 60, isDefault = true),
        MainColor(id = "green", name = "Green", hex = "#5F6F48", sortOrder = 70, isDefault = true),
        MainColor(id = "red", name = "Red", hex = "#9E3D35", sortOrder = 80, isDefault = true),
        MainColor(id = "pink", name = "Pink", hex = "#D4879A", sortOrder = 90, isDefault = true),
        MainColor(id = "purple", name = "Purple", hex = "#765A91", sortOrder = 100, isDefault = true),
        MainColor(id = "yellow-mustard", name = "Yellow / Mustard", hex = "#D6B84C", sortOrder = 110, isDefault = true),
        MainColor(id = "orange", name = "Orange", hex = "#C56F33", sortOrder = 120, isDefault = true),
    )
}
