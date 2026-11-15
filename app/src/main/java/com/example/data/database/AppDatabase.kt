package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.*
import com.example.data.model.*
import com.example.util.SecurityUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Student::class,
        Batch::class,
        Staff::class,
        AttendanceRecord::class,
        PaymentRecord::class,
        ExpenseRecord::class,
        Exam::class,
        ExamResult::class,
        CommunicationLog::class,
        InstituteConfig::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun studentDao(): StudentDao
    abstract fun batchDao(): BatchDao
    abstract fun staffDao(): StaffDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun paymentDao(): PaymentDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun examDao(): ExamDao
    abstract fun communicationDao(): CommunicationDao
    abstract fun instituteConfigDao(): InstituteConfigDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private fun hasColumn(
            db: SupportSQLiteDatabase,
            tableName: String,
            columnName: String
        ): Boolean {
            db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex == -1) return false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex).equals(columnName, ignoreCase = true)) {
                        return true
                    }
                }
            }
            return false
        }

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "batches", "classesPerCycle")) {
                    db.execSQL("ALTER TABLE `batches` ADD COLUMN `classesPerCycle` INTEGER NOT NULL DEFAULT 0")
                }
                if (!hasColumn(db, "attendance", "reason")) {
                    db.execSQL("ALTER TABLE `attendance` ADD COLUMN `reason` TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "institute_config", "defaultClassesPerCycle")) {
                    db.execSQL("ALTER TABLE `institute_config` ADD COLUMN `defaultClassesPerCycle` INTEGER NOT NULL DEFAULT 30")
                }
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "payments", "discount")) {
                    db.execSQL("ALTER TABLE `payments` ADD COLUMN `discount` REAL NOT NULL DEFAULT 0.0")
                }
                if (!hasColumn(db, "payments", "isoTimestamp")) {
                    db.execSQL("ALTER TABLE `payments` ADD COLUMN `isoTimestamp` TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "communication_logs", "isoTimestamp")) {
                    db.execSQL("ALTER TABLE `communication_logs` ADD COLUMN `isoTimestamp` TEXT NOT NULL DEFAULT ''")
                }
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "students", "rollNo")) {
                    db.execSQL("ALTER TABLE `students` ADD COLUMN `rollNo` TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "students", "parentPhone")) {
                    db.execSQL("ALTER TABLE `students` ADD COLUMN `parentPhone` TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "students", "notes")) {
                    db.execSQL("ALTER TABLE `students` ADD COLUMN `notes` TEXT NOT NULL DEFAULT ''")
                }
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Hash existing plaintext adminPin and adminPassword in institute_config without losing any data
                db.query("SELECT `id`, `adminPin`, `adminPassword` FROM `institute_config`").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getInt(0)
                        val rawPin = cursor.getString(1) ?: ""
                        val rawPassword = cursor.getString(2) ?: ""
                        val hashedPin = if (rawPin.isBlank()) {
                            SecurityUtils.DEFAULT_ADMIN_PIN_HASH
                        } else {
                            SecurityUtils.hashSecret(rawPin)
                        }
                        val hashedPassword = if (rawPassword.isBlank()) {
                            SecurityUtils.DEFAULT_ADMIN_PASSWORD_HASH
                        } else {
                            SecurityUtils.hashSecret(rawPassword)
                        }
                        db.execSQL(
                            "UPDATE `institute_config` SET `adminPin` = ?, `adminPassword` = ? WHERE `id` = ?",
                            arrayOf<Any>(hashedPin, hashedPassword, id)
                        )
                    }
                }
            }
        }

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5
        )

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "coachmaster_db"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                INSTANCE?.instituteConfigDao()?.saveConfig(
                                    InstituteConfig(
                                        id = 1,
                                        instituteName = "CoachMaster",
                                        address = "",
                                        contactPhone = "",
                                        adminPin = SecurityUtils.DEFAULT_ADMIN_PIN_HASH,
                                        adminPassword = SecurityUtils.DEFAULT_ADMIN_PASSWORD_HASH,
                                        darkMode = false,
                                        isBiometricEnabled = true,
                                        currencySymbol = "₹",
                                        defaultClassesPerCycle = 30
                                    )
                                )
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
