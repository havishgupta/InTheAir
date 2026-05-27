package com.havish.foreflight

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class VoyagesActivity : AppCompatActivity() {

    private lateinit var voyageManager: VoyageManager
    private lateinit var rvVoyages: RecyclerView
    private lateinit var tvEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voyages)

        voyageManager = VoyageManager(this)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        rvVoyages = findViewById(R.id.rvVoyages)
        tvEmpty = findViewById(R.id.tvEmpty)
        rvVoyages.layoutManager = LinearLayoutManager(this)

        loadVoyages()
    }

    private fun loadVoyages() {
        val voyages = voyageManager.getSavedVoyages()
        if (voyages.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            rvVoyages.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            rvVoyages.visibility = View.VISIBLE
            rvVoyages.adapter = VoyageAdapter(voyages)
        }
    }

    inner class VoyageAdapter(private val voyages: List<VoyageData>) : RecyclerView.Adapter<VoyageAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvVoyageName: TextView = view.findViewById(R.id.tvVoyageName)
            val tvVoyageInfo: TextView = view.findViewById(R.id.tvVoyageInfo)
            val ivView: ImageView = view.findViewById(R.id.ivView)
            val ivRename: ImageView = view.findViewById(R.id.ivRename)
            val ivDelete: ImageView = view.findViewById(R.id.ivDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_route_bottom_sheet, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val voyage = voyages[position]
            holder.tvVoyageName.text = voyage.name
            holder.tvVoyageInfo.text = java.text.SimpleDateFormat("MMM dd, yyyy").format(java.util.Date(voyage.startTime))

            holder.itemView.setOnClickListener {
                val intent = Intent(this@VoyagesActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                intent.putExtra("view_voyage_id", voyage.id)
                startActivity(intent)
                finish() // optionally close this activity when viewing on map
            }
            holder.ivView.setOnClickListener {
                holder.itemView.performClick()
            }
            holder.ivRename.setOnClickListener {
                showRenameVoyageDialog(voyage)
            }
            holder.ivDelete.setOnClickListener {
                AlertDialog.Builder(this@VoyagesActivity)
                    .setTitle("Confirm Delete")
                    .setMessage("Are you sure you want to delete ${voyage.name}?")
                    .setPositiveButton("Delete") { _, _ ->
                        voyageManager.deleteVoyage(voyage.id)
                        Toast.makeText(this@VoyagesActivity, "Deleted ${voyage.name}", Toast.LENGTH_SHORT).show()
                        loadVoyages()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        override fun getItemCount() = voyages.size
    }

    private fun showRenameVoyageDialog(voyage: VoyageData) {
        val input = android.widget.EditText(this)
        input.setText(voyage.name)
        AlertDialog.Builder(this)
            .setTitle("Rename Voyage")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString()
                if (newName.isNotBlank()) {
                    voyageManager.renameVoyage(voyage.id, newName)
                    Toast.makeText(this, "Renamed to $newName", Toast.LENGTH_SHORT).show()
                    loadVoyages()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}