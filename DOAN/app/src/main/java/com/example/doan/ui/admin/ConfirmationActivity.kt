package com.example.doan.ui.admin

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.doan.R
import com.example.doan.model.ConfirmTicketItem
import com.example.doan.utils.Constants
import com.google.firebase.database.*

class ConfirmationActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ItemAdapter
    private lateinit var database: DatabaseReference
    private val itemList = mutableListOf<ConfirmTicketItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_xac_nhan)

        recyclerView = findViewById(R.id.recyclerViewConfirmList)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = ItemAdapter(itemList) { item ->
            confirmItem(item)
        }
        recyclerView.adapter = adapter

        database = FirebaseDatabase.getInstance().getReference(Constants.NODE_TICKER)
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            @SuppressLint("NotifyDataSetChanged")
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    itemList.clear()
                    for (userSnapshot in snapshot.children) {
                        val id = userSnapshot.key ?: "Không có ID"
                        val phim = userSnapshot.child("phim").getValue(String::class.java) ?: "Không có"
                        val ngaydat = userSnapshot.child("ngaydat").getValue(String::class.java) ?: "Không có"
                        val ghe = userSnapshot.child("ghe").getValue(String::class.java) ?: "Không có"
                        val lich = userSnapshot.child("lich").getValue(String::class.java) ?: "Không có"
                        val rap = userSnapshot.child("rap").getValue(String::class.java) ?: "Không có"
                        val tt = userSnapshot.child("tt").getValue(String::class.java) ?: "Không có"

                        if (tt == "Chờ xác nhận") {
                            itemList.add(ConfirmTicketItem(id, "$phim\n$ngaydat\n$ghe\n$lich\n$rap"))
                        }
                    }
                    Log.d("ConfirmationActivity", "Added ${itemList.size} items")
                    adapter.notifyDataSetChanged()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@ConfirmationActivity, "Lỗi: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun confirmItem(item: ConfirmTicketItem) {
        database.child(item.id).child("tt").setValue("Đã xác nhận").addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(this, "Đã xác nhận: ${item.name}", Toast.LENGTH_SHORT).show()
                itemList.remove(item)
                adapter.notifyDataSetChanged()
            } else {
                Toast.makeText(this, "Lỗi khi xác nhận: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    class ItemAdapter(
        private val itemList: MutableList<ConfirmTicketItem>,
        private val onConfirmClick: (ConfirmTicketItem) -> Unit
    ) : RecyclerView.Adapter<ItemAdapter.ItemViewHolder>() {

        class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val textItemName: TextView = itemView.findViewById(R.id.textItemName)
            val btnXacNhan: Button = itemView.findViewById(R.id.btnXacNhan)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_xac_nhan, parent, false)
            return ItemViewHolder(view)
        }

        override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
            val item = itemList[position]
            holder.textItemName.text = item.name
            holder.btnXacNhan.setOnClickListener {
                onConfirmClick(item)
            }
        }

        override fun getItemCount(): Int = itemList.size
    }
}
