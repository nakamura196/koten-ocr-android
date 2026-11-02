package com.nakamura196.kotenocr.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class TranslationException(
    val errorCode: ErrorCode,
    message: String = ""
) : Exception(message) {
    enum class ErrorCode {
        NO_KEY,
        TOO_LONG,
        SERVER,
        INVALID_RESPONSE,
        /** On-device model: not supported on this device. */
        LOCAL_UNAVAILABLE,
        /** On-device model: busy or the per-app quota is used up; try again later. */
        LOCAL_BUSY,
        /** On-device model: refused because the app went to the background. */
        LOCAL_BACKGROUND,
        /** On-device model: not enough storage to download it. */
        LOCAL_NO_SPACE
    }
}

object TranslationService {

    private const val MAX_TEXT_LENGTH = 5000

    suspend fun translate(
        text: String,
        apiKey: String,
        endpoint: String,
        model: String,
        targetLanguage: TargetLanguage = TargetLanguage.JA,
        level: TranslationLevel = TranslationLevel.GENERAL
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw TranslationException(TranslationException.ErrorCode.NO_KEY)
        }
        if (text.length > MAX_TEXT_LENGTH) {
            throw TranslationException(TranslationException.ErrorCode.TOO_LONG)
        }

        val systemPrompt = buildSystemPrompt(targetLanguage, level)

        val messagesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", text)
            })
        }

        val requestBody = JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
            put("temperature", 0.3)
            put("max_tokens", 2000)
        }

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            connectTimeout = 60_000
            readTimeout = 60_000
            doOutput = true
        }

        try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val responseBody = stream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
            } ?: ""

            if (responseCode !in 200..299) {
                val errorMessage = try {
                    JSONObject(responseBody)
                        .getJSONObject("error")
                        .getString("message")
                } catch (_: Exception) {
                    responseBody.take(200).ifEmpty { "HTTP $responseCode" }
                }
                throw TranslationException(TranslationException.ErrorCode.SERVER, errorMessage)
            }

            val json = JSONObject(responseBody)
            val choices = json.optJSONArray("choices")
                ?: throw TranslationException(TranslationException.ErrorCode.INVALID_RESPONSE)
            if (choices.length() == 0) {
                throw TranslationException(TranslationException.ErrorCode.INVALID_RESPONSE)
            }

            val content = choices.getJSONObject(0)
                .getJSONObject("message")
                .getString("content")

            content.trim()
        } catch (e: TranslationException) {
            throw e
        } catch (e: Exception) {
            throw TranslationException(
                TranslationException.ErrorCode.SERVER,
                e.localizedMessage ?: "Network error"
            )
        } finally {
            connection.disconnect()
        }
    }

    /** Shared with [OnDeviceTranslator] so both use the same instructions (as on iOS). */
    internal fun buildSystemPrompt(
        targetLanguage: TargetLanguage,
        level: TranslationLevel
    ): String {
        return when (targetLanguage) {
            TargetLanguage.JA -> when (level) {
                TranslationLevel.GENERAL -> """
                    あなたは古典日本語の専門家です。与えられた古文を現代日本語に翻訳してください。
                    以下の形式で出力してください：

                    【現代語訳】
                    自然で読みやすい現代語訳を書いてください。

                    【解説】
                    この文章がどのような作品・資料のものか（わかる場合）、時代背景や内容の簡単な説明を2〜3文で書いてください。
                """.trimIndent()

                TranslationLevel.STUDENT -> """
                    あなたは古典日本語の専門家です。与えられた古文を学習者向けに詳しく翻訳・解説してください。
                    以下の形式で出力してください：

                    【現代語訳】
                    原文に忠実で正確な現代語訳を書いてください。

                    【語注】
                    重要な古語・文法事項を箇条書きで説明してください（例：「あけぼの」＝夜明け）。

                    【解説】
                    作品名（わかる場合）、時代、ジャンル、文学的特徴などを説明してください。
                """.trimIndent()

                TranslationLevel.RESEARCHER -> """
                    あなたは古典日本語・古典文学の研究者です。与えられた古文を学術的に正確に分析してください。
                    以下の形式で出力してください：

                    【直訳】
                    原文の構造に忠実な逐語的翻訳を書いてください。

                    【語釈】
                    重要な語句について、語義・用法・文法的説明を箇条書きで記してください。

                    【解題】
                    作品・資料の同定（可能な場合）、成立時期、ジャンル、書誌的情報、文学史的・歴史的位置づけについて記してください。
                """.trimIndent()
            }

            TargetLanguage.EN -> when (level) {
                TranslationLevel.GENERAL -> """
                    You are an expert in classical Japanese literature. Translate the given classical Japanese (kobun/kuzushiji) text into modern English.
                    Use the following format:

                    [Translation]
                    Provide a natural, readable English translation.

                    [Commentary]
                    Briefly describe what kind of text/work this is (if identifiable), its historical period, and context in 2-3 sentences.
                """.trimIndent()

                TranslationLevel.STUDENT -> """
                    You are an expert in classical Japanese literature. Translate and annotate the given classical Japanese text for learners.
                    Use the following format:

                    [Translation]
                    Provide an accurate English translation faithful to the original.

                    [Vocabulary]
                    List important classical Japanese terms with their readings and meanings (e.g., あけぼの (akebono) = dawn).

                    [Commentary]
                    Explain the work (if identifiable), its period, genre, and literary significance.
                """.trimIndent()

                TranslationLevel.RESEARCHER -> """
                    You are a scholar of classical Japanese language and literature. Provide an academic analysis of the given classical Japanese text.
                    Use the following format:

                    [Literal Translation]
                    Provide a word-by-word translation preserving the original syntactic structure.

                    [Glossary]
                    For key terms, provide: reading, meaning, grammatical analysis, and usage notes.

                    [Bibliographic Note]
                    Identify the work (if possible), its date of composition, genre, bibliographic details, and place in literary/historical context.
                """.trimIndent()
            }
        }
    }
}
