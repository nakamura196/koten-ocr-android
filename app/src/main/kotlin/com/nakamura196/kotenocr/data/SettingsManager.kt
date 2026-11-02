package com.nakamura196.kotenocr.data

import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.LocaleList
import com.nakamura196.kotenocr.ocr.Deskew
import com.nakamura196.kotenocr.ocr.OCRMode
import java.util.Locale

enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromKey(key: String): ThemeMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

enum class AppLanguage(val key: String, val tag: String) {
    SYSTEM("system", ""),
    JA("ja", "ja"),
    EN("en", "en");

    companion object {
        fun fromKey(key: String): AppLanguage =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

enum class ApiProvider(
    val key: String,
    val displayName: String,
    val endpoint: String,
    val models: List<Pair<String, String>>
) {
    OPENAI(
        "openai", "OpenAI",
        "https://api.openai.com/v1/chat/completions",
        listOf("gpt-4o-mini" to "GPT-4o mini", "gpt-4o" to "GPT-4o", "gpt-4.1-mini" to "GPT-4.1 mini", "gpt-4.1-nano" to "GPT-4.1 nano")
    ),
    OPENROUTER(
        "openrouter", "OpenRouter",
        "https://openrouter.ai/api/v1/chat/completions",
        listOf("google/gemini-2.5-flash" to "Gemini 2.5 Flash", "anthropic/claude-sonnet-4" to "Claude Sonnet 4", "openai/gpt-4o-mini" to "GPT-4o mini", "deepseek/deepseek-chat-v3-0324" to "DeepSeek V3")
    ),
    GEMINI(
        "gemini", "Google Gemini",
        "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
        listOf("gemini-2.5-flash" to "Gemini 2.5 Flash", "gemini-2.5-pro" to "Gemini 2.5 Pro", "gemini-2.0-flash" to "Gemini 2.0 Flash")
    ),
    CUSTOM("custom", "Custom", "", emptyList());

    companion object {
        fun fromKey(key: String): ApiProvider =
            entries.firstOrNull { it.key == key } ?: OPENAI
    }
}

enum class TargetLanguage(val key: String) {
    JA("ja"),
    EN("en");

    companion object {
        fun fromKey(key: String): TargetLanguage =
            entries.firstOrNull { it.key == key } ?: JA
    }
}

enum class TranslationLevel(val key: String) {
    GENERAL("general"),
    STUDENT("student"),
    RESEARCHER("researcher");

    companion object {
        fun fromKey(key: String): TranslationLevel =
            entries.firstOrNull { it.key == key } ?: GENERAL
    }
}

enum class CoachMark(val prefKey: String) {
    SHUTTER("coach_seen_shutter"),
    CROP_CORNER("coach_seen_cropCorner")
}

class SettingsManager(context: Context) {

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences =
        context.getSharedPreferences("koten_ocr_prefs", Context.MODE_PRIVATE)

    var hasCompletedOnboarding: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()

    var hasSeenLaunchStamp: Boolean
        get() = prefs.getBoolean(KEY_HAS_SEEN_LAUNCH_STAMP, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_SEEN_LAUNCH_STAMP, value).apply()

    var themeMode: ThemeMode
        get() = ThemeMode.fromKey(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.key) ?: ThemeMode.SYSTEM.key)
        set(value) = prefs.edit().putString(KEY_THEME, value.key).apply()

    // Android 13+ keeps the per-app language itself (also editable in the system Settings),
    // so read it from there; older versions use the stored preference.
    var appLanguage: AppLanguage
        get() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val locales = appContext.getSystemService(LocaleManager::class.java)?.applicationLocales
                if (locales == null || locales.isEmpty) return AppLanguage.SYSTEM
                val lang = locales[0].language
                return AppLanguage.entries.firstOrNull { it.tag == lang } ?: AppLanguage.SYSTEM
            }
            return AppLanguage.fromKey(prefs.getString(KEY_LANGUAGE, AppLanguage.SYSTEM.key) ?: AppLanguage.SYSTEM.key)
        }
        set(value) {
            prefs.edit().putString(KEY_LANGUAGE, value.key).apply()
            applyLanguage(value)
        }

    var saveToGallery: Boolean
        get() = prefs.getBoolean(KEY_SAVE_TO_GALLERY, false)
        set(value) = prefs.edit().putBoolean(KEY_SAVE_TO_GALLERY, value).apply()

    var showTiltGuide: Boolean
        get() = prefs.getBoolean(KEY_SHOW_TILT_GUIDE, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_TILT_GUIDE, value).apply()

    var autoDeskew: Boolean
        get() = prefs.getBoolean(Deskew.SETTINGS_KEY, true)
        set(value) = prefs.edit().putBoolean(Deskew.SETTINGS_KEY, value).apply()

    var preferredOCRMode: OCRMode
        get() = OCRMode.fromKey(prefs.getString(KEY_PREFERRED_OCR_MODE, OCRMode.KOTEN.key))
        set(value) = prefs.edit().putString(KEY_PREFERRED_OCR_MODE, value.key).apply()

    fun hasSeenCoachMark(mark: CoachMark): Boolean =
        prefs.getBoolean(mark.prefKey, false)

    fun markCoachMarkSeen(mark: CoachMark) {
        prefs.edit().putBoolean(mark.prefKey, true).apply()
    }

    fun resetCoachMarks() {
        val editor = prefs.edit()
        for (mark in CoachMark.entries) {
            editor.remove(mark.prefKey)
        }
        editor.apply()
    }

    var apiProvider: ApiProvider
        get() = ApiProvider.fromKey(prefs.getString(KEY_API_PROVIDER, ApiProvider.OPENAI.key) ?: ApiProvider.OPENAI.key)
        set(value) {
            prefs.edit().putString(KEY_API_PROVIDER, value.key).apply()
            if (value != ApiProvider.CUSTOM) {
                apiEndpoint = value.endpoint
                if (value.models.none { it.first == apiModel }) {
                    apiModel = value.models.first().first
                }
            }
        }

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var apiEndpoint: String
        get() = prefs.getString(KEY_API_ENDPOINT, DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT
        set(value) = prefs.edit().putString(KEY_API_ENDPOINT, value).apply()

    var apiModel: String
        get() = prefs.getString(KEY_API_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString(KEY_API_MODEL, value).apply()

    var targetLanguage: TargetLanguage
        get() = TargetLanguage.fromKey(prefs.getString(KEY_TARGET_LANG, TargetLanguage.JA.key) ?: TargetLanguage.JA.key)
        set(value) = prefs.edit().putString(KEY_TARGET_LANG, value.key).apply()

    var translationLevel: TranslationLevel
        get() = TranslationLevel.fromKey(prefs.getString(KEY_TRANSLATION_LEVEL, TranslationLevel.GENERAL.key) ?: TranslationLevel.GENERAL.key)
        set(value) = prefs.edit().putString(KEY_TRANSLATION_LEVEL, value.key).apply()

    fun applyLanguage(language: AppLanguage = appLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = appContext.getSystemService(LocaleManager::class.java)
            localeManager?.applicationLocales = if (language == AppLanguage.SYSTEM) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(language.tag)
            }
        } else if (language != AppLanguage.SYSTEM) {
            Locale.setDefault(Locale.forLanguageTag(language.tag))
        }
    }

    companion object {
        private const val KEY_ONBOARDING = "has_completed_onboarding"
        private const val KEY_HAS_SEEN_LAUNCH_STAMP = "hasSeenLaunchStamp"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_SAVE_TO_GALLERY = "save_to_gallery"
        private const val KEY_SHOW_TILT_GUIDE = "showTiltGuide"
        private const val KEY_PREFERRED_OCR_MODE = "preferredOCRMode"
        private const val KEY_API_PROVIDER = "api_provider"
        private const val KEY_API_KEY = "translation_api_key"
        private const val KEY_API_ENDPOINT = "translation_api_endpoint"
        private const val KEY_API_MODEL = "translation_api_model"
        private const val KEY_TARGET_LANG = "target_language"
        private const val KEY_TRANSLATION_LEVEL = "translation_level"
        const val DEFAULT_ENDPOINT = "https://api.openai.com/v1/chat/completions"
        const val DEFAULT_MODEL = "gpt-4o-mini"
    }
}
