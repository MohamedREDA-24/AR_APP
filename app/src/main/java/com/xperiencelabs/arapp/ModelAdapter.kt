import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.xperiencelabs.arapp.ModelItem
import com.xperiencelabs.arapp.R

class ModelAdapter(
    private val models: List<ModelItem>,
    private val onItemClick: (ModelItem) -> Unit
) : RecyclerView.Adapter<ModelAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val modelThumbnail: ImageView = view.findViewById(R.id.model_thumbnail)
        val modelName: TextView = view.findViewById(R.id.model_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_model, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val model = models[position]
        holder.modelName.text = model.name  // ✅ Bind Model Name
        holder.modelThumbnail.setImageResource(R.drawable.ic_placeholder)

        holder.itemView.setOnClickListener {
            onItemClick(model)
        }
    }

    override fun getItemCount() = models.size
}
