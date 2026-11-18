package com.projectoralarm.sunrise

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Main TV-friendly sunrise alarm dashboard, pitch-black night blackout controller,
 * and progressive sunrise + procedural birdsong orchestrator.
 */
class MainActivity : AppCompatActivity() {

    private enum class UiState {
        DASHBOARD,
        BLACKOUT,
        SUNRISE
    }

    private lateinit var sunriseView: SunriseView
    private lateinit var dashboardCard: View
    private lateinit var tvCurrentClock: TextView
    private lateinit var tvAlarmHour: TextView
    private lateinit var tvAlarmMinute: TextView
    private lateinit var tvDurationValue: TextView
    private lateinit var tvStatusBanner: TextView
    private lateinit var btnHourMinus: Button
    private lateinit var btnHourPlus: Button
    private lateinit var btnMinuteMinus: Button
    private lateinit var btnMinutePlus: Button
    private lateinit var btnDurationCycle: Button
    private lateinit var btnBlackout: Button
    private lateinit var btnPreview: Button

    private val handler = Handler(Looper.getMainLooper())
    private val birdSoundGenerator = BirdSoundGenerator()
    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    private val durationOptionsMinutes = intArrayOf(1, 10, 20, 30)
    private var alarmHour = 7
    private var alarmMinute = 0
    private var durationIndex = 1 // Default: 10 minutes
    private var scheduledTriggerWallTimeMs = 0L

    private var uiState = UiState.DASHBOARD
    private var activeSunriseDurationMs = 600_000L
    private var sunriseStartedElapsedMs = 0L
    private var activeMinBrightness = 0.01f

    private val clockTickRunnable = object : Runnable {
        override fun run() {
            val nowText = timeFormatter.format(Date())
            tvCurrentClock.text = nowText

            if (uiState == UiState.BLACKOUT &&
                scheduledTriggerWallTimeMs > 0L &&
                System.currentTimeMillis() >= scheduledTriggerWallTimeMs
            ) {
                startSunrise(durationMinutes = durationOptionsMinutes[durationIndex], isPreview = false)
            }

            handler.postDelayed(this, 1000L)
        }
    }

    private val sunriseFrameRunnable = object : Runnable {
        override fun run() {
            if (uiState != UiState.SUNRISE) return

            val elapsed = (SystemClock.elapsedRealtime() - sunriseStartedElapsedMs).coerceAtLeast(0L)
            val progress = (elapsed.toFloat() / activeSunriseDurationMs.toFloat()).coerceIn(0f, 1f)
            val nowText = timeFormatter.format(Date())

            sunriseView.updateSunrise(progress, nowText, showHint = true)
            birdSoundGenerator.setVolumeFactor(progress)
            setWindowBrightness(activeMinBrightness + (1.0f - activeMinBrightness) * progress)

            handler.postDelayed(this, 33L)
        }
    }

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (uiState != UiState.DASHBOARD) {
                exitToDashboard()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWindowFlags()
        setContentView(R.layout.activity_main)

        onBackPressedDispatcher.addCallback(this, backPressedCallback)

        bindViews()
        loadPreferences()
        setupListeners()
        refreshConfigLabels()

        if (intent?.getBooleanExtra(AlarmReceiver.EXTRA_TRIGGER_SUNRISE, false) == true) {
            startSunrise(durationMinutes = durationOptionsMinutes[durationIndex], isPreview = false)
        } else {
            enterDashboardState()
        }

        handler.post(clockTickRunnable)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(AlarmReceiver.EXTRA_TRIGGER_SUNRISE, false)) {
            startSunrise(durationMinutes = durationOptionsMinutes[durationIndex], isPreview = false)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        birdSoundGenerator.stop()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (uiState != UiState.DASHBOARD && event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_SPACE,
                KeyEvent.KEYCODE_BACK -> {
                    exitToDashboard()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun configureWindowFlags() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        hideSystemBars()
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun bindViews() {
        sunriseView = findViewById(R.id.sunriseView)
        dashboardCard = findViewById(R.id.dashboardCard)
        tvCurrentClock = findViewById(R.id.tvCurrentClock)
        tvAlarmHour = findViewById(R.id.tvAlarmHour)
        tvAlarmMinute = findViewById(R.id.tvAlarmMinute)
        tvDurationValue = findViewById(R.id.tvDurationValue)
        tvStatusBanner = findViewById(R.id.tvStatusBanner)
        btnHourMinus = findViewById(R.id.btnHourMinus)
        btnHourPlus = findViewById(R.id.btnHourPlus)
        btnMinuteMinus = findViewById(R.id.btnMinuteMinus)
        btnMinutePlus = findViewById(R.id.btnMinutePlus)
        btnDurationCycle = findViewById(R.id.btnDurationCycle)
        btnBlackout = findViewById(R.id.btnBlackout)
        btnPreview = findViewById(R.id.btnPreview)
    }

    private fun setupListeners() {
        btnHourMinus.setOnClickListener {
            alarmHour = (alarmHour + 23) % 24
            savePreferences()
            refreshConfigLabels()
        }
        btnHourPlus.setOnClickListener {
            alarmHour = (alarmHour + 1) % 24
            savePreferences()
            refreshConfigLabels()
        }
        btnMinuteMinus.setOnClickListener {
            alarmMinute = (alarmMinute + 55) % 60
            savePreferences()
            refreshConfigLabels()
        }
        btnMinutePlus.setOnClickListener {
            alarmMinute = (alarmMinute + 5) % 60
            savePreferences()
            refreshConfigLabels()
        }
        btnDurationCycle.setOnClickListener {
            durationIndex = (durationIndex + 1) % durationOptionsMinutes.size
            savePreferences()
            refreshConfigLabels()
        }
        btnBlackout.setOnClickListener {
            armAndEnterBlackout()
        }
        btnPreview.setOnClickListener {
            startSunrise(durationMinutes = 1, isPreview = true)
        }
        sunriseView.setOnClickListener {
            if (uiState != UiState.DASHBOARD) {
                exitToDashboard()
            }
        }
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        alarmHour = prefs.getInt(KEY_ALARM_HOUR, 7).coerceIn(0, 23)
        alarmMinute = prefs.getInt(KEY_ALARM_MINUTE, 0).coerceIn(0, 59)
        durationIndex = prefs.getInt(KEY_DURATION_INDEX, 1)
            .coerceIn(0, durationOptionsMinutes.lastIndex)
    }

    private fun savePreferences() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_ALARM_HOUR, alarmHour)
            .putInt(KEY_ALARM_MINUTE, alarmMinute)
            .putInt(KEY_DURATION_INDEX, durationIndex)
            .apply()
    }

    private fun refreshConfigLabels() {
        tvAlarmHour.text = String.format(Locale.getDefault(), "%02d", alarmHour)
        tvAlarmMinute.text = String.format(Locale.getDefault(), "%02d", alarmMinute)
        val minutes = durationOptionsMinutes[durationIndex]
        tvDurationValue.text = if (minutes == 1) {
            getString(R.string.duration_demo_format, minutes)
        } else {
            getString(R.string.duration_minutes_format, minutes)
        }
    }

    private fun armAndEnterBlackout() {
        scheduledTriggerWallTimeMs = computeNextTriggerTimeMillis(alarmHour, alarmMinute)
        scheduleSystemAlarm(scheduledTriggerWallTimeMs)

        uiState = UiState.BLACKOUT
        handler.removeCallbacks(sunriseFrameRunnable)
        birdSoundGenerator.stop()

        dashboardCard.visibility = View.GONE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setWindowBrightness(0.01f)
        hideSystemBars()

        val formattedAlarm = String.format(Locale.getDefault(), "%02d:%02d", alarmHour, alarmMinute)
        sunriseView.enterBlackout(formattedAlarm)
    }

    private fun startSunrise(durationMinutes: Int, isPreview: Boolean) {
        if (uiState == UiState.SUNRISE) return

        uiState = UiState.SUNRISE
        scheduledTriggerWallTimeMs = 0L
        activeSunriseDurationMs = (durationMinutes.coerceAtLeast(1) * 60_000L)
        sunriseStartedElapsedMs = SystemClock.elapsedRealtime()
        activeMinBrightness = if (isPreview) 0.25f else 0.01f

        dashboardCard.visibility = View.GONE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setWindowBrightness(activeMinBrightness)
        hideSystemBars()

        birdSoundGenerator.start(initialVolumeFactor = 0.05f)
        handler.removeCallbacks(sunriseFrameRunnable)
        handler.post(sunriseFrameRunnable)
    }

    private fun exitToDashboard() {
        cancelSystemAlarm()
        scheduledTriggerWallTimeMs = 0L
        handler.removeCallbacks(sunriseFrameRunnable)
        birdSoundGenerator.stop()
        enterDashboardState()
    }

    private fun enterDashboardState() {
        uiState = UiState.DASHBOARD
        dashboardCard.visibility = View.VISIBLE
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setWindowBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        sunriseView.enterDashboard()
        tvStatusBanner.text = getString(R.string.status_ready)
        btnBlackout.requestFocus()
    }

    private fun setWindowBrightness(brightness: Float) {
        val params = window.attributes
        params.screenBrightness = brightness
        window.attributes = params
    }

    private fun computeNextTriggerTimeMillis(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis
    }

    private fun createAlarmPendingIntent(): PendingIntent {
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_SUNRISE
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(this, ALARM_REQUEST_CODE, intent, flags)
    }

    private fun scheduleSystemAlarm(triggerAtMillis: Long) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = createAlarmPendingIntent()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun cancelSystemAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(createAlarmPendingIntent())
    }

    companion object {
        private const val PREFS_NAME = "projector_alarm_prefs"
        private const val KEY_ALARM_HOUR = "alarm_hour"
        private const val KEY_ALARM_MINUTE = "alarm_minute"
        private const val KEY_DURATION_INDEX = "duration_index"
        private const val ALARM_REQUEST_CODE = 1001
    }
}
