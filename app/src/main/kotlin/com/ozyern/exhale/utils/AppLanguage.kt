/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.utils

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList

/**
 * The app's own language, independent of the phone's.
 *
 * Android has carried per-app languages since 13, and the app's `minSdk` is 33, so there is no
 * compatibility path to write: the system stores the choice, restarts what needs restarting and
 * shows the same setting in its own app info screen. All this file owns is *which* languages are
 * offered and what they are called.
 *
 * Offered, not every language present: the repo carries partial translations for twenty locales,
 * and a list of twenty entries where half are a third translated is a worse experience than a
 * short list that works. This is the set with enough coverage to be worth choosing, which is why
 * the row wears a Beta label - the strings behind it are still filling in.
 */
object AppLanguage {

    /** The empty tag: whatever the phone is set to. */
    const val SYSTEM = ""

    /**
     * Tag to display name, in the order the picker shows them.
     *
     * Every tag here has a `values-*` folder behind it (or is an English variant, which resolves
     * to the default resources), and matches the locale config AGP generates from those folders.
     * A tag with no resources is not an option, it is a way to get an English app with a Chinese
     * label on the row.
     *
     * Names are written in the language itself, as every system language picker does - somebody
     * looking for Chinese is looking for 简体中文, not for the word "Chinese" in a language they
     * cannot read. English keeps its variants because they differ in the places that matter to a
     * music app (the spelling of "favourite", the shape of a date) even where the strings are
     * currently shared.
     */
    val SUPPORTED: List<Pair<String, String>> = listOf(
        "en-US" to "English (United States)",
        "en-GB" to "English (United Kingdom)",
        "en-IN" to "English (India)",
        "zh-CN" to "简体中文",
        "hi" to "हिन्दी",
        "es" to "Español",
        "fr" to "Français",
        "de-DE" to "Deutsch",
        "pt-BR" to "Português (Brasil)",
        "ru" to "Русский",
        "ja" to "日本語",
        "ko" to "한국어",
        "ar" to "العربية",
        "id-ID" to "Bahasa Indonesia",
        "vi" to "Tiếng Việt",
        "tr" to "Türkçe",
        "it" to "Italiano",
        "nl" to "Nederlands",
        "uk" to "Українська",
        "ms" to "Bahasa Melayu",
    )

    /** What the row shows for [tag], falling back to the system entry. */
    fun labelFor(tag: String): String? =
        SUPPORTED.firstOrNull { it.first == tag }?.second

    /** The language in force right now, as a tag this object knows, or [SYSTEM]. */
    fun current(context: Context): String {
        val locales = context.getSystemService(LocaleManager::class.java)
            ?.applicationLocales
            ?: return SYSTEM
        if (locales.isEmpty) return SYSTEM
        val tag = locales[0]?.toLanguageTag() ?: return SYSTEM
        // An exact match first, then the language on its own: the system may hand back "en-US"
        // for a request of "en", or a tag carrying a script or extension we did not ask for.
        return SUPPORTED.firstOrNull { it.first.equals(tag, ignoreCase = true) }?.first
            ?: SUPPORTED.firstOrNull { it.first.substringBefore('-') == tag.substringBefore('-') }?.first
            ?: SYSTEM
    }

    /** Applies [tag], or hands the choice back to the system when it is [SYSTEM]. */
    fun apply(context: Context, tag: String) {
        val manager = context.getSystemService(LocaleManager::class.java) ?: return
        manager.applicationLocales = if (tag == SYSTEM) {
            LocaleList.getEmptyLocaleList()
        } else {
            LocaleList.forLanguageTags(tag)
        }
    }
}
