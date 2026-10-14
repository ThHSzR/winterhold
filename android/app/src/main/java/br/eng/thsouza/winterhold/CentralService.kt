package br.eng.thsouza.winterhold

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log

/** Owns the local central independently of the activity. No Termux or external service. */
class CentralService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val monitor = object : Runnable {
        override fun run() {
            DemoCentral.ensureStarted(applicationContext)
            DemoCentral.refreshUrls()
            DemoCentral.save()
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
            main.postDelayed(this, 10_000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Central Winterhold", NotificationManager.IMPORTANCE_LOW)
        )
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP || !isEnabled(this)) {
            preferences(this).edit().putBoolean("enabled", false).commit()
            stopSelf()
            return START_NOT_STICKY
        }
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "winterhold:central").apply { acquire() }
            @Suppress("DEPRECATION")
            wifiLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "winterhold:web").apply { acquire() }
        }
        main.removeCallbacks(monitor)
        monitor.run()
        return START_STICKY
    }

    override fun onDestroy() {
        main.removeCallbacks(monitor)
        DemoCentral.stop()
        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, CentralService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Winterhold · central demo local")
            .setContentText(DemoCentral.urls.firstOrNull() ?: DemoCentral.status)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "Parar central", stop).build())
            .build()
    }

    companion object {
        private const val CHANNEL = "winterhold-central"
        private const val NOTIFICATION_ID = 8790
        private const val STOP = "br.eng.thsouza.winterhold.STOP_CENTRAL"
        private fun preferences(context: Context) = context.getSharedPreferences("central", Context.MODE_PRIVATE)
        fun isEnabled(context: Context) = preferences(context).getBoolean("enabled", false)

        fun batteryExempt(context: Context) = context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)

        fun requestBatterySetup(context: Context) {
            val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(android.net.Uri.parse("package:${context.packageName}"))
            try {
                context.startActivity(intent)
            } catch (_: android.content.ActivityNotFoundException) {
                context.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }

        fun start(context: Context, enable: Boolean = true) {
            DemoCentral.prepare(context)
            if (enable) preferences(context).edit().putBoolean("enabled", true).commit()
            if (!isEnabled(context)) return
            try {
                context.startForegroundService(Intent(context, CentralService::class.java))
            } catch (error: RuntimeException) {
                DemoCentral.reportStartFailure(error)
                Log.e("Winterhold", "Não foi possível iniciar a central", error)
            }
        }

        fun stop(context: Context) {
            preferences(context).edit().putBoolean("enabled", false).commit()
            context.stopService(Intent(context, CentralService::class.java))
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            CentralService.start(context, enable = false)
        }
    }
}
