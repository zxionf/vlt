package io.zx.password

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PwdDao {
    @Insert
    suspend fun insert(entity: PasswordEntry)

    @Update
    suspend fun update(entity: PasswordEntry)

    @Delete
    suspend fun delete(entity: PasswordEntry)

    @Query("SELECT * FROM passwords WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<PasswordEntry>>

    @Query("SELECT * FROM passwords WHERE id = :id")
    suspend fun getById(id: String): PasswordEntry?
}

@Dao
interface TagDao {
    @Insert
    suspend fun insert(tag: Tag): Long

    @Delete
    suspend fun delete(tag: Tag)

    @Query("SELECT * FROM tags")
    fun getAll(): Flow<List<Tag>>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): Tag?

    @Insert
    suspend fun insertJoin(join: PasswordTagJoin)

    @Delete
    suspend fun deleteJoin(join: PasswordTagJoin)

    @Query("DELETE FROM password_tag_join WHERE passwordId = :passwordId")
    suspend fun deleteJoinsForPassword(passwordId: String)

    @Query("""
        SELECT t.* FROM tags t
        INNER JOIN password_tag_join ptj ON t.id = ptj.tagId
        WHERE ptj.passwordId = :passwordId
    """)
    fun getTagsForPassword(passwordId: String): Flow<List<Tag>>
}

@Dao
interface KeyPairDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: KeyPairEntity)

    @Query("SELECT * FROM key_pair WHERE id = 1")
    suspend fun get(): KeyPairEntity?
}

@Dao
interface DeviceDao {
    @Insert
    suspend fun insert(entity: DeviceEntity)

    @Query("SELECT * FROM devices WHERE isCurrentDevice = 1 LIMIT 1")
    suspend fun getCurrentDevice(): DeviceEntity?

    @Query("SELECT * FROM devices")
    fun getAll(): Flow<List<DeviceEntity>>
}
