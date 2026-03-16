package org.freewheel.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TripDataDbEntry::class], version = 3, exportSchema = false)
abstract class TripDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao

    companion object {
        @Volatile
        private var INSTANCE: TripDatabase? = null

        private val migration1To2: Migration = object: Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    execSQL("ALTER TABLE `trip_database` ADD COLUMN `distance` INTEGER NOT NULL DEFAULT 0;")
                    execSQL("ALTER TABLE `trip_database` ADD COLUMN `consumptionTotal` REAL NOT NULL DEFAULT 0;")
                    execSQL("ALTER TABLE `trip_database` ADD COLUMN `consumptionByKm` REAL NOT NULL DEFAULT 0;")
                }
            }
        }

        /**
         * Drop ElectroClub columns and other unused fields using the copy-table
         * approach (required for minSdk 21 which lacks DROP COLUMN support).
         */
        private val migration2To3: Migration = object: Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    execSQL("""
                        CREATE TABLE IF NOT EXISTS `trip_database_new` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `fileName` TEXT NOT NULL,
                            `start` INTEGER NOT NULL DEFAULT 0,
                            `duration` INTEGER NOT NULL DEFAULT 0,
                            `maxSpeed` REAL NOT NULL DEFAULT 0,
                            `avgSpeed` REAL NOT NULL DEFAULT 0,
                            `maxPwm` REAL NOT NULL DEFAULT 0,
                            `maxCurrent` REAL NOT NULL DEFAULT 0,
                            `maxPower` REAL NOT NULL DEFAULT 0,
                            `distance` INTEGER NOT NULL DEFAULT 0,
                            `consumptionTotal` REAL NOT NULL DEFAULT 0,
                            `consumptionByKm` REAL NOT NULL DEFAULT 0
                        )
                    """.trimIndent())
                    execSQL("""
                        INSERT INTO `trip_database_new`
                            (`id`, `fileName`, `start`, `duration`, `maxSpeed`, `avgSpeed`,
                             `maxPwm`, `maxCurrent`, `maxPower`, `distance`,
                             `consumptionTotal`, `consumptionByKm`)
                        SELECT `id`, `fileName`, `start`, `duration`, `maxSpeed`, `avgSpeed`,
                               `maxPwm`, `maxCurrent`, `maxPower`, `distance`,
                               `consumptionTotal`, `consumptionByKm`
                        FROM `trip_database`
                    """.trimIndent())
                    execSQL("DROP TABLE `trip_database`")
                    execSQL("ALTER TABLE `trip_database_new` RENAME TO `trip_database`")
                    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_trip_database_fileName` ON `trip_database` (`fileName`)")
                }
            }
        }

        fun getDataBase(context: Context): TripDatabase {
            val tempInstance = INSTANCE
            if (tempInstance != null) {
                return tempInstance
            }
            synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TripDatabase::class.java,
                    "trip_database"
                )
                    .addMigrations(migration1To2, migration2To3)
                    .build()
                INSTANCE = instance
                return instance
            }
        }
    }
}