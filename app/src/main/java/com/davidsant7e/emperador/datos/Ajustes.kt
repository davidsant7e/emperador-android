package com.davidsant7e.emperador.datos

import android.content.Context
import com.davidsant7e.emperador.nucleo.Gemini

/** Ajustes simples (la clave de Gemini va aparte, cifrada, en Boveda). */
class Ajustes(ctx: Context) {
    private val p = ctx.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    var modelo: String
        get() = p.getString("modelo", null)?.takeIf { it.isNotBlank() } ?: Gemini.MODELO
        set(v) = p.edit().putString("modelo", v.trim()).apply()
}
