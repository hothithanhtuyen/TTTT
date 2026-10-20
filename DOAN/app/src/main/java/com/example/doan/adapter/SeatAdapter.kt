package com.example.doan.adapter

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.example.doan.R

class SeatAdapter(private val context: Context, private val seats: List<String>) : BaseAdapter() {
    private val selectedSeats = mutableSetOf<String>()
    private val disabledSeats = mutableSetOf<String>()

    override fun getCount(): Int = seats.size
    override fun getItem(position: Int): Any = seats[position]
    override fun getItemId(position: Int): Long = position.toLong()

    fun disableSeats(seatsToDisable: List<String>) {
        disabledSeats.addAll(seatsToDisable)
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_seat, parent, false)
        val tvSeat = view.findViewById<TextView>(R.id.tvSeat)
        val seat = seats[position]
        tvSeat.text = seat

        if (selectedSeats.contains(seat)) {
            tvSeat.setBackgroundColor(Color.parseColor("#D32F2F"))
            tvSeat.setTextColor(Color.WHITE)
        } else if (disabledSeats.contains(seat)) {
            tvSeat.setBackgroundColor(Color.BLACK)
            tvSeat.isEnabled = false
        } else {
            tvSeat.setBackgroundColor(Color.LTGRAY)
            tvSeat.setTextColor(Color.BLACK)
            tvSeat.isEnabled = true
        }

        return view
    }

    fun toggleSeatSelection(seat: String) {
        if (disabledSeats.contains(seat)) {
            return
        }
        if (selectedSeats.contains(seat)) {
            selectedSeats.remove(seat)
        } else {
            selectedSeats.add(seat)
        }
        notifyDataSetChanged()
    }

    fun getSelectedSeats(): Set<String> {
        return selectedSeats
    }
}
