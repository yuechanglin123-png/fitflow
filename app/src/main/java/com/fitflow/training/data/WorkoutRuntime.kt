package com.fitflow.training.data

import android.content.Context
import com.fitflow.training.reminder.RestReminder
import com.fitflow.training.session.SessionViewModel

class WorkoutRuntime private constructor(context: Context) {
    val repository = WorkoutRepository.open(context)
    val session = SessionViewModel(repository, RestReminder(context))
    companion object {
        @Volatile private var instance: WorkoutRuntime? = null
        fun get(context: Context): WorkoutRuntime = instance ?: synchronized(this) {
            instance ?: WorkoutRuntime(context.applicationContext).also { instance = it }
        }
    }
}
