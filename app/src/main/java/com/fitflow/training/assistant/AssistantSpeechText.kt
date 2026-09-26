package com.fitflow.training.assistant

/** Only known same-pronunciation spellings of the full repeated wake phrase. */
object AssistantSpeechText {
    private val punctuation=Regex("[\\s\\p{P}]")
    private val wake=Regex("^小[练炼恋链]小[练炼恋链]")
    fun normalize(text:String)=text.replace(punctuation,"")
    fun afterWake(text:String):String? {
        val clean=normalize(text)
        val match=wake.find(clean) ?: return null
        return clean.substring(match.value.length)
    }
}
