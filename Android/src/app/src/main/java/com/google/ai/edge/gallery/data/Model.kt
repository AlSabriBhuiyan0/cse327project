

package com.google.ai.edge.gallery.data

import android.content.Context
import java.io.File

data class ModelDataFile(
  val name: String,
  val url: String,
  val downloadFileName: String,
  val sizeInBytes: Long,
)

const val IMPORTS_DIR = "__imports"
private val NORMALIZE_NAME_REGEX = Regex("[^a-zA-Z0-9]")

data class PromptTemplate(val title: String, val description: String, val prompt: String)

/** A model for a task */
data class Model(
  /** The name (for display purpose) of the model. */
  val name: String,
  
  /** The URL to download the model from. */
  var downloadUrl: String = "",
  
  /** 
   * The name of the downloaded model file.
   * The final file path of the downloaded model will be:
   * {context.getExternalFilesDir}/{normalizedName}/{version}/{downloadFileName}
   */
  var downloadFileName: String = "",
  
  /** The size of the model file in bytes. */
  var fileSizeBytes: Long = 0,
  
  /** Whether the downloaded file is a zip archive. */
  val isZipFile: Boolean = false,
  
  /** The directory to extract the zip file to (if isZipFile is true). */
  val extractToDir: String = "",
  
  /** The access token for authenticated downloads. */
  var accessToken: String? = null,

  /** The version of the model. */
  val version: String = "_",

  /** 
   * @deprecated Use downloadUrl instead.
   * The URL to download the model from.
   */
  @Deprecated("Use downloadUrl instead")
  var url: String = "",
  
  /**
   * @deprecated Use fileSizeBytes instead.
   * The size of the model file in bytes.
   */
  @Deprecated("Use fileSizeBytes instead")
  var sizeInBytes: Long = 0,

  /** A list of additional data files required by the model. */
  val extraDataFiles: List<ModelDataFile> = listOf(),

  /**
   * A description or information about the model.
   *
   * Will be shown at the start of the chat session and in the expanded model item.
   */
  val info: String = "",

  /** The url to jump to when clicking "learn more" in expanded model item. */
  val learnMoreUrl: String = "",

  /** A list of configurable parameters for the model. */
  val configs: List<Config> = listOf(),

  /** Whether to show the "run again" button in the UI. */
  val showRunAgainButton: Boolean = true,

  /** Whether to show the "benchmark" button in the UI. */
  val showBenchmarkButton: Boolean = true,

  /** Indicates whether the model is a zip file. */
  val isZip: Boolean = false,
  
  /** The name of the directory to unzip the model to (if it's a zip file). */
  val unzipDir: String = "",

  /** The prompt templates for the model (only for LLM). */
  val llmPromptTemplates: List<PromptTemplate> = listOf(),

  /** Whether the LLM model supports image input. */
  val llmSupportImage: Boolean = false,

  /** Whether the LLM model supports audio input. */
  val llmSupportAudio: Boolean = false,

  /** Whether the model is imported or not. */
  val imported: Boolean = false,

  // The following fields are managed by the app. Don't need to set manually.
  var normalizedName: String = "",
  var instance: Any? = null,
  var initializing: Boolean = false,
  // TODO(jingjin): use a "queue" system to manage model init and cleanup.
  var cleanUpAfterInit: Boolean = false,
  var configValues: Map<String, Any> = mapOf(),
  var totalBytes: Long = 0L,
  var accessToken: String? = null,

  /** The estimated peak memory in byte to run the model. */
  val estimatedPeakMemoryInBytes: Long? = null,
) {
  init {
    normalizedName = NORMALIZE_NAME_REGEX.replace(name, "_")
    
    // Backward compatibility for deprecated properties
    if (downloadUrl.isEmpty() && url.isNotEmpty()) {
      this.downloadUrl = url
    }
    
    if (fileSizeBytes == 0L && sizeInBytes > 0) {
      this.fileSizeBytes = sizeInBytes
    }
    
    // Initialize totalBytes if not set
    if (totalBytes == 0L) {
      totalBytes = this.fileSizeBytes + extraDataFiles.sumOf { it.sizeInBytes }
    }
  }

  fun preProcess() {
    val configValues: MutableMap<String, Any> = mutableMapOf()
    for (config in this.configs) {
      configValues[config.key.label] = config.defaultValue
    }
    this.configValues = configValues
    this.totalBytes = this.sizeInBytes + this.extraDataFiles.sumOf { it.sizeInBytes }
  }

  fun getPath(context: Context, fileName: String = downloadFileName): String {
    if (imported) {
      return listOf(context.getExternalFilesDir(null)?.absolutePath ?: "", fileName)
        .joinToString(File.separator)
    }

    val baseDir =
      listOf(context.getExternalFilesDir(null)?.absolutePath ?: "", normalizedName, version)
        .joinToString(File.separator)
    return if (this.isZip && this.unzipDir.isNotEmpty()) {
      "$baseDir/${this.unzipDir}"
    } else {
      "$baseDir/$fileName"
    }
  }

  fun getIntConfigValue(key: ConfigKey, defaultValue: Int = 0): Int {
    return getTypedConfigValue(key = key, valueType = ValueType.INT, defaultValue = defaultValue)
      as Int
  }

  fun getFloatConfigValue(key: ConfigKey, defaultValue: Float = 0.0f): Float {
    return getTypedConfigValue(key = key, valueType = ValueType.FLOAT, defaultValue = defaultValue)
      as Float
  }

  fun getBooleanConfigValue(key: ConfigKey, defaultValue: Boolean = false): Boolean {
    return getTypedConfigValue(
      key = key,
      valueType = ValueType.BOOLEAN,
      defaultValue = defaultValue,
    )
      as Boolean
  }

  fun getStringConfigValue(key: ConfigKey, defaultValue: String = ""): String {
    return getTypedConfigValue(key = key, valueType = ValueType.STRING, defaultValue = defaultValue)
      as String
  }

  fun getExtraDataFile(name: String): ModelDataFile? {
    return extraDataFiles.find { it.name == name }
  }

  private fun getTypedConfigValue(key: ConfigKey, valueType: ValueType, defaultValue: Any): Any {
    return convertValueToTargetType(
      value = configValues.getOrDefault(key.label, defaultValue),
      valueType = valueType,
    )
  }
  
  /**
   * Creates a copy of the model with the specified properties updated.
   */
  fun copy(
    name: String = this.name,
    downloadUrl: String = this.downloadUrl,
    downloadFileName: String = this.downloadFileName,
    fileSizeBytes: Long = this.fileSizeBytes,
    isZipFile: Boolean = this.isZipFile,
    extractToDir: String = this.extractToDir,
    accessToken: String? = this.accessToken,
    version: String = this.version,
    url: String = this.url,
    sizeInBytes: Long = this.sizeInBytes,
    extraDataFiles: List<ModelDataFile> = this.extraDataFiles,
    info: String = this.info,
    learnMoreUrl: String = this.learnMoreUrl,
    configs: List<Config> = this.configs,
    showRunAgainButton: Boolean = this.showRunAgainButton,
    showBenchmarkButton: Boolean = this.showBenchmarkButton,
    isZip: Boolean = this.isZip,
    unzipDir: String = this.unzipDir,
    llmPromptTemplates: List<PromptTemplate> = this.llmPromptTemplates,
    llmSupportImage: Boolean = this.llmSupportImage,
    llmSupportAudio: Boolean = this.llmSupportAudio,
    imported: Boolean = this.imported,
    estimatedPeakMemoryInBytes: Long? = this.estimatedPeakMemoryInBytes
  ): Model {
    return Model(
      name = name,
      downloadUrl = downloadUrl,
      downloadFileName = downloadFileName,
      fileSizeBytes = fileSizeBytes,
      isZipFile = isZipFile,
      extractToDir = extractToDir,
      accessToken = accessToken,
      version = version,
      url = url,
      sizeInBytes = sizeInBytes,
      extraDataFiles = extraDataFiles,
      info = info,
      learnMoreUrl = learnMoreUrl,
      configs = configs,
      showRunAgainButton = showRunAgainButton,
      showBenchmarkButton = showBenchmarkButton,
      isZip = isZip,
      unzipDir = unzipDir,
      llmPromptTemplates = llmPromptTemplates,
      llmSupportImage = llmSupportImage,
      llmSupportAudio = llmSupportAudio,
      imported = imported,
      estimatedPeakMemoryInBytes = estimatedPeakMemoryInBytes
    ).apply {
      // Copy over the internal state
      this.normalizedName = this@Model.normalizedName
      this.instance = this@Model.instance
      this.initializing = this@Model.initializing
      this.cleanUpAfterInit = this@Model.cleanUpAfterInit
      this.configValues = this@Model.configValues.toMutableMap()
      this.totalBytes = this@Model.totalBytes
    }
  }
}

enum class ModelDownloadStatusType {
  NOT_DOWNLOADED,
  PARTIALLY_DOWNLOADED,
  IN_PROGRESS,
  UNZIPPING,
  SUCCEEDED,
  FAILED,
}

data class ModelDownloadStatus(
  val status: ModelDownloadStatusType,
  val totalBytes: Long = 0,
  val receivedBytes: Long = 0,
  val errorMessage: String = "",
  val bytesPerSecond: Long = 0,
  val remainingMs: Long = 0,
  val retryCount: Int = 0,
  val maxRetries: Int = 3,
  val nextRetryDelayMs: Long = 0,
  val isRetrying: Boolean = false,
  val lastErrorTime: Long = 0
) {
  val progress: Float
    get() = if (totalBytes > 0) {
      (receivedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
    } else {
      0f
    }
    
  val progressPercent: Int
    get() = (progress * 100).toInt()
    
  val formattedRemainingTime: String
    get() = when {
      remainingMs <= 0 -> ""
      remainingMs < 60_000 -> "< 1 min"
      else -> {
        val minutes = remainingMs / 60_000
        "~$minutes min"
      }
    }
    
  val formattedSpeed: String
    get() = if (bytesPerSecond > 0) {
      "${formatFileSize(bytesPerSecond)}/s"
    } else {
      ""
    }
    
  val formattedReceived: String
    get() = formatFileSize(receivedBytes)
    
  val formattedTotal: String
    get() = formatFileSize(totalBytes)
    
  val hasRetryInfo: Boolean
    get() = retryCount > 0 && status == ModelDownloadStatusType.FAILED && maxRetries > 0
}

////////////////////////////////////////////////////////////////////////////////////////////////////
// Configs.

val MOBILENET_CONFIGS: List<Config> =
  listOf(
    NumberSliderConfig(
      key = ConfigKey.MAX_RESULT_COUNT,
      sliderMin = 1f,
      sliderMax = 5f,
      defaultValue = 3f,
      valueType = ValueType.INT,
    ),
    BooleanSwitchConfig(key = ConfigKey.USE_GPU, defaultValue = false),
  )

val IMAGE_GENERATION_CONFIGS: List<Config> =
  listOf(
    NumberSliderConfig(
      key = ConfigKey.ITERATIONS,
      sliderMin = 5f,
      sliderMax = 50f,
      defaultValue = 10f,
      valueType = ValueType.INT,
      needReinitialization = false,
    )
  )

const val TEXT_CLASSIFICATION_INFO =
  "Model is trained on movie reviews dataset. Type a movie review below and see the scores of positive or negative sentiment."

const val TEXT_CLASSIFICATION_LEARN_MORE_URL =
  "https://ai.google.dev/edge/mediapipe/solutions/text/text_classifier"

const val IMAGE_CLASSIFICATION_INFO = ""

const val IMAGE_CLASSIFICATION_LEARN_MORE_URL = "https://ai.google.dev/edge/litert/android"

const val IMAGE_GENERATION_INFO =
  "Powered by [MediaPipe Image Generation API](https://ai.google.dev/edge/mediapipe/solutions/vision/image_generator/android)"

val MODEL_TEXT_CLASSIFICATION_MOBILEBERT: Model =
  Model(
    name = "MobileBert",
    downloadFileName = "bert_classifier.tflite",
    url =
      "https://storage.googleapis.com/mediapipe-models/text_classifier/bert_classifier/float32/latest/bert_classifier.tflite",
    sizeInBytes = 25707538L,
    info = TEXT_CLASSIFICATION_INFO,
    learnMoreUrl = TEXT_CLASSIFICATION_LEARN_MORE_URL,
  )

val MODEL_TEXT_CLASSIFICATION_AVERAGE_WORD_EMBEDDING: Model =
  Model(
    name = "Average word embedding",
    downloadFileName = "average_word_classifier.tflite",
    url =
      "https://storage.googleapis.com/mediapipe-models/text_classifier/average_word_classifier/float32/latest/average_word_classifier.tflite",
    sizeInBytes = 775708L,
    info = TEXT_CLASSIFICATION_INFO,
  )

val MODEL_IMAGE_CLASSIFICATION_MOBILENET_V1: Model =
  Model(
    name = "Mobilenet V1",
    downloadFileName = "mobilenet_v1.tflite",
    url = "https://storage.googleapis.com/tfweb/app_gallery_models/mobilenet_v1.tflite",
    sizeInBytes = 16900760L,
    extraDataFiles =
      listOf(
        ModelDataFile(
          name = "labels",
          url =
            "https://raw.githubusercontent.com/leferrad/tensorflow-mobilenet/refs/heads/master/imagenet/labels.txt",
          downloadFileName = "mobilenet_labels_v1.txt",
          sizeInBytes = 21685L,
        )
      ),
    configs = MOBILENET_CONFIGS,
    info = IMAGE_CLASSIFICATION_INFO,
    learnMoreUrl = IMAGE_CLASSIFICATION_LEARN_MORE_URL,
  )

val MODEL_IMAGE_CLASSIFICATION_MOBILENET_V2: Model =
  Model(
    name = "Mobilenet V2",
    downloadFileName = "mobilenet_v2.tflite",
    url = "https://storage.googleapis.com/tfweb/app_gallery_models/mobilenet_v2.tflite",
    sizeInBytes = 13978596L,
    extraDataFiles =
      listOf(
        ModelDataFile(
          name = "labels",
          url =
            "https://raw.githubusercontent.com/leferrad/tensorflow-mobilenet/refs/heads/master/imagenet/labels.txt",
          downloadFileName = "mobilenet_labels_v2.txt",
          sizeInBytes = 21685L,
        )
      ),
    configs = MOBILENET_CONFIGS,
    info = IMAGE_CLASSIFICATION_INFO,
  )

val MODEL_IMAGE_GENERATION_STABLE_DIFFUSION: Model =
  Model(
    name = "Stable diffusion",
    downloadFileName = "sd15.zip",
    isZip = true,
    unzipDir = "sd15",
    url = "https://storage.googleapis.com/tfweb/app_gallery_models/sd15.zip",
    sizeInBytes = 1906219565L,
    showRunAgainButton = false,
    showBenchmarkButton = false,
    info = IMAGE_GENERATION_INFO,
    configs = IMAGE_GENERATION_CONFIGS,
    learnMoreUrl = "https://huggingface.co/litert-community",
  )

val EMPTY_MODEL: Model =
  Model(name = "empty", downloadFileName = "empty.tflite", url = "", sizeInBytes = 0L)

////////////////////////////////////////////////////////////////////////////////////////////////////
// Model collections for different tasks.

val MODELS_TEXT_CLASSIFICATION: MutableList<Model> =
  mutableListOf(
    MODEL_TEXT_CLASSIFICATION_MOBILEBERT,
    MODEL_TEXT_CLASSIFICATION_AVERAGE_WORD_EMBEDDING,
  )

val MODELS_IMAGE_CLASSIFICATION: MutableList<Model> =
  mutableListOf(MODEL_IMAGE_CLASSIFICATION_MOBILENET_V1, MODEL_IMAGE_CLASSIFICATION_MOBILENET_V2)

val MODELS_IMAGE_GENERATION: MutableList<Model> =
  mutableListOf(MODEL_IMAGE_GENERATION_STABLE_DIFFUSION)
