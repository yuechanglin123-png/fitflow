package com.fitflow.training.assistant

class AssistantIntentParser {
    fun parse(text: String): AssistantIntent {
        val clean = AssistantSpeechText.normalize(text)
            .removePrefix("请问").removePrefix("请").removePrefix("麻烦").removePrefix("帮我")
            .removeSuffix("吧").removeSuffix("一下")
        val command = when (clean) {
            "跳过休息", "结束休息" -> TrainingCommand.SKIP_REST
            "延长30秒休息时间", "延长三十秒休息时间", "延长30秒休息", "延长三十秒休息",
            "延长休息30秒", "延长休息三十秒", "延长30秒", "延长三十秒", "加30秒", "加三十秒",
            "休息加30秒", "休息加三十秒", "再休息30秒", "再休息三十秒" -> TrainingCommand.EXTEND_REST_30
            "暂停训练", "暂停锻炼", "暂停一下训练", "暂停一下锻炼" -> TrainingCommand.PAUSE
            "完成本组", "本组完成", "这组完成了", "完成这一组", "这一组做完了", "这组做完了", "本组做完了" -> TrainingCommand.COMPLETE_SET
            else -> null
        }
        if (command != null) return AssistantIntent.Command(command)
        val question = when (clean) {
            "现在几点", "现在几点了", "现在时间", "现在是什么时间" -> LocalQuestion.TIME
            "今天几号", "今天星期几", "今天日期", "今天是几号" -> LocalQuestion.DATE
            "现在练什么", "当前动作", "现在是什么动作" -> LocalQuestion.CURRENT_EXERCISE
            "还剩几组", "剩余组数", "还有几组", "现在第几组" -> LocalQuestion.REMAINING_SETS
            "还要休息多久", "休息还剩多久", "剩余休息时间" -> LocalQuestion.REST_REMAINING
            else -> null
        }
        if (question != null) return AssistantIntent.Query(question)
        val weather = Regex("([\\p{IsHan}]{0,12}?)(?:今天)?天气(?:怎么样|如何|怎样)?").matchEntire(clean)
        if (weather != null) {
            val city = weather.groupValues[1].ifBlank { null }
            if (city == null || listOf("不要", "别", "不是", "然后", "完成", "暂停", "我说").none { city.contains(it) })
                return AssistantIntent.Weather(city)
        }
        return AssistantIntent.Unsupported
    }
}
