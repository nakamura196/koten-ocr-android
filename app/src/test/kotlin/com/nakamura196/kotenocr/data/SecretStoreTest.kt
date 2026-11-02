package com.nakamura196.kotenocr.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64

class SecretStoreTest {
    private class MapPrefs : SecretPrefs {
        val map = mutableMapOf<String, String>()
        override fun getString(key: String) = map[key]
        override fun putString(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
    }

    /** Reversible stand-in for the Keystore cipher; [broken] makes decryption fail. */
    private class XorCipher : SecretCipher {
        var broken = false
        var encryptBroken = false
        override fun encrypt(plain: ByteArray): ByteArray {
            if (encryptBroken) throw IllegalStateException("keystore unavailable")
            return plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        }
        override fun decrypt(sealed: ByteArray): ByteArray {
            if (broken) throw IllegalStateException("key invalidated")
            return sealed.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        }
    }

    private lateinit var prefs: MapPrefs
    private lateinit var cipher: XorCipher
    private lateinit var store: SecretStore

    @Before
    fun setUp() {
        Base64Codec.encode = { Base64.getEncoder().encodeToString(it) }
        Base64Codec.decode = { Base64.getDecoder().decode(it) }
        prefs = MapPrefs(); cipher = XorCipher(); store = SecretStore(prefs, cipher)
    }

    @Test fun roundTripAndNotStoredInPlain() {
        store.set("k", "sk-test-123")
        assertEquals("sk-test-123", store.get("k"))
        assertFalse(prefs.map.getValue("k").contains("sk-test"))
    }

    @Test fun emptyRemoves() {
        store.set("k", "abc"); store.set("k", "")
        assertNull(prefs.map["k"]); assertEquals("", store.get("k"))
    }

    @Test fun missingReadsEmpty() = assertEquals("", store.get("k"))

    @Test fun undecryptableIsDroppedNotThrown() {
        store.set("k", "abc"); cipher.broken = true
        assertEquals("", store.get("k"))
        assertNull(prefs.map["k"])
    }

    @Test fun migratesPlainLegacyValueAndDeletesIt() {
        val legacy = MapPrefs().apply { map["translation_api_key"] = "sk-old" }
        store.migrateLegacy("translation_api_key", legacy, "translation_api_key")
        assertEquals("sk-old", store.get("translation_api_key"))
        assertTrue(legacy.map.isEmpty())
    }

    @Test fun migrationKeepsPlainCopyIfEncryptionFails() {
        val legacy = MapPrefs().apply { map["k"] = "sk-old" }
        cipher.broken = true
        store.migrateLegacy("k", legacy, "k")
        assertEquals("sk-old", legacy.map["k"])
    }

    @Test fun migrationDoesNotOverwriteExistingEncryptedValue() {
        store.set("k", "sk-new")
        val legacy = MapPrefs().apply { map["k"] = "sk-old" }
        store.migrateLegacy("k", legacy, "k")
        assertEquals("sk-new", store.get("k")); assertTrue(legacy.map.isEmpty())
    }

    @Test fun migrationKeepsPlainCopyIfEncryptThrows() {
        val legacy = MapPrefs().apply { map["k"] = "sk-old" }
        cipher.encryptBroken = true
        store.migrateLegacy("k", legacy, "k")
        assertEquals("sk-old", legacy.map["k"]); assertNull(prefs.map["k"])
    }
}
