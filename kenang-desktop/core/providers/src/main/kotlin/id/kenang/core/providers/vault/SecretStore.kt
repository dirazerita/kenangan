package id.kenang.core.providers.vault

/**
 * Low-level named-secret storage. Two implementations (AD-11):
 *  - [WindowsCredentialStore] — Windows Credential Manager via JNA (primary)
 *  - [AesGcmFileStore] — encrypted file fallback (per-machine derived key)
 *
 * Secrets are NEVER logged and NEVER synced anywhere.
 */
interface SecretStore {
    fun put(name: String, secret: String)
    fun get(name: String): String?
    fun delete(name: String)

    /** True if this store works on this machine (used to pick primary vs fallback). */
    fun selfTest(): Boolean
}
