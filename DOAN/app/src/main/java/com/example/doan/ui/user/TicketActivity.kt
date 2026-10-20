package com.example.doan.ui.user

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.doan.R
import com.example.doan.adapter.TicketAdapter
import com.example.doan.adapter.TicketPagerAdapter
import com.example.doan.model.Ticket
import com.example.doan.utils.Constants
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.database.*

class TicketActivity : AppCompatActivity() {

    companion object {
        var userEmail: String = "user@gmail.com"
        var tabStatus: String = "1"
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ticket)

        val tabLayout: TabLayout = findViewById(R.id.tabLayout)
        val viewPager: ViewPager2 = findViewById(R.id.viewPager)
        val emailArg = intent.getStringExtra("userEmail") ?: ""
        userEmail = if (emailArg.isNotEmpty() && emailArg != "null") emailArg else "user@gmail.com"

        val adapter = TicketPagerAdapter(this)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = if (position == 0) "Vé đang đặt" else "Vé đã đặt"
        }.attach()

        val btnBack: ImageView = findViewById(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }
    }
}

class TicketListFragment : Fragment() {

    private val ticketList = mutableListOf<Ticket>()
    private lateinit var ticketAdapter: TicketAdapter
    private lateinit var database: DatabaseReference
    private var isHistory: Boolean = false

    companion object {
        fun newInstance(isHistory: Boolean) = TicketListFragment().apply {
            arguments = Bundle().apply { putBoolean("isHistory", isHistory) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isHistory = arguments?.getBoolean("isHistory") ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.ticket_list, container, false)
        val recyclerView: RecyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)

        ticketAdapter = TicketAdapter(ticketList)
        recyclerView.adapter = ticketAdapter

        loadTickets()
        return view
    }

    private fun loadTickets() {
        database = FirebaseDatabase.getInstance().getReference(Constants.NODE_TICKER)
        val email = if (TicketActivity.userEmail.isNotEmpty() && TicketActivity.userEmail != "null") {
            TicketActivity.userEmail
        } else {
            "user@gmail.com"
        }

        database.addValueEventListener(object : ValueEventListener {
                @SuppressLint("NotifyDataSetChanged")
                override fun onDataChange(snapshot: DataSnapshot) {
                    ticketList.clear()
                    if (snapshot.exists()) {
                        val allUserTickets = mutableListOf<Ticket>()
                        for (userSnapshot in snapshot.children) {
                            val tEmail = userSnapshot.child("email").getValue(String::class.java) ?: ""
                            if (tEmail.isEmpty() || tEmail.equals(email, ignoreCase = true) || email.contains("admin")) {
                                val g1 = userSnapshot.child("phim").getValue(String::class.java) ?: ""
                                val g2 = userSnapshot.child("ngaydat").getValue(String::class.java) ?: ""
                                val g3 = userSnapshot.child("ghe").getValue(String::class.java) ?: ""
                                val g4 = userSnapshot.child("lich").getValue(String::class.java) ?: ""
                                val g5 = userSnapshot.child("rap").getValue(String::class.java) ?: ""
                                val g6 = userSnapshot.child("tt").getValue(String::class.java) ?: "Đã xác nhận"
                                val gia = userSnapshot.child("gia").getValue(String::class.java) ?: "42000"
                                val pttt = userSnapshot.child("pttt").getValue(String::class.java) ?: "VietQR"
                                val key = userSnapshot.key ?: ""
                                val ticketCode = "CINE-${key.takeLast(6).uppercase()}"

                                val ticket = Ticket(
                                    movieName = g1,
                                    showTime = g2,
                                    lich = g4,
                                    ghe = g3,
                                    rap = g5,
                                    gia = gia,
                                    tt = g6,
                                    pttt = pttt,
                                    code = ticketCode
                                )
                                allUserTickets.add(ticket)

                                if (isHistory) {
                                    if (g6 == "Đã xác nhận") {
                                        ticketList.add(ticket)
                                    }
                                } else {
                                    if (g6 != "Đã xác nhận") {
                                        ticketList.add(ticket)
                                    }
                                }
                            }
                        }
                        // Nếu tab này không có vé riêng, hiển thị tất cả vé của người dùng
                        if (ticketList.isEmpty() && allUserTickets.isNotEmpty()) {
                            ticketList.addAll(allUserTickets)
                        }
                    }
                    ticketAdapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }
}
