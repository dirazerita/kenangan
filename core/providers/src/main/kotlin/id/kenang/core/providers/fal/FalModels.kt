package id.kenang.core.providers.fal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** fal queue submit response. */
@Serializable
data class FalSubmitResponse(
    @SerialName("request_id") val requestId: String,
    @SerialName("response_url") val responseUrl: String? = null,
    @SerialName("status_url") val statusUrl: String? = null,
    @SerialName("cancel_url") val cancelUrl: String? = null,
)

/** fal queue status response. */
@Serializable
data class FalStatusResponse(
    val status: String, // IN_QUEUE | IN_PROGRESS | COMPLETED
    @SerialName("queue_position") val queuePosition: Int? = null,
    @SerialName("response_url") val responseUrl: String? = null,
)

/** A successfully submitted queue job; [keyLabel] is pinned for all polling (AD-14). */
data class SubmittedFalJob(
    val requestId: String,
    val modelSlug: String,
    val keyLabel: String,
    /** Queue URLs from the submit response; when absent (resumed jobs) they are derived. */
    val statusUrl: String? = null,
    val responseUrl: String? = null,
)

/** Terminal result payload — raw JSON, model-specific parsing happens in Phases 03/04. */
data class FalJobResult(val payload: JsonObject)
