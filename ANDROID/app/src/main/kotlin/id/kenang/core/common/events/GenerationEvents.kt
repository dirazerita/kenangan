package id.kenang.core.common.events

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Seam between Phase 03 and Phase 04: the storyboard confirm dialog emits
 * [StartGenerationRequest]; the Phase-04 orchestrator subscribes and owns
 * everything after. Until 04 lands, the app shows a stub notice.
 */
data class StartGenerationRequest(
    val projectId: String,
    val tier: String,
)

class GenerationEvents {
    private val _start = MutableSharedFlow<StartGenerationRequest>(extraBufferCapacity = 4)
    val start: SharedFlow<StartGenerationRequest> = _start

    suspend fun requestStart(request: StartGenerationRequest) {
        _start.emit(request)
    }
}
