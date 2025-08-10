package com.google.ai.edge.gallery.bridge.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.ai.edge.gallery.bridge.MessageBridgeRepository
import com.google.ai.edge.gallery.bridge.MessagePlatform
import com.google.ai.edge.gallery.bridge.gmail.GmailService
import com.google.ai.edge.gallery.bridge.telegram.TelegramService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Background worker that periodically checks for new messages in Telegram and Gmail
 * and applies bridge rules to forward messages between platforms.
 */
@HiltWorker
class MessageMonitoringWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val telegramService: TelegramService,
    private val gmailService: GmailService
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "MessageMonitoringWorker"
        private const val WORK_NAME = "message_monitoring_worker"

        /**
         * Schedules the periodic background work
         */
        fun schedule(context: Context) {
            Log.d(TAG, "Scheduling message monitoring worker")

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<MessageMonitoringWorker>(
                15, TimeUnit.MINUTES, // Run every 15 minutes
                5, TimeUnit.MINUTES  // Flex period of 5 minutes
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE, // Replace existing work if it exists
                workRequest
            )
        }

        /**
         * Cancels the scheduled background work
         */
        fun cancel(context: Context) {
            Log.d(TAG, "Canceling message monitoring worker")
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Message monitoring worker started")

        return try {
            // Check if we have active bridge rules
            val bridgeRules = MessageBridgeRepository.bridgeRules.value
            if (bridgeRules.isEmpty()) {
                Log.d(TAG, "No bridge rules configured, skipping work")
                return Result.success()
            }

            // Check if services are connected
            val telegramConnected = MessageBridgeRepository.telegramConnected.value
            val gmailConnected = MessageBridgeRepository.gmailConnected.value

            if (!telegramConnected && !gmailConnected) {
                Log.d(TAG, "No services connected, skipping work")
                return Result.success()
            }

            // Process Telegram rules if connected
            if (telegramConnected) {
                processTelegramMessages(bridgeRules.filter {
                    it.enabled && it.sourceType == MessagePlatform.TELEGRAM
                })
            }

            // Process Gmail rules if connected
            if (gmailConnected) {
                processGmailMessages(bridgeRules.filter {
                    it.enabled && it.sourceType == MessagePlatform.GMAIL
                })
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in message monitoring worker", e)
            Result.retry()
        }
    }

    /**
     * Processes Telegram messages for forwarding to Gmail
     */
    private suspend fun processTelegramMessages(rules: List<com.google.ai.edge.gallery.bridge.BridgeRule>) {
        withContext(Dispatchers.IO) {
            if (rules.isEmpty()) return@withContext

            Log.d(TAG, "Processing ${rules.size} Telegram bridge rules")

            // In a real implementation, you would:
            // 1. Fetch recent messages from each chat in the rules
            // 2. Filter for messages newer than last check
            // 3. Forward matching messages to Gmail

            // For this example, we'll just log the intent
            rules.forEach { rule ->
                Log.d(TAG, "Would check Telegram chat ${rule.sourceIdentifier} for new messages to forward to ${rule.targetIdentifier}")
            }
        }
    }

    /**
     * Processes Gmail messages for forwarding to Telegram
     */
    private suspend fun processGmailMessages(rules: List<com.google.ai.edge.gallery.bridge.BridgeRule>) {
        withContext(Dispatchers.IO) {
            if (rules.isEmpty()) return@withContext

            Log.d(TAG, "Processing ${rules.size} Gmail bridge rules")

            // In a real implementation, you would:
            // 1. Fetch recent messages from each label/sender in the rules
            // 2. Filter for messages newer than last check
            // 3. Forward matching messages to Telegram

            // For this example, we'll just log the intent
            rules.forEach { rule ->
                Log.d(TAG, "Would check Gmail ${rule.sourceIdentifier} for new messages to forward to ${rule.targetIdentifier}")
            }
        }
    }
}
