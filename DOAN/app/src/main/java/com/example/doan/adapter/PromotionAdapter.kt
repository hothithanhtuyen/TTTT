package com.example.doan.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.doan.R
import com.example.doan.model.Promotion

class PromotionAdapter(
    private var promotions: List<Promotion>,
    private val onClick: (Promotion) -> Unit
) : RecyclerView.Adapter<PromotionAdapter.PromotionViewHolder>() {

    class PromotionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val promotionImage: ImageView = view.findViewById(R.id.promotionImage)
        val promotionTitle: TextView = view.findViewById(R.id.promotionTitle)
        val promotionDate: TextView = view.findViewById(R.id.promotionDate)
        val promotionlocation: TextView = view.findViewById(R.id.promotionlocation)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PromotionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.promotion_item, parent, false)
        return PromotionViewHolder(view)
    }

    override fun onBindViewHolder(holder: PromotionViewHolder, position: Int) {
        val promotion = promotions[position]
        holder.promotionTitle.text = promotion.title
        holder.promotionDate.text = promotion.date
        holder.promotionlocation.text = promotion.location

        Glide.with(holder.itemView.context)
            .load(promotion.imageUrl)
            .placeholder(R.drawable.ic_launcher_background)
            .into(holder.promotionImage)

        holder.itemView.setOnClickListener {
            onClick(promotion)
        }
    }

    override fun getItemCount() = promotions.size

    fun updateList(newList: MutableList<Promotion>) {
        promotions = newList
        notifyDataSetChanged()
    }
}
