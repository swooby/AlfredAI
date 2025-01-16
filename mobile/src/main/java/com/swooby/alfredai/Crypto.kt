package com.swooby.alfredai

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object Crypto {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "AlfredAiKeyAlias"
    private const val IV_LENGTH = 12

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            keyGenerator.generateKey()
        } else {
            keyStore.getKey(KEY_ALIAS, null) as SecretKey
        }
    }

    fun hardwareEncrypt(unencrypted: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = getOrCreateKey()

        // Initialize cipher for encryption; the Keystore will generate a random IV
        cipher.init(Cipher.ENCRYPT_MODE, key)

        // Retrieve the generated IV
        val iv = cipher.iv

        // Encrypt the data
        val encryptedData = cipher.doFinal(unencrypted)

        // Prepend the IV to the encrypted data
        return iv + encryptedData
    }

    fun hardwareDecrypt(encrypted: ByteArray): ByteArray {
        if (encrypted.size < IV_LENGTH) {
            return ByteArray(0)
        }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = getOrCreateKey()

        // Extract the IV from the encrypted data
        val iv = encrypted.copyOfRange(0, IV_LENGTH)
        val encryptedData = encrypted.copyOfRange(IV_LENGTH, encrypted.size)

        // Initialize cipher for decryption with the extracted IV
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))

        // Decrypt the data
        return cipher.doFinal(encryptedData)
    }

    fun hardwareEncrypt(inputText: String): String {
        val encryptedData = hardwareEncrypt(inputText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encryptedData, Base64.DEFAULT)
    }

    fun hardwareDecrypt(inputBase64: String): String {
        val encryptedText = Base64.decode(inputBase64, Base64.DEFAULT)
        val decryptedBytes = hardwareDecrypt(encryptedText)
        return decryptedBytes.toString(Charsets.UTF_8)
    }
}
