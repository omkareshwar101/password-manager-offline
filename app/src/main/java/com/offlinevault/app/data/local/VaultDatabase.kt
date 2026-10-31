package com.offlinevault.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.offlinevault.app.util.Constants
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [AccountEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    companion object {
        fun build(context: Context, passphrase: ByteArray): VaultDatabase {
            SQLiteDatabase.loadLibs(context.applicationContext)
            val factory = SupportFactory(passphrase)
            return Room.databaseBuilder(
                context.applicationContext,
                VaultDatabase::class.java,
                Constants.DATABASE_NAME,
            )
                .openHelperFactory(factory)
                .setJournalMode(JournalMode.TRUNCATE)
                .build()
        }
    }
}
