package com.senniapp.brickwares.data.repository

import com.senniapp.brickwares.data.local.CollectionCopyEntity
import com.senniapp.brickwares.data.local.SalesEntity
import com.senniapp.brickwares.data.local.WishlistEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backup file must round-trip exactly: what export writes, import reads back field for field,
 * including the awkward content (commas, quotes, newlines, Vietnamese, a BOM from Excel) and the
 * variant identity of shared-number sets.
 */
class CollectionCsvTest {

    private fun copy(
        id: String = "c1", setId: Long? = 1234L, setNumber: String = "10123", notes: String? = null,
        currency: String = "USD", pricePaid: Long = 15_000L, quantity: Int = 2,
    ) = CollectionCopyEntity(
        id = id, setId = setId, figNum = null, itemKind = "set", setNumber = setNumber,
        name = "Tòa nhà cổ điển, \"phiên bản\" đặc biệt", theme = "Icons", subtheme = "Modular",
        releaseYear = 2026, releaseMonth = 3, pieces = 3000, minifigs = 5, retailPrice = 29_999L,
        status = "AVAILABLE", imageUrl = "https://img.brickwares.app/x.png",
        quantity = quantity, condition = "used", pricePaid = pricePaid, currency = currency,
        acquiredOn = "2026-09-01", notes = notes, deleted = false, updatedAt = 1L, dirty = false,
    )

    private fun sale() = SalesEntity(
        id = "s1", setId = null, figNum = "sw0001", itemKind = "minifig", setNumber = "sw0001",
        name = "Luke", theme = "Star Wars", releaseYear = 2020, releaseMonth = 0, imageUrl = null,
        retailPrice = null, quantity = 1, condition = "new", pricePaid = 250_000L, salePrice = 400_000L,
        currency = "VND", soldOn = "2026-09-10", notes = "bán, nhanh", deleted = false, updatedAt = 1L, dirty = false,
    )

    private fun wish() = WishlistEntity(
        id = "w1", setId = 5678L, figNum = null, itemKind = "set", setNumber = "71050",
        name = "CMF", theme = "Collectable Minifigures", subtheme = "Series 30", releaseYear = 2026, releaseMonth = 5,
        pieces = 9, minifigs = 1, retailPrice = 499L, status = "PENDING", imageUrl = null,
        deleted = false, updatedAt = 1L, dirty = false,
    )

    private fun exportAndParse(
        copies: List<CollectionCopyEntity> = emptyList(),
        sales: List<SalesEntity> = emptyList(),
        wishlist: List<WishlistEntity> = emptyList(),
        variantOf: (Long?, String) -> Int? = { _, _ -> null },
    ): CollectionCsv.Parsed = CollectionCsv.parse(CollectionCsv.encode(copies, sales, wishlist, variantOf))

    @Test
    fun `a collection copy round-trips every field`() {
        val c = copy(notes = "Hàng mới, có hộp\nDòng thứ hai \"trích dẫn\", dấu phẩy")
        val parsed = exportAndParse(copies = listOf(c), variantOf = { setId, _ -> if (setId == 1234L) 3 else null })
        assertEquals(CollectionCsv.FORMAT_VERSION, CollectionCsv.versionOf(parsed))
        assertEquals(1, parsed.rows.size)
        val row = parsed.rows.single()
        assertEquals("collection", CollectionCsv.recordType(row))
        assertEquals(c.setNumber, row["set_number"])
        assertEquals(c.name, row["name"])
        assertEquals("set", row["item_kind"])
        assertEquals("1234", row["set_id"])
        assertEquals("3", row["number_variant"])
        assertEquals(c.theme, row["theme"])
        assertEquals(c.subtheme, row["subtheme"])
        assertEquals("2026", row["release_year"])
        assertEquals("3", row["release_month"])
        assertEquals("3000", row["pieces"])
        assertEquals("5", row["minifigs"])
        assertEquals("29999", row["retail_price"])
        assertEquals("AVAILABLE", row["status"])
        assertEquals(c.imageUrl, row["image_url"])
        assertEquals("2", row["quantity"])
        assertEquals("used", row["condition"])
        assertEquals("USD", row["currency"])
        assertEquals("15000", row["price_paid"])
        assertEquals("2026-09-01", row["acquired_on"])
        assertEquals(c.notes, row["notes"]) // commas, quotes and the newline survive the quoting
        assertEquals("", row["sale_price"])
        assertEquals("", row["sold_on"])
    }

    @Test
    fun `sales and wishlist rows are tagged and keep their own fields`() {
        val parsed = exportAndParse(copies = listOf(copy()), sales = listOf(sale()), wishlist = listOf(wish()))
        assertEquals(listOf("collection", "sale", "wishlist"), parsed.rows.map { CollectionCsv.recordType(it) })
        val s = parsed.rows[1]
        assertEquals("minifig", s["item_kind"])
        assertEquals("sw0001", s["fig_num"])
        assertEquals("", s["set_id"])
        assertEquals("VND", s["currency"])
        assertEquals("250000", s["price_paid"])
        assertEquals("400000", s["sale_price"])
        assertEquals("2026-09-10", s["sold_on"])
        assertEquals("bán, nhanh", s["notes"])
        val w = parsed.rows[2]
        assertEquals("71050", w["set_number"])
        assertEquals("5678", w["set_id"])
        assertEquals("PENDING", w["status"])
        assertEquals("", w["quantity"]) // a wishlist row has no copy fields
    }

    @Test
    fun `a legacy row without set_id exports blank identity columns rather than a guessed variant`() {
        val parsed = exportAndParse(copies = listOf(copy(setId = null, setNumber = "71050")))
        val row = parsed.rows.single()
        assertEquals("", row["set_id"])
        assertEquals("", row["number_variant"])
        assertEquals("71050", row["set_number"])
    }

    @Test
    fun `sync and identity fields are never exported`() {
        val parsed = exportAndParse(copies = listOf(copy()))
        for (hidden in listOf("id", "deleted", "updatedAt", "updated_at", "dirty")) {
            assertTrue("column $hidden must not be exported", hidden !in parsed.header)
        }
    }

    @Test
    fun `Excel's UTF-8 BOM is stripped so the version gate still sees format_version`() {
        val csv = CollectionCsv.encode(listOf(copy()), emptyList(), emptyList())
        val parsed = CollectionCsv.parse("﻿$csv")
        assertEquals("format_version", parsed.header.first())
        assertEquals(CollectionCsv.FORMAT_VERSION, CollectionCsv.versionOf(parsed))
    }

    @Test
    fun `CRLF line endings and a missing final newline parse the same as LF`() {
        val lf = CollectionCsv.encode(listOf(copy(), copy(id = "c2")), emptyList(), emptyList())
        val crlf = lf.replace("\n", "\r\n").removeSuffix("\r\n")
        assertEquals(CollectionCsv.parse(lf).rows, CollectionCsv.parse(crlf).rows)
    }

    @Test
    fun `a newer file version is detected and an older or missing one defaults`() {
        val parsed = CollectionCsv.parse("format_version,set_number,item_kind\n99,10123,set\n")
        assertEquals(99, CollectionCsv.versionOf(parsed))
        val v1 = CollectionCsv.parse("set_number,item_kind\n10123,set\n")
        assertEquals(1, CollectionCsv.versionOf(v1))
        assertEquals("collection", CollectionCsv.recordType(v1.rows.single())) // a v1 row is a copy
    }

    @Test
    fun `empty and header-only files parse to no rows without throwing`() {
        assertTrue(CollectionCsv.parse("").rows.isEmpty())
        assertTrue(CollectionCsv.parse("").header.isEmpty())
        val headerOnly = CollectionCsv.parse(CollectionCsv.encode(emptyList(), emptyList(), emptyList()))
        assertTrue(headerOnly.rows.isEmpty())
        assertTrue("set_number" in headerOnly.header && "item_kind" in headerOnly.header)
    }

    @Test
    fun `blank lines and a short row are tolerated`() {
        val parsed = CollectionCsv.parse("set_number,item_kind,notes\n10123,set\n\n\n10124,set,x\n")
        assertEquals(2, parsed.rows.size)
        assertEquals("", parsed.rows[0]["notes"]) // missing trailing column reads blank
        assertEquals("x", parsed.rows[1]["notes"])
    }
}
