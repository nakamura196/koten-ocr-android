package com.nakamura196.kotenocr.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts / decrypts small secrets. Split out so the store logic can be tested on the JVM. */
interface SecretCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(sealed: ByteArray): ByteArray
}

/** Minimal string key-value storage (SharedPreferences in the app, a map in tests). */
interface SecretPrefs {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

/**
 * Stores the translation API key encrypted (iOS keeps it in the Keychain).
 *
 * - The ciphertext lives in its own prefs file ([PREFS_FILE]), which is excluded from
 *   cloud backup and device transfer (res/xml/backup_rules.xml, data_extraction_rules.xml):
 *   the Keystore key never leaves the device, so a restored ciphertext could not be read anyway.
 * - Versions up to 2.1.x kept the key in plain text in the main prefs; [migrateLegacy]
 *   moves it here once and deletes the plain copy.
 * - If decryption fails (key invalidated, data restored from elsewhere), the value is dropped
 *   and the key reads as empty, so the user is asked to enter it again instead of crashing.
 */
class SecretStore(
    private val prefs: SecretPrefs,
    private val cipher: SecretCipher
) {
    fun get(name: String): String {
        val stored = prefs.getString(name) ?: return ""
        return try {
            String(cipher.decrypt(Base64Codec.decode(stored)), Charsets.UTF_8)
        } catch (e: Exception) {
            prefs.remove(name)
            ""
        }
    }

    fun set(name: String, value: String) {
        if (value.isEmpty()) {
            prefs.remove(name)
        } else {
            prefs.putString(name, Base64Codec.encode(cipher.encrypt(value.toByteArray(Charsets.UTF_8))))
        }
    }

    /** Move a plain-text value from the legacy prefs into this store, then delete it there. */
    fun migrateLegacy(name: String, legacy: SecretPrefs, legacyKey: String) {
        val plain = legacy.getString(legacyKey) ?: return
        if (plain.isNotEmpty() && prefs.getString(name) == null) {
            // Keep the plain copy if encryption fails or does not stick, so the key is not lost.
            try {
                set(name, plain)
            } catch (e: Exception) {
                return
            }
            if (get(name) != plain) return
        }
        legacy.remove(legacyKey)
    }

    companion object {
        const val PREFS_FILE = "koten_ocr_secrets"

        fun create(context: Context): SecretStore =
            SecretStore(
                SharedPrefsAdapter(context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)),
                KeystoreCipher()
            )
    }
}

/** android.util.Base64 is a stub on the JVM; keep the codec swappable for tests. */
internal object Base64Codec {
    var encode: (ByteArray) -> String = { Base64.encodeToString(it, Base64.NO_WRAP) }
    var decode: (String) -> ByteArray = { Base64.decode(it, Base64.NO_WRAP) }
}

class SharedPrefsAdapter(private val prefs: SharedPreferences) : SecretPrefs {
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) { prefs.edit().putString(key, value).commit() }
    override fun remove(key: String) { prefs.edit().remove(key).commit() }
}

/** AES-256-GCM with a non-exportable key in the Android Keystore. Output = IV (12 bytes) + ciphertext. */
class KeystoreCipher(private val alias: String = "kotenocr_secret_v1") : SecretCipher {
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    override fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.ENCRYPT_MODE, key())
        return c.iv + c.doFinal(plain)
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        require(sealed.size > IV_LEN) { "sealed value too short" }
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, IV_LEN))
        return c.doFinal(sealed, IV_LEN, sealed.size - IV_LEN)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LEN = 12
    }
}
