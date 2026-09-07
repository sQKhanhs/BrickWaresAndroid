package com.senniapp.brickwares.data.repository

import com.senniapp.brickwares.data.local.CollectionCopyEntity

/**
 * CSV (de)serialization for the collection export/import in Settings. The file is a plain, portable
 * backup the user can save and later re-import; columns are ordered user-meaningful-first, but import
 * maps by header **name** (not position) so a hand-edited or future-versioned file still loads.
 *
 * Every field needed to rebuild a [CollectionCopyEntity] offline is exported (the technical `set_id` /
 * `status` / … tail included), so a normal round-trip never depends on the catalog. Sync/identity
 * fields (`id`, `deleted`, `updatedAt`, `dirty`) are NOT exported — import always mints a fresh id and
 * marks the row dirty. RFC-4180-style quoting handles commas, quotes and newlines inside notes/names.
 */
object CollectionCsv {
    /**
     * The export format version, written into every row's `format_version` column. Import reads it: a
     * file at this version or older loads (name-mapping + defaults absorb added/removed columns); a
     * NEWER file is refused with [CsvTooNewException] so the user is told to update rather than importing
     * with silent gaps. Bump this only when a change can't be absorbed by name-mapping (e.g. a column's
     * meaning changes) — a file with no column at all reads as version 1 (predates the marker).
     */
    const val FORMAT_VERSION = 1

    // Header order (version marker first, then user-facing fields). Import is by name, so order is free.
    private val COLUMNS = listOf(
        "format_version",
        "set_number", "name", "item_kind", "quantity", "condition", "price_paid", "currency",
        "acquired_on", "notes", "theme", "subtheme", "release_year", "release_month",
        "pieces", "minifigs", "retail_price", "status", "set_id", "fig_num", "image_url",
    )

    /** Serialize active copies to CSV text (a header row + one row per copy). */
    fun encode(copies: List<CollectionCopyEntity>): String {
        val sb = StringBuilder()
        sb.append(COLUMNS.joinToString(",") { escape(it) }).append('\n')
        for (c in copies) {
            val row = listOf(
                FORMAT_VERSION.toString(),
                c.setNumber, c.name, c.itemKind, c.quantity.toString(), c.condition,
                c.pricePaid.toString(), c.currency, c.acquiredOn.orEmpty(), c.notes.orEmpty(),
                c.theme, c.subtheme, c.releaseYear.toString(), c.releaseMonth.toString(),
                c.pieces.toString(), c.minifigs.toString(), c.retailPrice?.toString().orEmpty(),
                c.status, c.setId?.toString().orEmpty(), c.figNum.orEmpty(), c.imageUrl.orEmpty(),
            )
            sb.append(row.joinToString(",") { escape(it) }).append('\n')
        }
        return sb.toString()
    }

    /** The declared format version of a parsed file (from the first row), or 1 when the marker is absent. */
    fun versionOf(parsed: Parsed): Int =
        parsed.rows.firstOrNull()?.get("format_version")?.trim()?.toIntOrNull() ?: 1

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
