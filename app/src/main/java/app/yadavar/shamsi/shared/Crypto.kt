package app.yadavar.shamsi.shared

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption of shared reminders: AES-256-GCM with a key made on the phone that created the group.
 * The key travels only inside the invite text (never to the server) and is stored on the phone wrapped by an
 * Android Keystore key, so it is not kept as plain text.
 */
object Crypto {
    private const val WRAP_ALIAS = "yadavar_shared_wrap"
    private val rng = SecureRandom()

    fun newGroupKey(): ByteArray = ByteArray(32).also { rng.nextBytes(it) }

    fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    fun unb64(text: String): ByteArray = Base64.decode(text, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    /** Encrypts [plain] with a group key. Output: base64url(iv + ciphertext + tag). */
    fun encrypt(key: ByteArray, plain: String): String {
        val iv = ByteArray(12).also { rng.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return b64(iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    /** Returns null when the text was not produced with this key (wrong key or tampered data). */
    fun decrypt(key: ByteArray, text: String): String? = runCatching {
        val all = unb64(text)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, all.copyOfRange(0, 12)))
        String(cipher.doFinal(all, 12, all.size - 12), Charsets.UTF_8)
    }.getOrNull()

    private fun wrapKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(WRAP_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(WRAP_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    /** Wraps a group key for storage on this phone. */
    fun protect(key: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrapKey())
        return b64(cipher.iv + cipher.doFinal(key))
    }

    fun unprotect(text: String): ByteArray? = runCatching {
        val all = unb64(text)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrapKey(), GCMParameterSpec(128, all.copyOfRange(0, 12)))
        cipher.doFinal(all, 12, all.size - 12)
    }.getOrNull()
}
