package com.fitflow.training.assistant

import com.fitflow.training.plan.PlannedExercise
import com.fitflow.training.session.*
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout

class AssistantAnswers(
    private val snapshot: ()->SessionSnapshot?,
    private val defaultCity: ()->WeatherCity?,
    private val exerciseName: (PlannedExercise)->String,
    private val weather: WeatherRepository,
    private val clock: ()->ZonedDateTime = { ZonedDateTime.now() },
) {
    suspend fun answer(intent:AssistantIntent):String = when(intent) {
        is AssistantIntent.Query -> local(intent.value)
        is AssistantIntent.Weather -> try {
            withTimeout(12_000) {
                val saved=defaultCity()
                val city=if(intent.city==null || intent.city==saved?.name || intent.city+"市"==saved?.name) saved
                    else {
                        val matches=weather.searchCities(intent.city)
                        if(matches.size>1) return@withTimeout "找到多个同名地点，请在助教设置中选择具体城市后再查询"
                        matches.singleOrNull()
                    }
                if(city==null) return@withTimeout if(intent.city==null) "请先在助教设置中设置默认城市" else "没有找到这个城市，请在助教设置中搜索选择"
                val result=weather.weather(city)
                "${city.name}，${result.description}，当前${number(result.temperature)}摄氏度，今天${number(result.low)}到${number(result.high)}摄氏度。天气来自Open Meteo"
            }
        } catch(e:kotlinx.coroutines.TimeoutCancellationException) { "天气查询超时，请稍后重试" }
          catch(e:CancellationException) { throw e }
          catch(e:Exception) { "暂时无法获取天气，请检查网络后重试" }
        else -> "请说时间、天气或训练进度问题"
    }
    private fun number(value:Double)=java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    private fun local(question:LocalQuestion):String {
        val now=clock()
        if(question==LocalQuestion.TIME) return "现在是${now.hour}点${now.minute}分"
        if(question==LocalQuestion.DATE) return "今天是${now.monthValue}月${now.dayOfMonth}日，星期${listOf("一","二","三","四","五","六","日")[now.dayOfWeek.value-1]}"
        val s=snapshot() ?: return "尚未开始训练"
        if(s.phase==Phase.FINISHED) return "今天的训练已经完成"
        return when(question) {
            LocalQuestion.CURRENT_EXERCISE -> s.plan.exercises.firstOrNull { e -> e.blocks.any { it.id==s.currentBlockId } }
                ?.let { "当前动作是${exerciseName(it)}" } ?: "当前没有训练动作"
            LocalQuestion.REMAINING_SETS -> "还剩${s.plan.exercises.sumOf { e -> e.blocks.count { (s.completedBlockSets[it.id]?:0)==0 } }}组"
            LocalQuestion.REST_REMAINING -> if(s.phase!=Phase.RESTING) "当前没有休息倒计时" else {
                val remaining=if(s.isPaused) s.pausedRestRemainingMillis?:0 else ((s.restEndsAtEpochMs?:0)-now.toInstant().toEpochMilli()).coerceAtLeast(0)
                "${if(s.isPaused) "训练已暂停，" else ""}休息还剩${(remaining+999)/1000}秒"
            }
            else -> error("Handled above")
        }
    }
}
