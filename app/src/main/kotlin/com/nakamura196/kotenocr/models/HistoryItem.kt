package com.nakamura196.kotenocr.models

import com.nakamura196.kotenocr.ocr.Detection
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class HistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val text: String,
    val detections: List<Detection>,
    val imageFileName: String,
    val translatedText: String? = null
) {
    val date: Date
        get() = Date(timestamp)

    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("timestamp", timestamp)
        json.put("date", timestamp)
        json.put("text", text)
        json.put("imageFileName", imageFileName)
        json.put("translatedText", translatedText ?: JSONObject.NULL)

        val detectionsArray = JSONArray()
        for (det in detections) {
            val detJson = JSONObject()
            val boxArray = JSONArray()
            for (v in det.box) boxArray.put(v)
            detJson.put("box", boxArray)
            detJson.put("score", det.score.toDouble())
            detJson.put("classIndex", det.classIndex)
            detJson.put("className", det.className)
            detJson.put("text", det.text)
            detJson.put("id", det.id)
            detJson.put("predCharCount", det.predCharCount.toDouble())
            detectionsArray.put(detJson)
        }
        json.put("detections", detectionsArray)

        return json
    }

    companion object {
        fun fromJson(json: JSONObject): HistoryItem {
            val detectionsArray = json.getJSONArray("detections")
            val detections = mutableListOf<Detection>()
            for (i in 0 until detectionsArray.length()) {
                val detJson = detectionsArray.getJSONObject(i)
                val boxArray = detJson.getJSONArray("box")
                val box = IntArray(boxArray.length()) { boxArray.getInt(it) }
                detections.add(
                    Detection(
                        id = detJson.optInt("id", i),
                        box = box,
                        score = detJson.getDouble("score").toFloat(),
                        classIndex = detJson.optInt("classIndex", detJson.optInt("classId", 1)),
                        className = detJson.getString("className"),
                        text = detJson.optString("text", ""),
                        predCharCount = detJson.optDouble("predCharCount", 100.0).toFloat()
                    )
                )
            }

            val translatedText = if (json.isNull("translatedText")) null
            else json.optString("translatedText", null)

            val ts = if (json.has("timestamp")) {
                json.getLong("timestamp")
            } else {
                json.optLong("date", System.currentTimeMillis())
            }

            return HistoryItem(
                id = json.getString("id"),
                timestamp = ts,
                text = json.getString("text"),
                detections = detections,
                imageFileName = json.getString("imageFileName"),
                translatedText = translatedText
            )
        }

        private val dateFormatter = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

        fun formatDate(date: Date): String = dateFormatter.format(date)
    }
}
