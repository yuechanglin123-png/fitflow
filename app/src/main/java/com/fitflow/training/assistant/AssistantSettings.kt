package com.fitflow.training.assistant

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AssistantSettings(context: Context) {
    private val prefs = context.getSharedPreferences("assistant_settings", Context.MODE_PRIVATE)
    var voice: VoiceChoice
        get() = runCatching { VoiceChoice.valueOf(prefs.getString("voice", "FEMALE")!!) }.getOrDefault(VoiceChoice.FEMALE)
        set(value) { prefs.edit().putString("voice", value.name).apply() }
    var city: WeatherCity?
        get() = prefs.getString("city", null)?.let { runCatching { Json.decodeFromString<WeatherCity>(it) }.getOrNull() }
        set(value) { prefs.edit().putString("city", value?.let { Json.encodeToString(it) }).apply() }
}
