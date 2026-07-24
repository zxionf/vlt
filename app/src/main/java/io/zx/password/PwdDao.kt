package io.zx.password

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PwdDao {
    @Insert
    suspend fun insert(entity: PasswdEntity)

    @Update
    suspend fun update(entity: PasswdEntity)

    @Delete
    suspend fun delete(entity: PasswdEntity)

    @Query("SELECT * FROM passwd ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<PasswdEntity>>

    @Query("SELECT * FROM passwd WHERE id = :id")
    suspend fun getById(id: Long): PasswdEntity?
}

@Dao
interface TagDao {
    @Insert
    suspend fun insert(tag: Tag)

    @Delete
    suspend fun delete(tag: Tag)

    @Query("SELECT * FROM tags")
    fun getAll(): Flow<List<Tag>>

    @Insert
    suspend fun insertJoin(join: PasswordTagJoin)

    @Delete
    suspend fun deleteJoin(join: PasswordTagJoin)

    @Query("""
        SELECT t.* FROM tags t 
        INNER JOIN password_tag_join ptj ON t.id = ptj.tagId 
        WHERE ptj.passwdId = :passwdId
    """)
    fun getTagsForPassword(passwdId: Long): Flow<List<Tag>>
}
