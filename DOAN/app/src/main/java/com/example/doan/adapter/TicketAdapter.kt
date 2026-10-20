package com.example.doan.adapter

import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.doan.R
import com.example.doan.model.Ticket
import com.example.doan.ui.user.TicketDetailActivity
import com.example.doan.ui.user.TicketListFragment
import com.example.doan.utils.MovieImageUtils

class TicketPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment = TicketListFragment.newInstance(position == 1)
}

class TicketAdapter(private val tickets: List<Ticket>) : RecyclerView.Adapter<TicketViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TicketViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.ticket_item, parent, false)
        return TicketViewHolder(view)
    }

    override fun onBindViewHolder(holder: TicketViewHolder, position: Int) {
        val ticket = tickets[position]
        holder.bind(ticket)
    }

    override fun getItemCount(): Int = tickets.size
}

class TicketViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val ticketTitle: TextView = itemView.findViewById(R.id.ticketTitle)
    private val ticketDate: TextView = itemView.findViewById(R.id.ticketDate)
    private val ticketlich: TextView = itemView.findViewById(R.id.ticketlich)
    private val ticketghe: TextView = itemView.findViewById(R.id.ticketghe)
    private val ticketrap: TextView = itemView.findViewById(R.id.ticketrap)
    private val ticketStatus: TextView = itemView.findViewById(R.id.ticketStatus)
    private val ticketPoster: ImageView = itemView.findViewById(R.id.ticketPoster)
    private val btnViewQR: TextView = itemView.findViewById(R.id.btnViewQR)

    fun bind(ticket: Ticket) {
        ticketTitle.text = ticket.movieName
        ticketDate.text = ticket.showTime
        ticketlich.text = ticket.lich
        ticketghe.text = "Ghế: ${ticket.ghe}"
        ticketrap.text = "Rạp: ${ticket.rap}"

        if (ticket.tt == "Đã xác nhận") {
            ticketStatus.text = "Đã xác nhận"
            ticketStatus.setBackgroundColor(Color.parseColor("#1B5E20"))
            ticketStatus.setTextColor(Color.parseColor("#A5D6A7"))
        } else {
            ticketStatus.text = "Chờ xác nhận"
            ticketStatus.setBackgroundColor(Color.parseColor("#E65100"))
            ticketStatus.setTextColor(Color.parseColor("#FFE0B2"))
        }

        MovieImageUtils.loadMoviePoster(ticketPoster, ticket.posterUrl, ticket.movieName)

        val openDetail = {
            val context = itemView.context
            val intent = Intent(context, TicketDetailActivity::class.java).apply {
                val code = if (ticket.code.isNotEmpty()) ticket.code else "CINE-DN-%05d".format((1000..99999).random())
                putExtra("TICKET_CODE", code)
                putExtra("FILM_TITLE", ticket.movieName)
                putExtra("cinema", ticket.rap)
                putExtra("LICH", ticket.lich)
                putExtra("Ghe", ticket.ghe)
                putExtra("ngaydat", ticket.showTime)
                putExtra("totalPrice", ticket.gia.toIntOrNull() ?: 42000)
                putExtra("pttt", if (ticket.pttt.isNotEmpty()) ticket.pttt else "VietQR")
                putExtra("tt", if (ticket.tt.isNotEmpty()) ticket.tt else "Đã xác nhận")
                putExtra("FILM_POSTER_URL", ticket.posterUrl)
            }
            context.startActivity(intent)
        }

        itemView.setOnClickListener { openDetail() }
        btnViewQR.setOnClickListener { openDetail() }
    }
}
