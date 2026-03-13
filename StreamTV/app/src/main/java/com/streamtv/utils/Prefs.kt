package com.streamtv.utils

import android.content.Context
import com.streamtv.data.model.SourceConfig
import com.streamtv.data.model.SourceType

object Prefs {
    private const val PREF_NAME = "streamtv_prefs"

    fun saveSource(ctx: Context, config: SourceConfig) {
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString("source_url",  config.url)
            .putString("source_name", config.name)
            .putString("source_type", config.type.name)
            .apply()
    }

    fun getSource(ctx: Context): SourceConfig? {
        val prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val url   = prefs.getString("source_url",  null) ?: return null
        val name  = prefs.getString("source_name", url)  ?: url
        val type  = SourceType.valueOf(prefs.getString("source_type", "CUSTOM") ?: "CUSTOM")
        return SourceConfig(url, name, type)
    }

    fun clearSource(ctx: Context) {
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
