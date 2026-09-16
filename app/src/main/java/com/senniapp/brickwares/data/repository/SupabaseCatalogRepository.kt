package com.senniapp.brickwares.data.repository

import com.senniapp.brickwares.data.model.Availability
import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.ItemType
import com.senniapp.brickwares.data.model.Minifig
import com.senniapp.brickwares.data.remote.SupabaseClientProvider
import com.senniapp.brickwares.util.CatalogImages
import com.senniapp.brickwares.util.CurrencyConverter
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Supabase-backed catalog (Decision 16). Every call is a bounded DB query — browse/search over indexed
 * columns and count views, single-row detail fetches, and batch resolves for the user's referenced
 * sets/figs. Nothing is held in memory beyond the FX rates the [init] block warms for price mapping.
 */
class SupabaseCatalogRepository(
    private val client: SupabaseClient,
) : CatalogRepository {

    init {
        // Warm the FX rates in the background (best-effort) so they don't block the catalog load —
        // catalog price mapping falls back to the USD constant until these arrive.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { CurrencyConverter.ensureRatesLoaded() }
        }
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 15_000L

        /** Max keys per `in.(...)` batch query, so a large user collection can't blow the URL length. */
        const val IN_CHUNK = 200

        /**
         * Brickset's placeholder name for an unrevealed/announced-but-unnamed set. Such rows carry no
         * real data yet (no name, image, pieces, or price), so they're filtered out of the catalog —
         * showing them is just scatter in search/browse. They reappear once Brickset names the set.
         */
        const val UNREVEALED_NAME = "{?}"

        /** The `sets` columns the app reads, shared by the full load and the server-side queries. */
        const val SET_COLS =
            "set_id,set_number,number_variant,name,item_type,theme,subtheme,box_image_url,render_url,year,pieces," +
                "minifigs,availability,notes,notes_vi,launch_date,exit_date," +
                "set_prices(region,retail_price,date_first_available,date_last_available)"

        /** The `minifigs` columns + the join to each fig's sets (for themes / set-count), shared by the
         *  full load and the server-side queries. */
        const val MINIFIG_COLS = "fig_num,name,num_parts,image_url,set_minifigs(set_id,sets(theme,subtheme))"
    }

    // ---- Server-side queries (Decision 16). Suspend + throw on failure; callers handle loading/error. ----

    /** Escape the user's text so their `%`/`_` are treated literally in the ILIKE pattern. */
    private fun likePattern(q: String): String =
        "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

    override suspend fun searchSets(query: String, limit: Int): List<CatalogSet> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val pattern = likePattern(q)
        return withTimeout(LOAD_TIMEOUT_MS) {
            client.from("sets").select(Columns.raw(SET_COLS)) {
                filter {
                    neq("name", UNREVEALED_NAME)
                    or {
                        ilike("set_number", pattern)
                        ilike("name", pattern)
                        ilike("theme", pattern)
                    }
                }
                order("year", Order.DESCENDING)
                limit(limit.toLong())
            }.decodeList<SetRow>()
                .map { it.toCatalogSet() }
                .filter { it.name.isNotBlank() && it.name.trim() != UNREVEALED_NAME }
                .distinctBy { it.id }
        }
    }

    override suspend fun themeCounts(): List<ThemeCount> = withTimeout(LOAD_TIMEOUT_MS) {
        client.from("catalog_theme_counts").select().decodeList<ThemeCountRow>()
            .map { ThemeCount(it.theme, it.setCount) }
    }

    override suspend fun subthemeCounts(): List<ThemeSubthemeCount> = withTimeout(LOAD_TIMEOUT_MS) {
        client.from("catalog_subtheme_counts").select().decodeList<SubthemeCountRow>()
            .map { ThemeSubthemeCount(it.theme, it.subtheme, it.setCount) }
    }

    override suspend fun setsInTheme(theme: String): List<CatalogSet> = withTimeout(LOAD_TIMEOUT_MS) {
        client.from("sets").select(Columns.raw(SET_COLS)) {
            filter {
                eq("theme", theme)
                neq("name", UNREVEALED_NAME)
            }
        }.decodeList<SetRow>()
            .map { it.toCatalogSet() }
            .filter { it.name.isNotBlank() && it.name.trim() != UNREVEALED_NAME }
            .distinctBy { it.id }
    }

    override suspend fun fetchSet(catalogKey: String): CatalogSet? = withTimeout(LOAD_TIMEOUT_MS) {
        // catalogKey is CatalogSet.id ("<number>-<variant>") or a bare number. Try the exact
        // number+variant first, then fall back to the number (lowest variant).
        val dash = catalogKey.lastIndexOf('-')
        val variant = if (dash > 0) catalogKey.substring(dash + 1).toIntOrNull() else null
        val number = if (variant != null) catalogKey.substring(0, dash) else catalogKey
        val exact = if (variant != null) {
            client.from("sets").select(Columns.raw(SET_COLS)) {
                filter { eq("set_number", number); eq("number_variant", variant) }
                limit(1)
            }.decodeList<SetRow>().firstOrNull()
        } else {
            null
        }
        val row = exact ?: client.from("sets").select(Columns.raw(SET_COLS)) {
            filter { eq("set_number", number) }
            order("number_variant", Order.ASCENDING)
            limit(1)
        }.decodeList<SetRow>().firstOrNull()
        row?.toCatalogSet()?.takeIf { it.name.isNotBlank() && it.name.trim() != UNREVEALED_NAME }
    }

    override suspend fun fetchSetsByNumbers(numbers: Collection<String>): List<CatalogSet> {
        val keys = numbers.filter { it.isNotBlank() }.distinct()
        if (keys.isEmpty()) return emptyList()
        // Chunked so a large collection can't blow the URL length of the `in.(...)` filter. Lowest
        // variant per number wins (mirrors setByNumber) so a CMF-style multi-variant number is stable.
        return keys.chunked(IN_CHUNK).flatMap { chunk ->
            withTimeout(LOAD_TIMEOUT_MS) {
                client.from("sets").select(Columns.raw(SET_COLS)) {
                    filter {
                        isIn("set_number", chunk)
                        neq("name", UNREVEALED_NAME)
                    }
                    order("number_variant", Order.ASCENDING)
                }.decodeList<SetRow>()
            }
        }
            .map { it.toCatalogSet() }
            .filter { it.name.isNotBlank() && it.name.trim() != UNREVEALED_NAME }
            .groupBy { it.setNumber }
            .mapNotNull { (_, group) -> group.minByOrNull { it.numberVariant } }
    }

    override suspend fun fetchSetsByIds(ids: Collection<Long>): List<CatalogSet> {
        val keys = ids.distinct()
        if (keys.isEmpty()) return emptyList()
        return keys.chunked(IN_CHUNK).flatMap { chunk ->
            withTimeout(LOAD_TIMEOUT_MS) {
                client.from("sets").select(Columns.raw(SET_COLS)) {
                    filter { isIn("set_id", chunk) }
                }.decodeList<SetRow>()
            }
        }
            .map { it.toCatalogSet() }
            .distinctBy { it.setId }
    }

    override suspend fun fetchMinifigsByNums(figNums: Collection<String>): List<Minifig> {
        val keys = figNums.filter { it.isNotBlank() }.distinct()
        if (keys.isEmpty()) return emptyList()
        return keys.chunked(IN_CHUNK).flatMap { chunk ->
            withTimeout(LOAD_TIMEOUT_MS) {
                client.from("minifigs").select(Columns.raw(MINIFIG_COLS)) {
                    filter { isIn("fig_num", chunk) }
                }.decodeList<MinifigRow>()
            }
        }
            .map { it.toMinifig() }
            .distinctBy { it.figNum }
    }

    override suspend fun newSetCandidates(): List<CatalogSet> = withTimeout(LOAD_TIMEOUT_MS) {
        // Start of the previous month — the widest cutoff that still covers pending (future launch)
        // and current/previous-month releases; NewSets narrows to the exact rule over these.
        val cutoff = LocalDate.now().withDayOfMonth(1).minusMonths(1).toString()
        client.from("sets").select(Columns.raw(SET_COLS)) {
            filter {
                gte("launch_date", cutoff)
                neq("name", UNREVEALED_NAME)
            }
        }.decodeList<SetRow>()
            .map { it.toCatalogSet() }
            .filter { it.name.isNotBlank() && it.name.trim() != UNREVEALED_NAME }
            .distinctBy { it.id }
    }

    // ---- Minifig server-side queries (Decision 16) ----

    override suspend fun fetchMinifigsMatching(query: String, limit: Int): List<Minifig> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val pattern = likePattern(q)
        return withTimeout(LOAD_TIMEOUT_MS) {
            client.from("minifigs").select(Columns.raw(MINIFIG_COLS)) {
                filter { or { ilike("fig_num", pattern); ilike("name", pattern) } }
                limit(limit.toLong())
            }.decodeList<MinifigRow>().map { it.toMinifig() }.distinctBy { it.figNum }
        }
    }

    override suspend fun minifigThemeCounts(): List<ThemeCount> = withTimeout(LOAD_TIMEOUT_MS) {
        client.from("catalog_minifig_theme_counts").select().decodeList<MinifigThemeCountRow>()
            .map { ThemeCount(it.theme, it.minifigCount) }
    }

    override suspend fun minifigSubthemeCounts(): List<ThemeSubthemeCount> = withTimeout(LOAD_TIMEOUT_MS) {
        client.from("catalog_minifig_subtheme_counts").select().decodeList<MinifigSubthemeCountRow>()
            .map { ThemeSubthemeCount(it.theme, it.subtheme, it.minifigCount) }
    }

    override suspend fun minifigsInTheme(theme: String): List<Minifig> = withTimeout(LOAD_TIMEOUT_MS) {
        // !inner so only figs that appear in a set of this theme come back; the embedded sets are also
        // filtered to this theme, which is what the in-theme browse wants.
        client.from("minifigs").select(
            Columns.raw("fig_num,name,num_parts,image_url,set_minifigs!inner(set_id,sets!inner(theme,subtheme))"),
        ) {
            filter { eq("set_minifigs.sets.theme", theme) }
        }.decodeList<MinifigRow>().map { it.toMinifig() }.distinctBy { it.figNum }
    }

    override suspend fun fetchMinifig(figNum: String): Minifig? = withTimeout(LOAD_TIMEOUT_MS) {
        client.from("minifigs").select(Columns.raw(MINIFIG_COLS)) {
            filter { eq("fig_num", figNum) }
            limit(1)
        }.decodeList<MinifigRow>().firstOrNull()?.toMinifig()
    }

    override suspend fun fetchSetsForMinifig(figNum: String): List<CatalogSet> = withTimeout(LOAD_TIMEOUT_MS) {
        client.from("set_minifigs").select(Columns.raw("sets($SET_COLS)")) {
            filter { eq("fig_num", figNum) }
        }.decodeList<SetWrapperRow>()
            .mapNotNull { it.sets?.toCatalogSet() }
            .filter { it.name.isNotBlank() && it.name.trim() != UNREVEALED_NAME }
            .distinctBy { it.id }
            .sortedByDescending { it.releaseYear }
    }

    override suspend fun fetchMinifigsForSet(setId: Long): List<Minifig> = withTimeout(LOAD_TIMEOUT_MS) {
        // The grid needs only fig identity/image, so skip the theme join (avoids recursive embedding).
        client.from("set_minifigs").select(Columns.raw("minifigs(fig_num,name,num_parts,image_url)")) {
            filter { eq("set_id", setId) }
        }.decodeList<MinifigWrapperRow>()
            .mapNotNull { it.minifigs?.toMinifig() }
            .distinctBy { it.figNum }
            .sortedBy { it.figNum }
    }

    /** Row shape for the `catalog_theme_counts` view (theme + set count). */
    @Serializable
    private data class ThemeCountRow(val theme: String, @SerialName("set_count") val setCount: Int)

    /** Row shape for the `catalog_subtheme_counts` view (theme + subtheme + set count). */
    @Serializable
    private data class SubthemeCountRow(
        val theme: String,
        val subtheme: String,
        @SerialName("set_count") val setCount: Int,
    )

    /** Row shape for the `catalog_minifig_theme_counts` view. */
    @Serializable
    private data class MinifigThemeCountRow(val theme: String, @SerialName("minifig_count") val minifigCount: Int)

    /** Row shape for the `catalog_minifig_subtheme_counts` view. */
    @Serializable
    private data class MinifigSubthemeCountRow(
        val theme: String,
        val subtheme: String,
        @SerialName("minifig_count") val minifigCount: Int,
    )

    /** Wrapper for a `set_minifigs -> sets(...)` embedded row (sets a minifig appears in). */
    @Serializable
    private data class SetWrapperRow(val sets: SetRow? = null)

    /** Wrapper for a `set_minifigs -> minifigs(...)` embedded row (figs in a set). */
    @Serializable
    private data class MinifigWrapperRow(val minifigs: MinifigRow? = null)

    /** Row shape for the `sets` table columns we read (unknown columns are ignored by the decoder). */
    @Serializable
    private data class SetRow(
        @SerialName("set_id") val setId: Long? = null,
        @SerialName("set_number") val setNumber: String,
        @SerialName("number_variant") val numberVariant: Int? = null,
        val name: String? = null,
        @SerialName("item_type") val itemType: String? = null,
        /** Box packaging image re-hosted in our Storage at ingest (reliable); null when none captured. */
        @SerialName("box_image_url") val boxImageUrl: String? = null,
        /**
         * Authoritative Rebrickable render URL captured at ingest for shared-number (multi-variant)
         * sets, where reconstructing the image from number+variant can resolve to the WRONG set (CMF /
         * comic-con exclusives). When present it overrides the reconstruction; null → reconstruct.
         */
        @SerialName("render_url") val renderUrl: String? = null,
        val theme: String? = null,
        val subtheme: String? = null,
        val year: Int? = null,
        val pieces: Int? = null,
        val minifigs: Int? = null,
        /** Brickset sales channel: "Retail", "LEGO exclusive", "Retail - limited", GWP, etc. */
        val availability: String? = null,
        val notes: String? = null,
        @SerialName("notes_vi") val notesVi: String? = null,
        /** Brickset set-level "Launch"/"Exit" dates — the canonical release + retirement dates. */
        @SerialName("launch_date") val launchDate: String? = null,
        @SerialName("exit_date") val exitDate: String? = null,
        @SerialName("set_prices") val prices: List<PriceRow> = emptyList(),
    ) {
        /**
         * Retail in **USD cents** (the canonical base — LEGO retail is USD). Prefer the US price
         * (stored exact, × 100, no FX); if a set has no US price, fall back to any other region's price
         * and cross-convert to USD. Null if no price exists (or the fallback region's rate isn't loaded).
         */
        private fun retailUsdCents(): Long? {
            val chosen = prices.firstOrNull { it.region == "US" && it.retailPrice != null }
                ?: prices.firstOrNull { it.retailPrice != null }
                ?: return null
            val currency = CurrencyConverter.currencyForRegion(chosen.region) ?: return null
            return CurrencyConverter.usdCentsFromRegion(chosen.retailPrice!!, currency)
        }

        private fun parseDate(s: String?): LocalDate? =
            s?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        /**
         * The set's release date: prefer the Brickset set-level `launch_date` (the official launch),
         * falling back to the earliest LEGO.com `date_first_available`. These differ when LEGO.com
         * availability starts before the official launch (VIP early access) — the launch is correct.
         */
        private fun releaseDate(): LocalDate? =
            parseDate(launchDate) ?: prices
                .mapNotNull { parseDate(it.dateFirstAvailable) }
                .minOrNull()

        /**
         * The set's retirement date: prefer the Brickset set-level `exit_date`, falling back to the
         * latest LEGO.com `date_last_available`. Null = still available. (Arch Decision: only mark
         * RETIRED when the exit date is actually in the past — no forward estimate.)
         */
        private fun retirementDate(): LocalDate? =
            parseDate(exitDate) ?: prices
                .mapNotNull { parseDate(it.dateLastAvailable) }
                .maxOrNull()

        private fun deriveStatus(): Availability {
            // Not yet released (launch date in the future) → Pending Release, ahead of everything else
            // (a set that isn't out yet is neither available nor retired).
            releaseDate()?.let { if (it.isAfter(LocalDate.now())) return Availability.PENDING }
            // Promotional items + magazine gifts are never sold at retail and Brickset gives them no
            // exit date, so they keep their own badge and are NEVER marked RETIRED (checked first).
            when {
                availability.equals("Promotional", ignoreCase = true) -> return Availability.PROMO
                availability.equals("Magazine gift", ignoreCase = true) -> return Availability.MAGAZINE
            }
            val retire = retirementDate()
            if (retire != null && retire.isBefore(LocalDate.now())) return Availability.RETIRED
            // A legacy set with NO LEGO.com date data at all — no launch/date-first-available AND no
            // exit/date-last-available (e.g. 4002 Riptide Racer, 1996: empty LEGOCom, no exitDate) —
            // can't be dated, so its raw availability ("Retail") would wrongly read AVAILABLE forever.
            // Fall back to the release year: a set from a past year with no date tracking is retired.
            // Modern sets keep their LEGO.com dates, so this only catches old catalog entries.
            if (releaseDate() == null && (year ?: 0) in 1 until LocalDate.now().year) return Availability.RETIRED
            return when {
                availability.equals("LEGO exclusive", ignoreCase = true) -> Availability.EXCLUSIVE
                availability.equals("LEGO Gift with Purchase", ignoreCase = true) -> Availability.GWP
                else -> Availability.AVAILABLE
            }
        }

        fun toCatalogSet(): CatalogSet = CatalogSet(
            setNumber = setNumber,
            name = name ?: "",
            itemType = if (itemType == "minifig") ItemType.MINIFIG else ItemType.SET,
            theme = theme ?: "",
            releaseYear = releaseDate()?.year ?: year ?: 0,
            // Month from the launch date (0 = unknown, e.g. a set with only a year on Brickset).
            releaseMonth = releaseDate()?.monthValue ?: 0,
            pieces = pieces ?: 0,
            minifigs = minifigs ?: 0,
            // Retail as USD cents (US price exact; other regions cross-converted); null if none.
            retailPrice = retailUsdCents(),
            status = deriveStatus(),
            // Retirement date shown on the detail page — only when the exit date is actually in the past.
            retiredYear = retirementDate()?.takeIf { it.isBefore(LocalDate.now()) }?.year ?: 0,
            retiredMonth = retirementDate()?.takeIf { it.isBefore(LocalDate.now()) }?.monthValue ?: 0,
            subtheme = subtheme ?: "General",
            // Brickset's image host is Cloudflare-blocked for non-browser clients, so the render + thumb
            // come from Rebrickable's CDN (addressed by set number + variant). The box shot is the
            // re-hosted `box_image_url` (our Storage, reliable) — null until the ingest captures it, in
            // which case the app shows the Rebrickable render instead.
            // Prefer the ingest-captured authoritative render (correct even for misindexed multi-variant
            // sets); otherwise reconstruct from number+variant (reliable for single-variant sets).
            imageUrl = renderUrl?.takeIf { it.isNotBlank() } ?: CatalogImages.renderUrl(setNumber, numberVariant ?: 1),
            boxImageUrl = boxImageUrl?.takeIf { it.isNotBlank() },
            // Small server-resized render for list cards: the box shot stays the preferred display
            // image, but a boxless set now falls back to this (~10–150 KB) instead of the full
            // multi-MB render — the main cause of slow-loading search thumbnails. Derived from the
            // authoritative render when we have one, so the card and the reconstruction never disagree.
            thumbnailUrl = renderUrl?.takeIf { it.isNotBlank() }?.let { CatalogImages.thumbFromRender(it) }
                ?: CatalogImages.thumbUrl(setNumber, numberVariant ?: 1),
            numberVariant = numberVariant ?: 1,
            notes = notes?.takeIf { it.isNotBlank() },
            notesVi = notesVi?.takeIf { it.isNotBlank() },
            setId = setId,
        )
    }

    /** Embedded row from `set_prices` (one per region) for the parent set. */
    @Serializable
    private data class PriceRow(
        val region: String? = null,
        @SerialName("retail_price") val retailPrice: Double? = null,
        /** LEGO.com first-available date (YYYY-MM-DD) for this region; source of the release month. */
        @SerialName("date_first_available") val dateFirstAvailable: String? = null,
        /** LEGO.com exit date (YYYY-MM-DD) for this region; past = retired. Null while still sold. */
        @SerialName("date_last_available") val dateLastAvailable: String? = null,
    )

    /** Row shape for `minifigs` + the embedded `set_minifigs → sets` join (for set-count + themes). */
    @Serializable
    private data class MinifigRow(
        @SerialName("fig_num") val figNum: String,
        val name: String? = null,
        @SerialName("num_parts") val numParts: Int? = null,
        @SerialName("image_url") val imageUrl: String? = null,
        @SerialName("set_minifigs") val setMinifigs: List<SetMinifigRow> = emptyList(),
    ) {
        fun toMinifig(): Minifig {
            val pairs = setMinifigs.mapNotNull { sm ->
                sm.sets?.theme?.takeIf(String::isNotBlank)?.let { it to (sm.sets.subtheme?.takeIf(String::isNotBlank) ?: "General") }
            }.distinct()
            return Minifig(
                figNum = figNum, name = name ?: figNum, imageUrl = imageUrl,
                numParts = numParts ?: 0, setCount = setMinifigs.size, themeSubthemes = pairs,
                setIds = setMinifigs.mapNotNull { it.setId }.distinct(),
            )
        }

        @Serializable
        data class SetMinifigRow(
            @SerialName("set_id") val setId: Long? = null,
            val sets: SetThemeRow? = null,
        )

        @Serializable
        data class SetThemeRow(val theme: String? = null, val subtheme: String? = null)
    }
}

/** App-wide singleton so every ViewModel shares one cached catalog (one network load). */
object CatalogRepositoryProvider {
    val instance: CatalogRepository by lazy {
        SupabaseCatalogRepository(SupabaseClientProvider.client)
    }
}
