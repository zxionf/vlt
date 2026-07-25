package io.zx.password.crypto

import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.security.KeyFactory
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {

    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA512"
    private const val PBKDF2_ITERATIONS = 600_000
    private const val AES_KEY_SIZE = 256
    private const val AES_ALGORITHM = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12
    private const val SALT_LENGTH = 32
    private const val RSA_ALGORITHM = "RSA"
    private const val RSA_KEY_SIZE = 3072

    // ─── PBKDF2 — 主密码派生 AES 密钥 ───

    fun deriveKey(masterPassword: String, salt: ByteArray? = null): Pair<SecretKey, ByteArray> {
        val actualSalt = salt ?: ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val spec = PBEKeySpec(masterPassword.toCharArray(), actualSalt, PBKDF2_ITERATIONS, AES_KEY_SIZE)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val key = factory.generateSecret(spec)
        return SecretKeySpec(key.encoded, "AES") to actualSalt
    }

    // ─── AES-GCM 加密/解密 ───

    fun encrypt(plaintext: String, key: SecretKey): EncryptResult {
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return EncryptResult(iv = iv, ciphertext = ciphertext)
    }

    fun encryptBytes(data: ByteArray, key: SecretKey): EncryptResult {
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(data)
        return EncryptResult(iv = iv, ciphertext = ciphertext)
    }

    fun decrypt(iv: ByteArray, ciphertext: ByteArray, key: SecretKey): String {
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    fun decryptBytes(iv: ByteArray, ciphertext: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }

    // ─── RSA-3072 密钥对 ───

    fun generateRsaKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance(RSA_ALGORITHM)
        generator.initialize(RSA_KEY_SIZE, SecureRandom())
        return generator.generateKeyPair()
    }

    fun publicKeyToString(key: PublicKey): String {
        return Base64.encodeToString(key.encoded, Base64.NO_WRAP)
    }

    fun privateKeyToString(key: PrivateKey): String {
        return Base64.encodeToString(key.encoded, Base64.NO_WRAP)
    }

    fun publicKeyFromString(encoded: String): PublicKey {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        return KeyFactory.getInstance(RSA_ALGORITHM).generatePublic(X509EncodedKeySpec(bytes))
    }

    fun privateKeyFromString(encoded: String): PrivateKey {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        return KeyFactory.getInstance(RSA_ALGORITHM).generatePrivate(PKCS8EncodedKeySpec(bytes))
    }

    fun bytesToBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    fun base64ToBytes(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)

    // ─── RSA 加密/解密（用于 Data Key）───

    fun rsaEncrypt(data: ByteArray, publicKey: PublicKey): EncryptResult {
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        // RSA 不需要 IV，但为了统一接口，放空数组
        return EncryptResult(iv = ByteArray(0), ciphertext = cipher.doFinal(data))
    }

    fun rsaDecrypt(iv: ByteArray, ciphertext: ByteArray, privateKey: PrivateKey): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        return cipher.doFinal(ciphertext)
    }

    // ─── Data Key 生成 ───

    fun generateDataKey(): SecretKey {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return SecretKeySpec(bytes, "AES")
    }

    fun rawToAesKey(raw: ByteArray): SecretKey = SecretKeySpec(raw, "AES")

    // ─── Magic Text 验证 ───

    const val MAGIC_TEXT = "PWD_MASTER_VERIFY_OK"

    data class EncryptResult(val iv: ByteArray, val ciphertext: ByteArray)
}
