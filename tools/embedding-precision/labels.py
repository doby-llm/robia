"""Candidate label texts for Robia default tags (internal, English). Shared by embed.py and report.py."""

CLASSIFICATION_PREFIX = "task: classification | text: "

CATEGORIES = {
    "category-shorts": ("Shorts", "shorts: short pants that end above the knee"),
    "category-jackets": ("Jackets", "a jacket: short outerwear with long sleeves and a front zip or buttons"),
    "category-jumpsuits": ("Jumpsuits", "a jumpsuit: one-piece garment combining a top and trousers"),
    "category-blouses": ("Blouses", "a blouse: light, loose top in a soft or sheer fabric, often with buttons or ruffles"),
    "category-dresses": ("Dresses", "a dress: one-piece garment with a bodice and a skirt"),
    "category-skirts": ("Skirts", "a skirt: garment that hangs from the waist, without separate legs"),
    "category-blazers": ("Blazers", "a blazer: tailored jacket with lapels, worn over a shirt"),
    "category-cardigans": ("Cardigans", "a cardigan: knitted top that opens at the front with buttons"),
    "category-bags": ("Bags", "a bag: handbag, backpack or tote for carrying things"),
    "category-tops": ("Tops", "a top: casual upper-body garment such as a t-shirt, tank top or camisole"),
    "category-knitwear": ("Knitwear", "knitwear: garment made of knitted yarn, such as a knit vest or knit top"),
    "category-trousers": ("Trousers", "trousers: long pants covering each leg down to the ankle, such as jeans or chinos"),
    "category-sweaters": ("Sweaters", "a sweater: warm knitted pullover with long sleeves and no front opening"),
    "category-shoes": ("Shoes", "shoes: footwear such as sneakers, boots, sandals or heels"),
    "category-shirts": ("Shirts", "a shirt: collared top with buttons down the front"),
    "category-vests": ("Vests", "a vest: sleeveless upper garment worn over another top"),
    "category-jewelry": ("Jewelry", "jewelry: necklace, earrings, bracelet or ring"),
    "category-accessories": ("Accessories", "an accessory: a belt or waist belt, a scarf, a hat, sunglasses or a similar item"),
    "category-coats": ("Coats", "a coat: long, warm outerwear reaching the thighs or knees"),
    "category-socks": ("Socks", "socks or tights: hosiery worn on the feet and legs"),
    "category-underwear": ("Underwear", "underwear: bra, briefs, boxers or lingerie worn under clothes"),
}

OCCASIONS = {
    "occasion-active": ("Active", "sportswear for exercise, the gym or running"),
    "occasion-statement": ("Statement", "a bold, eye-catching piece with a striking color, pattern or design"),
    "occasion-dressed-up": ("Dressed-up", "an elegant outfit for going out, dinners or parties"),
    "occasion-formal": ("Formal", "formal wear for weddings, galas or ceremonies"),
    "occasion-everyday": ("Everyday", "casual everyday clothing for errands and relaxing"),
    "occasion-business": ("Business", "office or business attire for work"),
}

# Text recipes compared in the report. "desc" is the recipe proposed for the app.
TEXT_RECIPES = {
    "name": lambda name, desc: CLASSIFICATION_PREFIX + name,
    "desc": lambda name, desc: CLASSIFICATION_PREFIX + desc,
}
