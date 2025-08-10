package com.google.ai.edge.gallery.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelManager
import com.google.ai.edge.gallery.util.KEY_IS_PAUSED
import com.google.ai.edge.gallery.util.KEY_RESUME_OFFSET
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Worker that handles pausing an ongoing download.
 * This worker saves the current download state so it can be resumed later.
 */
class PauseWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val modelName = inputData.getString("model_name") ?: return@withContext Result.failure()
                val progress = inputData.getInt("progress", 0)
                
                // Save the pause state
                val prefs = applicationContext.getSharedPreferences("download_states", Context.MODE_PRIVATE)
                prefs.edit()
                    .putBoolean("${modelName}_$KEY_IS_PAUSED", true)
                    .putInt("${modelName}_progress", progress)
                    .apply()
                
                Log.d(TAG, "Paused download for model: $modelName at $progress%")
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "Error pausing download", e)
                Result.failure()
            }
        }
    }
    
    companion object {
        private const val TAG = "PauseWorker"
        
        /**
         * Gets the saved progress for a paused download.
         *
         * @param context The application context.
         * @param modelName The name of the model.
         * @return The progress percentage (0-100) if found, or null if not paused.
         */
        fun getPausedProgress(context: Context, modelName: String): Int? {
            val prefs = context.getSharedPreferences("download_states", Context.MODE_PRIVATE)
            return if (prefs.getBoolean("${modelName}_$KEY_IS_PAUSED", false)) {
                prefs.getInt("${modelName}_progress", -1).takeIf { it >= 0 }
            } else {
                null
            }
        }
        
        /**
         * Clears the paused state for a model.
         *
         * @param context The application context.
         * @param modelName The name of the model.
         */
        fun clearPausedState(context: Context, modelName: String) {
            val prefs = context.getSharedPreferences("download_states", Context.MODE_PRIVATE)
            prefs.edit()
                .remove("${modelName}_$KEY_IS_PAUSED")
                .remove("${modelName}_progress")
                .apply()
        }
    }
}
