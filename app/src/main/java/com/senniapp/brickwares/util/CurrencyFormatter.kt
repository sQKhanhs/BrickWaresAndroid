package com.senniapp.brickwares.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale

/** App-wide display currency. VND is the default per the architecture (Vietnam-first). */
enum class AppCurrency(val symbol: String) {
    VND("₫"),
    USD("$"),
}

/**
 * Formats a money amount for display.
 *
 * NOTE (mock stage): amounts are treated as already being in [currency]'s major unit —
 * there is no FX conversion yet. VND renders with '.' thousands separators and a *suffixed*
 * symbol ("90.608.440₫"); USD renders with ',' separators and a prefixed "$".
 */
fun formatMoney(amount: Long, currency: AppCurrency): String = when (currency) {
    AppCurrency.VND -> {
        val symbols = DecimalFormatSymbols(Locale.US).apply { groupingSeparator = '.' }
        DecimalFormat("#,###", symbols).format(amount) + currency.symbol
    }
    AppCurrency.USD -> currency.symbol + NumberFormat.getNumberInstance(Locale.US).format(amount)
}

/**
 * Retail-price label. A null amount means no retail is available for the selected currency:
 * VND (which converts from any region) shows a generic message; other currencies name themselves
 * (e.g. "No retail price for USD") since they show only that region's native price.
 */
fun formatRetail(amount: Long?, currency: AppCurrency): String = when {
    amount != null -> formatMoney(amount, currency)
    currency == AppCurrency.VND -> "No retail price"
    else -> "No retail price for ${currency.name}"
}

/** Formats a plain integer count with grouping separators, e.g. 28553 -> "28,553". */
fun formatCount(value: Int): String =
    NumberFormat.getNumberInstance(Locale.US).format(value)

/** Formats a signed growth percentage, e.g. 9.0 -> "+9%", -3.5 -> "-3.5%". */
fun formatGrowth(percent: Double): String {
    val rounded = if (percent % 1.0 == 0.0) percent.toInt().toString()
    else DecimalFormat("0.#").format(percent)
    val sign = if (percent > 0) "+" else ""
    return "$sign$rounded%"
}
