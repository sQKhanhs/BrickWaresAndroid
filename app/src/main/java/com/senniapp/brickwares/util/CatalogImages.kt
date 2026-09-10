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
     * The full-resolution [renderUrl] for a stored [thumbUrl] — same set + **variant**. User rows
     * (collection/wishlist/sales) persist the small thumb in `imageUrl` but NOT the number variant, so
     * a card can't rebuild the render from `setNumber` alone (that would drop the variant and, for
     * shared numbers like CMF/SDCC exclusives, resolve a *different* set). This recovers the crisp
     * gallery image from the thumb the row already carries. Non-thumb URLs (or null) pass through.
     */
    fun renderFromThumb(thumbUrl: String?): String? {
        if (thumbUrl == null) return null
        val marker = "/media/thumbs/sets/"
        val i = thumbUrl.indexOf(marker)
        if (i < 0) return thumbUrl
        val slug = thumbUrl.substring(i + marker.length).substringBefore(".jpg")
        return "https://cdn.rebrickable.com/media/sets/$slug.jpg"
    }

    /**
     * The card-sized [thumbUrl] for a stored Rebrickable *render* URL (e.g. `sets.render_url`, the
     * authoritative image captured at ingest) — the inverse of [renderFromThumb]. Lets a card show the
     * small thumbnail of an authoritative render without knowing the set's number/variant. A URL that
     * isn't a standard Rebrickable render (or a non-Rebrickable host) is returned unchanged, so it's
     * still shown — just at full size.
     */
    fun thumbFromRender(renderUrl: String, size: Int = 320): String {
        val marker = "/media/sets/"
        val i = renderUrl.indexOf(marker)
        if (i < 0) return renderUrl
        val slug = renderUrl.substring(i + marker.length).substringBefore(".jpg")
        return "https://cdn.rebrickable.com/media/thumbs/sets/$slug.jpg/${size}x${size}p.jpg"
    }
}
