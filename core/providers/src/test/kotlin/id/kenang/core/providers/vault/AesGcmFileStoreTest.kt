package id.kenang.core.providers.vault

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AesGcmFileStoreTest {

    private val dir = createTempDirectory("kenang-vault-test").toFile()

    @Test
    fun `roundtrip and delete`() {
        val store = AesGcmFileStore(dir, machineSecret = "test-machine")
        store.put("fal_keys", """[{"label":"Utama","key":"fal_secret_123"}]""")
        assertEquals("""[{"label":"Utama","key":"fal_secret_123"}]""", store.get("fal_keys"))
        store.delete("fal_keys")
        assertNull(store.get("fal_keys"))
    }

    @Test
    fun `different machine secret cannot decrypt`() {
        val store = AesGcmFileStore(dir, machineSecret = "machine-a")
        store.put("secret", "value")
        val other = AesGcmFileStore(dir, machineSecret = "machine-b")
        assertNull(other.get("secret"))
    }

    @Test
    fun `keyvault stores ordered fal key list`() {
        val vault = KeyVault(AesGcmFileStore(dir, machineSecret = "m"))
        vault.saveFalKeys(listOf(FalKey("Utama", "fal_aaa111"), FalKey("Klien B", "fal_bbb222")))
        val keys = vault.falKeys()
        assertEquals(listOf("Utama", "Klien B"), keys.map { it.label })
        assertEquals("fal…a111", keys[0].masked)
    }
}
