package com.sys.svc
import android.content.Context
import java.util.UUID
object Prefs {
    private const val P = "sysprefs"
    fun deviceId(ctx: Context): String {
        val sp = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
        var id = sp.getString("did", null)
        if (id == null) { id = UUID.randomUUID().toString(); sp.edit().putString("did", id).apply() }
        return id
    }
}
