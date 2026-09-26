package com.fitflow.training.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.fitflow.training.MainActivity
import com.fitflow.training.session.SessionSnapshot
import com.fitflow.training.session.RestKind
import com.fitflow.training.session.Phase

private const val CHANNEL = "rest_end"

 data class ReminderCapability(val notifications: Boolean, val exactAlarms: Boolean)
 data class ReminderStatus(val exact: Boolean, val notifications: Boolean)

class RestReminder(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    fun capability() = ReminderCapability(
        notifications = Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        exactAlarms = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms(),
    )

    fun schedule(restEndEpochMs: Long, sessionId: String): ReminderStatus {
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "训练休息提醒", NotificationManager.IMPORTANCE_DEFAULT))
        val pending = pendingIntent(sessionId, restEndEpochMs)
        val caps = capability()
        if (caps.exactAlarms) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, restEndEpochMs, pending)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, restEndEpochMs, pending)
        return ReminderStatus(caps.exactAlarms, caps.notifications)
    }

    fun cancel(sessionId: String) {
        val intent = Intent(context, RestAlarmReceiver::class.java).putExtra("sessionId", sessionId)
        val existing = PendingIntent.getBroadcast(context, sessionId.hashCode(), intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (existing != null) { alarms.cancel(existing); existing.cancel() }
    }

    fun notifyRestEnd(snapshot:SessionSnapshot,kind:RestKind?) {
        if(!capability().notifications) return
        notifications.createNotificationChannel(NotificationChannel(CHANNEL,"训练休息提醒",NotificationManager.IMPORTANCE_DEFAULT))
        val open=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(context,CHANNEL).setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(if(kind==RestKind.EXERCISE) "动作间休息结束" else "组间休息结束")
            .setContentText(if(snapshot.phase==Phase.FINISHED) "训练已完成，查看总结" else if(kind==RestKind.EXERCISE) "下一个动作已就绪，返回健身助手继续训练" else "下一组已就绪，返回健身助手继续训练")
            .setContentIntent(open).setAutoCancel(true).build()
        notifications.notify(snapshot.id.hashCode(),notification)
    }

    private fun pendingIntent(sessionId: String, deadline: Long): PendingIntent {
        val intent = Intent(context, RestAlarmReceiver::class.java)
            .putExtra("sessionId", sessionId).putExtra("deadline", deadline)
        return PendingIntent.getBroadcast(context, sessionId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
