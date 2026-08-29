package id.kenang.core.providers.vault

import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * BYOK secret store: AES-256-GCM encrypted files under [dir], one file per
 * secret. On Android [dir] is inside the app's private storage and
 * [machineSecret] is device-derived (see KenangApp), so a copied vault file
 * does not decrypt on another device (AD-11).
 *
 * File layout: [16-byte salt][12-byte IV][ciphertext+tag].
 */
class AesGcmFileStore(
    private val dir: File,
    /** Device-derived identity string; supplied by the DI module. */
    private val machineSecret: String,
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
        // 120k PBKDF2 rounds is imperceptible on desktop but adds ~0.5s per
        // vault read on a low-end phone; the vault is read on nearly every
        // provider call, so Android uses a lower (still sane) round count.
        val spec = PBEKeySpec(machineSecret.toCharArray(), salt, 20_000, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
        return SecretKeySpec(key.encoded, "AES")
    }
}
