package com.senniapp.brickwares.data.model

/** Condition of an owned copy, chosen in the Add-to-Collection sheet. */
enum class Condition { NEW, USED }

/**
 * Catalog (reference) data for a set, returned by set-number search in the Add sheet.
 * In production this comes from Brickset; here it's mock data. It carries everything
 * needed to build a [CollectionItem] except the user-supplied fields (paid, condition…).
 */
data class CatalogSet(
    val setNumber: String,
    val name: String,
    val itemType: ItemType,
    val theme: String,
    val releaseYear: Int,
    val releaseMonth: Int,
    val pieces: Int,
    val minifigs: Int,
    val retailPrice: Long,
    val status: Availability,
)
