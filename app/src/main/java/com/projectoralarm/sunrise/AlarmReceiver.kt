package com.projectoralarm.sunrise

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

/**
 * Receives exact AlarmManager broadcasts and launches MainActivity
 * in sunrise mode while holding a temporary partial WakeLock.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ProjectorAlarm:SunriseWakeLock"
        )
        wakeLock?.acquire(15_000L)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_TRIGGER_SUNRISE
            putExtra(EXTRA_TRIGGER_SUNRISE, true)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }
        context.startActivity(launchIntent)
    }

    companion object {
        const val ACTION_TRIGGER_SUNRISE = "com.projectoralarm.sunrise.ACTION_TRIGGER_SUNRISE"
        const val EXTRA_TRIGGER_SUNRISE = "extra_trigger_sunrise"
    }
}
