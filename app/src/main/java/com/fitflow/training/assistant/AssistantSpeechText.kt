package com.fitflow.training.assistant

/** Require the exact wake name at the start of the utterance. */
object AssistantSpeechText {
    private val punctuation=Regex("[\\s\\p{P}]")
    private val wake=Regex("^你好教练")
    fun normalize(text:String)=text.replace(punctuation,"")
    fun afterWake(text:String):String? {
        val clean=normalize(text)
        val match=wake.find(clean) ?: return null
        return clean.substring(match.value.length)
    }
}
