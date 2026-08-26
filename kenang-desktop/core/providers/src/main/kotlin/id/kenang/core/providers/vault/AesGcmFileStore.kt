package id.kenang.core.providers.vault

import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Fallback secret store: AES-256-GCM encrypted files under [dir], one file per
 * secret. The key is derived per-machine (machine identity + user name) via
 * PBKDF2 — moving the files to another machine will not decrypt them (AD-11).
 *
 * File layout: [16-byte salt][12-byte IV][ciphertext+tag].
 */
class AesGcmFileStore(
    private val dir: File,
    /** Override for tests; defaults to a per-machine identity string. */
    private val machineSecret: String = defaultMachineSecret(),
) : SecretStore {

    private val random = SecureRandom()

    override fun put(name: String, secret: String) {
        dir.mkdirs()
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(salt), GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        fileFor(name).writeBytes(salt + iv + ct)
    }

    override fun get(name: String): String? {
        val f = fileFor(name)
        if (!f.isFile) return null
        return runCatching {
            val bytes = f.readBytes()
            require(bytes.size > 28) { "corrupt vault file" }
            val salt = bytes.copyOfRange(0, 16)
            val iv = bytes.copyOfRange(16, 28)
            val ct = bytes.copyOfRange(28, bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(salt), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        }.getOrNull()
    }

    override fun delete(name: String) {
        fileFor(name).delete()
    }

    override fun selfTest(): Boolean = runCatching {
        put("__selftest__", "ok")
        val v = get("__selftest__")
        delete("__selftest__")
        v == "ok"
    }.getOrDefault(false)

    private fun fileFor(name: String): File =
        File(dir, name.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".bin")

    private fun deriveKey(salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(machineSecret.toCharArray(), salt, 120_000, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
        return SecretKeySpec(key.encoded, "AES")
    }

    companion object {
        private fun defaultMachineSecret(): String {
            val machineGuid = runCatching {
                com.sun.jna.platform.win32.Advapi32Util.registryGetStringValue(
                    com.sun.jna.platform.win32.WinReg.HKEY_LOCAL_MACHINE,
                    "SOFTWARE\\Microsoft\\Cryptography",
                    "MachineGuid",
                )
            }.getOrElse { System.getenv("COMPUTERNAME") ?: "kenang-machine" }
            val user = System.getProperty("user.name") ?: "kenang-user"
            return "kenang|$machineGuid|$user"
        }
    }
}
