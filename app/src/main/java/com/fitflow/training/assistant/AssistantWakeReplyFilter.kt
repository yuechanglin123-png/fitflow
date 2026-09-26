package com.fitflow.training.assistant

/** Ignore the locally played wake reply while retaining a user command after its echo. */
object AssistantWakeReplyFilter {
    private const val reply="我在请说指令"
    private val fragments=reply.indices.flatMap { start ->
        (start+1..reply.length).map { end -> reply.substring(start,end) }
    }.distinct().sortedByDescending { it.length }

    fun userText(recognized:String):String? {
        val clean=AssistantSpeechText.normalize(recognized)
        if(clean.isBlank()) return null
        val prefix=fragments.firstOrNull { clean.startsWith(it) }
        return (if(prefix==null) clean else clean.removePrefix(prefix)).ifBlank { null }
    }
}
