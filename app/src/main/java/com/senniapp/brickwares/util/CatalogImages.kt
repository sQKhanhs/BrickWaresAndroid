package com.senniapp.brickwares.util

/**
 * Public image URLs for a set, constructed from its number + variant (no catalog column needed —
 * both hosts are deterministic and load over plain HTTP, so Coil fetches them directly).
 *
 * The box shot is the preferred display image (buyers are drawn to the packaging first); the
 * Rebrickable render is the fallback for sets that have no BrickLink "original box" item (404).
 *
 * The set number is **lowercased**: both CDNs are case-sensitive and store alphanumeric set numbers
 * (e.g. "COMCON022", "DC1") in lowercase, while Brickset gives them uppercase — so building the URL
 * from the raw Brickset number 404'd for those (numeric sets are unaffected). Verified 2026-09-03.
 */
object CatalogImages {
    /** BrickLink "original box" packaging photo. Some sets (polybags/promos) have none → 404. */
    fun boxUrl(setNumber: String, variant: Int = 1): String =
        "https://img.bricklink.com/ItemImage/ON/0/${setNumber.lowercase()}-$variant.png"

    /** Rebrickable studio render of the built set. */
    fun renderUrl(setNumber: String, variant: Int = 1): String =
        "https://cdn.rebrickable.com/media/sets/${setNumber.lowercase()}-$variant.jpg"
}
