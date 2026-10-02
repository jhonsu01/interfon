package com.jhonsu.interfon

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Idioma de la app (independiente del sistema).
 *
 * Android 13+: se delega en LocaleManager (aparece tambien en
 * Ajustes del sistema > Idioma de la app). Android 8-12: se guarda en Prefs
 * y cada Activity/Service envuelve su contexto con [wrap].
 */
object Lang {

    data class Option(val tag: String, val label: String)

    val OPTIONS = listOf(
        Option("es", "Español"),
        Option("en", "English"),
        Option("zh", "中文"),
        Option("pt", "Português"),
        Option("ko", "한국어"),
        Option("ru", "Русский"),
        Option("ja", "日本語"),
        Option("fr", "Français"),
    )

    /** Idioma elegido por el usuario, o null si aun no eligio (primer arranque). */
    fun chosen(ctx: Context): String? {
        if (Build.VERSION.SDK_INT >= 33) {
            val locales = ctx.getSystemService(LocaleManager::class.java).applicationLocales
            if (!locales.isEmpty) return locales[0].language
        }
        return Prefs.language(ctx)
    }

    /** Idioma efectivo para marcar la opcion activa en la UI. */
    fun current(ctx: Context): String =
        chosen(ctx) ?: ctx.resources.configuration.locales[0].language

    fun set(activity: Activity, tag: String) {
        Prefs.setLanguage(activity, tag)
        if (Build.VERSION.SDK_INT >= 33) {
            // El sistema recrea las Activities con el nuevo idioma
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(tag)
        } else {
            activity.recreate()
        }
        InterfonApp.createChannels(localized(activity.applicationContext))
    }

    /**
     * Android 13+: si hay idioma guardado pero el sistema no lo tiene (p. ej. tras
     * restaurar una copia de ajustes), se lo aplica al sistema.
     */
    fun syncSystem(ctx: Context) {
        if (Build.VERSION.SDK_INT < 33) return
        val tag = Prefs.language(ctx) ?: return
        val lm = ctx.getSystemService(LocaleManager::class.java)
        if (lm.applicationLocales.isEmpty) lm.applicationLocales = LocaleList.forLanguageTags(tag)
    }

    /** Envuelve un contexto con el idioma guardado (solo hace falta antes de Android 13). */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = Prefs.language(base) ?: return base
        val conf = Configuration(base.resources.configuration)
        conf.setLocales(LocaleList(Locale.forLanguageTag(tag)))
        return base.createConfigurationContext(conf)
    }

    /** Contexto de la app con el idioma vigente (toasts y notificaciones). */
    fun localized(ctx: Context = InterfonApp.appContext): Context = wrap(ctx)

    fun str(id: Int, vararg args: Any): String = localized().getString(id, *args)
}
