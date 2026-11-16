package com.sys.svc

import android.content.Context

object SpoofPrefs {
    private const val P = "spoofprefs"
    
    fun spoofOn(ctx: Context) = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
        .getBoolean("on", false)
    fun setSpoofOn(ctx: Context, v: Boolean) = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
        .edit().putBoolean("on", v).apply()
        
    fun fakeTitle(ctx: Context) = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
        .getString("title", "") ?: ""
    fun setFakeTitle(ctx: Context, v: String) = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
        .edit().putString("title", v).apply()
        
    fun fakeText(ctx: Context) = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
        .getString("text", "") ?: ""
    fun setFakeText(ctx: Context, v: String) = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
        .edit().putString("text", v).apply()
        
    fun iconPkg(ctx: Context) = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
        .getString("iconpkg", "com.whatsapp") ?: "com.whatsapp"
    fun setIconPkg(ctx: Context, v: String) = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
        .edit().putString("iconpkg", v).apply()
}