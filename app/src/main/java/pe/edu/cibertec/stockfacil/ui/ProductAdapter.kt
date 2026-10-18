package pe.edu.cibertec.stockfacil.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import pe.edu.cibertec.stockfacil.data.Product
import pe.edu.cibertec.stockfacil.databinding.ItemProductBinding
import java.text.NumberFormat
import java.util.Locale

class ProductAdapter(
    private val onEdit: (Product) -> Unit,
    private val onDelete: (Product) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    private val products = mutableListOf<Product>()
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("es", "PE"))

    fun submitProducts(newProducts: List<Product>) {
        products.clear()
        products.addAll(newProducts)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemProductBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(products[position])
    }

    override fun getItemCount(): Int = products.size

    inner class ProductViewHolder(
        private val binding: ItemProductBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(product: Product) {
            binding.tvName.text = product.name
            binding.tvCode.text = buildString {
                append("SKU: ${product.code}")
                if (product.categoryName.isNotBlank()) append("  ·  ${product.categoryName}")
            }
            binding.tvDetails.text = buildString {
                append("${currencyFormat.format(product.price)}  ·  Stock: ${product.quantity}")
                if (product.lowStock) append("  ·  STOCK BAJO")
                if (product.id < 0) append(if (product.awaitingConfirmation)
                    "\nEnvío sin confirmar · pulsa Enviar al portal"
                else "\nGuardado en el celular · pendiente de envío")
            }
            binding.btnEdit.isEnabled = !product.awaitingConfirmation
            binding.btnDelete.isEnabled = !product.awaitingConfirmation
            binding.btnDelete.text = if (product.id < 0) "Descartar" else "Eliminar"
            binding.btnEdit.setOnClickListener { onEdit(product) }
            binding.btnDelete.setOnClickListener { onDelete(product) }
        }
    }
}
