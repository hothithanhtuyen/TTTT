package com.example.doan.ui.admin

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.doan.R
import com.example.doan.adapter.EventAdminAdapter
import com.example.doan.model.Event
import com.example.doan.model.EventAdmin
import com.example.doan.utils.Constants
import com.google.firebase.database.*

class EventManagementActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var eventAdapter: EventAdminAdapter
    private val eventList = mutableListOf<Event>()
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.event_management_activity)

        recyclerView = findViewById(R.id.recyclerViewEvents)
        recyclerView.layoutManager = LinearLayoutManager(this)

        database = FirebaseDatabase.getInstance().reference.child(Constants.NODE_EVENT)
        loadEventData()

        eventAdapter = EventAdminAdapter(eventList, ::editEvent, ::deleteEvent)
        recyclerView.adapter = eventAdapter

        findViewById<Button>(R.id.btnUpdateEvent).setOnClickListener {
            addEvent()
        }
    }

    private fun addEvent() {
        val title = findViewById<EditText>(R.id.edtEventTitle).text.toString()
        val date = findViewById<EditText>(R.id.edtEventDate).text.toString()
        val location = findViewById<EditText>(R.id.edtEventLocation).text.toString()
        val imageUrl = findViewById<EditText>(R.id.edtEventImage).text.toString()

        if (title.isNotEmpty() && date.isNotEmpty() && location.isNotEmpty()) {
            saveEventToDatabase(title, date, location, imageUrl)
            Toast.makeText(this, "Đã thêm sự kiện!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun editEvent(event: Event) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_event, null)
        val edtTitle = dialogView.findViewById<EditText>(R.id.txtEventTitle)
        val edtDate = dialogView.findViewById<EditText>(R.id.txtEventDate)
        val edtLocation = dialogView.findViewById<EditText>(R.id.txtEventLocation)
        val edtImage = dialogView.findViewById<EditText>(R.id.txtEventImage)

        edtTitle.setText(event.title)
        edtDate.setText(event.date)
        edtLocation.setText(event.location)
        edtImage.setText(event.imageUrl)

        AlertDialog.Builder(this)
            .setTitle("Chỉnh sửa sự kiện")
            .setView(dialogView)
            .setPositiveButton("Cập nhật") { _, _ ->
                val updatedEvent = EventAdmin(
                    title = edtTitle.text.toString(),
                    days = edtDate.text.toString(),
                    pot = edtLocation.text.toString(),
                    image = edtImage.text.toString()
                )
                updateEventInDatabase(event.title, updatedEvent)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun updateEventInDatabase(oldTitle: String, updatedEvent: EventAdmin) {
        val eventRef = database.orderByChild("title").equalTo(oldTitle)
        eventRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (eventSnapshot in snapshot.children) {
                    eventSnapshot.ref.setValue(updatedEvent)
                }
                Toast.makeText(this@EventManagementActivity, "Sự kiện đã được cập nhật!", Toast.LENGTH_SHORT).show()
                loadEventData()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@EventManagementActivity, "Lỗi khi cập nhật sự kiện!", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun deleteEvent(event: Event) {
        val eventRef = database.orderByChild("title").equalTo(event.title)
        eventRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (eventSnapshot in snapshot.children) {
                    eventSnapshot.ref.removeValue()
                }
                eventList.remove(event)
                eventAdapter.updateList(eventList)
                Toast.makeText(this@EventManagementActivity, "Đã xóa sự kiện!", Toast.LENGTH_SHORT).show()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@EventManagementActivity, "Lỗi khi xóa!", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun saveEventToDatabase(title: String, days: String, pot: String, image: String) {
        val eventId = database.push().key
        val event = EventAdmin(title, days, pot, image)

        if (eventId != null) {
            database.child(eventId).setValue(event)
                .addOnSuccessListener {
                    Toast.makeText(this, "Đăng ký thành công!", Toast.LENGTH_SHORT).show()
                    findViewById<EditText>(R.id.edtEventTitle).setText("")
                    findViewById<EditText>(R.id.edtEventDate).setText("")
                    findViewById<EditText>(R.id.edtEventLocation).setText("")
                    findViewById<EditText>(R.id.edtEventImage).setText("")
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Đăng ký thất bại!", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun loadEventData() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                eventList.clear()
                for (userSnapshot in snapshot.children) {
                    val title = userSnapshot.child("title").getValue(String::class.java) ?: ""
                    val days = userSnapshot.child("days").getValue(String::class.java) ?: ""
                    val pot = userSnapshot.child("pot").getValue(String::class.java) ?: ""
                    val imagee = userSnapshot.child("image").getValue(String::class.java) ?: ""
                    val events = Event(title, days, imagee, pot)
                    eventList.add(events)
                }
                eventList.reverse()
                eventAdapter.updateList(eventList)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@EventManagementActivity, "Lỗi tải dữ liệu!", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
