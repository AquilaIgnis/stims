package acidburn.stims

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class StimsService : Service() {

    // Samsung and certain OEMs disable SCREEN_BRIGHT_WAKE_LOCK; use a 1x1 overlay with
    // FLAG_KEEP_SCREEN_ON instead, which the WindowManager honours on all devices.
    private var useOverlayStrategy = OVERLAY_VENDORS.any {
        Build.MANUFACTURER.equals(it, ignoreCase = true)
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var overlayView: View? = null
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }

    private var selectedPackages = mutableSetOf<String>()
    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval = 10000L // Check every 10 seconds
    private var lastForegroundPackage: String? = null

    private val monitorRunnable = object : Runnable {
        override fun run() {
            checkForegroundApp()
            handler.postDelayed(this, checkInterval)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val packages = intent?.getStringArrayListExtra("selected_packages")

        if (packages != null) {
            selectedPackages = packages.toMutableSet()
            if (BuildConfig.DEBUG) Log.d(TAG, "Updated stimmed packages: $selectedPackages")
            updateNotification()
        }

        val forceOverlay = intent?.getBooleanExtra(EXTRA_FORCE_OVERLAY, false) ?: false
        useOverlayStrategy = forceOverlay || OVERLAY_VENDORS.any {
            Build.MANUFACTURER.equals(it, ignoreCase = true)
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "useOverlayStrategy=$useOverlayStrategy forceOverlay=$forceOverlay")

        if (action == ACTION_STOP) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Stop action received")
            stopSelf()
            return START_NOT_STICKY
        }

        if (BuildConfig.DEBUG) Log.d(TAG, "Service starting")
        startForegroundService()

        return START_STICKY
    }

    private fun startForegroundService() {
        createNotificationChannel()
        updateNotification()
        handler.removeCallbacks(monitorRunnable)
        handler.post(monitorRunnable)
    }

    private fun updateNotification() {
        // Ensure the channel exists before startForeground(); onStartCommand may call
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Stims Daemon Running")
            .setContentText("Monitoring ${selectedPackages.size} apps")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun checkForegroundApp() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isInteractive) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Screen not interactive, releasing screen lock")
            releaseScreenLock()
            return
        }

        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(time - checkInterval * 2, time)
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastForegroundPackage = event.packageName
            }
        }

        val shouldHold = lastForegroundPackage != null && selectedPackages.contains(lastForegroundPackage)
        if (BuildConfig.DEBUG) Log.d(TAG, "Poll: foreground=$lastForegroundPackage shouldHold=$shouldHold wakeLockHeld=${wakeLock?.isHeld}")

        if (shouldHold) {
            acquireScreenLock()
        } else {
            releaseScreenLock()
        }
    }

    private fun acquireScreenLock() {
        if (useOverlayStrategy) acquireOverlay() else acquireWakeLock()
    }

    private fun releaseScreenLock() {
        if (useOverlayStrategy) releaseOverlay() else releaseWakeLock()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null || !wakeLock!!.isHeld) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "Stims::WakeLock"
            )
            @Suppress("WakelockTimeout")
            wakeLock?.acquire()
            if (BuildConfig.DEBUG) Log.d(TAG, "Wake lock acquired for $lastForegroundPackage")
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            if (BuildConfig.DEBUG) Log.d(TAG, "Wake lock released")
        }
        wakeLock = null
    }

    private fun acquireOverlay() {
        if (overlayView != null) return
        if (!Settings.canDrawOverlays(this)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Overlay permission not granted, cannot acquire overlay")
            return
        }
        val params = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )
        overlayView = View(this)
        windowManager.addView(overlayView, params)
        if (BuildConfig.DEBUG) Log.d(TAG, "Overlay acquired for $lastForegroundPackage")
    }

    private fun releaseOverlay() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
            if (BuildConfig.DEBUG) Log.d(TAG, "Overlay released")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val serviceChannel = NotificationChannel(
                    CHANNEL_ID, "Stims Service", NotificationManager.IMPORTANCE_LOW
                )
                manager.createNotificationChannel(serviceChannel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(monitorRunnable)
        releaseScreenLock()
    }

    companion object {
        const val CHANNEL_ID = "StimsServiceChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "acidburn.stims.STOP"
        const val EXTRA_FORCE_OVERLAY = "force_overlay"
        private const val TAG = "StimsService"

        // Vendors that disable SCREEN_BRIGHT_WAKE_LOCK — use overlay strategy instead
        val OVERLAY_VENDORS = setOf("samsung","Realme")
    }
}
