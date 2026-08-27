package com.senniapp.brickwares.util

/**
 * Public image URLs for a set, constructed from its number + variant (no catalog column needed —
 * both hosts are deterministic and load over plain HTTP, so Coil fetches them directly).
 *
 * The box shot is the preferred display image (buyers are drawn to the packaging first); the
 * Rebrickable render is the fallback for sets that have no BrickLink "original box" item (404).
 */
object CatalogImages {
    /** BrickLink "original box" packaging photo. Some sets (polybags/promos) have none → 404. */
    fun boxUrl(setNumber: String, variant: Int = 1): String =
        "https://img.bricklink.com/ItemImage/ON/0/$setNumber-$variant.png"

    /** Rebrickable studio render of the built set. */
    fun renderUrl(setNumber: String, variant: Int = 1): String =
        "https://cdn.rebrickable.com/media/sets/$setNumber-$variant.jpg"
}
