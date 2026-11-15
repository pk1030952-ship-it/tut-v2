package com.example.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object WorkScheduler {
    const val WORK_MORNING_ROLL_CALL = "coachmaster_morning_roll_call_9am"
    const val WORK_NIGHTLY_ENCRYPTED_BACKUP = "coachmaster_nightly_encrypted_backup"

    /**
     * Schedules both:
     * 1) 9:00 AM Daily Morning Roll-Call Reminder ("Aaj ki tuition class start ho gayi hai! Roll Call lein.")
     * 2) 11:00 PM Daily Nightly Encrypted Local Backup Worker
     */
    fun scheduleDailyAutomation(context: Context) {
        RollCallReminderWorker.ensureNotificationChannel(context)
        val workManager = WorkManager.getInstance(context)

        // 1. Morning 9:00 AM Roll-Call Reminder
        val delayTo9AmMs = computeDelayUntilHour(targetHour24 = 9, targetMinute = 0)
        val rollCallRequest = PeriodicWorkRequestBuilder<RollCallReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayTo9AmMs, TimeUnit.MILLISECONDS)
            .addTag(WORK_MORNING_ROLL_CALL)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_MORNING_ROLL_CALL,
            ExistingPeriodicWorkPolicy.KEEP,
            rollCallRequest
        )

        // 2. Nightly 11:00 PM Encrypted JSON Auto-Backup
        val delayTo11PmMs = computeDelayUntilHour(targetHour24 = 23, targetMinute = 0)
        val backupRequest = PeriodicWorkRequestBuilder<DailyEncryptedBackupWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayTo11PmMs, TimeUnit.MILLISECONDS)
            .addTag(WORK_NIGHTLY_ENCRYPTED_BACKUP)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NIGHTLY_ENCRYPTED_BACKUP,
            ExistingPeriodicWorkPolicy.KEEP,
            backupRequest
        )
    }

    fun computeDelayUntilHour(targetHour24: Int, targetMinute: Int): Long {
        val now = Calendar.getInstance()
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, targetHour24)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return (target.timeInMillis - now.timeInMillis).coerceAtLeast(1000L)
    }
}
