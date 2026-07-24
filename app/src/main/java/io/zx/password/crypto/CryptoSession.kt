package io.zx.password.crypto

import android.content.Context
import io.zx.password.PwdDB
import javax.crypto.SecretKey

object CryptoSession {
    var key: SecretKey? = null

    fun encrypt(plaintext: String): CryptoManager.EncryptResult {
        return CryptoManager.encrypt(plaintext, key!!)
    }

    fun decrypt(ivString: String, cipherString: String): String {
        val iv = CryptoManager.base64ToBytes(ivString)
        val ciphertext = CryptoManager.base64ToBytes(cipherString)
        return CryptoManager.decrypt(iv, ciphertext, key!!)
    }

    /** 验证主密码并加载密钥，返回是否成功 */
    suspend fun verifyAndLoad(context: Context, masterPassword: String): Boolean {
        return try {
            val db = PwdDB.getInstance(context)
            val keyPairEntity = db.KeyPairDao().get() ?: return false
            val salt = CryptoManager.base64ToBytes(keyPairEntity.salt)
            val (derivedKey, _) = CryptoManager.deriveKey(masterPassword, salt)
            // 验证 magic text
            val magicIv = CryptoManager.base64ToBytes(keyPairEntity.magicTextIv)
            val magicCipher = CryptoManager.base64ToBytes(keyPairEntity.magicTextCipher)
            val decrypted = CryptoManager.decrypt(magicIv, magicCipher, derivedKey)
            if (decrypted == CryptoManager.MAGIC_TEXT) {
                key = derivedKey
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun loadKey(context: Context, masterPassword: String): SecretKey? {
        val db = PwdDB.getInstance(context)
        val keyPairEntity = db.KeyPairDao().get() ?: return null
        val salt = CryptoManager.base64ToBytes(keyPairEntity.salt)
        val (derivedKey, _) = CryptoManager.deriveKey(masterPassword, salt)
        key = derivedKey
        return derivedKey
    }
}
