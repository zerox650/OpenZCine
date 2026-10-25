package com.opencapture.openzcine

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.os.Process
import java.util.Locale

/**
 * The operator's app language, an override of the language this phone is set to.
 *
 * The choice is a BCP-47 tag stored in its own small preference file, and it is applied by
 * wrapping the [Application] and the activity's base contexts: every `getString` on a
 * UI-derived context then resolves in the chosen language, including the copy the shared
 * OpenCine modules read through the context they are handed.
 *
 * A blank tag (or [FOLLOW_SYSTEM]) keeps the device's own language, which is what the app did
 * before this existed — and what the resources already fall back to.
 */
object AppLanguage {
    private const val PREFERENCES = "openzcine.language"
    private const val KEY_TAG = "tag"

    /** Follow the device: no override, the resources fall back to the system locale. */
    const val FOLLOW_SYSTEM = ""
    const val ENGLISH = "en"
    const val CHINESE_SIMPLIFIED = "zh-CN"

    /** The languages the app ships, in menu order. A null tag is [FOLLOW_SYSTEM]. */
    fun tags(): List<String?> = listOf(null, ENGLISH, CHINESE_SIMPLIFIED)

    fun stored(context: Context): String =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_TAG, FOLLOW_SYSTEM) ?: FOLLOW_SYSTEM

    fun save(context: Context, tag: String?) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(KEY_TAG, tag ?: FOLLOW_SYSTEM).apply()
    }

    /** A context whose resources read [tag], or the untouched context when it follows the system. */
    fun wrap(context: Context, tag: String = stored(context)): Context {
        if (tag.isBlank()) return context
        val locale = Locale.forLanguageTag(tag)
        if (locale.language.isEmpty()) return context
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            configuration.setLocale(locale)
        }
        @Suppress("DEPRECATION")
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }

    /**
     * Relaunch the app from its launcher entry and end this process.
     *
     * A language change cannot be applied by recreating the activity alone: the retained owners
     * ([com.opencapture.openzcine.AppRoot]) survive a recreation and were built against the old
     * application context, so their copy would keep the old language. A fresh process rebuilds
     * every context, including the application's, from the stored tag.
     */
    fun restart(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launch)
        Process.killProcess(Process.myPid())
    }
}

/** Applies the stored language to the application context, so owners built from it read it too. */
class OpenZCineApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }
}
