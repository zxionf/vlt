package io.zx.password

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pwd")
data class Pwd(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val description: String,
    val passwd: String
)
