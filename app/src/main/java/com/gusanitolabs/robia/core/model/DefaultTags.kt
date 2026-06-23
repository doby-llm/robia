package com.gusanitolabs.robia.core.model

object DefaultTags {
    val categories = listOf(
        TagCategory(id = "category", name = "Category", sortOrder = 10, isSystem = true),
        TagCategory(id = "season", name = "Season", sortOrder = 20, isSystem = true),
        TagCategory(id = "occasion", name = "Occasion", sortOrder = 40, isSystem = true),
        TagCategory(id = "location", name = "Location", sortOrder = 50, isSystem = true),
    )

    val tags = listOf(
        GarmentTag(id = "category-shorts", categoryId = "category", name = "Shorts", sortOrder = 10),
        GarmentTag(id = "category-jackets", categoryId = "category", name = "Jackets", sortOrder = 20),
        GarmentTag(id = "category-jumpsuits", categoryId = "category", name = "Jumpsuits", sortOrder = 30),
        GarmentTag(id = "category-blouses", categoryId = "category", name = "Blouses", sortOrder = 40),
        GarmentTag(id = "category-dresses", categoryId = "category", name = "Dresses", sortOrder = 50),
        GarmentTag(id = "category-skirts", categoryId = "category", name = "Skirts", sortOrder = 60),
        GarmentTag(id = "category-blazers", categoryId = "category", name = "Blazers", sortOrder = 70),
        GarmentTag(id = "category-cardigans", categoryId = "category", name = "Cardigans", sortOrder = 80),
        GarmentTag(id = "category-bags", categoryId = "category", name = "Bags", sortOrder = 90),
        GarmentTag(id = "category-tops", categoryId = "category", name = "Tops", sortOrder = 100),
        GarmentTag(id = "category-knitwear", categoryId = "category", name = "Knitwear", sortOrder = 110),
        GarmentTag(id = "category-trousers", categoryId = "category", name = "Trousers", sortOrder = 120),
        GarmentTag(id = "category-sweaters", categoryId = "category", name = "Sweaters", sortOrder = 130),
        GarmentTag(id = "category-shoes", categoryId = "category", name = "Shoes", sortOrder = 140),
        GarmentTag(id = "category-shirts", categoryId = "category", name = "Shirts", sortOrder = 150),
        GarmentTag(id = "category-vests", categoryId = "category", name = "Vests", sortOrder = 160),
        GarmentTag(id = "category-jewelry", categoryId = "category", name = "Jewelry", sortOrder = 170),
        GarmentTag(id = "category-accessories", categoryId = "category", name = "Accessories", sortOrder = 180),
        GarmentTag(id = "category-coats", categoryId = "category", name = "Coats", sortOrder = 190),
        GarmentTag(id = "season-spring", categoryId = "season", name = "Spring", sortOrder = 10, isSystem = true),
        GarmentTag(id = "season-summer", categoryId = "season", name = "Summer", sortOrder = 20, isSystem = true),
        GarmentTag(id = "season-fall", categoryId = "season", name = "Fall", sortOrder = 30, isSystem = true),
        GarmentTag(id = "season-winter", categoryId = "season", name = "Winter", sortOrder = 40, isSystem = true),
        GarmentTag(id = "occasion-active", categoryId = "occasion", name = "Active", sortOrder = 10),
        GarmentTag(id = "occasion-statement", categoryId = "occasion", name = "Statement", sortOrder = 20),
        GarmentTag(id = "occasion-dressed-up", categoryId = "occasion", name = "Dressed-up", sortOrder = 30),
        GarmentTag(id = "occasion-formal", categoryId = "occasion", name = "Formal", sortOrder = 40),
        GarmentTag(id = "occasion-everyday", categoryId = "occasion", name = "Everyday", sortOrder = 50),
        GarmentTag(id = "occasion-business", categoryId = "occasion", name = "Business", sortOrder = 60),
        GarmentTag(id = "location-main-closet", categoryId = "location", name = "Main closet", sortOrder = 10),
    )

    val mainColors = listOf(
        MainColor(id = "black", name = "Black", hex = "#1F1F1F", sortOrder = 10, isDefault = true),
        MainColor(id = "white", name = "White", hex = "#F8F9FA", sortOrder = 20, isDefault = true),
        MainColor(id = "gray-charcoal", name = "Beige / Cream", hex = "#D8C3A5", sortOrder = 30, isDefault = true),
        MainColor(id = "brown", name = "Brown", hex = "#8B6848", sortOrder = 40, isDefault = true),
        MainColor(id = "navy-blue", name = "Navy / Blue", hex = "#315F8E", sortOrder = 60, isDefault = true),
        MainColor(id = "green", name = "Green", hex = "#5F6F48", sortOrder = 70, isDefault = true),
        MainColor(id = "red", name = "Red", hex = "#9E3D35", sortOrder = 80, isDefault = true),
        MainColor(id = "pink", name = "Pink", hex = "#D4879A", sortOrder = 90, isDefault = true),
        MainColor(id = "purple", name = "Purple", hex = "#765A91", sortOrder = 100, isDefault = true),
        MainColor(id = "yellow-mustard", name = "Yellow / Mustard", hex = "#D6B84C", sortOrder = 110, isDefault = true),
        MainColor(id = "orange", name = "Orange", hex = "#C56F33", sortOrder = 120, isDefault = true),
    )
}
