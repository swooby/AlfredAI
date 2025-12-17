package com.swooby.alfredai

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object Crypto {
    /**
     * The standard cryptographic transformation string for AES in Galois/Counter Mode (GCM).
     *
     * "AES" specifies the algorithm (Advanced Encryption Standard).
     * "GCM" specifies the mode of operation (Galois/Counter Mode), which provides
     * both confidentiality and authenticity (Authenticated Encryption with Associated Data - AEAD).
     * "NoPadding" is required for GCM mode; GCM is a stream cipher mode and does not use padding.
     */
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"

    /**
     * The standard authentication tag length for AES-GCM in bits.
     *
     * Per <a href="https://developer.android.com/reference/javax/crypto/spec/GCMParameterSpec">Android GCMParameterSpec reference</a>,
     * <a href="http://csrc.nist.gov/publications/nistpubs/800-38D/SP-800-38D.pdf">NIST Special Publication 800-38D</>
     * > "states that tLen may only have the values {128, 120, 112, 104, 96}, or {64, 32} for certain applications."
     * A full 128-bit tag length provides the strongest security guarantee against forgery attacks
     * and is the recommended default for most applications.
     *
     * @see <a href="https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-38d.pdf">NIST SP 800-38D (PDF)</a>
     */
    private const val AES_GCM_TAG_BITS = 128

    /**
     * The length of the initialization vector (IV) in bytes for GCM mode.
     * The value 12 (96 bits) is the standard recommended IV length per NIST Special Publication 800-38D,
     * Recommendation for Block Cipher Modes of Operation: Galois/Counter Mode (GCM).
     *
     * This length allows for the most efficient internal processing of the IV
     * by avoiding an extra GHASH operation, which improves performance and
     * ensures interoperability across different cryptographic libraries.
     *
     * @see <a href="https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-38d.pdf">NIST SP 800-38D (PDF)</a>
     */
    private const val GCM_IV_LENGTH_BYTES = 12

    private fun getCipherInstance(): Cipher {
        return Cipher.getInstance(AES_GCM_TRANSFORMATION)
    }

    /**
     * The standard provider name for the Android Keystore system.
     * On modern devices (API 23+, and especially API 28+), this implementation
     * is typically backed by secure hardware (like a TEE or StrongBox chip)
     * which prevents key material from ever leaving the device in plaintext.
     * If secure hardware is unavailable on older devices, it may fall back
     * to a software implementation, but the API ensures the best available security.
     */
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    /**
     * Retrieves an existing AES SecretKey from the Android Keystore, or generates a new one
     * if the key does not already exist.
     *
     * On devices running Android 9.0 (API 28) or higher, this function attempts to generate a
     * key protected by a dedicated **StrongBox** hardware security module for maximum
     * tamper resistance. If StrongBox is unavailable, it falls back to the standard
     * hardware-backed (TEE) or software Keystore implementation.
     *
     * The key is configured for GCM block mode and no padding.
     *
     * @param keystoreAlias The alias to use when storing or retrieving the key in the Keystore.
     * @return The existing or newly generated SecretKey stored securely in the Keystore.
     */
    private fun getOrGenerateKey(keystoreAlias: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        // Attempt to retrieve existing key first
        val existingKey = keyStore.getKey(keystoreAlias, null) as? SecretKey
        if (existingKey != null) return existingKey

        // If key doesn't exist, attempt to generate it using StrongBox (API 28+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            try {
                return generateKey(keystoreAlias, preferStrongBox = true)
            } catch (_: StrongBoxUnavailableException) {
                // StrongBox not available, fall back to standard hardware/software keystore
                // Log this event if needed
            }
        }

        // Default generation (standard hardware/software backing)
        return generateKey(keystoreAlias, preferStrongBox = false)
    }

    private fun generateKey(keystoreAlias: String, preferStrongBox: Boolean): SecretKey {
        val keyGenParameterSpecBuilder = KeyGenParameterSpec.Builder(
            keystoreAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)

        if (preferStrongBox && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            keyGenParameterSpecBuilder.setIsStrongBoxBacked(true)
        }

        val keyGenParameterSpec = keyGenParameterSpecBuilder.build()

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(keyGenParameterSpec) }
            .generateKey()
    }

    fun hardwareEncrypt(keystoreAlias: String, unencrypted: ByteArray): ByteArray {
        val cipher = getCipherInstance()
        val key = getOrGenerateKey(keystoreAlias)

        // Initialize cipher for encryption; the Keystore will generate a random IV
        cipher.init(Cipher.ENCRYPT_MODE, key)

        // Retrieve the generated IV
        val iv = cipher.iv

        // Encrypt the data
        val encryptedData = cipher.doFinal(unencrypted)

        // Prepend the IV to the encrypted data
        return iv + encryptedData
    }

    fun hardwareDecrypt(keystoreAlias: String, encrypted: ByteArray): ByteArray {
        if (encrypted.size < GCM_IV_LENGTH_BYTES) {
            return ByteArray(0)
        }

        // Extract the IV from the encrypted data
        val iv = encrypted.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val encryptedData = encrypted.copyOfRange(GCM_IV_LENGTH_BYTES, encrypted.size)

        val cipher = getCipherInstance()
        val key = getOrGenerateKey(keystoreAlias)

        // Initialize cipher for decryption with the extracted IV
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(AES_GCM_TAG_BITS, iv))

        // Decrypt the data
        return cipher.doFinal(encryptedData)
    }

    fun hardwareEncrypt(keystoreAlias: String, inputText: String): String {
        val encryptedData = hardwareEncrypt(keystoreAlias, inputText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encryptedData, Base64.DEFAULT)
    }

    fun hardwareDecrypt(keystoreAlias: String, inputBase64: String): String {
        val encryptedText = Base64.decode(inputBase64, Base64.DEFAULT)
        val decryptedBytes = hardwareDecrypt(keystoreAlias, encryptedText)
        return decryptedBytes.toString(Charsets.UTF_8)
    }
}
