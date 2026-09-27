package com.fitflow.training.assistant

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ModelCorpusTest {
    @Test fun historicalRecognitionKeepsWakeBoundariesAcrossNoiseConditions() {
        val samples=Json.parseToJsonElement(javaClass.getResource("/assistant/asr-v9-corpus.json")!!.readText()).jsonArray
        val parser=AssistantIntentParser()
        val correct=mutableMapOf<String,Int>()
        val positive=mutableMapOf<String,Int>()
        samples.forEach { element ->
            val s=element.jsonObject
            val condition=s["condition"]!!.jsonPrimitive.content
            val expected=s["expected"]!!.jsonPrimitive.content
            // Old extension and rest-status labels now intentionally resolve to skip-rest.
            if(expected=="EXTEND_REST_30" || expected=="REST_REMAINING") return@forEach
            // Historical ASR fixtures were recorded with the retired wake name.
            // Substitute only that exact prefix to keep testing command parsing.
            val recognized=s["recognized"]!!.jsonPrimitive.content.replaceFirst(Regex("^小练小练"),"你好教练")
            val afterWake=AssistantSpeechText.afterWake(recognized)
            val command=if(s["requiresWake"]!!.jsonPrimitive.boolean) afterWake else recognized
            val intent=parser.parse(command.orEmpty())
            if(expected=="no_action") {
                // Negation samples with one operation word intentionally changed under
                // the new policy. Keep asserting the old unsupported and mixed cases.
                val broadCategories=listOf("完成" in command.orEmpty(),
                    "休息" in command.orEmpty() || "跳过" in command.orEmpty(),
                    "暂停" in command.orEmpty()).count { it }
                if(!s["requiresWake"]!!.jsonPrimitive.boolean) {
                    assertNull("${s["file"]} $condition: $recognized",afterWake)
                } else if(broadCategories==0) {
                    assertFalse("${s["file"]} $condition: $recognized",intent is AssistantIntent.Command)
                } else if(broadCategories>1) {
                    assertEquals("${s["file"]} $condition: $recognized",AssistantIntent.MultipleCommands,intent)
                }
            } else {
                positive[condition]=(positive[condition] ?: 0)+1
                val actual=if(command==null) "no_wake" else if(command.isEmpty() && afterWake!=null) "wake" else when(intent) {
                    is AssistantIntent.Command -> intent.value.name
                    is AssistantIntent.Query -> intent.value.name
                    is AssistantIntent.Weather -> "WEATHER:${intent.city}"
                    AssistantIntent.MultipleCommands -> "multiple_commands"
                    AssistantIntent.Unsupported -> "unsupported"
                }
                if(actual==expected) correct[condition]=(correct[condition] ?: 0)+1
            }
        }
        listOf("clean","noise10db").forEach { condition ->
            assertEquals(30,positive[condition])
            assertTrue("$condition: ${correct[condition]}/${positive[condition]}",(correct[condition] ?: 0)>=27)
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
