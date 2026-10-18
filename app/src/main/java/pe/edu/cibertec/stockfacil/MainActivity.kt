package pe.edu.cibertec.stockfacil

import android.os.Bundle
import android.content.Intent
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.ScrollView
import android.widget.Toast
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import pe.edu.cibertec.stockfacil.data.Product
import pe.edu.cibertec.stockfacil.data.ProductCategory
import pe.edu.cibertec.stockfacil.data.StockMovement
import pe.edu.cibertec.stockfacil.data.StockFacilApi
import pe.edu.cibertec.stockfacil.data.ProductDbHelper
import pe.edu.cibertec.stockfacil.data.ApiException
import pe.edu.cibertec.stockfacil.data.InventoryRules
import pe.edu.cibertec.stockfacil.databinding.ActivityMainBinding
import pe.edu.cibertec.stockfacil.ui.ProductAdapter
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val api by lazy { StockFacilApi(applicationContext) }
    private val network = Executors.newSingleThreadExecutor()
    private lateinit var localDb: ProductDbHelper
    private var pendingProducts: List<Product> = emptyList()
    private var sendingPending = false
    private var portalLoaded = false
    private var loadingPortal = false
    private lateinit var productAdapter: ProductAdapter
    private var defaultCategoryId = 1
    private var currentProducts: List<Product> = emptyList()
    private var allProducts: List<Product> = emptyList()
    private var currentCategories: List<ProductCategory> = emptyList()
    private var currentMovements: List<StockMovement> = emptyList()
    private val localNetworkPermission = "android.permission.ACCESS_LOCAL_NETWORK"
    private val requestLocalNetwork = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loadAllData()
        else binding.tvConnectionStatus.text = "Sin permiso de red local. Para conectar con el portal, permite Dispositivos cercanos en los ajustes de StockFácil y pulsa Actualizar. Tus borradores se conservan."
    }

    private fun hasLocalNetworkPermission(): Boolean = Build.VERSION.SDK_INT < 37 ||
        ContextCompat.checkSelfPermission(this, localNetworkPermission) == PackageManager.PERMISSION_GRANTED

    private fun connectToPortal() {
        if (hasLocalNetworkPermission()) loadAllData()
        else {
            binding.tvConnectionStatus.text = "Android necesita tu permiso para conectar StockFácil con el portal de esta computadora."
            requestLocalNetwork.launch(localNetworkPermission)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        localDb = ProductDbHelper(applicationContext)
        // Evita abrir inventario desde una actividad sin sesion autenticada.
        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding.btnSignOut.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
        productAdapter = ProductAdapter(::editProduct, ::confirmDelete)
        binding.rvProducts.layoutManager = LinearLayoutManager(this)
        binding.rvProducts.adapter = productAdapter
        binding.btnAddProduct.setOnClickListener { showProductDialog() }
        binding.btnSendPending.setOnClickListener { sendPendingProducts() }
        binding.btnRefreshPortal.setOnClickListener { connectToPortal() }
        binding.btnServerSettings.setOnClickListener { showServerSettings() }
        binding.btnStockMovement.setOnClickListener { showStockMovementDialog() }
        binding.btnAddCategory.setOnClickListener { showCategoryDialog() }
        binding.btnAddMovement.setOnClickListener { showStockMovementDialog() }
        binding.navBottom.setOnItemSelectedListener { item ->
            showPage(item.itemId)
            true
        }
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                refreshProducts(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        refreshLocalData()
        connectToPortal()
    }

    override fun onDestroy() {
        network.execute { localDb.close() }
        network.shutdown()
        super.onDestroy()
    }

    private fun showPage(itemId: Int) {
        binding.pageDashboard.visibility = if (itemId == R.id.navDashboard) View.VISIBLE else View.GONE
        binding.pageProducts.visibility = if (itemId == R.id.navProducts) View.VISIBLE else View.GONE
        binding.pageCategories.visibility = if (itemId == R.id.navCategories) View.VISIBLE else View.GONE
        binding.pageMovements.visibility = if (itemId == R.id.navMovements) View.VISIBLE else View.GONE
    }

    /** Permite cambiar la direccion del portal sin recompilar la app. */
    private fun showServerSettings() {
        if (loadingPortal || sendingPending) {
            Toast.makeText(this, "Espera a que termine la operacion actual.", Toast.LENGTH_LONG).show()
            return
        }
        val field = TextInputLayout(this).apply {
            hint = "Direccion del portal"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        }
        val input = TextInputEditText(field.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            setText(api.serverUrl())
            selectAll()
        }
        field.addView(input)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), 0)
            addView(TextView(this@MainActivity).apply {
                text = "Usa la IP de la computadora y el puerto 5080. Ambos dispositivos deben estar conectados a la misma red Wi-Fi."
                textSize = 13f
                setPadding(0, 0, 0, dp(12))
            })
            addView(field)
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Conexion con el portal")
            .setView(content)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar y conectar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                field.error = null
                try {
                    val saved = api.saveServerUrl(input.text?.toString().orEmpty())
                    portalLoaded = false
                    allProducts = emptyList()
                    currentCategories = emptyList()
                    currentMovements = emptyList()
                    binding.tvConnectionStatus.text = "Conectando a $saved..."
                    refreshProducts()
                    renderDashboard()
                    renderCategories()
                    renderMovements()
                    dialog.dismiss()
                    connectToPortal()
                } catch (error: IllegalArgumentException) {
                    field.error = error.message ?: "Direccion no valida."
                }
            }
        }
        dialog.show()
    }

    private fun loadAllData() {
        if (isDestroyed || isFinishing || network.isShutdown || loadingPortal) return
        if (!hasLocalNetworkPermission()) {
            binding.tvConnectionStatus.text = "Falta autorizar la red local. Pulsa Actualizar para conectar con el portal."
            return
        }
        loadingPortal = true
        binding.btnRefreshPortal.isEnabled = false
        network.execute {
            runCatching {
                val categories = api.getCategories()
                localDb.cacheCategories(categories)
                Triple(api.getProducts(), categories, api.getStockMovements())
            }.onSuccess { (products, categories, movements) ->
                runOnUiThread {
                    if (isDestroyed || isFinishing) return@runOnUiThread
                    loadingPortal = false
                    binding.btnRefreshPortal.isEnabled = true
                    allProducts = products
                    currentCategories = categories
                    currentMovements = movements
                    defaultCategoryId = categories.firstOrNull()?.id ?: 1
                    portalLoaded = true
                    binding.tvConnectionStatus.text = "Datos del portal actualizados. Los borradores siguen en el celular hasta enviarlos."
                    refreshProducts()
                    renderDashboard()
                    renderCategories()
                    renderMovements()
                }
            }.onFailure {
                runOnUiThread {
                    if (isDestroyed || isFinishing) return@runOnUiThread
                    loadingPortal = false
                    binding.btnRefreshPortal.isEnabled = true
                    binding.tvConnectionStatus.text = if (portalLoaded)
                        "No se pudo actualizar el portal. Se muestran los últimos datos consultados y tus borradores."
                    else "Portal no disponible. Puedes trabajar con borradores locales; las existencias del portal no están cargadas."
                    refreshProducts()
                }
            }
        }
    }

    private fun refreshProducts(search: String = binding.etSearch.text?.toString().orEmpty()) {
        val term = search.trim()
        currentProducts = (pendingProducts + allProducts).filter { product ->
            term.isBlank() || listOf(product.name, product.code, product.brand, product.model)
                .any { it.contains(term, ignoreCase = true) }
        }
        showProducts(currentProducts)
        binding.tvPendingCount.text = "${pendingProducts.size} borradores sin confirmación local · el resumen muestra las existencias consultadas del portal"
        binding.btnSendPending.isEnabled = pendingProducts.isNotEmpty() && !sendingPending
        binding.btnSendPending.text = if (sendingPending) "Enviando…" else "Enviar al portal"
    }

    private fun refreshLocalData() {
        if (isDestroyed || isFinishing || network.isShutdown) return
        network.execute {
            runCatching { localDb.pendingProducts() to localDb.cachedCategories() }
                .onSuccess { (drafts, categories) -> runOnUiThread {
                    if (isDestroyed || isFinishing) return@runOnUiThread
                    pendingProducts = drafts
                    if (!portalLoaded) currentCategories = categories
                    refreshProducts()
                } }
                .onFailure { showMessage("No se pudieron leer los borradores: ${it.message}") }
        }
    }

    private fun sendPendingProducts() {
        if (sendingPending || pendingProducts.isEmpty()) return
        if (!hasLocalNetworkPermission()) {
            connectToPortal()
            return
        }
        sendingPending = true
        refreshProducts()
        network.execute {
            var sent = 0
            val problems = mutableListOf<String>()
            runCatching {
                for (draft in localDb.pendingProducts()) {
                    localDb.markAttempted(draft, true)
                    try {
                        api.sendDraft(draft)
                        localDb.acknowledgeDraft(draft)
                        sent++
                    } catch (error: Exception) {
                        val rejected = error is ApiException && error.statusCode in listOf(400, 404, 409, 422)
                        if (rejected) localDb.markAttempted(draft, false)
                        problems.add("${draft.code}: ${error.message ?: "no se pudo confirmar el envío"}")
                        if (!rejected) break
                    }
                }
            }.onFailure { problems.add(it.message ?: "No se pudo completar el envío") }
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                sendingPending = false
                refreshLocalData()
                loadAllData()
                MaterialAlertDialogBuilder(this)
                    .setTitle("Envío al portal")
                    .setMessage(buildString {
                        append("$sent productos confirmados por el portal.")
                        if (problems.isNotEmpty()) {
                            append("\n\nLos registros no confirmados se conservan en el celular.\n\n")
                            append(problems.joinToString("\n"))
                            append("\n\nSi falta conexión, vuelve a pulsar Enviar al portal cuando se restablezca. Si el portal rechaza los datos, corrige el borrador.")
                        }
                    })
                    .setPositiveButton("Entendido", null).show()
            }
        }
    }

    private fun showProducts(products: List<Product>) {
        productAdapter.submitProducts(products)
        binding.tvProductCount.text = if (products.size == 1) "1 producto" else "${products.size} productos"
        binding.tvEmptyState.text = if (binding.etSearch.text?.isNotBlank() == true)
            "No hay coincidencias en los productos cargados ni en los borradores."
        else if (!portalLoaded) "Aún no se cargó el inventario del portal. Tus nuevos productos aparecerán aquí como borradores."
        else "Aún no hay productos. Agrega el primero para comenzar."
        binding.tvEmptyState.visibility = if (products.isEmpty()) View.VISIBLE else View.GONE
        binding.rvProducts.visibility = if (products.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun renderDashboard() {
        val lowStock = allProducts.filter { it.lowStock }
        binding.tvStatProducts.text = allProducts.size.toString()
        binding.tvStatUnits.text = allProducts.sumOf { it.quantity.toLong() }.toString()
        binding.tvStatLow.text = lowStock.size.toString()
        binding.tvStatCategories.text = currentCategories.size.toString()

        binding.llLowStock.removeAllViews()
        if (lowStock.isEmpty()) {
            binding.llLowStock.addView(simpleText("No hay productos con stock bajo."))
        } else {
            lowStock.forEach { product ->
                binding.llLowStock.addView(simpleText("${product.name}\n${product.quantity} unidades · mínimo ${product.minimumStock}"))
            }
        }

        binding.llRecentMovements.removeAllViews()
        if (currentMovements.isEmpty()) {
            binding.llRecentMovements.addView(simpleText("Aún no hay movimientos registrados."))
        } else {
            currentMovements.take(4).forEach { movement ->
                val label = if (movement.type == "IN") "Ingreso" else "Salida"
                binding.llRecentMovements.addView(simpleText("$label · ${movement.productName}\n${movement.quantity} unidades · ${formatDate(movement.createdAt)}"))
            }
        }
    }

    private fun renderCategories() {
        binding.llCategories.removeAllViews()
        if (currentCategories.isEmpty()) {
            binding.llCategories.addView(simpleText("No hay categorías registradas."))
            return
        }
        currentCategories.forEach { category ->
            val productCount = allProducts.count { it.categoryId == category.id }
            val card = createCard()
            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val padding = dp(14)
                setPadding(padding, padding, padding, padding)
            }
            content.addView(simpleText(category.name, 16, true))
            content.addView(simpleText(category.description.ifBlank { "$productCount productos" }, 13))
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.addView(MaterialButton(this).apply {
                text = "Editar"
                setOnClickListener { showCategoryDialog(category) }
            })
            buttons.addView(MaterialButton(this).apply {
                text = "Desactivar"
                setOnClickListener { confirmDeleteCategory(category) }
            })
            content.addView(buttons)
            card.addView(content)
            binding.llCategories.addView(card)
        }
    }

    private fun renderMovements() {
        binding.llMovements.removeAllViews()
        if (currentMovements.isEmpty()) {
            binding.llMovements.addView(simpleText("Aún no hay movimientos. Registra un ingreso o una salida."))
            return
        }
        currentMovements.forEach { movement ->
            val label = if (movement.type == "IN") "INGRESO" else "SALIDA"
            val details = buildString {
                append("$label · ${movement.quantity} unidades\n")
                append("SKU: ${movement.sku} · ${formatDate(movement.createdAt)}")
                if (movement.notes.isNotBlank()) append("\n${movement.notes}")
            }
            val card = createCard()
            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val padding = dp(14)
                setPadding(padding, padding, padding, padding)
            }
            content.addView(simpleText(movement.productName, 15, true))
            content.addView(simpleText(details, 12))
            card.addView(content)
            binding.llMovements.addView(card)
        }
    }

    private fun createCard(): MaterialCardView = MaterialCardView(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) }
        radius = dp(14).toFloat()
        cardElevation = dp(1).toFloat()
        setCardBackgroundColor(getColor(android.R.color.white))
        strokeWidth = dp(1)
        setStrokeColor(getColor(R.color.stock_border))
    }

    private fun simpleText(text: String, size: Int = 13, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text
        textSize = size.toFloat()
        setTextColor(getColor(R.color.stock_text))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(5), 0, dp(5))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun formatDate(value: String): String = runCatching {
        java.time.Instant.parse(value).atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"))
    }.getOrDefault(value.take(16).replace('T', ' '))

    private fun showProductDialog(product: Product? = null) {
        if (sendingPending) { showMessage("Espera a que termine el envío."); return }
        if (product?.awaitingConfirmation == true) {
            showMessage("Primero vuelve a enviar para confirmar si el portal recibió este producto.")
            return
        }
        if (currentCategories.isEmpty()) {
            showMessage("Conéctate al portal y pulsa Actualizar para cargar las categorías antes de crear productos.")
            return
        }
        val formCategories = currentCategories.toList()
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val horizontalPadding = (24 * resources.displayMetrics.density).toInt()
            val topPadding = (8 * resources.displayMetrics.density).toInt()
            setPadding(horizontalPadding, topPadding, horizontalPadding, 0)
        }

        val (nameLayout, nameInput) = createInput(form, "Artículo o modelo", InputType.TYPE_CLASS_TEXT)
        val (codeLayout, codeInput) = createInput(form, "SKU o código único", InputType.TYPE_CLASS_TEXT)
        val categoryLabel = simpleText("Categoría", 12)
        form.addView(categoryLabel)
        val categorySpinner = Spinner(this)
        categorySpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            formCategories.map { it.name }
        )
        val productCategoryIndex = formCategories.indexOfFirst { it.id == product?.categoryId }
        if (product != null && productCategoryIndex < 0) {
            categoryLabel.text = "Categoría anterior no disponible; elige una categoría activa"
        }
        if (productCategoryIndex >= 0) categorySpinner.setSelection(productCategoryIndex)
        form.addView(categorySpinner)
        val (brandLayout, brandInput) = createInput(form, "Marca", InputType.TYPE_CLASS_TEXT)
        val (modelLayout, modelInput) = createInput(form, "Modelo", InputType.TYPE_CLASS_TEXT)
        val (priceLayout, priceInput) = createInput(form, "Precio de venta (S/)", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val (quantityLayout, quantityInput) = createInput(form, "Unidades en stock", InputType.TYPE_CLASS_NUMBER)
        val (minimumLayout, minimumInput) = createInput(form, "Stock mínimo", InputType.TYPE_CLASS_NUMBER)
        if (product != null && product.id > 0) {
            quantityInput.isEnabled = false
            quantityLayout.helperText = "Para cambiar las unidades utiliza Mover stock."
        }

        product?.let {
            nameInput.setText(it.name)
            codeInput.setText(it.code)
            brandInput.setText(it.brand)
            modelInput.setText(it.model)
            priceInput.setText(it.price.toString())
            quantityInput.setText(it.quantity.toString())
            minimumInput.setText(it.minimumStock.toString())
        }
        if (product == null) minimumInput.setText("2")

        val formScroll = ScrollView(this).apply { addView(form) }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (product == null) "Nuevo producto" else "Editar producto")
            .setMessage(if (product == null || product.id < 0)
                "Se guardará en este celular. Después pulsa Enviar al portal para compartirlo."
            else "Este producto ya está en el portal; sus cambios requieren conexión.")
            .setView(formScroll)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton(if (product == null || product.id < 0) "Guardar en celular" else "Guardar", null)
            .create()

        dialog.setOnShowListener {
            keepDialogActionsVisible(dialog, formScroll)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                listOf(nameLayout, codeLayout, brandLayout, modelLayout, priceLayout, quantityLayout, minimumLayout).forEach { it.error = null }
                val name = nameInput.text?.toString()?.trim().orEmpty()
                val code = codeInput.text?.toString()?.trim().orEmpty()
                val brand = brandInput.text?.toString()?.trim().orEmpty()
                val model = modelInput.text?.toString()?.trim().orEmpty()
                val price = priceInput.text?.toString()?.trim()?.replace(',', '.')?.toDoubleOrNull()
                val quantity = quantityInput.text?.toString()?.trim()?.toIntOrNull()
                val minimumStock = minimumInput.text?.toString()?.trim()?.toIntOrNull()
                var valid = true
                if (name.isBlank()) { nameLayout.error = "Escribe el nombre del producto"; valid = false }
                if (code.isBlank()) { codeLayout.error = "Escribe un código"; valid = false }
                if (price == null || !price.isFinite() || price < 0) { priceLayout.error = "Ingresa un precio válido"; valid = false }
                if (quantity == null || quantity < 0) { quantityLayout.error = "Ingresa una cantidad válida"; valid = false }
                if (minimumStock == null || minimumStock < 0) { minimumLayout.error = "Ingresa un mínimo válido"; valid = false }
                if (currentCategories.isEmpty()) { categoryLabel.setTextColor(getColor(android.R.color.holo_red_dark)); valid = false }
                if (!valid) return@setOnClickListener

                val editedProduct = Product(
                    id = product?.id ?: 0,
                    name = name,
                    code = code,
                    price = price!!,
                    quantity = quantity!!,
                    categoryId = formCategories[categorySpinner.selectedItemPosition].id,
                    categoryName = formCategories[categorySpinner.selectedItemPosition].name,
                    minimumStock = minimumStock!!,
                    brand = brand,
                    model = model
                )
                val errors = InventoryRules.productErrors(editedProduct)
                val fields = mapOf("name" to nameLayout, "code" to codeLayout, "brand" to brandLayout,
                    "model" to modelLayout, "price" to priceLayout, "quantity" to quantityLayout,
                    "minimum" to minimumLayout)
                errors.forEach { (field, message) -> fields[field]?.error = message }
                if (errors.isNotEmpty()) return@setOnClickListener
                setDialogBusy(dialog, form, true)
                val isLocal = editedProduct.id <= 0
                network.execute {
                    runCatching {
                        if (isLocal) {
                            check(localDb.saveDraft(editedProduct)) {
                                "El código ya existe en los borradores o el registro está esperando confirmación."
                            }
                        } else api.saveProduct(editedProduct, editedProduct.categoryId, editedProduct.minimumStock)
                    }
                        .onSuccess {
                            runOnUiThread {
                                if (isDestroyed || isFinishing) return@runOnUiThread
                                dialog.dismiss()
                                if (isLocal) refreshLocalData() else loadAllData()
                                showMessage(if (isLocal) "Guardado en el celular. Pendiente de envío al portal." else "Producto actualizado en el portal")
                            }
                        }
                        .onFailure { error -> runOnUiThread {
                            if (isDestroyed || isFinishing) return@runOnUiThread
                            codeLayout.error = error.message ?: "No se pudo guardar"
                            setDialogBusy(dialog, form, false)
                            if (product != null && product.id > 0) quantityInput.isEnabled = false
                        } }
                }
            }
        }
        dialog.show()
    }

    private fun createInput(parent: LinearLayout, label: String, inputType: Int): Pair<TextInputLayout, TextInputEditText> {
        val field = TextInputLayout(this).apply {
            hint = label
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            val margin = (8 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = margin }
        }
        val input = TextInputEditText(field.context).apply { this.inputType = inputType; isSingleLine = true }
        field.addView(input)
        parent.addView(field)
        return field to input
    }

    private fun editProduct(product: Product) = showProductDialog(product)

    /** Reserva espacio para el título y las acciones, también al mostrar el teclado. */
    private fun keepDialogActionsVisible(dialog: AlertDialog, scroll: ScrollView) {
        val window = dialog.window ?: return
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val decor = window.decorView
        val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            val visible = android.graphics.Rect()
            decor.getWindowVisibleDisplayFrame(visible)
            val available = visible.height().takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
            val bodyHeight = (available * 0.45f).toInt().coerceAtMost(dp(420)).coerceAtLeast(dp(72))
            if (scroll.layoutParams.height != bodyHeight) {
                scroll.layoutParams = scroll.layoutParams.apply { height = bodyHeight }
            }
        }
        decor.viewTreeObserver.addOnGlobalLayoutListener(listener)
        dialog.setOnDismissListener {
            if (decor.viewTreeObserver.isAlive) decor.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
        listener.onGlobalLayout()
    }

    private fun setDialogBusy(dialog: AlertDialog, form: View, busy: Boolean) {
        fun enable(view: View) {
            view.isEnabled = !busy
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) enable(view.getChildAt(i))
        }
        enable(form)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !busy
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = !busy
        dialog.setCancelable(!busy)
        dialog.setCanceledOnTouchOutside(!busy)
    }

    private fun showStockMovementDialog() {
        if (allProducts.isEmpty()) {
            Toast.makeText(this, "Primero carga o registra un producto.", Toast.LENGTH_SHORT).show()
            return
        }
        val formProducts = allProducts.toList()

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 3, padding, 0)
        }
        val productSpinner = Spinner(this)
        productSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            formProducts.map { "${it.name} · ${it.code} · stock ${it.quantity}" }
        )
        form.addView(productSpinner)

        val typeGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val incoming = RadioButton(this).apply { id = View.generateViewId(); text = "Ingreso"; isChecked = true }
        val outgoing = RadioButton(this).apply { id = View.generateViewId(); text = "Salida" }
        typeGroup.addView(incoming)
        typeGroup.addView(outgoing)
        form.addView(typeGroup)

        val (quantityLayout, quantityInput) = createInput(form, "Cantidad de unidades", InputType.TYPE_CLASS_NUMBER)
        val (notesLayout, notesInput) = createInput(form, "Motivo o referencia (opcional)", InputType.TYPE_CLASS_TEXT)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Movimiento de stock")
            .setMessage("El movimiento actualizará el inventario del portal y del teléfono.")
            .setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Registrar", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val quantity = quantityInput.text?.toString()?.toIntOrNull()
                if (quantity == null || quantity !in 1..InventoryRules.MAX_UNITS) {
                    quantityLayout.error = "Ingresa un entero entre 1 y 1 000 000"
                    return@setOnClickListener
                }
                quantityLayout.error = null
                val selected = formProducts[productSpinner.selectedItemPosition]
                val notes = notesInput.text?.toString()?.trim().orEmpty()
                notesLayout.error = InventoryRules.note(notes)
                if (notesLayout.error != null) return@setOnClickListener
                val type = if (incoming.isChecked) "IN" else "OUT"
                if (type == "OUT" && quantity > selected.quantity) {
                    quantityLayout.error = "La salida supera las unidades disponibles; actualiza el inventario si cambió."
                    return@setOnClickListener
                }
                if (type == "IN" && selected.quantity.toLong() + quantity > InventoryRules.MAX_UNITS) {
                    quantityLayout.error = "El ingreso supera el límite de 1 000 000 de unidades"
                    return@setOnClickListener
                }
                setDialogBusy(dialog, form, true)
                network.execute {
                    runCatching { api.recordStockMovement(selected.id, type, quantity, notes) }
                        .onSuccess {
                            runOnUiThread {
                                if (isDestroyed || isFinishing) return@runOnUiThread
                                dialog.dismiss()
                                loadAllData()
                                Toast.makeText(this, "Movimiento registrado", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .onFailure { error ->
                            runOnUiThread {
                                if (isDestroyed || isFinishing) return@runOnUiThread
                                setDialogBusy(dialog, form, false)
                                val rejected = error is ApiException && error.statusCode in listOf(400, 404, 409, 422)
                                if (rejected) {
                                    quantityLayout.error = error.message
                                } else {
                                    quantityLayout.error = "No se recibió confirmación. El movimiento podría haberse guardado. Revisa el historial antes de registrarlo otra vez."
                                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                                        text = "Consultar historial"
                                        setOnClickListener {
                                            dialog.dismiss()
                                            binding.navBottom.selectedItemId = R.id.navMovements
                                            loadAllData()
                                        }
                                    }
                                }
                            }
                        }
                }
            }
        }
        dialog.show()
    }

    private fun showCategoryDialog(category: ProductCategory? = null) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val (nameLayout, nameInput) = createInput(form, "Nombre de categoría", InputType.TYPE_CLASS_TEXT)
        val (descriptionLayout, descriptionInput) = createInput(form, "Descripción", InputType.TYPE_CLASS_TEXT)
        category?.let {
            nameInput.setText(it.name)
            descriptionInput.setText(it.description)
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (category == null) "Nueva categoría" else "Editar categoría")
            .setView(form)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text?.toString()?.trim().orEmpty()
                val description = descriptionInput.text?.toString()?.trim().orEmpty()
                nameLayout.error = InventoryRules.label(name, 80)
                descriptionLayout.error = InventoryRules.note(description)
                if (nameLayout.error != null || descriptionLayout.error != null) return@setOnClickListener
                setDialogBusy(dialog, form, true)
                network.execute {
                    runCatching { api.saveCategory(category?.id ?: 0, name, description) }
                        .onSuccess { runOnUiThread {
                            if (isDestroyed || isFinishing) return@runOnUiThread
                            dialog.dismiss(); loadAllData()
                        } }
                        .onFailure { error -> runOnUiThread {
                            if (isDestroyed || isFinishing) return@runOnUiThread
                            nameLayout.error = error.message ?: "No se pudo guardar"
                            setDialogBusy(dialog, form, false)
                        } }
                }
            }
        }
        dialog.show()
    }

    private fun confirmDeleteCategory(category: ProductCategory) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Desactivar categoría")
            .setMessage("¿Deseas desactivar ${category.name}? Las categorías con productos activos no se pueden desactivar.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Desactivar") { _, _ ->
                network.execute {
                    runCatching { api.deleteCategory(category.id) }
                        .onSuccess { runOnUiThread { loadAllData() } }
                        .onFailure { error -> showMessage(error.message ?: "No se pudo desactivar") }
                }
            }
            .show()
    }

    private fun confirmDelete(product: Product) {
        if (sendingPending || product.awaitingConfirmation) {
            showMessage("Confirma primero el envío al portal antes de modificar este registro.")
            return
        }
        if (product.id < 0) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Descartar borrador")
                .setMessage("¿Descartar ${product.name}? Se eliminará solamente del celular.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Descartar") { _, _ ->
                    network.execute {
                        runCatching { check(localDb.deleteDraft(product.id)) { "El borrador ya se envió o espera confirmación." } }
                            .onSuccess { runOnUiThread { refreshLocalData() } }
                            .onFailure { showMessage(it.message ?: "No se pudo descartar") }
                    }
                }.show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Eliminar producto")
            .setMessage("¿Deseas eliminar ${product.name} del inventario?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                network.execute {
                    runCatching { api.deleteProduct(product.id) }
                        .onSuccess { runOnUiThread { loadAllData(); Toast.makeText(this, "Producto eliminado", Toast.LENGTH_SHORT).show() } }
                        .onFailure { error -> showMessage("No se pudo eliminar: ${error.message}") }
                }
            }
            .show()
    }

    private fun showMessage(message: String) = runOnUiThread {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
