package com.havish.foreflight

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class GlobalNotesActivity : AppCompatActivity() {

    private lateinit var globalNotesManager: GlobalNotesManager
    private lateinit var rvNoteGroups: RecyclerView
    private lateinit var tvEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_global_notes)

        globalNotesManager = GlobalNotesManager(this)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        rvNoteGroups = findViewById(R.id.rvNoteGroups)
        tvEmpty = findViewById(R.id.tvEmpty)
        rvNoteGroups.layoutManager = LinearLayoutManager(this)

        loadTags()
    }

    private fun loadTags() {
        val tags = globalNotesManager.getTags()
        if (tags.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            rvNoteGroups.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            rvNoteGroups.visibility = View.VISIBLE
            rvNoteGroups.adapter = NotesAdapter(tags)
        }
    }

    inner class NotesAdapter(private val tags: List<String>) : RecyclerView.Adapter<NotesAdapter.ViewHolder>() {

        private val prefs = getSharedPreferences("foreflight_prefs", Context.MODE_PRIVATE)

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTagName: TextView = view.findViewById(R.id.tvTagName)
            val tvTagCount: TextView = view.findViewById(R.id.tvTagCount)
            val switchVisibility: Switch = view.findViewById(R.id.switchVisibility)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_note_group, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val tag = tags[position]
            holder.tvTagName.text = tag
            
            val count = globalNotesManager.getNotes().count { it.tag == tag }
            holder.tvTagCount.text = "$count notes"

            holder.switchVisibility.setOnCheckedChangeListener(null)
            holder.switchVisibility.isChecked = prefs.getBoolean("tag_vis_$tag", true)
            holder.switchVisibility.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("tag_vis_$tag", isChecked).apply()
            }
        }

        override fun getItemCount() = tags.size
    }
}