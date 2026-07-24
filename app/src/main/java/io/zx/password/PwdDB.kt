package io.zx.password

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PasswdEntity::class, Tag::class, PasswordTagJoin::class], version = 2, exportSchema = false)
abstract class PwdDB : RoomDatabase(){
    abstract fun PwdDao(): PwdDao
    abstract fun TagDao(): TagDao

    companion object {
        // 使用单例模式避免多次创建数据库实例，这是一种常见的优化做法
        @Volatile
        private var INSTANCE: PwdDB? = null

        fun getInstance(context: Context): PwdDB {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PwdDB::class.java,
                    "passwd"
                )
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}