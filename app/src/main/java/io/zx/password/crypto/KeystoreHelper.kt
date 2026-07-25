package io.zx.password.crypto

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

object KeystoreHelper {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val ALIAS_KM_PROTECTOR = "pwd_kmaster_protector"
    private const val PREFS_NAME = "pwd_keystore_prefs"
    private const val KEY_KM_IV = "km_iv"
    private const val KEY_KM_CT = "km_ct"
    private const val AES_MODE = "AES/GCM/NoPadding"

    // ─── 通用 Keystore 密钥（无生物识别要求）───

    private fun getOrCreateKey(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        keyStore.getEntry(alias, null)?.let {
            return (it as KeyStore.SecretKeyEntry).secretKey
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    // ─── K_master 本地缓存（用 Keystore 加密，生物识别由系统锁屏保证）───

    fun hasBiometricKey(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_KM_CT)
    }

    /** 用 Keystore 密钥加密 K_master 并存到 SharedPreferences */
    fun storeKmForBiometric(context: Context, kMaster: SecretKey) {
        android.util.Log.d("KEYSTORE", "storeKmForBiometric 调用")
        try {
            val enc = encryptWithKeystore(kMaster.encoded)
            val ok = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_KM_IV, Base64.encodeToString(enc.iv, Base64.NO_WRAP))
                .putString(KEY_KM_CT, Base64.encodeToString(enc.ciphertext, Base64.NO_WRAP))
                .commit() // 同步写入，避免 onComplete 后立即检查时还没写完
            android.util.Log.d("KEYSTORE", "storeKmForBiometric 完成, commit=$ok")
        } catch (e: Exception) {
            android.util.Log.e("KEYSTORE", "storeKmForBiometric 失败", e)
        }
    }

    /** 从 SharedPreferences + Keystore 解密恢复 K_master */
    fun getKmFromBiometric(context: Context): SecretKey? {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val ivB64 = prefs.getString(KEY_KM_IV, null) ?: return null
            val ctB64 = prefs.getString(KEY_KM_CT, null) ?: return null
            android.util.Log.d("KEYSTORE", "getKmFromBiometric 读取缓存")
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)
            val ct = Base64.decode(ctB64, Base64.NO_WRAP)
            val raw = decryptWithKeystore(iv, ct)
            return javax.crypto.spec.SecretKeySpec(raw, "AES")
        } catch (_: Exception) {
            return null
        }
    }

    data class EncryptedBytes(val iv: ByteArray, val ciphertext: ByteArray)

    fun encryptWithKeystore(plaintext: ByteArray): EncryptedBytes {
        val key = getOrCreateKey(ALIAS_KM_PROTECTOR)
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return EncryptedBytes(iv = cipher.iv, ciphertext = cipher.doFinal(plaintext))
    }

    fun decryptWithKeystore(iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val key = getOrCreateKey(ALIAS_KM_PROTECTOR)
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }
}
