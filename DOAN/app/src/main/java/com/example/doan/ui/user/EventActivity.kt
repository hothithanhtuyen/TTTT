package com.example.doan.ui.user

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.doan.R
import com.example.doan.adapter.EventAdapter
import com.example.doan.model.Event
import com.example.doan.utils.Constants
import com.google.firebase.database.*

class EventActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var eventAdapter: EventAdapter
    private val eventList = mutableListOf<Event>()
    private lateinit var database: DatabaseReference

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event)

        val btnBack: ImageView = findViewById(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        recyclerView = findViewById(R.id.recyclerViewEvent)
        recyclerView.layoutManager = LinearLayoutManager(this)

        eventAdapter = EventAdapter(eventList) { event ->
            Log.d("EventActivity", "Selected Event: Title: ${event.title}, Days: ${event.date}, Pot: ${event.location}")

            val intent = Intent(this, ShowInfoActivity::class.java).apply {
                putExtra("eventTitle", event.title)
                putExtra("eventDays", event.date)
                putExtra("eventImageUrl", event.imageUrl)
                putExtra("eventPot", event.location)
            }
            startActivity(intent)
        }
        recyclerView.adapter = eventAdapter

        database = FirebaseDatabase.getInstance().reference.child(Constants.NODE_EVENT)
        fetchEventsFromFirebase()
    }

    private fun fetchEventsFromFirebase() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                eventList.clear()
                for (eventSnapshot in snapshot.children) {
                    val title = eventSnapshot.child("title").getValue(String::class.java) ?: ""
                    val days = eventSnapshot.child("days").getValue(String::class.java) ?: ""
                    val imageUrl = eventSnapshot.child("image").getValue(String::class.java) ?: ""
                    val pot = eventSnapshot.child("pot").getValue(String::class.java) ?: ""

                    val event = Event(title, days, imageUrl, pot)
                    eventList.add(event)
                }

                eventList.reverse()
                eventAdapter.updateList(eventList)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@EventActivity, "Lỗi tải dữ liệu!", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
