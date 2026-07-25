package io.zx.password.crypto

import android.content.Context
import io.zx.password.PwdDB
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.security.PrivateKey
import javax.crypto.SecretKey

object SessionManager {
    var dataKey: SecretKey? = null

    fun isUnlocked(): Boolean = dataKey != null

    fun encrypt(plaintext: String): String {
        val result = CryptoManager.encrypt(plaintext, dataKey!!)
        return "${CryptoManager.bytesToBase64(result.iv)}:${CryptoManager.bytesToBase64(result.ciphertext)}"
    }

    fun decrypt(encoded: String): String {
        val parts = encoded.split(":", limit = 2)
        val iv = CryptoManager.base64ToBytes(parts[0])
        val ct = CryptoManager.base64ToBytes(parts[1])
        return CryptoManager.decrypt(iv, ct, dataKey!!)
    }

    /** 通过主密码解锁 */
    fun unlock(context: Context, masterPassword: String): Boolean {
        val db = PwdDB.getInstance(context)
        val keyPairEntity = runBlocking(Dispatchers.IO) { db.KeyPairDao().get() } ?: return false
        val salt = CryptoManager.base64ToBytes(keyPairEntity.salt)
        val (kMaster, _) = CryptoManager.deriveKey(masterPassword, salt)
        return unlockWithKMaster(context, kMaster)
    }

    /** 通过生物识别（从 Keystore 恢复 K_master）解锁 */
    fun unlockWithBiometric(context: Context): Boolean {
        val kMaster = KeystoreHelper.getKmFromBiometric(context) ?: return false
        return unlockWithKMaster(context, kMaster)
    }

    /** 验证 K_master 正确性并完成解锁流程。K_master 调用方负责清理 */
    private fun unlockWithKMaster(context: Context, kMaster: SecretKey): Boolean {
        return try {
            val db = PwdDB.getInstance(context)
            val keyPairEntity = runBlocking(Dispatchers.IO) { db.KeyPairDao().get() } ?: return false

            // 验证 Magic Text
            val magicIv = CryptoManager.base64ToBytes(keyPairEntity.magicTextIv)
            val magicCt = CryptoManager.base64ToBytes(keyPairEntity.magicTextCipher)
            val verified = try {
                CryptoManager.decrypt(magicIv, magicCt, kMaster) == CryptoManager.MAGIC_TEXT
            } catch (e: Exception) { false }
            if (!verified) return false

            // 解密设备私钥
            val privKeyParts = keyPairEntity.encryptedPrivateKey.split(":", limit = 2)
            val privIv = CryptoManager.base64ToBytes(privKeyParts[0])
            val privCt = CryptoManager.base64ToBytes(privKeyParts[1])
            val rawPrivateKey = CryptoManager.decrypt(privIv, privCt, kMaster)
            val privateKey: PrivateKey = CryptoManager.privateKeyFromString(rawPrivateKey)

            // 解密 Data Key
            val device = runBlocking(Dispatchers.IO) { db.DeviceDao().getCurrentDevice() } ?: return false
            if (device.encryptedDataKey == null) return false
            val dkParts = device.encryptedDataKey!!.split(":", limit = 2)
            val dkIv = CryptoManager.base64ToBytes(dkParts[0])
            val dkCt = CryptoManager.base64ToBytes(dkParts[1])
            val rawDataKey = CryptoManager.rsaDecrypt(dkIv, dkCt, privateKey)
            dataKey = CryptoManager.rawToAesKey(rawDataKey)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun lock() {
        dataKey = null
    }
}
