package io.zx.password

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "passwd",
    indices = [
        // category index removed - no category field in entity,
        Index(value = ["title"]),
        Index(value = ["createdAt"])
    ]
)
data class PasswdEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title :String,
    val username : String,
    val encryptedPasswd :String,
    val iv : String,
    val notes :String? = null,
    val url : String? = null,
    val passwd: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tags")
data class Tag(
    @PrimaryKey(autoGenerate = true)
    val id:Long = 0,
    val name :String
)

@Entity(
    tableName = "password_tag_join",
    primaryKeys = ["passwdId", "tagId"],
    indices = [Index(value = ["tagId"])],
    foreignKeys = [
        ForeignKey(entity = PasswdEntity::class, parentColumns = ["id"], childColumns = ["passwdId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Tag::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class PasswordTagJoin(
    val passwdId: Long,
    val tagId: Long
)