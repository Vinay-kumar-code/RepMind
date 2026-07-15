package com.example.workouttracker

import com.example.workouttracker.db.SessionEntity
import com.example.workouttracker.db.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class NotionSyncManager(private val repo: SessionRepository) {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun syncUnsyncedSessions(apiKey: String, dbId: String): Result<Int> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || dbId.isBlank()) return@withContext Result.failure(Exception("API Key or Database ID is empty"))

        try {
            val unsynced = repo.getUnsyncedSessions()
            if (unsynced.isEmpty()) return@withContext Result.success(0)

            val syncedIds = mutableListOf<Long>()

            for (session in unsynced) {
                val json = JSONObject().apply {
                    put("parent", JSONObject().apply { put("database_id", dbId) })
                    put("properties", JSONObject().apply {
                        put("Name", createTitleProp("Session ${session.id}"))
                        put("SessionID", createNumberProp(session.id))
                        put("Timestamp", createDateProp(session.timestampIso))
                        put("Exercise", createRichTextProp(session.exercise))
                        put("Reps", createNumberProp(session.reps))
                        put("Duration", createNumberProp(session.durationSeconds))
                        put("TotalXP", createNumberProp(Math.round(session.totalXp * 100.0) / 100.0))
                        put("Is Manual", createCheckboxProp(session.isManual))
                    })
                }

                val request = Request.Builder()
                    .url("https://api.notion.com/v1/pages")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Notion-Version", "2022-06-28")
                    .post(json.toString().toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        syncedIds.add(session.id)
                    } else {
                        // Log or handle error if needed
                    }
                }
            }

            if (syncedIds.isNotEmpty()) {
                repo.markSessionsSynced(syncedIds)
            }
            Result.success(syncedIds.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun retrieveAllSessions(apiKey: String, dbId: String): Result<Int> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || dbId.isBlank()) return@withContext Result.failure(Exception("API Key or Database ID is empty"))

        try {
            var hasMore = true
            var nextCursor: String? = null
            val fetchedSessions = mutableListOf<SessionEntity>()

            while (hasMore) {
                val jsonBody = JSONObject()
                if (nextCursor != null) {
                    jsonBody.put("start_cursor", nextCursor)
                }
                
                val request = Request.Builder()
                    .url("https://api.notion.com/v1/databases/$dbId/query")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Notion-Version", "2022-06-28")
                    .post(jsonBody.toString().toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext Result.failure(IOException("Failed to retrieve from Notion: ${response.code}"))

                    val responseBody = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                    val json = JSONObject(responseBody)
                    val results = json.getJSONArray("results")
                    
                    hasMore = json.optBoolean("has_more", false)
                    nextCursor = json.optString("next_cursor", "").takeIf { it.isNotEmpty() }
                    
                    for (i in 0 until results.length()) {
                        val page = results.getJSONObject(i)
                        val properties = page.optJSONObject("properties") ?: continue
                        
                        val sessionId = getNumberProp(properties, "SessionID")?.toLong() ?: 0L
                        val timestamp = getDateProp(properties, "Timestamp") ?: ""
                        val exercise = getRichTextProp(properties, "Exercise") ?: "Unknown"
                        val reps = getNumberProp(properties, "Reps")?.toInt() ?: 0
                        val duration = getNumberProp(properties, "Duration")?.toFloat() ?: 0f
                        val xp = getNumberProp(properties, "TotalXP")?.toFloat() ?: 0f
                        val isManual = getCheckboxProp(properties, "Is Manual")
                        
                        if (sessionId > 0 && timestamp.isNotEmpty()) {
                            fetchedSessions.add(
                                SessionEntity(
                                    id = sessionId,
                                    timestampIso = timestamp,
                                    exercise = exercise,
                                    reps = reps,
                                    durationSeconds = duration,
                                    totalXp = xp,
                                    syncedToNotion = true,
                                    isManual = isManual
                                )
                            )
                        }
                    }
                }
            }    
                if (fetchedSessions.isNotEmpty()) {
                    repo.resetAllProgress()
                    repo.insertAllSessions(fetchedSessions)
                    
                    // Recalculate total XP
                    var totalXp = 0f
                    for (s in fetchedSessions) { totalXp += s.totalXp }
                    repo.upsertProfile(totalXp)
                }
                
                Result.success(fetchedSessions.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun createTitleProp(text: String): JSONObject {
        return JSONObject().apply {
            put("title", JSONArray().apply {
                put(JSONObject().apply {
                    put("text", JSONObject().apply { put("content", text) })
                })
            })
        }
    }

    private fun createRichTextProp(text: String): JSONObject {
        return JSONObject().apply {
            put("rich_text", JSONArray().apply {
                put(JSONObject().apply {
                    put("text", JSONObject().apply { put("content", text) })
                })
            })
        }
    }

    private fun createNumberProp(number: Number): JSONObject {
        return JSONObject().apply { put("number", number) }
    }

    private fun createDateProp(dateStr: String): JSONObject {
        return JSONObject().apply {
            put("date", JSONObject().apply { put("start", dateStr) })
        }
    }

    private fun getNumberProp(properties: JSONObject, name: String): Number? {
        return properties.optJSONObject(name)?.optDouble("number")?.let { if (it.isNaN()) null else it }
    }

    private fun getDateProp(properties: JSONObject, name: String): String? {
        return properties.optJSONObject(name)?.optJSONObject("date")?.optString("start")
    }

    private fun getRichTextProp(properties: JSONObject, name: String): String? {
        val array = properties.optJSONObject(name)?.optJSONArray("rich_text")
        if (array != null && array.length() > 0) {
            return array.optJSONObject(0)?.optJSONObject("text")?.optString("content")
        }
        return null
    }

    private fun createCheckboxProp(checked: Boolean): JSONObject {
        return JSONObject().apply { put("checkbox", checked) }
    }

    private fun getCheckboxProp(properties: JSONObject, name: String): Boolean {
        return properties.optJSONObject(name)?.optBoolean("checkbox", false) ?: false
    }
}
