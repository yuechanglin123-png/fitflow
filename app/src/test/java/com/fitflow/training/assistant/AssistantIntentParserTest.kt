package com.fitflow.training.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantIntentParserTest {
    private val parser = AssistantIntentParser()
    @Test fun acceptsEverydayPhrasingWithoutGuessingDifferentDurations() {
        mapOf(
            "帮我跳过休息吧" to TrainingCommand.SKIP_REST,
            "麻烦暂停一下训练" to TrainingCommand.PAUSE,
            "这一组做完了" to TrainingCommand.COMPLETE_SET,
            "休息加三十秒" to TrainingCommand.EXTEND_REST_30,
            "再休息三十秒" to TrainingCommand.EXTEND_REST_30,
        ).forEach { (text,command)-> assertEquals(text,AssistantIntent.Command(command),parser.parse(text)) }
        listOf("帮我不要完成本组", "是不是完成本组", "完成本组了吗", "先暂停训练再跳过休息", "休息加十三秒", "再休息三分钟")
            .forEach { assertEquals(it,AssistantIntent.Unsupported,parser.parse(it)) }
    }

    @Test fun parsesCommandsAndAliases() {
        mapOf(
            "跳过休息" to TrainingCommand.SKIP_REST,
            "延长30秒休息时间" to TrainingCommand.EXTEND_REST_30,
            "延长三十秒休息时间" to TrainingCommand.EXTEND_REST_30,
            "加三十秒" to TrainingCommand.EXTEND_REST_30,
            "暂停训练" to TrainingCommand.PAUSE,
            "完成本组" to TrainingCommand.COMPLETE_SET,
            "这组完成了！" to TrainingCommand.COMPLETE_SET,
        ).forEach { (text, expected) -> assertEquals(text, AssistantIntent.Command(expected), parser.parse(text)) }
    }

    @Test fun refusesNegationCompoundAndAmbiguousSpeech() {
        listOf("不要完成本组", "别暂停训练", "完成本组然后暂停训练", "我刚才说了完成本组", "延长三分钟", "完成", "").forEach {
            assertEquals(it, AssistantIntent.Unsupported, parser.parse(it))
        }
    }

    @Test fun parsesQuestionsAndCity() {
        assertEquals(AssistantIntent.Query(LocalQuestion.TIME), parser.parse("现在几点？"))
        assertEquals(AssistantIntent.Query(LocalQuestion.REMAINING_SETS), parser.parse("还剩几组"))
        assertEquals(AssistantIntent.Weather(null), parser.parse("今天天气怎么样"))
        assertEquals(AssistantIntent.Weather("北京"), parser.parse("北京今天天气"))
        assertEquals(AssistantIntent.Weather("上海"), parser.parse("请问上海今天天气怎么样？"))
    }
}
