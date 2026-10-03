package com.senniapp.brickwares.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which theme a minifig is filed under when added — the page it was added from, not the join's first row. */
class MinifigTest {

    private val fig = Minifig(
        figNum = "fig-000001", name = "Shared Fig",
        themeSubthemes = listOf("Star Wars" to "Episode IV", "City" to "Police", "Star Wars" to "Episode V"),
    )

    @Test
    fun `added from a theme page it is filed under that theme`() {
        assertEquals("City", fig.themeWhenBrowsing("City"))
        assertEquals("Star Wars", fig.themeWhenBrowsing("Star Wars"))
    }

    @Test
    fun `with no theme page open it falls back to its first theme`() {
        assertEquals("Star Wars", fig.themeWhenBrowsing(null))
    }

    @Test
    fun `a theme the fig is not in is ignored`() {
        // e.g. a stale open-theme from another page: never file a fig under a theme it doesn't belong to.
        assertEquals("Star Wars", fig.themeWhenBrowsing("Ninjago"))
    }

    @Test
    fun `a fig with no themes records a blank theme`() {
        assertEquals("", Minifig(figNum = "fig-000002", name = "Loose").themeWhenBrowsing("City"))
        assertEquals("", Minifig(figNum = "fig-000002", name = "Loose").themeWhenBrowsing(null))
    }
}
