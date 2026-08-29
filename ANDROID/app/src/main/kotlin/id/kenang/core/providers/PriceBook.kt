package id.kenang.core.providers

import id.kenang.core.data.config.ConfigRepository

/** An estimate — ALWAYS shown with the "estimasi" label, never exact billing (MEMORY §5). */
data class PriceEstimate(
    val modelSlug: String,
    val unit: String,
    val qty: Double,
    val usd: Double,
    val idr: Double,
)

/**
 * Client-side rate table (AD-10): rates come from config price_hints, never
 * from code. Unknown slugs return null — callers must handle it and show
 * "biaya tidak diketahui" rather than 0.
 */
class PriceBook(private val configRepository: ConfigRepository) {

    fun estimate(modelSlug: String, qty: Double): PriceEstimate? {
        val config = configRepository.current()
        val hint = config.priceHints.firstOrNull { it.modelSlug == modelSlug } ?: return null
        val usd = when (hint.unit) {
            "per_1k_chars" -> hint.usd * (qty / 1000.0)
            else -> hint.usd * qty // per_second, per_image: qty is seconds/images
        }
        return PriceEstimate(modelSlug, hint.unit, qty, usd, usd * config.fxIdr)
    }

    fun fxIdr(): Double = configRepository.current().fxIdr
}
