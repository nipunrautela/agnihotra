package com.nipunrautela.agnihotra.core

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

fun Context.withAppLanguage(language: String): Context {
    if (language == "system") return this
    return createConfigurationContext(Configuration(resources.configuration).apply {
        setLocale(Locale.forLanguageTag(language))
    })
}
