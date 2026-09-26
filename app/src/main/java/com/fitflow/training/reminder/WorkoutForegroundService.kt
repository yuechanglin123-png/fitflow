package com.fitflow.training.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.media.AudioAttributes
import com.fitflow.training.MainActivity
import com.fitflow.training.assistant.AssistantRuntime
import com.fitflow.training.assistant.AssistantStage
import com.fitflow.training.data.WorkoutRuntime
import com.fitflow.training.session.Phase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

class WorkoutForegroundService : Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val assistant by lazy { AssistantRuntime.get(this) }
    private val workout by lazy { WorkoutRuntime.get(this) }
    private var wakeLock:PowerManager.WakeLock?=null
    private var audioFocus:AudioFocusRequest?=null
    private var microphoneForeground=false
    private var terminalStop:Job?=null
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            workout.session.resume()
            while(isActive) {
                val snapshot=workout.session.session.value
                if(snapshot?.phase==Phase.FINISHED) { stopAfterFinalReply();break }
                val events=workout.session.tickAssistant(assistant.controller.state.value.enabled)
                workout.session.reconcile()
                events.sortedByDescending { it.priority }.forEach(assistant.controller::notify)
                delay(1000)
            }
        }
        scope.launch {
            assistant.controller.state.collect { state ->
                if(!state.enabled && microphoneForeground) { releaseMicrophoneResources();showNotification(false) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            if(workout.session.session.value?.phase==Phase.FINISHED) {
                stopAfterFinalReply()
                return START_NOT_STICKY
            }
            assistant.controller.disable()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if(intent?.action==ACTION_DISABLE) { assistant.controller.disable();releaseMicrophoneResources() }
        showNotification(microphoneForeground)
        if(intent?.action==ACTION_ENABLE) scope.launch {
            workout.session.resume()
            if(assistant.controller.state.value.enabled || workout.session.session.value?.phase==Phase.FINISHED) return@launch
            try {
                showNotification(true)
                val manager=getSystemService(AudioManager::class.java)
                val focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setOnAudioFocusChangeListener { change ->
                        if(change==AudioManager.AUDIOFOCUS_LOSS || change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                            assistant.controller.fail("音频被其他应用占用，助教已关闭。需要时请手动开启")
                        }
                    }.build()
                if(manager.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED) error("Audio focus denied")
                audioFocus=focus
                wakeLock=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"fitflow:assistant").apply { acquire() }
                assistant.controller.enable()
            } catch(error:Exception) {
                assistant.controller.fail("无法开启麦克风，请保持应用在前台并检查录音权限")
                releaseMicrophoneResources();showNotification(false)
            }
        }
        return START_NOT_STICKY
    }

    private fun showNotification(microphone:Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "正在训练", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("健身助手 · 训练进行中")
            .setContentText(if(microphone) "语音助教已开启 · 说“铁蛋铁蛋”唤醒" else "返回应用记录下一组并查看休息时间")
            .setContentIntent(open)
            .setOngoing(true)
        if(microphone) {
            val close=PendingIntent.getService(this,4002,Intent(this,WorkoutForegroundService::class.java).setAction(ACTION_DISABLE),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(Notification.Action.Builder(android.R.drawable.ic_media_pause,"关闭助教",close).build())
        }
        if (Build.VERSION.SDK_INT >= 31) builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        val notification = builder.build()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or if(microphone) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
        else if(Build.VERSION.SDK_INT>=30 && microphone) startForeground(NOTIFICATION_ID,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(NOTIFICATION_ID, notification)
        microphoneForeground=microphone
    }

    private fun releaseMicrophoneResources() {
        wakeLock?.let { if(it.isHeld) it.release() };wakeLock=null
        audioFocus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) };audioFocus=null
    }
    private fun stopAfterFinalReply() {
        if(terminalStop?.isActive==true) return
        terminalStop=scope.launch {
            withTimeoutOrNull(15_000) {
                while(assistant.controller.state.value.enabled && assistant.controller.state.value.stage in listOf(AssistantStage.PROCESSING,AssistantStage.SPEAKING)) delay(50)
            }
            assistant.controller.disable()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }
    override fun onDestroy() {
        assistant.controller.disable()
        releaseMicrophoneResources()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "com.fitflow.training.WORKOUT_START"
        private const val ACTION_STOP = "com.fitflow.training.WORKOUT_STOP"
        private const val ACTION_ENABLE="com.fitflow.training.ASSISTANT_ENABLE"
        private const val ACTION_DISABLE="com.fitflow.training.ASSISTANT_DISABLE"
        private const val CHANNEL = "workout_active"
        private const val NOTIFICATION_ID = 4001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, WorkoutForegroundService::class.java).setAction(ACTION_START))
        }
        fun stop(context: Context) {
            context.startService(Intent(context, WorkoutForegroundService::class.java).setAction(ACTION_STOP))
        }
        fun enableAssistant(context:Context) {
            try { context.startForegroundService(Intent(context,WorkoutForegroundService::class.java).setAction(ACTION_ENABLE)) }
            catch(error:Exception) { AssistantRuntime.get(context).controller.fail("请在训练页面开启助教，并检查麦克风权限") }
        }
        fun disableAssistant(context:Context) {
            context.startService(Intent(context,WorkoutForegroundService::class.java).setAction(ACTION_DISABLE))
        }
    }
}
