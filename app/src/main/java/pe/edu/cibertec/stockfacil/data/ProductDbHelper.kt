package pe.edu.cibertec.stockfacil.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ProductDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_PRODUCTS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_NAME TEXT NOT NULL,
                $COLUMN_CODE TEXT NOT NULL UNIQUE,
                $COLUMN_PRICE REAL NOT NULL CHECK($COLUMN_PRICE >= 0),
                $COLUMN_QUANTITY INTEGER NOT NULL CHECK($COLUMN_QUANTITY >= 0)
            )
            """.trimIndent()
        )
        insertDemoProducts(db)
        createDraftTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2 && newVersion >= 2 && isProductsTableEmpty(db)) {
            insertDemoProducts(db)
        }
        if (oldVersion < 3) createDraftTables(db)
    }

    private fun createDraftTables(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS product_drafts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                request_id TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL, code TEXT NOT NULL UNIQUE,
                price REAL NOT NULL CHECK(price >= 0),
                quantity INTEGER NOT NULL CHECK(quantity >= 0),
                category_id INTEGER NOT NULL, category_name TEXT NOT NULL,
                brand TEXT NOT NULL, model TEXT NOT NULL,
                minimum_stock INTEGER NOT NULL CHECK(minimum_stock >= 0),
                attempted INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS cached_categories (
                id INTEGER PRIMARY KEY, name TEXT NOT NULL, description TEXT NOT NULL
            )
        """.trimIndent())
    }

    fun cacheCategories(categories: List<ProductCategory>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("cached_categories", null, null)
            categories.forEach { category ->
                db.insertOrThrow("cached_categories", null, ContentValues().apply {
                    put("id", category.id); put("name", category.name); put("description", category.description)
                })
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun cachedCategories(): List<ProductCategory> = buildList {
        readableDatabase.rawQuery("SELECT * FROM cached_categories ORDER BY name", null).use { cursor ->
            while (cursor.moveToNext()) add(ProductCategory(
                cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("name")),
                cursor.getString(cursor.getColumnIndexOrThrow("description"))
            ))
        }
    }

    fun pendingProducts(): List<Product> = buildList {
        readableDatabase.rawQuery("SELECT * FROM product_drafts ORDER BY id", null).use { cursor ->
            while (cursor.moveToNext()) {
                fun text(column: String) = cursor.getString(cursor.getColumnIndexOrThrow(column))
                fun number(column: String) = cursor.getInt(cursor.getColumnIndexOrThrow(column))
                add(Product(
                    id = -cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    name = text("name"), code = text("code"),
                    price = cursor.getDouble(cursor.getColumnIndexOrThrow("price")),
                    quantity = number("quantity"), categoryId = number("category_id"),
                    categoryName = text("category_name"), brand = text("brand"), model = text("model"),
                    minimumStock = number("minimum_stock"), requestId = text("request_id"),
                    awaitingConfirmation = number("attempted") != 0
                ))
            }
        }
    }

    fun saveDraft(product: Product): Boolean {
        require(InventoryRules.productErrors(product).isEmpty()) { "Revisa los datos del producto." }
        val values = ContentValues().apply {
            put("name", product.name); put("code", product.code); put("price", product.price)
            put("quantity", product.quantity); put("category_id", product.categoryId)
            put("category_name", product.categoryName); put("brand", product.brand)
            put("model", product.model); put("minimum_stock", product.minimumStock)
        }
        return try {
            if (product.id < 0) {
                writableDatabase.update("product_drafts", values, "id = ? AND attempted = 0",
                    arrayOf((-product.id).toString())) > 0
            } else {
                values.put("request_id", java.util.UUID.randomUUID().toString())
                writableDatabase.insertOrThrow("product_drafts", null, values) >= 0
            }
        } catch (_: SQLiteConstraintException) { false }
    }

    // Conserva contenido y clave si no se sabe todavía si el portal recibió el producto.
    fun markAttempted(product: Product, attempted: Boolean) {
        check(writableDatabase.update("product_drafts", ContentValues().apply {
            put("attempted", if (attempted) 1 else 0)
        }, "id = ? AND request_id = ?", arrayOf((-product.id).toString(), product.requestId)) == 1)
    }

    fun acknowledgeDraft(product: Product) {
        check(writableDatabase.delete("product_drafts", "id = ? AND request_id = ?",
            arrayOf((-product.id).toString(), product.requestId)) == 1)
    }

    fun deleteDraft(id: Long): Boolean = writableDatabase.delete(
        "product_drafts", "id = ? AND attempted = 0", arrayOf((-id).toString())
    ) > 0

    fun searchProducts(search: String = ""): List<Product> {
        val products = mutableListOf<Product>()
        val term = "%${search.trim()}%"
        readableDatabase.query(
            TABLE_PRODUCTS,
            arrayOf(COLUMN_ID, COLUMN_NAME, COLUMN_CODE, COLUMN_PRICE, COLUMN_QUANTITY),
            if (search.isBlank()) null else "$COLUMN_NAME LIKE ? OR $COLUMN_CODE LIKE ?",
            if (search.isBlank()) null else arrayOf(term, term),
            null,
            null,
            "$COLUMN_NAME COLLATE NOCASE ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                products += Product(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                    name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME)),
                    code = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CODE)),
                    price = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_PRICE)),
                    quantity = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_QUANTITY))
                )
            }
        }
        return products
    }

    fun insertProduct(product: Product): Boolean = try {
        writableDatabase.insertOrThrow(TABLE_PRODUCTS, null, product.toContentValues()) >= 0
    } catch (_: SQLiteConstraintException) {
        false
    }

    fun updateProduct(product: Product): Boolean = try {
        writableDatabase.update(
            TABLE_PRODUCTS,
            product.toContentValues(),
            "$COLUMN_ID = ?",
            arrayOf(product.id.toString())
        ) > 0
    } catch (_: SQLiteConstraintException) {
        false
    }

    fun deleteProduct(id: Long): Boolean =
        writableDatabase.delete(TABLE_PRODUCTS, "$COLUMN_ID = ?", arrayOf(id.toString())) > 0

    private fun Product.toContentValues() = ContentValues().apply {
        put(COLUMN_NAME, name)
        put(COLUMN_CODE, code)
        put(COLUMN_PRICE, price)
        put(COLUMN_QUANTITY, quantity)
    }

    private fun isProductsTableEmpty(db: SQLiteDatabase): Boolean =
        db.rawQuery("SELECT COUNT(*) FROM $TABLE_PRODUCTS", null).use { cursor ->
            cursor.moveToFirst() && cursor.getInt(0) == 0
        }

    private fun insertDemoProducts(db: SQLiteDatabase) {
        val demoProducts = listOf(
            Product(name = "Mouse Gamer Nebula M120", code = "DEMO-MOU-001", price = 79.90, quantity = 18),
            Product(name = "Teclado Mecánico Nova K87", code = "DEMO-TEC-002", price = 159.90, quantity = 9),
            Product(name = "SSD FlashCore 1 TB", code = "DEMO-SSD-003", price = 289.90, quantity = 12),
            Product(name = "Memoria RAM Orion DDR4 16 GB", code = "DEMO-RAM-004", price = 149.90, quantity = 14),
            Product(name = "Monitor PixelView 24 FHD", code = "DEMO-MON-005", price = 629.90, quantity = 5),
            Product(name = "Audífonos Pulse H5", code = "DEMO-AUD-006", price = 119.90, quantity = 11)
        )
        demoProducts.forEach { product ->
            db.insertOrThrow(TABLE_PRODUCTS, null, product.toContentValues())
        }
    }

    companion object {
        private const val DATABASE_NAME = "stock_facil.db"
        private const val DATABASE_VERSION = 3
        private const val TABLE_PRODUCTS = "products"
        private const val COLUMN_ID = "id"
        private const val COLUMN_NAME = "name"
        private const val COLUMN_CODE = "code"
        private const val COLUMN_PRICE = "price"
        private const val COLUMN_QUANTITY = "quantity"
    }
}
