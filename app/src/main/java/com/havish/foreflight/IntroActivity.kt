package com.havish.foreflight

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class IntroActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("foreflight_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("intro_shown", false)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        
        // Hide Action Bar if present
        supportActionBar?.hide()
        setContentView(R.layout.activity_intro)

        val btnNext = findViewById<Button>(R.id.btnNext)
        
        btnNext.setOnClickListener {
            finishIntro()
        }
    }

    private fun finishIntro() {
        val prefs = getSharedPreferences("foreflight_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("intro_shown", true).apply()
        
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
