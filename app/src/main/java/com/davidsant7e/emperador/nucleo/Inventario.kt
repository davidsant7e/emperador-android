package com.davidsant7e.emperador.nucleo

/** Reglas del inventario: unir lo que trae Gemini con lo ya revisado, y exportar. */
object Inventario {

    /**
     * Aplica un análisis nuevo: lo revisado (confirmado) se queda; las sugerencias anteriores sin revisar
     * se reemplazan por las nuevas; y no se agrega una sugerencia que ya está revisada con el mismo nombre.
     */
    fun aplicarAnalisis(h: Habitacion, nuevos: List<Objeto>, fecha: String): Habitacion {
        val revisados = h.objetos.filter { it.confirmado }
        val nombres = revisados.map { Texto.simple(it.nombre) }.toMutableSet()
        val agregados = ArrayList<Objeto>()
        for (o in nuevos) {
            val clave = Texto.simple(o.nombre)
            if (clave.isEmpty() || clave in nombres) continue
            nombres += clave
            agregados += o.copy(confirmado = false)
        }
        return h.copy(objetos = revisados + agregados, analizada = fecha)
    }

    /** Quita una foto: también se borra la referencia en los objetos que apuntaban a ella. */
    fun quitarFoto(h: Habitacion, archivo: String): Habitacion = h.copy(
        fotos = h.fotos - archivo,
        objetos = h.objetos.map { if (it.foto == archivo) it.copy(foto = null) else it },
    )

    fun ordenados(objetos: List<Objeto>): List<Objeto> =
        objetos.sortedWith(compareBy<Objeto>({ Categorias.CLAVES.indexOf(it.categoria).let { i -> if (i < 0) 99 else i } }, { Texto.simple(it.nombre) }))

    /** Total de unidades por categoría, en el orden fijo de las categorías. */
    fun porCategoria(casa: Casa): List<Pair<String, Int>> {
        val suma = HashMap<String, Int>()
        casa.habitaciones.forEach { h -> h.objetos.forEach { suma[it.categoria] = (suma[it.categoria] ?: 0) + it.cantidad } }
        return Categorias.CLAVES.mapNotNull { k -> suma[k]?.let { k to it } }
    }

    // ---------- exportar ----------

    /** CSV con BOM (Excel lo abre con tildes) y separador coma; una fila por objeto. */
    fun csv(casa: Casa, incluirSinRevisar: Boolean = true): String {
        val sb = StringBuilder("﻿")
        sb.append("Habitación,Objeto,Categoría,Cantidad,Ubicación,Notas,Revisado\r\n")
        for (h in casa.habitaciones) {
            for (o in ordenados(h.objetos)) {
                if (!incluirSinRevisar && !o.confirmado) continue
                listOf(h.nombre, o.nombre, Categorias.nombre(o.categoria), o.cantidad.toString(), o.ubicacion, o.notas, if (o.confirmado) "sí" else "no")
                    .joinTo(sb, ",") { celda(it) }
                sb.append("\r\n")
            }
        }
        return sb.toString()
    }

    private fun celda(s: String): String {
        // Evita que Excel interprete una celda como fórmula.
        val t = if (s.startsWith("=") || s.startsWith("+") || s.startsWith("-") || s.startsWith("@")) "'$s" else s
        return if (t.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + t.replace("\"", "\"\"") + "\"" else t
    }

    /** Texto para pegarle a Claude (u otro asistente): una sección por habitación. */
    fun markdown(casa: Casa): String {
        val sb = StringBuilder()
        val total = casa.habitaciones.sumOf { it.totalObjetos }
        sb.append("# Inventario de ").append(casa.nombre).append("\n\n")
        sb.append(casa.habitaciones.size).append(" habitaciones · ").append(total).append(" objetos")
        val pendientes = casa.habitaciones.sumOf { it.sinRevisar }
        if (pendientes > 0) sb.append(" · ").append(pendientes).append(" sin revisar (marcados con *)")
        sb.append("\n")
        for (h in casa.habitaciones) {
            sb.append("\n## ").append(h.nombre).append(" (").append(h.totalObjetos).append(")\n")
            if (h.notas.isNotBlank()) sb.append(h.notas.trim()).append("\n")
            if (h.objetos.isEmpty()) { sb.append("- (sin objetos)\n"); continue }
            var categoria: String? = null
            for (o in ordenados(h.objetos)) {
                if (o.categoria != categoria) {
                    categoria = o.categoria
                    sb.append("\n**").append(Categorias.nombre(o.categoria)).append("**\n")
                }
                sb.append("- ").append(o.nombre)
                if (o.cantidad > 1) sb.append(" ×").append(o.cantidad)
                if (!o.confirmado) sb.append(" *")
                val extra = listOf(o.ubicacion, o.notas).filter { it.isNotBlank() }
                if (extra.isNotEmpty()) sb.append(" — ").append(extra.joinToString("; "))
                sb.append("\n")
            }
        }
        return sb.toString()
    }

    /** Nombre de archivo seguro para exportar. */
    fun nombreArchivo(casa: Casa, extension: String, fecha: String): String {
        val base = Texto.simple(casa.nombre).replace(' ', '-').ifEmpty { "casa" }
        return "inventario-$base-$fecha.$extension"
    }
}
