package com.nipunrautela.agnihotra.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

fun shareText(context: Context, text: String, chooserTitle: String): Boolean = try {
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }, chooserTitle))
    true
} catch (_: ActivityNotFoundException) { false }
