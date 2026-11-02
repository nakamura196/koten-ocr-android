package com.nakamura196.kotenocr.data

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.PromptPrefix
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.flow.first

/** Whether the on-device model (Gemini Nano, through AICore) can be used on this device. */
enum class LocalAIStatus {
    AVAILABLE,
    /** Supported, but the model still has to be downloaded (done on the first translation). */
    DOWNLOADABLE,
    DOWNLOADING,
    /** The device (or its AICore) does not support it. */
    UNAVAILABLE;

    companion object {
        /** Maps ML Kit's `FeatureStatus` value. Unknown values are treated as unavailable. */
        fun fromFeatureStatus(status: Int): LocalAIStatus = when (status) {
            FeatureStatus.AVAILABLE -> AVAILABLE
            FeatureStatus.DOWNLOADABLE -> DOWNLOADABLE
            FeatureStatus.DOWNLOADING -> DOWNLOADING
            else -> UNAVAILABLE
        }
    }
}

/**
 * Translation with the on-device model, the Android counterpart of iOS's "Local AI (Apple)"
 * (`TranslationService.translateWithLocalAI`, Foundation Models). No API key, nothing leaves
 * the device. Same prompts as the cloud providers ([TranslationService.buildSystemPrompt]).
 */
object OnDeviceTranslator {

    /**
     * Gemini Nano's context is under 4,000 tokens for prompt + answer together, so much less
     * text than the cloud limit (5,000). Japanese runs about one token per character.
     */
    const val MAX_TEXT_LENGTH = 1500
    private const val MAX_OUTPUT_TOKENS = 2000

    private val model: GenerativeModel by lazy { Generation.getClient() }

    /** Never throws: a device without AICore reports [LocalAIStatus.UNAVAILABLE]. */
    suspend fun status(): LocalAIStatus = try {
        LocalAIStatus.fromFeatureStatus(model.checkStatus())
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        LocalAIStatus.UNAVAILABLE
    }

    suspend fun translate(
        text: String,
        targetLanguage: TargetLanguage,
        level: TranslationLevel
    ): String {
        if (text.length > MAX_TEXT_LENGTH) {
            throw TranslationException(TranslationException.ErrorCode.TOO_LONG)
        }
        try {
            when (status()) {
                LocalAIStatus.UNAVAILABLE ->
                    throw TranslationException(TranslationException.ErrorCode.LOCAL_UNAVAILABLE)
                LocalAIStatus.DOWNLOADABLE, LocalAIStatus.DOWNLOADING -> awaitDownload()
                LocalAIStatus.AVAILABLE -> Unit
            }
            val request = generateContentRequest(TextPart(text)) {
                promptPrefix = PromptPrefix(TranslationService.buildSystemPrompt(targetLanguage, level))
                temperature = 0.3f
                maxOutputTokens = MAX_OUTPUT_TOKENS
            }
            val result = model.generateContent(request).candidates.firstOrNull()?.text?.trim()
            if (result.isNullOrEmpty()) {
                throw TranslationException(TranslationException.ErrorCode.INVALID_RESPONSE)
            }
            return result
        } catch (e: GenAiException) {
            throw TranslationException(errorCodeFor(e.errorCode), e.message ?: "")
        }
    }

    /** Waits until the model download ends; a failed download is thrown as its [GenAiException]. */
    private suspend fun awaitDownload() {
        val end = model.download().first {
            it is DownloadStatus.DownloadCompleted || it is DownloadStatus.DownloadFailed
        }
        if (end is DownloadStatus.DownloadFailed) throw end.e
    }

    /** Maps ML Kit's `GenAiException.ErrorCode` to the messages the result screen shows. */
    fun errorCodeFor(genAiErrorCode: Int): TranslationException.ErrorCode = when (genAiErrorCode) {
        GenAiException.ErrorCode.NOT_AVAILABLE,
        GenAiException.ErrorCode.AICORE_INCOMPATIBLE,
        GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE -> TranslationException.ErrorCode.LOCAL_UNAVAILABLE
        GenAiException.ErrorCode.BUSY,
        GenAiException.ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED -> TranslationException.ErrorCode.LOCAL_BUSY
        GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED -> TranslationException.ErrorCode.LOCAL_BACKGROUND
        GenAiException.ErrorCode.NOT_ENOUGH_DISK_SPACE -> TranslationException.ErrorCode.LOCAL_NO_SPACE
        GenAiException.ErrorCode.REQUEST_TOO_LARGE -> TranslationException.ErrorCode.TOO_LONG
        else -> TranslationException.ErrorCode.SERVER
    }
}
