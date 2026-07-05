package io.bettercommerce.screenmirror.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide capture status, observed by the Sender UI and updated by
 * [ScreenCaptureService]. A simple singleton is sufficient for M1 — the service
 * and the UI live in the same process. Later milestones can move this behind a
 * repository / DI.
 */
object CaptureState {

    sealed interface Status {
        /** Nothing running. */
        data object Idle : Status

        /** Projection granted, encoder starting up. */
        data object Starting : Status

        /** Actively capturing and encoding to [outputPath]. */
        data class Recording(val outputPath: String) : Status

        /** Finished; a playable file was written to [outputPath]. */
        data class Finished(val outputPath: String) : Status

        /** Auto-stopped because the free-tier session limit was reached. */
        data class LimitReached(val message: String) : Status

        /** Something failed. */
        data class Error(val message: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    fun update(status: Status) {
        _status.value = status
    }
}
