package com.havish.foreflight

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class NotesActivity : AppCompatActivity() {

    private lateinit var notesManager: NotesManager
    private lateinit var rvNoteGroups: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var llFaqSection: LinearLayout
    private lateinit var tvFaqToggle: TextView
    private lateinit var cvInstructions: CardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notes)

        notesManager = NotesManager(this)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        rvNoteGroups = findViewById(R.id.rvNoteGroups)
        tvEmpty = findViewById(R.id.tvEmpty)
        llFaqSection = findViewById(R.id.llFaqSection)
        tvFaqToggle = findViewById(R.id.tvFaqToggle)
        cvInstructions = findViewById(R.id.cvInstructions)
        
        rvNoteGroups.layoutManager = LinearLayoutManager(this)

        setupFaq()
        loadTags()
    }

    private fun setupFaq() {
        var isExpanded = false
        tvFaqToggle.setOnClickListener {
            isExpanded = !isExpanded
            if (isExpanded) {
                cvInstructions.visibility = View.VISIBLE
                tvFaqToggle.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, android.R.drawable.arrow_up_float, 0)
            } else {
                cvInstructions.visibility = View.GONE
                tvFaqToggle.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, android.R.drawable.arrow_down_float, 0)
            }
        }
    }

    private fun loadTags() {
        val allNotes = notesManager.getNotes()
        
        // Hide FAQ if 2 or more notes exist
        if (allNotes.size >= 2) {
            llFaqSection.visibility = View.GONE
        } else {
            llFaqSection.visibility = View.VISIBLE
        }

        val tags = notesManager.getTags()
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
        private val expandedStates = mutableMapOf<String, Boolean>()

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val llTagHeader: LinearLayout = view.findViewById(R.id.llTagHeader)
            val tvTagName: TextView = view.findViewById(R.id.tvTagName)
            val tvTagCount: TextView = view.findViewById(R.id.tvTagCount)
            val switchVisibility: Switch = view.findViewById(R.id.switchVisibility)
            val llNotesContainer: LinearLayout = view.findViewById(R.id.llNotesContainer)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_note_group, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val tag = tags[position]
            holder.tvTagName.text = tag
            
            val notesForTag = notesManager.getNotesForTag(tag)
            holder.tvTagCount.text = "${notesForTag.size} notes"

            // Tag visibility switch
            holder.switchVisibility.setOnCheckedChangeListener(null)
            holder.switchVisibility.isChecked = prefs.getBoolean("tag_vis_$tag", true)
            holder.switchVisibility.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("tag_vis_$tag", isChecked).apply()
            }

            // Expand/collapse logic
            val isExpanded = expandedStates[tag] ?: false
            holder.llNotesContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE
            
            holder.llTagHeader.setOnClickListener {
                val currentlyExpanded = expandedStates[tag] ?: false
                expandedStates[tag] = !currentlyExpanded
                notifyItemChanged(position)
            }

            // Populate individual notes if expanded
            if (isExpanded) {
                holder.llNotesContainer.removeAllViews()
                // Re-add the divider first
                val divider = View(holder.itemView.context)
                divider.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                divider.setBackgroundColor(android.graphics.Color.parseColor("#E5E7EB"))
                holder.llNotesContainer.addView(divider)

                for (note in notesForTag) {
                    val noteView = LayoutInflater.from(holder.itemView.context).inflate(R.layout.item_note_detail, holder.llNotesContainer, false)
                    
                    val ivNoteIcon: ImageView = noteView.findViewById(R.id.ivNoteIcon)
                    val tvNoteText: TextView = noteView.findViewById(R.id.tvNoteText)
                    val tvNoteTags: TextView = noteView.findViewById(R.id.tvNoteTags)
                    val ivToggleVisibility: ImageView = noteView.findViewById(R.id.ivToggleVisibility)
                    val ivEditNote: ImageView = noteView.findViewById(R.id.ivEditNote)
                    val ivDeleteNote: ImageView = noteView.findViewById(R.id.ivDeleteNote)

                    ivNoteIcon.setImageResource(AppIcons.getIconRes(note.icon))
                    tvNoteText.text = note.text
                    tvNoteTags.text = "Tags: ${note.tags.joinToString(", ")}"

                    // Note specific visibility (override for just this note if needed, though this interacts with group vis)
                    // For now, toggle visibility means toggling a pref specific to this note ID
                    val noteVisKey = "note_vis_${note.id}"
                    var isNoteVisible = prefs.getBoolean(noteVisKey, true)
                    ivToggleVisibility.alpha = if (isNoteVisible) 1.0f else 0.3f
                    
                    ivToggleVisibility.setOnClickListener {
                        isNoteVisible = !isNoteVisible
                        prefs.edit().putBoolean(noteVisKey, isNoteVisible).apply()
                        ivToggleVisibility.alpha = if (isNoteVisible) 1.0f else 0.3f
                    }

                    // Edit
                    ivEditNote.setOnClickListener {
                        val input = EditText(this@NotesActivity)
                        input.setText(note.text)
                        AlertDialog.Builder(this@NotesActivity)
                            .setTitle("Edit Note Text")
                            .setView(input)
                            .setPositiveButton("Save") { _, _ ->
                                val newText = input.text.toString()
                                if (newText.isNotBlank()) {
                                    notesManager.updateNote(note.id, newText = newText)
                                    loadTags()
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }

                    // Delete
                    ivDeleteNote.setOnClickListener {
                        AlertDialog.Builder(this@NotesActivity)
                            .setTitle("Delete Note")
                            .setMessage("Delete this note forever?")
                            .setPositiveButton("Delete") { _, _ ->
                                notesManager.deleteNote(note.id)
                                loadTags()
                                Toast.makeText(this@NotesActivity, "Note deleted", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }

                    holder.llNotesContainer.addView(noteView)
                }
            }
        }

        override fun getItemCount() = tags.size
    }
}