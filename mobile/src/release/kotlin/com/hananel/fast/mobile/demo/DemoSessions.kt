package com.hananel.fast.mobile.demo

import android.content.Context

/** The release build ships no demo data; this stands in for the debug build's generator. */
object DemoSessions {

    const val AVAILABLE = false

    fun add(context: Context) = Unit

    fun remove(context: Context) = Unit
}
