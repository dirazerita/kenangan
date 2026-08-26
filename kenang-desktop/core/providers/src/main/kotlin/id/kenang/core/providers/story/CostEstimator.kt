package id.kenang.core.providers.story

import id.kenang.core.data.config.ConfigRepository
import id.kenang.core.db.Scene
import id.kenang.core.providers.PriceBook

/**
 * Live storyboard estimate (MASTER_PROMPT_03):
 *   estimate_usd = Σ scenes (duration_s × price_per_second[tier i2v])
 *                + Σ regen_count × price_per_image[tier keyframe]
 * ALL rates from PriceBook/config — never hardcoded. Always labeled
 * "Estimasi — tagihan riil ada di akun provider Anda".
 */
data class StoryboardEstimate(
    val usd: Double,
    val idr: Double,
    val i2vUsd: Double,
    val keyframeUsd: Double,
    val totalDurationS: Long,
    val sceneCount: Int,
    /** False when a routed model has no price hint — UI must show "biaya tidak diketahui". */
    val complete: Boolean,
)

class CostEstimator(
    private val configRepository: ConfigRepository,
    private val priceBook: PriceBook,
) {
    fun estimate(scenes: List<Scene>, tier: String): StoryboardEstimate {
        val config = configRepository.current()
        val routed = config.tierRouting.resolve(tier)

        var complete = true
        val perSecond = priceBook.estimate(routed.i2v, 1.0)?.usd ?: run { complete = false; 0.0 }
        val perImage = priceBook.estimate(routed.keyframe, 1.0)?.usd ?: run { complete = false; 0.0 }

        // Formula per MASTER_PROMPT_03 §Cost estimator: upcoming I2V spend plus
        // keyframe REGENS only (first keyframes are already-spent, tracked by CostTracker).
        val totalDuration = scenes.sumOf { it.duration_s }
        val totalRegens = scenes.sumOf { it.regen_count }
        val i2vUsd = totalDuration * perSecond
        val keyframeUsd = totalRegens * perImage
        val usd = i2vUsd + keyframeUsd
        return StoryboardEstimate(
            usd = usd,
            idr = usd * config.fxIdr,
            i2vUsd = i2vUsd,
            keyframeUsd = keyframeUsd,
            totalDurationS = totalDuration,
            sceneCount = scenes.size,
            complete = complete,
        )
    }
}
