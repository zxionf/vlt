package io.zx.password

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PwdDao {
    @Insert
    suspend fun insert(pwd : Pwd)

    @Update
    suspend fun update(pwd : Pwd)

    @Delete
    suspend fun delete(pwd : Pwd)

    @Query("SELECT * FROM pwd")
    fun getAll() : Flow<List<Pwd>>

    @Query("SELECT * FROM pwd WHERE id = :id")
    suspend fun getPwdById(id : Int): Pwd?
}