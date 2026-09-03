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

    /** Rebrickable studio render of the built set (full resolution — can be several MB). */
    fun renderUrl(setNumber: String, variant: Int = 1): String =
        "https://cdn.rebrickable.com/media/sets/${setNumber.lowercase()}-$variant.jpg"

    /**
     * Rebrickable's server-resized, square-padded thumbnail of the render — the small image for list
     * cards and small heroes. The full render is often 1–5 MB; this variant is ~10–150 KB, which is
     * the difference between a card image appearing in <1s vs several seconds on mobile. It 404s
     * cleanly (like the render) when a set has no image, so a caller's fallback chain still fires.
     * [size] is the box edge in px; 320 is crisp for a 72–96 dp thumbnail on high-density screens.
     */
    fun thumbUrl(setNumber: String, variant: Int = 1, size: Int = 320): String =
        "https://cdn.rebrickable.com/media/thumbs/sets/${setNumber.lowercase()}-$variant.jpg/${size}x${size}p.jpg"

    /**
     * Full-resolution gallery candidates for a set — the box shot + the render. Either may 404 (the
     * gallery drops those). Use where only the set number is on hand (collection / wishlist / sold
     * items); callers holding a [com.senniapp.brickwares.data.model.CatalogSet] can pass its
     * `boxImageUrl` + `imageUrl` directly instead (they already carry the correct variant).
     */
    fun galleryUrls(setNumber: String, variant: Int = 1): List<String> =
        listOf(boxUrl(setNumber, variant), renderUrl(setNumber, variant))
}
