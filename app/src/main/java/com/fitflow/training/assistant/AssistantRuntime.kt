package com.fitflow.training.assistant

import android.content.Context
import com.fitflow.training.assistant.speech.*
import com.fitflow.training.catalog.CatalogRepository
import com.fitflow.training.data.WorkoutRuntime
import kotlinx.coroutines.*

class AssistantRuntime private constructor(context:Context) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    val settings=AssistantSettings(context)
    val weather:WeatherRepository=OpenMeteoWeatherRepository()
    private val workout=WorkoutRuntime.get(context)
    private val catalog=CatalogRepository.load(context)
    private val answers=AssistantAnswers({workout.session.session.value},{settings.city},
        {it.customName?:catalog.all().firstOrNull { exercise->exercise.id==it.exerciseId }?.name?:"动作"},weather)
    val controller=AssistantController(scope,{SherpaRecognizer(context)},{SherpaSynthesizer(context)},
        {workout.session.session.value},workout.session::executeAssistant,answers::answer,{settings.voice})
    companion object {
        @Volatile private var instance:AssistantRuntime?=null
        fun get(context:Context)=instance?:synchronized(this) {
            instance?:AssistantRuntime(context.applicationContext).also { instance=it }
        }
    }
}
