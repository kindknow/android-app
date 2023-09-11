package one.mixin.android.ui.wallet.fiatmoney

object AmountUtil {
    private val full_currency = listOf(
        "JPY",
        "KRW",
    )

    fun fullCurrency(currency: String): Boolean {
        return full_currency.contains(currency)
    }

    fun toAmount(value: String, currency: String): Long? {
        val v = value.toFloatOrNull() ?: return null
        if (currency in full_currency) {
            return v.toLong()
        } else {
            return (v * 100).toLong()
        }
    }

    fun realAmount(value: Long, currency: String): String {
        return if (currency in full_currency) {
            value.toString()
        } else {
            (value / 100f).toString()
        }
    }

    fun illegal(text: String, currency: String): Boolean {
        return if (full_currency.contains(currency)) {
            !text.matches(Regex("^\\d{1,9}\$"))
        } else {
            return !text.matches(Regex("^\\d{1,7}(\\.\\d{0,2})?\$"))
        }
    }
}