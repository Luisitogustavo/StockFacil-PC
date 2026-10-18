package pe.edu.cibertec.stockfacil.data

data class Product(
    val id: Long = 0,
    val name: String,
    val code: String,
    val price: Double,
    val quantity: Int,
    val categoryId: Int = 0,
    val brand: String = "",
    val model: String = "",
    val categoryName: String = "",
    val minimumStock: Int = 0,
    val lowStock: Boolean = false,
    // Los borradores locales usan un id negativo para no confundirse con ids del portal.
    val requestId: String = "",
    val awaitingConfirmation: Boolean = false
)

data class ProductCategory(val id: Int, val name: String, val description: String)

data class StockMovement(
    val id: Int,
    val productId: Int,
    val productName: String,
    val sku: String,
    val type: String,
    val quantity: Int,
    val createdAt: String,
    val notes: String
)
