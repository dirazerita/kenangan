package id.kenang.core.providers.vault

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.nio.charset.StandardCharsets

/**
 * Windows Credential Manager storage via advapi32 CredWriteW/CredReadW/CredDeleteW.
 * Target names are namespaced "Kenang/<name>".
 *
 * Note: CRED_MAX_CREDENTIAL_BLOB_SIZE is 2560 bytes — plenty for a JSON list
 * of fal keys; [KeyVault] enforces sane sizes before writing.
 */
class WindowsCredentialStore : SecretStore {

    override fun put(name: String, secret: String) {
        val blobBytes = secret.toByteArray(StandardCharsets.UTF_8)
        require(blobBytes.size <= 2560) { "Secret too large for Credential Manager" }
        val blob = Memory(maxOf(blobBytes.size.toLong(), 1L))
        blob.write(0, blobBytes, 0, blobBytes.size)

        val cred = CREDENTIALW().apply {
            Flags = 0
            Type = CRED_TYPE_GENERIC
            TargetName = WString(target(name))
            Comment = WString("Kenang BYOK vault")
            CredentialBlobSize = blobBytes.size
            CredentialBlob = blob
            Persist = CRED_PERSIST_LOCAL_MACHINE
            UserName = WString(System.getProperty("user.name") ?: "kenang")
        }
        cred.write()
        if (!Advapi32Cred.INSTANCE.CredWriteW(cred, 0)) {
            throw IllegalStateException("CredWriteW failed (err=${Native.getLastError()})")
        }
    }

    override fun get(name: String): String? {
        val ref = PointerByReference()
        val ok = Advapi32Cred.INSTANCE.CredReadW(WString(target(name)), CRED_TYPE_GENERIC, 0, ref)
        if (!ok) return null
        val p = ref.value ?: return null
        try {
            val cred = CREDENTIALW(p)
            val size = cred.CredentialBlobSize
            val blob = cred.CredentialBlob ?: return null
            if (size <= 0) return ""
            return String(blob.getByteArray(0, size), StandardCharsets.UTF_8)
        } finally {
            Advapi32Cred.INSTANCE.CredFree(p)
        }
    }

    override fun delete(name: String) {
        Advapi32Cred.INSTANCE.CredDeleteW(WString(target(name)), CRED_TYPE_GENERIC, 0)
    }

    override fun selfTest(): Boolean = runCatching {
        val probe = "__selftest__"
        put(probe, "ok")
        val read = get(probe)
        delete(probe)
        read == "ok"
    }.getOrDefault(false)

    private fun target(name: String) = "Kenang/$name"

    companion object {
        const val CRED_TYPE_GENERIC = 1
        const val CRED_PERSIST_LOCAL_MACHINE = 2
    }

    @Structure.FieldOrder(
        "Flags", "Type", "TargetName", "Comment", "LastWritten",
        "CredentialBlobSize", "CredentialBlob", "Persist",
        "AttributeCount", "Attributes", "TargetAlias", "UserName",
    )
    open class CREDENTIALW : Structure {
        @JvmField var Flags: Int = 0
        @JvmField var Type: Int = 0
        @JvmField var TargetName: WString? = null
        @JvmField var Comment: WString? = null
        @JvmField var LastWritten: WinBase.FILETIME = WinBase.FILETIME()
        @JvmField var CredentialBlobSize: Int = 0
        @JvmField var CredentialBlob: Pointer? = null
        @JvmField var Persist: Int = 0
        @JvmField var AttributeCount: Int = 0
        @JvmField var Attributes: Pointer? = null
        @JvmField var TargetAlias: WString? = null
        @JvmField var UserName: WString? = null

        constructor() : super()
        constructor(p: Pointer) : super(p) { read() }
    }

    interface Advapi32Cred : StdCallLibrary {
        fun CredWriteW(credential: CREDENTIALW, flags: Int): Boolean
        fun CredReadW(targetName: WString, type: Int, flags: Int, credential: PointerByReference): Boolean
        fun CredDeleteW(targetName: WString, type: Int, flags: Int): Boolean
        fun CredFree(cred: Pointer)

        companion object {
            val INSTANCE: Advapi32Cred =
                Native.load("Advapi32", Advapi32Cred::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
    }
}
