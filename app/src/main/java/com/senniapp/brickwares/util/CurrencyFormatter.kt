package com.senniapp.brickwares.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * App-wide currency. **USD is the canonical/base currency** (LEGO retail is USD; money is stored in
 * USD cents or the entry's own currency unit). ₫ is a converted display option. A Long amount's unit
 * depends on its currency: USD = integer **cents**, VND = whole **₫**.
 */
enum class AppCurrency(val symbol: String) {
    VND("₫"),
    USD("$"),
}

/**
 * Formats a Long already in [currency]'s own unit — **USD cents** ("$3,485.02") or whole **₫**
 * ("90.608.440₫"). Low-level renderer; most callers use [formatMoney] (USD-canonical amounts) or
 * [formatMoneyFrom] (an amount whose source currency is known).
 */
fun formatIn(amount: Long, currency: AppCurrency): String = when (currency) {
    AppCurrency.VND -> {
        val symbols = DecimalFormatSymbols(Locale.US).apply { groupingSeparator = '.' }
        DecimalFormat("#,###", symbols).format(amount) + currency.symbol
    }
    AppCurrency.USD -> {
        // Sign before the symbol so a loss reads "-$6.92", not "$-6.92".
        val sign = if (amount < 0) "-" else ""
        sign + currency.symbol + DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US)).format(abs(amount) / 100.0)
    }
}

/**
 * Formats [amount] (in currency [from]) for display in currency [to]. Exact when [from] == [to];
 * otherwise converts via USD cents ([CurrencyConverter]). Retail and community-value amounts are
 * USD-canonical, so they use [formatMoney] ([from] = USD); a paid/sale row passes its own recorded
 * currency, so a ₫-typed price stays exactly ₫ and only converts when shown in USD.
 */
fun formatMoneyFrom(amount: Long, from: AppCurrency, to: AppCurrency): String =
    if (from == to) formatIn(amount, to)
    else formatIn(CurrencyConverter.fromUsdCents(CurrencyConverter.usdCentsOf(amount, from), to), to)

/** Formats a **USD-cents** amount (retail, current value, aggregated totals) in the display [currency]. */
fun formatMoney(amountUsdCents: Long, currency: AppCurrency): String =
    formatMoneyFrom(amountUsdCents, AppCurrency.USD, currency)

/**
 * Plain (symbol-less) prefill text for [amount] (in currency [from]) shown in a field of currency [to].
 * Exact when [from] == [to]; else converted. VND is a whole integer; USD shows cents with trailing
 * ".00" trimmed ("80"). Null amount → "".
 */
fun moneyFieldText(amount: Long?, from: AppCurrency, to: AppCurrency): String {
    if (amount == null) return ""
    val v = if (from == to) amount else CurrencyConverter.fromUsdCents(CurrencyConverter.usdCentsOf(amount, from), to)
    return when (to) {
        AppCurrency.VND -> v.toString()
        AppCurrency.USD -> if (v % 100L == 0L) (v / 100L).toString()
            else DecimalFormat("0.00", DecimalFormatSymbols(Locale.US)).format(v / 100.0)
    }
}

/**
 * Retail-price label. [amountUsdCents] is the catalog's canonical USD retail (cents); renders in the
 * display [currency], or "No data" when the catalog has no retail figure at all.
 */
fun formatRetail(amountUsdCents: Long?, currency: AppCurrency): String =
    if (amountUsdCents != null) formatMoney(amountUsdCents, currency) else "No data"

/**
 * Sanitizes raw text from a money input field for [currency]: VND keeps digits only (capped to
 * [maxDigits]); USD allows digits plus a single '.' with at most two decimal places. Kept next to
 * [moneyInputToAmount] so a field's filter and its parse-on-save stay in lockstep.
 */
fun sanitizeMoneyInput(raw: String, currency: AppCurrency, maxDigits: Int = 10): String =
    when (currency) {
        AppCurrency.USD -> {
            val filtered = raw.filter { it.isDigit() || it == '.' }
            val dot = filtered.indexOf('.')
            if (dot < 0) filtered.take(maxDigits)
            else filtered.substring(0, dot).take(maxDigits) + "." +
                filtered.substring(dot + 1).filter { it.isDigit() }.take(2)
        }
        AppCurrency.VND -> raw.filter { it.isDigit() }.take(maxDigits)
    }

/**
 * Parses a money field's text (typed in [currency]) into a Long in **that currency's own unit** —
 * USD → cents ("80.50" → 8050), VND → whole ₫. Stored as-is with its [currency] tag; the original
 * currency is recorded (no conversion at input). Falls back to [fallback] when blank/unparseable.
 */
fun moneyInputToAmount(text: String, currency: AppCurrency, fallback: Long = 0L): Long =
    when (currency) {
        AppCurrency.USD -> text.toDoubleOrNull()?.let { (it * 100.0).roundToLong() } ?: fallback
        AppCurrency.VND -> text.toLongOrNull() ?: fallback
    }

/** Formats a plain integer count with grouping separators, e.g. 28553 -> "28,553". */
fun formatCount(value: Int): String =
    NumberFormat.getNumberInstance(Locale.US).format(value)

/** Formats a number to exactly one decimal place (period separator), e.g. 0.48 -> "0.5", -3.0 -> "-3.0". */
fun oneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)

/** Formats a signed growth percentage, e.g. 9.0 -> "+9%", -3.5 -> "-3.5%". */
fun formatGrowth(percent: Double): String {
    val rounded = if (percent % 1.0 == 0.0) percent.toInt().toString()
    else DecimalFormat("0.#").format(percent)
    val sign = if (percent > 0) "+" else ""
    return "$sign$rounded%"
}
