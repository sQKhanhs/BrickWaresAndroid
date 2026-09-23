package com.senniapp.brickwares.data.repository

import com.senniapp.brickwares.data.local.WishlistEntity
import java.time.Instant
import java.time.OffsetDateTime

/**
 * The pure decision rules behind [SyncCoordinator] — no Room, no network — so the conflict and paging
 * logic is unit-testable on the JVM. The coordinator owns the I/O; everything that decides *what* to do
 * with a row or a page lives here.
 */
internal object SyncRules {
    /** Rows per pull page. Below PostgREST's `max_rows` (1000 on both stacks) so a page is never silently cut. */
    const val PULL_PAGE_SIZE = 500

    /** Bounded backoff after a failed sync while online (the online edge can fire before the network routes). */
    val RETRY_DELAYS_MS: List<Long> = listOf(2_000L, 8_000L, 30_000L)

    /**
     * A table's pull position: the newest SERVER stamp applied so far plus the greatest row id AT that
     * stamp, so the next pull resumes with `stamp > s OR (stamp = s AND id > lastId)`. Keyset paging on
     * (server_updated_at, id) is what makes a batch of rows sharing ONE stamp (a bulk upsert stamps them
     * all with the transaction's `now()`) safe to split across pulls — a stamp-only cursor with `>`
     * skipped every sibling past the page cap. Encoded as `stamp|lastId`; a bare stamp (the pre-keyset
     * format) decodes with no id and is read as `stamp > s`, which re-fetches nothing it already has.
     */
    data class Cursor(val stamp: String, val lastId: String?) {
        fun encode(): String = if (lastId == null) stamp else "$stamp$SEP$lastId"

        companion object {
            private const val SEP = '|'
            fun decode(raw: String?): Cursor? {
                if (raw.isNullOrBlank()) return null
                val i = raw.indexOf(SEP)
                return if (i < 0) Cursor(raw, null) else Cursor(raw.substring(0, i), raw.substring(i + 1).ifBlank { null })
            }
        }
    }

    /**
     * Last-writer-wins on the CLIENT `updated_at`: a remote row replaces the local one only when it is
     * strictly newer. A dirty (unpushed) local edit that is newer-or-equal keeps winning and is pushed;
     * an older dirty edit is superseded by the newer remote row (e.g. another device's delete) so it can
     * never resurrect it. Ties keep local — the push then re-sends the same values, which is harmless.
     */
    fun remoteWins(localUpdatedAt: Long?, remoteUpdatedAt: Long): Boolean =
        localUpdatedAt == null || remoteUpdatedAt > localUpdatedAt

    /** A page shorter than the page size is the last one. */
    fun isLastPage(received: Int, pageSize: Int = PULL_PAGE_SIZE): Boolean = received < pageSize

    /** Flatten pulled pages, de-duplicated by id (a row that moved between pages keeps its LAST version). */
    fun <T> mergePages(pages: List<List<T>>, idOf: (T) -> String): List<T> {
        val out = LinkedHashMap<String, T>()
        pages.forEach { page -> page.forEach { out[idOf(it)] = it } }
        return out.values.toList()
    }

    /**
     * The cursor to store after [rows] applied: the newest server stamp among them and, of the rows AT
     * that exact stamp, the greatest id (uuid text order = Postgres uuid order). Null when no row carries
     * a stamp (nothing pulled) — the caller then leaves the cursor untouched.
     */
    fun <T> nextCursor(rows: List<T>, stampOf: (T) -> String?, idOf: (T) -> String): Cursor? {
        val stamped = rows.mapNotNull { r -> stampOf(r)?.let { s -> parseInstant(s)?.let { Triple(s, it, idOf(r)) } } }
        if (stamped.isEmpty()) return null
        val newest = stamped.maxOf { it.second }
        val atNewest = stamped.filter { it.second == newest }
        return Cursor(stamp = atNewest.first().first, lastId = atNewest.maxOf { it.third })
    }

    /**
     * Which LOCAL active wishlist rows duplicate an incoming remote row for the same item and must be
     * tombstoned (dirty, so the tombstone pushes). The server allows ONE live wishlist row per user per
     * item (partial unique index); two devices wishlisting the same set offline each mint their own id,
     * the first to push wins, and the other device's row would violate the index on every push forever.
     * The row that already landed on the server ([keepId]) is the survivor.
     */
    fun wishlistDuplicates(active: List<WishlistEntity>, keepId: String, setId: Long?, figNum: String?): List<WishlistEntity> =
        active.filter { row ->
            row.id != keepId && !row.deleted &&
                ((setId != null && row.setId == setId) || (figNum != null && row.figNum == figNum))
        }

    /** Full-precision parse of a PostgREST timestamptz ("…+00:00" or "…Z"); null when unparseable. */
    fun parseInstant(s: String): Instant? =
        runCatching { OffsetDateTime.parse(s).toInstant() }
            .recoverCatching { Instant.parse(s) }
            .getOrNull()

    /**
     * Epoch millis of a PostgREST timestamptz, 0 when unparseable. `Instant.parse` accepts the "+00:00"
     * offset form only on newer java.time (JDK 12+ / recent Android) — elsewhere it throws and every
     * remote row would read as epoch 0, so LWW would always keep local. OffsetDateTime handles both.
     */
    fun parseIso(s: String): Long = parseInstant(s)?.toEpochMilli() ?: 0L
}
