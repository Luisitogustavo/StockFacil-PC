package pe.edu.cibertec.stockfacil.data

object InventoryRules {
    const val MAX_UNITS = 1_000_000
    private const val MAX_PRICE = 9_999_999.99

    fun note(value: String): String? = when {
        value.trim().length > 300 -> "Admite hasta 300 caracteres"
        value.any { it.isISOControl() && it != '\n' && it != '\r' } -> "Contiene caracteres no admitidos"
        else -> null
    }

    fun label(value: String, maxLength: Int, required: Boolean = true): String? {
        val text = value.trim()
        if (text.isEmpty()) return if (required) "Completa este campo" else null
        if (text.length > maxLength) return "Admite hasta $maxLength caracteres"
        if (!text.any { it.isLetterOrDigit() } ||
            !Regex("^[\\p{L}\\p{M}\\p{N} .,_+()/&:'\"%°-]+$").matches(text))
            return "Usa letras, números y signos habituales como guion, punto o paréntesis"
        return null
    }

    fun productErrors(product: Product): Map<String, String> = buildMap {
        label(product.name, 120)?.let { put("name", it) }
        label(product.brand, 60, false)?.let { put("brand", it) }
        label(product.model, 80, false)?.let { put("model", it) }
        if (!Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,39}$").matches(product.code))
            put("code", "Usa de 1 a 40 letras o números, con guion, punto o guion bajo; sin espacios")
        if (!product.price.isFinite() || product.price < 0 || product.price > MAX_PRICE ||
            kotlin.math.abs(product.price - Math.round(product.price * 100) / 100.0) > 0.0000001)
            put("price", "Entre 0 y 9 999 999,99; hasta dos decimales")
        if (product.quantity !in 0..MAX_UNITS) put("quantity", "Entero entre 0 y 1 000 000")
        if (product.minimumStock !in 0..MAX_UNITS) put("minimum", "Entero entre 0 y 1 000 000")
        if (product.categoryId <= 0) put("category", "Selecciona una categoría")
    }
}
