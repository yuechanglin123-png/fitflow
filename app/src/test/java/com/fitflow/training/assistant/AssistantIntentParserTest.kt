package com.fitflow.training.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantIntentParserTest {
    private val parser = AssistantIntentParser()
    @Test fun keywordAnywhereInAwakeUtteranceSelectsItsOperation() {
        mapOf(
            "现在这组完成了吗" to TrainingCommand.COMPLETE_SET,
            "不要完成" to TrainingCommand.COMPLETE_SET,
            "多休息一下" to TrainingCommand.SKIP_REST,
            "跳过当前间歇" to TrainingCommand.SKIP_REST,
            "不要暂停" to TrainingCommand.PAUSE,
            "延长休息" to TrainingCommand.SKIP_REST,
            "还要休息多久" to TrainingCommand.SKIP_REST,
        ).forEach { (text, command) -> assertEquals(text, AssistantIntent.Command(command), parser.parse(text)) }
        assertEquals(AssistantIntent.Command(TrainingCommand.EXTEND_REST_30), parser.parse("延长三十秒"))
    }
    @Test fun acceptsEverydayPhrasingAndAppliesBroadKeywords() {
        mapOf(
            "帮我跳过休息吧" to TrainingCommand.SKIP_REST,
            "麻烦暂停一下训练" to TrainingCommand.PAUSE,
            "这一组做完了" to TrainingCommand.COMPLETE_SET,
            "休息加三十秒" to TrainingCommand.SKIP_REST,
            "再休息三十秒" to TrainingCommand.SKIP_REST,
            "帮我不要完成本组" to TrainingCommand.COMPLETE_SET,
            "休息加十三秒" to TrainingCommand.SKIP_REST,
        ).forEach { (text,command)-> assertEquals(text,AssistantIntent.Command(command),parser.parse(text)) }
        assertEquals(AssistantIntent.MultipleCommands,parser.parse("先暂停训练再跳过休息"))
    }

    @Test fun parsesCommandsAndAliases() {
        mapOf(
            "跳过休息" to TrainingCommand.SKIP_REST,
            "延长30秒休息时间" to TrainingCommand.SKIP_REST,
            "延长三十秒休息时间" to TrainingCommand.SKIP_REST,
            "加三十秒" to TrainingCommand.EXTEND_REST_30,
            "暂停训练" to TrainingCommand.PAUSE,
            "完成本组" to TrainingCommand.COMPLETE_SET,
            "这组完成了！" to TrainingCommand.COMPLETE_SET,
        ).forEach { (text, expected) -> assertEquals(text, AssistantIntent.Command(expected), parser.parse(text)) }
    }

    @Test fun rejectsMultipleOperationsAndUnknownSpeech() {
        listOf(
            "完成本组然后暂停训练", "完成后休息", "跳过暂停",
            "这组做完了然后暂停训练", "加三十秒然后完成本组",
            "这组做完了然后加三十秒",
        ).forEach {
            assertEquals(it, AssistantIntent.MultipleCommands, parser.parse(it))
        }
        listOf("延长三分钟", "").forEach { assertEquals(it,AssistantIntent.Unsupported,parser.parse(it)) }
    }

    @Test fun parsesQuestionsAndCity() {
        assertEquals(AssistantIntent.Query(LocalQuestion.TIME), parser.parse("现在几点？"))
        assertEquals(AssistantIntent.Query(LocalQuestion.REMAINING_SETS), parser.parse("还剩几组"))
        assertEquals(AssistantIntent.Command(TrainingCommand.SKIP_REST), parser.parse("还要休息多久"))
        assertEquals(AssistantIntent.Weather(null), parser.parse("今天天气怎么样"))
        assertEquals(AssistantIntent.Weather("北京"), parser.parse("北京今天天气"))
        assertEquals(AssistantIntent.Weather("上海"), parser.parse("请问上海今天天气怎么样？"))
    }
}
