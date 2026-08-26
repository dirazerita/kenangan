package id.kenang.core.common.license

/**
 * THE single licensing seam (AD-13, D-002).
 *
 * Licensing is DEFERRED. Every future license touchpoint — activation screen,
 * trial watermark, export counter, update gating — must read ONLY from this
 * interface. Never scatter license checks anywhere else.
 *
 * TODO(D-002): Phase 05 replaces [DevFullLicense] with the real Ed25519-verified
 * implementation (activation, heartbeat, 30-day offline grace, seat management).
 */
interface LicenseGate {
    fun state(): LicenseState
}

sealed class LicenseState {
    /** Development build: full access, no watermark, no export limits. */
    data object DevFull : LicenseState()

    // TODO(D-002): Phase 05 adds Trial / Personal / Studio states here,
    // carrying the cached, signature-verified LicenseState blob (MEMORY §6).

    /** True when outputs must carry the "Kenang Trial" watermark. */
    val watermarkRequired: Boolean
        get() = false // TODO(D-002): true for Trial in Phase 05.

    /** Remaining exports, or null for unlimited. */
    val exportsRemaining: Int?
        get() = null // TODO(D-002): Trial counter in Phase 05 (settings table keys reserved).

    /** Short tag shown in the Home top bar. */
    val displayTag: String
        get() = when (this) {
            is DevFull -> "Dev Build"
        }
}

/** Stub wired through DI for all development builds (D-002). */
class DevFullLicense : LicenseGate {
    override fun state(): LicenseState = LicenseState.DevFull
}
