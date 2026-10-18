package pe.edu.cibertec.stockfacil.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

/** Cliente REST sencillo para la API local de StockFácil. */
class StockFacilApi {
    fun getProducts(search: String = ""): List<Product> {
        val query = if (search.isBlank()) "" else "?search=${URLEncoder.encode(search, "UTF-8")}" 
        val json = request("/api/products/$query") as JSONArray
        return (0 until json.length()).map { index ->
            val item = json.getJSONObject(index)
            Product(
                id = item.getLong("id"),
                name = item.getString("name"),
                code = item.getString("sku"),
                price = item.getDouble("price"),
                quantity = item.getInt("stock"),
                categoryId = item.getInt("categoryId"),
                brand = item.optString("brand"),
                model = item.optString("model"),
                categoryName = item.optString("category"),
                minimumStock = item.optInt("minimumStock"),
                lowStock = item.optBoolean("lowStock")
            )
        }
    }

    fun getCategories(): List<ProductCategory> {
        val json = request("/api/categories/") as JSONArray
        return (0 until json.length()).map { index ->
            val item = json.getJSONObject(index)
            ProductCategory(item.getInt("id"), item.getString("name"), item.optString("description"))
        }
    }

    fun getStockMovements(): List<StockMovement> {
        val json = request("/api/stock-movements/") as JSONArray
        return (0 until json.length()).map { index ->
            val item = json.getJSONObject(index)
            StockMovement(
                id = item.getInt("id"),
                productId = item.getInt("productId"),
                productName = item.optString("productName"),
                sku = item.optString("sku"),
                type = item.optString("type"),
                quantity = item.getInt("quantity"),
                createdAt = item.optString("createdAt"),
                notes = item.optString("notes")
            )
        }
    }

    fun getDefaultCategoryId(): Int {
        val categories = getCategories()
        if (categories.isEmpty()) error("La API no tiene categorías activas.")
        return categories.first().id
    }

    fun saveCategory(id: Int, name: String, description: String) {
        val body = JSONObject().apply {
            put("name", name)
            put("description", description)
        }
        request("/api/categories/" + (if (id == 0) "" else id.toString()), if (id == 0) "POST" else "PUT", body)
    }

    fun deleteCategory(id: Int) {
        request("/api/categories/$id", "DELETE")
    }

    fun saveProduct(product: Product, categoryId: Int, minimumStock: Int = 2) {
        val body = JSONObject().apply {
            put("categoryId", categoryId)
            put("sku", product.code)
            put("name", product.name)
            put("brand", product.brand)
            put("model", product.model)
            put("price", product.price)
            put("stock", product.quantity)
            put("minimumStock", minimumStock)
        }
        if (product.id == 0L) request("/api/products/", "POST", body)
        else request("/api/products/${product.id}", "PUT", body)
    }

    fun deleteProduct(id: Long) {
        request("/api/products/$id", "DELETE")
    }

    fun sendDraft(product: Product): Long {
        require(product.id < 0 && product.requestId.isNotBlank())
        val response = request("/api/products/", "POST", JSONObject().apply {
            put("clientRequestId", product.requestId)
            put("categoryId", product.categoryId); put("sku", product.code)
            put("name", product.name); put("brand", product.brand); put("model", product.model)
            put("price", product.price); put("stock", product.quantity)
            put("minimumStock", product.minimumStock)
        }) as JSONObject
        return response.getLong("id").also { check(it > 0) { "El portal no confirmó el producto." } }
    }

    fun recordStockMovement(productId: Long, type: String, quantity: Int, notes: String = "") {
        val body = JSONObject().apply {
            put("productId", productId)
            put("type", type)
            put("quantity", quantity)
            put("notes", notes)
        }
        request("/api/stock-movements/", "POST", body)
    }

    private fun request(path: String, method: String = "GET", body: JSONObject? = null): Any {
        val connection = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 5000
            readTimeout = 5000
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(text).optString("message") }.getOrDefault("")
                throw ApiException(code, message.ifBlank { "El servicio respondió con error HTTP $code." })
            }
            if (text.isBlank()) return JSONObject()
            return if (text.trimStart().startsWith("[")) JSONArray(text) else JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        // El Android Emulator accede al servicio local del PC mediante este alias.
        // En el teléfono físico se mantiene la dirección LAN del entorno de desarrollo.
        private val BASE_URL = if (android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
            "http://10.0.2.2:5080"
        else "http://192.168.18.9:5080"
    }
}

class ApiException(val statusCode: Int, message: String) : Exception(message)
