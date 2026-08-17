package com.example.workouttracker

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ManualWorkoutType(
    val id: String,
    val name: String,
    val isDurationBased: Boolean,
    val isCustom: Boolean = false
)

class ManualWorkoutPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("manual_workout_prefs", Context.MODE_PRIVATE)
    
    private val _workoutTypes = MutableStateFlow<List<ManualWorkoutType>>(emptyList())
    val workoutTypes: StateFlow<List<ManualWorkoutType>> = _workoutTypes.asStateFlow()

    companion object {
        val DEFAULT_TYPES = listOf(
            ManualWorkoutType("default_pushups", "Pushups", isDurationBased = false, isCustom = false),
            ManualWorkoutType("default_squats", "Squats", isDurationBased = false, isCustom = false),
            ManualWorkoutType("default_lunges", "Lunges", isDurationBased = false, isCustom = false),
            ManualWorkoutType("default_bicep_left", "Bicep Curl - Left", isDurationBased = false, isCustom = false),
            ManualWorkoutType("default_bicep_right", "Bicep Curl - Right", isDurationBased = false, isCustom = false),
            ManualWorkoutType("default_shoulder_press", "Shoulder Press", isDurationBased = false, isCustom = false),
            ManualWorkoutType("default_pullups", "Pullups", isDurationBased = false, isCustom = false),
            ManualWorkoutType("default_jumping_jacks", "Jumping Jacks", isDurationBased = false, isCustom = false),
            ManualWorkoutType("default_running", "Running", isDurationBased = true, isCustom = false),
            ManualWorkoutType("default_cycling", "Cycling", isDurationBased = true, isCustom = false),
            ManualWorkoutType("default_swimming", "Swimming", isDurationBased = true, isCustom = false),
            ManualWorkoutType("default_plank", "Plank", isDurationBased = true, isCustom = false)
        )
        private const val KEY_CUSTOM_TYPES = "custom_workout_types_json"
    }

    init {
        loadTypes()
    }

    private fun loadTypes() {
        val jsonString = prefs.getString(KEY_CUSTOM_TYPES, null)
        val customTypes = mutableListOf<ManualWorkoutType>()
        
        if (!jsonString.isNullOrBlank()) {
            try {
                val array = JSONArray(jsonString)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    customTypes.add(
                        ManualWorkoutType(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            name = obj.getString("name"),
                            isDurationBased = obj.optBoolean("isDurationBased", false),
                            isCustom = true
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _workoutTypes.value = DEFAULT_TYPES + customTypes
    }

    private fun saveCustomTypes(types: List<ManualWorkoutType>) {
        val customOnly = types.filter { it.isCustom }
        val array = JSONArray()
        for (item in customOnly) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("isDurationBased", item.isDurationBased)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_CUSTOM_TYPES, array.toString()).apply()
        _workoutTypes.value = DEFAULT_TYPES + customOnly
    }

    fun addCustomType(name: String, isDurationBased: Boolean): ManualWorkoutType {
        val trimmed = name.trim()
        val newType = ManualWorkoutType(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            isDurationBased = isDurationBased,
            isCustom = true
        )
        val current = _workoutTypes.value.toMutableList()
        current.add(newType)
        saveCustomTypes(current)
        return newType
    }

    fun updateCustomType(id: String, newName: String, isDurationBased: Boolean) {
        val current = _workoutTypes.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            val item = current[index]
            if (item.isCustom) {
                current[index] = item.copy(name = newName.trim(), isDurationBased = isDurationBased)
                saveCustomTypes(current)
            }
        }
    }

    fun deleteCustomType(id: String) {
        val current = _workoutTypes.value.toMutableList()
        val index = current.indexOfFirst { it.id == id && it.isCustom }
        if (index != -1) {
            current.removeAt(index)
            saveCustomTypes(current)
        }
    }

    fun resetToDefaults() {
        prefs.edit().remove(KEY_CUSTOM_TYPES).apply()
        _workoutTypes.value = DEFAULT_TYPES
    }
}
