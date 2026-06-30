package com.flowpay.app.utils

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity

/**
 * Walks context wrappers to find the hosting [ComponentActivity].
 * [LocalContext] in Compose is not always the activity itself (e.g. [android.view.ContextThemeWrapper]).
 */
fun Context.findComponentActivity(): ComponentActivity? {
    var ctx: Context = this
    while (true) {
        when (ctx) {
            is ComponentActivity -> return ctx
            is ContextWrapper -> {
                val base = ctx.baseContext
                if (base === ctx) return null
                ctx = base
            }
            else -> return null
        }
    }
}
