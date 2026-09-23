package com.senniapp.brickwares.data.repository

import com.senniapp.brickwares.data.local.CollectionCopyEntity
import com.senniapp.brickwares.data.local.SalesEntity
import com.senniapp.brickwares.data.local.WishlistEntity

/**
 * CSV (de)serialization for the user-data export/import in Settings — the collection, sales, AND
 * wishlist in one file. The file is a plain, portable backup the user can save and later re-import;
 * every row is tagged with a `record_type` (`collection` | `sale` | `wishlist`) and the columns are the
 * union across the three (a row leaves the ones its type doesn't use blank). Import maps by header
 * **name** (not position) so a hand-edited or future-versioned file still loads.
 *
 * Every field needed to rebuild a row offline is exported (the technical `set_id` / `status` / … tail
 * included), so a normal round-trip never depends on the catalog. Sync/identity fields (`id`,
 * `deleted`, `updatedAt`, `dirty`) are NOT exported — import always mints a fresh id and marks the row
 * dirty. RFC-4180-style quoting handles commas, quotes and newlines inside notes/names.
 */
object CollectionCsv {
    /**
     * The export format version, written into every row's `format_version` column. Import reads it: a
     * file at this version or older loads (name-mapping + defaults absorb added/removed columns); a
     * NEWER file is refused with [CsvTooNewException] so the user is told to update rather than importing
     * with silent gaps. Bump this only when a change can't be absorbed by name-mapping (e.g. a column's
     * meaning changes) — a file with no column at all reads as version 1 (predates the marker).
     *
     * v2 added `record_type` + the sales/wishlist columns (one file now backs up all three lists). The
     * bump matters: a v1 app must REFUSE a v2 file rather than import its sale/wishlist rows as
     * collection copies — which is exactly what the "refuse newer" gate does. A v1 file (no
     * `record_type`) still imports here: every row reads as a `collection` copy, as before.
     */
    const val FORMAT_VERSION = 2

    // The union of columns across the three record types (version + record_type first, then the shared
    // identity/display fields, then the per-type value fields). Import maps by name, so order is free and
    // a row simply leaves the columns its type doesn't use blank.
    private val COLUMNS = listOf(
        "format_version", "record_type",
        "set_number", "name", "item_kind", "fig_num", "set_id", "number_variant",
        "theme", "subtheme", "release_year", "release_month", "pieces", "minifigs",
        "retail_price", "status", "image_url",
        "quantity", "condition", "currency", "price_paid", "sale_price",
        "acquired_on", "sold_on", "notes",
    )

    /**
     * Serialize the user's collection, sales and wishlist to one CSV (a header row + one tagged row per
     * item). Row order is collection → sales → wishlist, but import keys off `record_type`, not order.
     */
    fun encode(
        copies: List<CollectionCopyEntity>,
        sales: List<SalesEntity>,
        wishlist: List<WishlistEntity>,
        /** The catalog `number_variant` for a row (from the caller's catalog overlay), or null when
         *  unknown (a legacy set_id-less row, or a cold overlay). Exported so a future re-import can
         *  resolve the EXACT variant of a shared-number (CMF/SDCC) set instead of the lowest one. */
        variantOf: (setId: Long?, setNumber: String) -> Int? = { _, _ -> null },
    ): String {
        val sb = StringBuilder()
        sb.append(COLUMNS.joinToString(",") { escape(it) }).append('\n')
        copies.forEach { appendRow(sb, it.toRow(variantOf(it.setId, it.setNumber))) }
        sales.forEach { appendRow(sb, it.toRow(variantOf(it.setId, it.setNumber))) }
        wishlist.forEach { appendRow(sb, it.toRow(variantOf(it.setId, it.setNumber))) }
        return sb.toString()
    }

    /** Emit one row's values in [COLUMNS] order; a column the row omits is written blank. */
    private fun appendRow(sb: StringBuilder, row: Map<String, String>) {
        sb.append(COLUMNS.joinToString(",") { escape(row[it].orEmpty()) }).append('\n')
    }

    private fun CollectionCopyEntity.toRow(variant: Int?): Map<String, String> = mapOf(
        "format_version" to FORMAT_VERSION.toString(), "record_type" to "collection",
        "set_number" to setNumber, "name" to name, "item_kind" to itemKind,
        "fig_num" to figNum.orEmpty(), "set_id" to setId?.toString().orEmpty(),
        "number_variant" to variant?.toString().orEmpty(),
        "theme" to theme, "subtheme" to subtheme,
        "release_year" to releaseYear.toString(), "release_month" to releaseMonth.toString(),
        "pieces" to pieces.toString(), "minifigs" to minifigs.toString(),
        "retail_price" to retailPrice?.toString().orEmpty(), "status" to status,
        "image_url" to imageUrl.orEmpty(),
        "quantity" to quantity.toString(), "condition" to condition, "currency" to currency,
        "price_paid" to pricePaid.toString(),
        "acquired_on" to acquiredOn.orEmpty(), "notes" to notes.orEmpty(),
    )

    private fun SalesEntity.toRow(variant: Int?): Map<String, String> = mapOf(
        "format_version" to FORMAT_VERSION.toString(), "record_type" to "sale",
        "set_number" to setNumber, "name" to name, "item_kind" to itemKind,
        "fig_num" to figNum.orEmpty(), "set_id" to setId?.toString().orEmpty(),
        "number_variant" to variant?.toString().orEmpty(),
        "theme" to theme,
        "release_year" to releaseYear.toString(), "release_month" to releaseMonth.toString(),
        "retail_price" to retailPrice?.toString().orEmpty(), "image_url" to imageUrl.orEmpty(),
        "quantity" to quantity.toString(), "condition" to condition, "currency" to currency,
        "price_paid" to pricePaid.toString(), "sale_price" to salePrice.toString(),
        "sold_on" to soldOn.orEmpty(), "notes" to notes.orEmpty(),
    )

    private fun WishlistEntity.toRow(variant: Int?): Map<String, String> = mapOf(
        "format_version" to FORMAT_VERSION.toString(), "record_type" to "wishlist",
        "set_number" to setNumber, "name" to name, "item_kind" to itemKind,
        "fig_num" to figNum.orEmpty(), "set_id" to setId?.toString().orEmpty(),
        "number_variant" to variant?.toString().orEmpty(),
        "theme" to theme, "subtheme" to subtheme,
        "release_year" to releaseYear.toString(), "release_month" to releaseMonth.toString(),
        "pieces" to pieces.toString(), "minifigs" to minifigs.toString(),
        "retail_price" to retailPrice?.toString().orEmpty(), "status" to status,
        "image_url" to imageUrl.orEmpty(),
    )

    /** The declared format version of a parsed file (from the first row), or 1 when the marker is absent. */
    fun versionOf(parsed: Parsed): Int =
        parsed.rows.firstOrNull()?.get("format_version")?.trim()?.toIntOrNull() ?: 1

    /** A parsed row's record type. A v1 file (no `record_type` column) is entirely collection copies. */
    fun recordType(row: Map<String, String>): String =
        row["record_type"]?.trim()?.lowercase()?.ifBlank { null } ?: "collection"

    /** A parsed CSV: the [header] columns and the header-keyed data [rows]. */
    data class Parsed(val header: List<String>, val rows: List<Map<String, String>>)

    /** Parse CSV text into its header + header-keyed rows (first non-blank record is the header). */
    fun parse(text: String): Parsed {
        val records = parseRecords(text)
        if (records.isEmpty()) return Parsed(emptyList(), emptyList())
        val header = records.first().map { it.trim() }
        val rows = records.drop(1)
            .filter { row -> row.any { it.isNotBlank() } } // skip blank lines
            .map { row -> header.indices.associate { i -> header[i] to row.getOrElse(i) { "" } } }
        return Parsed(header, rows)
    }

    private fun escape(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else {
            s
        }

    /** Split CSV text into records of fields, honoring quoted fields (embedded commas/quotes/newlines). */
    private fun parseRecords(text: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var fields = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cur.append('"'); i++ } else inQuotes = false
                } else {
                    cur.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> { fields.add(cur.toString()); cur.setLength(0) }
                    '\n' -> { fields.add(cur.toString()); cur.setLength(0); records.add(fields); fields = mutableListOf() }
                    '\r' -> {} // CRLF line ending — the '\n' closes the record
                    else -> cur.append(ch)
                }
            }
            i++
        }
        // Flush a final record when the file doesn't end in a newline.
        if (cur.isNotEmpty() || fields.isNotEmpty()) { fields.add(cur.toString()); records.add(fields) }
        return records
    }
}

/** A CSV export declares a [fileVersion] newer than [CollectionCsv.FORMAT_VERSION] — the app can't read it. */
class CsvTooNewException(val fileVersion: Int) :
    Exception("CSV format v$fileVersion is newer than supported v${CollectionCsv.FORMAT_VERSION}")
