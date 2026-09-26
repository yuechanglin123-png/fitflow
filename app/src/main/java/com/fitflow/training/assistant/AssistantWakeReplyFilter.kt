package com.fitflow.training.assistant

/** Ignore the locally played wake reply while retaining a user command after its echo. */
object AssistantWakeReplyFilter {
    private val prefixes=listOf("我在请说指令", "请说指令", "我在")

    fun userText(recognized:String):String? {
        val clean=AssistantSpeechText.normalize(recognized)
        if(clean.isBlank() || prefixes.any { it.startsWith(clean) }) return null
        val prefix=prefixes.firstOrNull { clean.startsWith(it) }
        return (if(prefix==null) clean else clean.removePrefix(prefix)).ifBlank { null }
    }
}
