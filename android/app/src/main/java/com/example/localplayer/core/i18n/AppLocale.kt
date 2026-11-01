package com.example.localplayer.core.i18n

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/**
 * Подмена локали приложения без AppCompat.
 *
 * Ресурсы Android сами выбирают `values-*` по локали конфигурации, поэтому
 * достаточно обернуть базовый контекст Application и Activity в контекст
 * с нужной локалью. Состояние хранится в [LanguagePreferences] и читается
 * синхронно в `attachBaseContext` — до того, как отрисуется первый экран.
 */
object AppLocale {

    /** Локаль системы, не испорченная нашей подменой. */
    fun systemLocale(): Locale = Resources.getSystem().configuration.locales[0]

    /** Локаль выбранного режима: у [LanguageMode.SYSTEM] — системная. */
    fun localeFor(mode: LanguageMode): Locale =
        mode.tag?.let(Locale::forLanguageTag) ?: systemLocale()

    /** Контекст с локалью из настроек: используется в Application и Activity. */
    fun wrap(context: Context): Context =
        withMode(context, LanguagePreferences.readStored(context))

    fun withMode(context: Context, mode: LanguageMode): Context {
        val locale = localeFor(mode)
        Locale.setDefault(locale)
        return createContext(context, locale)
    }

    /**
     * Обновляет ресурсы уже созданного Application.
     *
     * Нужно при смене языка на лету: Activity пересоздаётся и получает новый
     * контекст, а фоновые задачи (уведомления, ViewModel-сообщения) держат
     * Application и должны видеть тот же язык сразу, не дожидаясь перезапуска.
     */
    @Suppress("DEPRECATION")
    fun applyToApplication(context: Context, mode: LanguageMode) {
        val locale = localeFor(mode)
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
    }

    private fun createContext(context: Context, locale: Locale): Context {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return context.createConfigurationContext(configuration)
    }
}
