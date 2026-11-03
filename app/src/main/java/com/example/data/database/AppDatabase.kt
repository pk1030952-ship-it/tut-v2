package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.*
import com.example.data.model.*
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
    version = 4,
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

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "coachmaster_db"
                )
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
                                        adminPin = "8833",
                                        adminPassword = "P@88337",
                                        darkMode = false,
                                        isBiometricEnabled = true,
                                        currencySymbol = "₹",
                                        defaultClassesPerCycle = 30
                                    )
                                )
                            }
                        }
                    })
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
