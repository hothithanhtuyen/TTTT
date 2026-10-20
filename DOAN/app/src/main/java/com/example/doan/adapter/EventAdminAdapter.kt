package com.example.doan.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.doan.R
import com.example.doan.model.Event

class EventAdminAdapter(
    private var eventList: MutableList<Event>,
    private val onEdit: (Event) -> Unit,
    private val onDelete: (Event) -> Unit
) : RecyclerView.Adapter<EventAdminAdapter.EventViewHolder>() {

    class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtTitle: TextView = view.findViewById(R.id.txtEventTitle)
        val txtDate: TextView = view.findViewById(R.id.txtEventDate)
        val txtLocation: TextView = view.findViewById(R.id.txtEventLocation)
        val txtImage: TextView = view.findViewById(R.id.txtEventImage)
        val btnEdit: Button = view.findViewById(R.id.btnEditEvent)
        val btnDelete: Button = view.findViewById(R.id.btnDeleteEvent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event_admin, parent, false)
        return EventViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val events = eventList[position]
        holder.txtTitle.text = events.title
        holder.txtDate.text = events.date
        holder.txtLocation.text = events.location
        holder.txtImage.text = events.imageUrl
        holder.btnEdit.setOnClickListener { onEdit(events) }
        holder.btnDelete.setOnClickListener { onDelete(events) }
    }

    override fun getItemCount(): Int = eventList.size

    fun updateList(newList: MutableList<Event>) {
        eventList = newList
        notifyDataSetChanged()
    }
}
