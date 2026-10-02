package com.jhonsu.interfon

import android.content.Context

/** Ajustes persistentes de la app (direccion del servidor). */
object Prefs {
    private const val FILE = "interfon"
    private const val KEY_URL = "server_url"
    const val DEFAULT_URL = "http://192.168.1.50:8765"

    fun url(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL

    fun setUrl(ctx: Context, value: String) {
        val v = value.trim().removeSuffix("/")
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_URL, v).apply()
    }
}
