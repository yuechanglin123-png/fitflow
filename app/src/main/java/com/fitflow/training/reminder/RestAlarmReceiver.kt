package com.fitflow.training.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.fitflow.training.MainActivity
import com.fitflow.training.data.WorkoutRepository
import com.fitflow.training.session.Phase
import com.fitflow.training.session.SessionReducer
import com.fitflow.training.session.RestKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val id = intent.getStringExtra("sessionId") ?: return@launch
                val deadline = intent.getLongExtra("deadline", -1)
                val model = com.fitflow.training.data.WorkoutRuntime.get(context).session
                val (updated, _) = model.reconcileRestAlarm(id, deadline) ?: return@launch
                if (updated.phase == Phase.FINISHED) WorkoutForegroundService.stop(context)
            } finally { pending.finish() }
        }
    }
}
