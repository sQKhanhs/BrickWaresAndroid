package com.senniapp.brickwares.data.repository

import com.senniapp.brickwares.data.local.WishlistEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sync engine's decision rules, exercised as the multi-device scenarios they exist for. The I/O
 * (Room, PostgREST) lives in SyncCoordinator; everything that decides WHAT to do is here and pure.
 */
class SyncRulesTest {

    // ---- Last-writer-wins on the client updated_at ----

    @Test
    fun `a newer remote row replaces the local one even when local is dirty`() {
        // Device B edited X offline at t1; device A deleted X at t2 > t1 and pushed. B's pull must let
        // the tombstone supersede B's stale dirty edit, so B never pushes it and X stays deleted.
        val t1 = 1_000L
        val t2 = 2_000L
        assertTrue(SyncRules.remoteWins(localUpdatedAt = t1, remoteUpdatedAt = t2))
    }

    @Test
    fun `an older remote row never overwrites a newer local edit`() {
        // A deleted X at t1; B edited X at t2 > t1 offline. B keeps its edit and pushes it, and the
        // server trigger accepts it (t2 >= t1) — the row is legitimately resurrected everywhere.
        assertFalse(SyncRules.remoteWins(localUpdatedAt = 2_000L, remoteUpdatedAt = 1_000L))
    }

    @Test
    fun `an equal timestamp keeps local`() {
        // Same instant on both sides (typically the same edit echoed back) — no churn, local stays.
        assertFalse(SyncRules.remoteWins(localUpdatedAt = 5_000L, remoteUpdatedAt = 5_000L))
    }

    @Test
    fun `a row unknown locally is always taken`() {
        assertTrue(SyncRules.remoteWins(localUpdatedAt = null, remoteUpdatedAt = 1L))
    }

    // ---- Pull paging: keyset cursor on (server_updated_at, id) ----

    private data class Row(val id: String, val stamp: String?)

    @Test
    fun `pages shorter than the page size end the loop`() {
        assertTrue(SyncRules.isLastPage(received = 0))
        assertTrue(SyncRules.isLastPage(received = SyncRules.PULL_PAGE_SIZE - 1))
        assertFalse(SyncRules.isLastPage(received = SyncRules.PULL_PAGE_SIZE))
    }

    @Test
    fun `page size stays under the PostgREST max_rows cap`() {
        // config.toml max_rows = 1000 (both stacks) — a page at or over it would be silently cut.
        assertTrue(SyncRules.PULL_PAGE_SIZE < 1000)
    }

    @Test
    fun `merged pages keep every row once and the last version of a repeated id`() {
        // Pages continue from the previous page's last row (keyset), so a row re-stamped by another
        // device mid-pull sorts to the end and simply REAPPEARS in a later page — it must not be
        // doubled, and its newer version must win.
        val p1 = listOf(Row("a", "s1"), Row("b", "s1"))
        val p2 = listOf(Row("b", "s2"), Row("c", "s2"))
        val merged = SyncRules.mergePages(listOf(p1, p2)) { it.id }
        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
        assertEquals("s2", merged.first { it.id == "b" }.stamp)
    }

    @Test
    fun `the cursor for the next page is the last row of the page just read`() {
        // What fetchTable feeds back as the keyset for page N+1: the exact stamp string + id of the
        // last row, so page N+1 asks for "stamp > s OR (stamp = s AND id > lastId)".
        val page = listOf(Row("a", "2026-09-23T10:00:00.1+00:00"), Row("b", "2026-09-23T10:00:00.2+00:00"))
        val next = SyncRules.Cursor(page.last().stamp!!, page.last().id)
        assertEquals("2026-09-23T10:00:00.2+00:00|b", next.encode())
        assertEquals(next, SyncRules.Cursor.decode(next.encode()))
    }

    @Test
    fun `cursor after a bulk batch sharing one stamp records the greatest id at that stamp`() {
        // A 1,200-row import lands with ONE server stamp. A stamp-only cursor advanced past a capped
        // page lost every sibling; the keyset cursor must name the last id so the rest can follow.
        val stamp = "2026-09-23T10:00:00.123456+00:00"
        val rows = listOf(
            Row("0a", "2026-09-23T09:59:59.000000+00:00"),
            Row("ff", stamp),
            Row("0b", stamp),
            Row("9c", stamp),
        )
        val cursor = SyncRules.nextCursor(rows, { it.stamp }, { it.id })!!
        assertEquals(stamp, cursor.stamp)
        assertEquals("ff", cursor.lastId)
        assertEquals("$stamp|ff", cursor.encode())
    }

    @Test
    fun `cursor picks the newest stamp by instant not by string`() {
        // Postgres trims trailing zeros ("...00.12+00:00" vs "...00.120000+00:00"): compare as instants.
        val rows = listOf(
            Row("x", "2026-09-23T10:00:00.12+00:00"),
            Row("y", "2026-09-23T10:00:00.120000+00:00"),
            Row("z", "2026-09-23T10:00:00.119999+00:00"),
        )
        val cursor = SyncRules.nextCursor(rows, { it.stamp }, { it.id })!!
        assertEquals("y", cursor.lastId) // x and y are the same instant; y has the greater id
    }

    @Test
    fun `no stamped rows means no cursor change`() {
        assertNull(SyncRules.nextCursor(emptyList<Row>(), { it.stamp }, { it.id }))
        assertNull(SyncRules.nextCursor(listOf(Row("a", null)), { it.stamp }, { it.id }))
    }

    @Test
    fun `a legacy stamp-only cursor still decodes and round-trips`() {
        val legacy = SyncRules.Cursor.decode("2026-09-05T00:00:00+00:00")!!
        assertEquals("2026-09-05T00:00:00+00:00", legacy.stamp)
        assertNull(legacy.lastId)
        assertEquals("2026-09-05T00:00:00+00:00", legacy.encode())
        val keyset = SyncRules.Cursor.decode("2026-09-05T00:00:00+00:00|abc")!!
        assertEquals("abc", keyset.lastId)
        assertNull(SyncRules.Cursor.decode(null))
        assertNull(SyncRules.Cursor.decode(""))
    }

    // ---- Timestamps ----

    @Test
    fun `PostgREST offset and Z forms both parse and agree`() {
        val plus = SyncRules.parseIso("2026-09-23T10:00:00.5+00:00")
        val z = SyncRules.parseIso("2026-09-23T10:00:00.5Z")
        assertEquals(plus, z)
        assertTrue(plus > 0L)
        assertEquals(0L, SyncRules.parseIso("not a date"))
        assertNull(SyncRules.parseInstant("not a date"))
    }

    // ---- Cross-device wishlist duplicates ----

    private fun wish(id: String, setId: Long? = null, figNum: String? = null, deleted: Boolean = false) = WishlistEntity(
        id = id, setId = setId, figNum = figNum, itemKind = if (figNum != null) "minifig" else "set",
        setNumber = figNum ?: "10123", name = "n", theme = "t", subtheme = "General",
        releaseYear = 2026, releaseMonth = 1, pieces = 0, minifigs = 0, retailPrice = null,
        status = "AVAILABLE", imageUrl = null, deleted = deleted, updatedAt = 1L, dirty = true,
    )

    @Test
    fun `the other device's row for the same set is tombstoned and the server's survivor kept`() {
        // Phone and tablet both wishlisted set 1234 offline with their own ids. The tablet's row landed
        // first; on the phone's pull it arrives as "keep". The phone's own row must go, or its every
        // push violates the one-live-row-per-item index forever.
        val active = listOf(wish("phone-row", setId = 1234), wish("keep", setId = 1234), wish("other", setId = 9999))
        val dups = SyncRules.wishlistDuplicates(active, keepId = "keep", setId = 1234, figNum = null)
        assertEquals(listOf("phone-row"), dups.map { it.id })
    }

    @Test
    fun `minifig rows are matched by fig_num and set rows never match a fig`() {
        val active = listOf(wish("a", figNum = "sw0001"), wish("b", figNum = "sw0001"), wish("c", setId = 1234))
        assertEquals(listOf("a"), SyncRules.wishlistDuplicates(active, keepId = "b", setId = null, figNum = "sw0001").map { it.id })
        assertTrue(SyncRules.wishlistDuplicates(active, keepId = "c", setId = 1234, figNum = null).isEmpty())
    }

    @Test
    fun `already-deleted rows and the survivor itself are never returned`() {
        val active = listOf(wish("keep", setId = 1), wish("gone", setId = 1, deleted = true))
        assertTrue(SyncRules.wishlistDuplicates(active, keepId = "keep", setId = 1, figNum = null).isEmpty())
    }

    // ---- Retry schedule ----

    @Test
    fun `retry backoff is bounded and increasing`() {
        val d = SyncRules.RETRY_DELAYS_MS
        assertTrue(d.size in 2..5)
        assertEquals(d.sorted(), d)
        assertTrue(d.first() >= 1_000L)
        assertTrue(d.last() <= 60_000L)
    }
}
