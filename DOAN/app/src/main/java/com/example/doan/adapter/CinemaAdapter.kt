package com.example.doan.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Spinner
import androidx.recyclerview.widget.RecyclerView
import com.example.doan.R
import com.example.doan.model.Cinema
import com.example.doan.utils.Constants

class CinemaAdapter : RecyclerView.Adapter<CinemaAdapter.CinemaViewHolder>() {

    private val cinemaList = mutableListOf<Cinema>()

    inner class CinemaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val provinceSpinner: Spinner = view.findViewById(R.id.cinemaLocation)
        val cinemaSpinner: Spinner = view.findViewById(R.id.spinnerCinema)
        val checkBoxes = mapOf(
            "Chủ Nhật" to view.findViewById<CheckBox>(R.id.checkBoxSunday),
            "Thứ Hai" to view.findViewById<CheckBox>(R.id.checkBoxMonday),
            "Thứ Ba" to view.findViewById<CheckBox>(R.id.checkBoxTuesday),
            "Thứ Tư" to view.findViewById<CheckBox>(R.id.checkBoxWednesday),
            "Thứ Năm" to view.findViewById<CheckBox>(R.id.checkBoxThursday),
            "Thứ Sáu" to view.findViewById<CheckBox>(R.id.checkBoxFriday),
            "Thứ Bảy" to view.findViewById<CheckBox>(R.id.checkBoxSaturday)
        )

        val slotCheckBoxesMap = mapOf(
            "Chủ Nhật" to listOf(
                view.findViewById<CheckBox>(R.id.checkBoxSlot1),
                view.findViewById<CheckBox>(R.id.checkBoxSlot2),
                view.findViewById<CheckBox>(R.id.checkBoxSlot3),
                view.findViewById<CheckBox>(R.id.checkBoxSlot4),
                view.findViewById<CheckBox>(R.id.checkBoxSlot5)),
            "Thứ Hai" to listOf(
                view.findViewById<CheckBox>(R.id.checkBoxSlot6),
                view.findViewById<CheckBox>(R.id.checkBoxSlot7),
                view.findViewById<CheckBox>(R.id.checkBoxSlot8),
                view.findViewById<CheckBox>(R.id.checkBoxSlot9),
                view.findViewById<CheckBox>(R.id.checkBoxSlot10)),
            "Thứ Ba" to listOf(
                view.findViewById<CheckBox>(R.id.checkBoxSlot11),
                view.findViewById<CheckBox>(R.id.checkBoxSlot12),
                view.findViewById<CheckBox>(R.id.checkBoxSlot13),
                view.findViewById<CheckBox>(R.id.checkBoxSlot14),
                view.findViewById<CheckBox>(R.id.checkBoxSlot15)),
            "Thứ Tư" to listOf(
                view.findViewById<CheckBox>(R.id.checkBoxSlot16),
                view.findViewById<CheckBox>(R.id.checkBoxSlot17),
                view.findViewById<CheckBox>(R.id.checkBoxSlot18),
                view.findViewById<CheckBox>(R.id.checkBoxSlot19),
                view.findViewById<CheckBox>(R.id.checkBoxSlot20)),
            "Thứ Năm" to listOf(
                view.findViewById<CheckBox>(R.id.checkBoxSlot21),
                view.findViewById<CheckBox>(R.id.checkBoxSlot22),
                view.findViewById<CheckBox>(R.id.checkBoxSlot23),
                view.findViewById<CheckBox>(R.id.checkBoxSlot24),
                view.findViewById<CheckBox>(R.id.checkBoxSlot25)),
            "Thứ Sáu" to listOf(
                view.findViewById<CheckBox>(R.id.checkBoxSlot26),
                view.findViewById<CheckBox>(R.id.checkBoxSlot27),
                view.findViewById<CheckBox>(R.id.checkBoxSlot28),
                view.findViewById<CheckBox>(R.id.checkBoxSlot29),
                view.findViewById<CheckBox>(R.id.checkBoxSlot30)),
            "Thứ Bảy" to listOf(
                view.findViewById<CheckBox>(R.id.checkBoxSlot31),
                view.findViewById<CheckBox>(R.id.checkBoxSlot32),
                view.findViewById<CheckBox>(R.id.checkBoxSlot33),
                view.findViewById<CheckBox>(R.id.checkBoxSlot34),
                view.findViewById<CheckBox>(R.id.checkBoxSlot35))
        )

        fun bind(cinema: Cinema) {
            val provinces = Constants.PROVINCES
            provinceSpinner.adapter = ArrayAdapter(itemView.context, android.R.layout.simple_spinner_item, provinces)
            provinceSpinner.setSelection(provinces.indexOf(cinema.location))

            provinceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                    cinema.location = provinces[position]
                    val cinemas = Constants.getCinemasForProvince(cinema.location)
                    cinemaSpinner.adapter = ArrayAdapter(itemView.context, android.R.layout.simple_spinner_item, cinemas)
                    cinema.theater = cinemas.firstOrNull() ?: ""
                }
                override fun onNothingSelected(parent: AdapterView<*>) {}
            }

            checkBoxes.forEach { (day, dayCheckBox) ->
                dayCheckBox.isChecked = cinema.schedule[day]?.isNotEmpty() ?: false
                dayCheckBox.setOnCheckedChangeListener { _, isChecked ->
                    if (!isChecked) {
                        cinema.schedule[day]?.clear()
                        slotCheckBoxesMap[day]?.forEach { it.isEnabled = false; it.isChecked = false }
                    } else {
                        slotCheckBoxesMap[day]?.forEach { it.isEnabled = true }
                    }
                }

                slotCheckBoxesMap[day]?.forEachIndexed { index, slotCheckBox ->
                    slotCheckBox.isEnabled = dayCheckBox.isChecked
                    slotCheckBox.isChecked = cinema.schedule[day]?.contains(index + 1) ?: false
                    slotCheckBox.setOnCheckedChangeListener { _, isChecked ->
                        val selectedSlots = cinema.schedule[day] ?: mutableListOf()
                        if (isChecked) {
                            if (!selectedSlots.contains(index + 1)) {
                                selectedSlots.add(index + 1)
                            }
                        } else {
                            selectedSlots.remove(index + 1)
                        }
                        cinema.schedule[day] = selectedSlots.sorted().toMutableList()
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CinemaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cinema, parent, false)
        return CinemaViewHolder(view)
    }

    override fun onBindViewHolder(holder: CinemaViewHolder, position: Int) {
        holder.bind(cinemaList[position])
    }

    override fun getItemCount(): Int = cinemaList.size

    fun addCinema() {
        cinemaList.add(Cinema())
        notifyItemInserted(cinemaList.size - 1)
    }

    fun getCinemas(): List<Cinema> = cinemaList
}
