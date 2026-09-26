package com.fitflow.training.assistant

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ModelCorpusTest {
    @Test fun tunedRecognitionPreservesCommandsAndRejectsNegativeSpeechAcrossNoiseConditions() {
        val samples=Json.parseToJsonElement(javaClass.getResource("/assistant/asr-v9-corpus.json")!!.readText()).jsonArray
        val parser=AssistantIntentParser()
        val correct=mutableMapOf<String,Int>()
        val positive=mutableMapOf<String,Int>()
        samples.forEach { element ->
            val s=element.jsonObject
            val condition=s["condition"]!!.jsonPrimitive.content
            val expected=s["expected"]!!.jsonPrimitive.content
            // Historical ASR fixtures were recorded with the retired wake name.
            // Substitute only that exact prefix to keep testing command parsing.
            val recognized=s["recognized"]!!.jsonPrimitive.content.replaceFirst(Regex("^小练小练"),"铁蛋")
            val afterWake=AssistantSpeechText.afterWake(recognized)
            val command=if(s["requiresWake"]!!.jsonPrimitive.boolean) afterWake else recognized
            val intent=parser.parse(command.orEmpty())
            if(expected=="no_action") {
                // Ambient phrases outside an awake turn never authorize commands.
                assertFalse("${s["file"]} $condition: $recognized",parser.parse(afterWake.orEmpty()) is AssistantIntent.Command)
            } else {
                positive[condition]=(positive[condition] ?: 0)+1
                val actual=if(command==null) "no_wake" else if(command.isEmpty() && afterWake!=null) "wake" else when(intent) {
                    is AssistantIntent.Command -> intent.value.name
                    is AssistantIntent.Query -> intent.value.name
                    is AssistantIntent.Weather -> "WEATHER:${intent.city}"
                    AssistantIntent.Unsupported -> "unsupported"
                }
                if(actual==expected) correct[condition]=(correct[condition] ?: 0)+1
            }
        }
        listOf("clean","noise10db").forEach { condition ->
            assertEquals(40,positive[condition])
            assertTrue("$condition: ${correct[condition]}/${positive[condition]}",(correct[condition] ?: 0)>=36)
        }
    }
    @Test fun historicalOfflineRecognitionMeetsSyntheticCorpusGate() {
        val text=javaClass.getResource("/assistant/asr-corpus.json")!!.readText()
        val samples=Json.parseToJsonElement(text).jsonObject["samples"]!!.jsonArray
        val parser=AssistantIntentParser()
        var positive=0; var correct=0; var accidental=0
        samples.forEach { element ->
            val s=element.jsonObject
            val recognized=s["recognized"]!!.jsonPrimitive.content
            if(s["commandSample"]!!.jsonPrimitive.boolean) {
                positive++
                val expected=parser.parse(s["phrase"]!!.jsonPrimitive.content)
                assertNotEquals(AssistantIntent.Unsupported,expected)
                if(recognized.startsWith("小练小练") && parser.parse(recognized.removePrefix("小练小练"))==expected) correct++
            } else if(recognized.startsWith("小练小练") && parser.parse(recognized.removePrefix("小练小练")) is AssistantIntent.Command) accidental++
        }
        assertTrue(positive>=40)
        assertTrue("Correct $correct/$positive",correct.toDouble()/positive>=0.90)
        assertEquals(0,accidental)
    }
}
