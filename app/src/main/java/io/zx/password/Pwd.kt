package io.zx.password

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 密码记录 — 表 passwords */
@Entity(
    tableName = "passwords",
    indices = [
        Index(value = ["title"]),
        Index(value = ["createdAt"])
    ]
)
data class PasswordEntry(
    @PrimaryKey
    val id: String,                     // UUID，创建设备生成，全局唯一
    val title: String,
    val username: String,
    val encryptedPassword: String,      // base64(iv):base64(ct)
    val encryptedNotes: String?,        // 同格式，null 表示无备注
    val url: String?,
    val createdDeviceId: String,        // 创建记录设备 ID
    val lastModifiedDeviceId: String,   // 最后修改设备 ID
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncVersion: Int = 1,           // 乐观锁版本号
    val isDeleted: Boolean = false      // 软删除
)

/** 标签 — 表 tags */
@Entity(tableName = "tags")
data class Tag(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String
)

/** 标签关联 — 表 password_tag_join */
@Entity(
    tableName = "password_tag_join",
    primaryKeys = ["passwordId", "tagId"],
    indices = [Index(value = ["tagId"])],
    foreignKeys = [
        ForeignKey(entity = PasswordEntry::class, parentColumns = ["id"], childColumns = ["passwordId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Tag::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class PasswordTagJoin(
    val passwordId: String,
    val tagId: Long
)

/** 设备信息 — 表 devices */
@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey
    val deviceId: String,               // UUID 全局唯一
    val deviceName: String,             // 如 "Pixel 7"
    val publicKey: String,              // RSA 公钥 Base64
    val encryptedDataKey: String?,      // 用本机公钥加密的 Data Key (base64(iv):base64(ct))
    val isCurrentDevice: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/** 密钥存储 — 表 key_pair */
@Entity(tableName = "key_pair")
data class KeyPairEntity(
    @PrimaryKey
    val id: Int = 1,
    val salt: String,                   // PBKDF2 盐
    val magicTextIv: String,
    val magicTextCipher: String,        // "PWD_MASTER_VERIFY_OK" 密文
    val encryptedPrivateKey: String,    // K_master 加密的设备私钥 (base64(iv):base64(ct))
    val privateKeyIv: String,           // 私钥加密的 IV
    val passwordHint: String            // 密码提示
)
