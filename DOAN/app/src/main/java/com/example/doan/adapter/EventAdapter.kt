package com.example.doan.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.doan.R
import com.example.doan.model.Event

class EventAdapter(
    private var events: List<Event>,
    private val onClick: (Event) -> Unit
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val eventImage: ImageView = view.findViewById(R.id.eventImage)
        val eventTitle: TextView = view.findViewById(R.id.eventTitle)
        val eventDate: TextView = view.findViewById(R.id.eventDate)
        val eventpot: TextView = view.findViewById(R.id.eventpot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.event_item, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]
        holder.eventTitle.text = event.title
        holder.eventDate.text = event.date
        holder.eventpot.text = event.location

        Glide.with(holder.itemView.context)
            .load(event.imageUrl)
            .placeholder(R.drawable.ic_launcher_background)
            .into(holder.eventImage)

        holder.itemView.setOnClickListener {
            onClick(event)
        }
    }

    override fun getItemCount() = events.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(newList: List<Event>) {
        events = newList
        notifyDataSetChanged()
    }
}
