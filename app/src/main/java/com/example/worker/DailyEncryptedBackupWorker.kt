package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.repository.CoachMasterRepository

class DailyEncryptedBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val repository = CoachMasterRepository.getInstance(applicationContext)
            repository.performEncryptedAutoBackup(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
