package com.example.doan.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.doan.R
import com.example.doan.model.Promotion

class PromotionAdminAdapter(
    private var promotionList: MutableList<Promotion>,
    private val onEdit: (Promotion) -> Unit,
    private val onDelete: (Promotion) -> Unit
) : RecyclerView.Adapter<PromotionAdminAdapter.PromotionViewHolder>() {

    class PromotionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtTitle: TextView = view.findViewById(R.id.txtPromotionTitle)
        val txtDate: TextView = view.findViewById(R.id.txtPromotionDate)
        val txtLocation: TextView = view.findViewById(R.id.txtPromotionLocation)
        val txtImage: TextView = view.findViewById(R.id.txtPromotionImage)
        val btnEdit: Button = view.findViewById(R.id.btnEditPromotion)
        val btnDelete: Button = view.findViewById(R.id.btnDeletePromotion)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PromotionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_promotion_admin, parent, false)
        return PromotionViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: PromotionViewHolder, position: Int) {
        val promotion = promotionList[position]
        holder.txtTitle.text = promotion.title
        holder.txtDate.text = promotion.date
        holder.txtLocation.text = promotion.location
        holder.txtImage.text = promotion.imageUrl
        holder.btnEdit.setOnClickListener { onEdit(promotion) }
        holder.btnDelete.setOnClickListener { onDelete(promotion) }
    }

    override fun getItemCount(): Int = promotionList.size

    fun updateList(newList: MutableList<Promotion>) {
        promotionList = newList
        notifyDataSetChanged()
    }
}
