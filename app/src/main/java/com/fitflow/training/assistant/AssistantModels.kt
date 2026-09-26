package com.fitflow.training.assistant

enum class TrainingCommand { SKIP_REST, EXTEND_REST_30, PAUSE, COMPLETE_SET }
enum class VoiceChoice { MALE, FEMALE }
enum class LocalQuestion { TIME, DATE, CURRENT_EXERCISE, REMAINING_SETS, REST_REMAINING }
sealed interface AssistantIntent {
    data class Command(val value: TrainingCommand) : AssistantIntent
    data class Query(val value: LocalQuestion) : AssistantIntent
    data class Weather(val city: String?) : AssistantIntent
    data object Unsupported : AssistantIntent
}
data class SpeechResult(val turnId: Long, val text: String, val isFinal: Boolean)
interface SpeechRecognizerAdapter {
    suspend fun initialize()
    fun start(onResult: (SpeechResult) -> Unit, onError: (String) -> Unit, onReady: () -> Unit)
    fun stop()
    fun close()
}
interface SpeechSynthesizerAdapter {
    suspend fun initialize()
    suspend fun speak(text: String, voice: VoiceChoice)
    fun stop()
    fun close()
}
