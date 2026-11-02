package com.nakamura196.kotenocr.data

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The on-device choice (iOS "Local AI") added to translation: which provider is the default,
 * when the API key is asked for, and how ML Kit's codes become the messages users see.
 */
class OnDeviceTranslatorTest {

    @Test
    fun newInstallDefaultsToOnDeviceWhereSupported() {
        assertEquals(ApiProvider.LOCAL_AI, ApiProvider.defaultFor(null, hasKey = { false }, localAISupported = true))
    }

    @Test
    fun newInstallOnUnsupportedDeviceKeepsOpenAI() {
        // Most Android phones (Pixel 9a included) have no Gemini Nano; do not default to a dead end.
        assertEquals(ApiProvider.OPENAI, ApiProvider.defaultFor(null, hasKey = { false }, localAISupported = false))
    }

    @Test
    fun storedProviderIsKept() {
        assertEquals(ApiProvider.OPENROUTER, ApiProvider.defaultFor("openrouter", hasKey = { false }, localAISupported = true))
        assertEquals(ApiProvider.LOCAL_AI, ApiProvider.defaultFor("localai", hasKey = { true }, localAISupported = false))
    }

    @Test
    fun keySavedWithoutProviderKeepsOldDefault() {
        // Before this change the default was OpenAI; such a user must not lose their setup.
        assertEquals(ApiProvider.OPENAI, ApiProvider.defaultFor(null, hasKey = { true }, localAISupported = true))
    }

    @Test
    fun onlyCloudProvidersNeedAKey() {
        assertFalse(ApiProvider.LOCAL_AI.requiresApiKey)
        ApiProvider.entries.filter { it != ApiProvider.LOCAL_AI }.forEach {
            assertTrue(it.name, it.requiresApiKey)
        }
    }

    @Test
    fun featureStatusMapping() {
        assertEquals(LocalAIStatus.AVAILABLE, LocalAIStatus.fromFeatureStatus(FeatureStatus.AVAILABLE))
        assertEquals(LocalAIStatus.DOWNLOADABLE, LocalAIStatus.fromFeatureStatus(FeatureStatus.DOWNLOADABLE))
        assertEquals(LocalAIStatus.DOWNLOADING, LocalAIStatus.fromFeatureStatus(FeatureStatus.DOWNLOADING))
        assertEquals(LocalAIStatus.UNAVAILABLE, LocalAIStatus.fromFeatureStatus(FeatureStatus.UNAVAILABLE))
        assertEquals(LocalAIStatus.UNAVAILABLE, LocalAIStatus.fromFeatureStatus(42))
    }

    @Test
    fun errorCodeMapping() {
        assertEquals(TranslationException.ErrorCode.LOCAL_UNAVAILABLE, OnDeviceTranslator.errorCodeFor(GenAiException.ErrorCode.NOT_AVAILABLE))
        assertEquals(TranslationException.ErrorCode.LOCAL_UNAVAILABLE, OnDeviceTranslator.errorCodeFor(GenAiException.ErrorCode.AICORE_INCOMPATIBLE))
        assertEquals(TranslationException.ErrorCode.LOCAL_BUSY, OnDeviceTranslator.errorCodeFor(GenAiException.ErrorCode.BUSY))
        assertEquals(TranslationException.ErrorCode.LOCAL_BUSY, OnDeviceTranslator.errorCodeFor(GenAiException.ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED))
        assertEquals(TranslationException.ErrorCode.LOCAL_BACKGROUND, OnDeviceTranslator.errorCodeFor(GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED))
        assertEquals(TranslationException.ErrorCode.LOCAL_NO_SPACE, OnDeviceTranslator.errorCodeFor(GenAiException.ErrorCode.NOT_ENOUGH_DISK_SPACE))
        assertEquals(TranslationException.ErrorCode.TOO_LONG, OnDeviceTranslator.errorCodeFor(GenAiException.ErrorCode.REQUEST_TOO_LARGE))
        assertEquals(TranslationException.ErrorCode.SERVER, OnDeviceTranslator.errorCodeFor(GenAiException.ErrorCode.UNKNOWN))
    }

    @Test
    fun onDeviceUsesTheSamePromptsAsCloud() {
        val p = TranslationService.buildSystemPrompt(TargetLanguage.JA, TranslationLevel.STUDENT)
        assertTrue(p.contains("【語注】"))
    }

    @Test
    fun onDeviceTextLimitLeavesRoomInTheContext() {
        // Context < 4,000 tokens for prompt + answer; ~1 token per Japanese character.
        val longestPrompt = TranslationLevel.entries.maxOf { TranslationService.buildSystemPrompt(TargetLanguage.JA, it).length }
        assertTrue(OnDeviceTranslator.MAX_TEXT_LENGTH + longestPrompt < 4000)
    }
}
