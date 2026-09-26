package com.fitflow.training.reminder

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.fitflow.training.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WorkoutForegroundServiceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun ongoingNotificationExistsDuringWorkout() {
        if (Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(rule.activity.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val manager = rule.activity.getSystemService(NotificationManager::class.java)
        rule.runOnUiThread { WorkoutForegroundService.start(rule.activity) }
        rule.waitUntil(5000) { manager.activeNotifications.isNotEmpty() }
        assertTrue(manager.activeNotifications.any { it.notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0 })
        rule.runOnUiThread { WorkoutForegroundService.stop(rule.activity) }
        rule.waitUntil(5000) { manager.activeNotifications.isEmpty() }
    }
}
