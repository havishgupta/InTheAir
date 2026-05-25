package com.havish.foreflight

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class IntroActivity : AppCompatActivity() {

    private var currentStep = 0

    private val titles = arrayOf(
        "Welcome to InTheAir",
        "Real-Time Telemetry",
        "Offline Maps"
    )

    private val descriptions = arrayOf(
        "Your personal map for flying! Track your real-time position right from your window seat.",
        "See your exact Speed, Altitude, Heading, and Climb Angle while in the air.",
        "No internet at 30,000 feet? Download offline vector maps (.map files) from OpenAndroMaps and load them directly."
    )

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
            currentStep++
            if (currentStep < 3) {
                updateUI()
            } else {
                finishIntro()
            }
        }
        
        startAnimations()
    }

    private fun startAnimations() {
        val plane = findViewById<ImageView>(R.id.ivIntroIcon)
        val cloud1 = findViewById<ImageView>(R.id.cloud1)
        val cloud2 = findViewById<ImageView>(R.id.cloud2)

        // Plane bobbing animation
        val planeAnim = ObjectAnimator.ofFloat(plane, "translationY", 0f, -20f, 0f)
        planeAnim.duration = 2000
        planeAnim.repeatCount = ValueAnimator.INFINITE
        planeAnim.repeatMode = ValueAnimator.REVERSE
        planeAnim.start()

        // Cloud moving animation (simulate flying)
        if (cloud1 != null && cloud2 != null) {
            val screenWidth = resources.displayMetrics.widthPixels.toFloat()
            
            val cloud1Anim = ObjectAnimator.ofFloat(cloud1, "translationX", screenWidth, -screenWidth)
            cloud1Anim.duration = 15000
            cloud1Anim.repeatCount = ValueAnimator.INFINITE
            cloud1Anim.interpolator = LinearInterpolator()
            cloud1Anim.start()

            val cloud2Anim = ObjectAnimator.ofFloat(cloud2, "translationX", screenWidth, -screenWidth)
            cloud2Anim.duration = 22000
            cloud2Anim.repeatCount = ValueAnimator.INFINITE
            cloud2Anim.interpolator = LinearInterpolator()
            // Start at a different position
            cloud2Anim.setCurrentFraction(0.5f)
            cloud2Anim.start()
        }
    }

    private fun updateUI() {
        val tvTitle = findViewById<TextView>(R.id.tvIntroTitle)
        val tvDesc = findViewById<TextView>(R.id.tvIntroDesc)
        val btnNext = findViewById<Button>(R.id.btnNext)

        val dot1 = findViewById<ImageView>(R.id.dot1)
        val dot2 = findViewById<ImageView>(R.id.dot2)
        val dot3 = findViewById<ImageView>(R.id.dot3)

        tvTitle.text = titles[currentStep]
        tvDesc.text = descriptions[currentStep]

        // Reset dots
        dot1.setImageResource(R.drawable.ic_dot_inactive)
        dot1.setColorFilter(getColor(R.color.cloud_white))
        dot2.setImageResource(R.drawable.ic_dot_inactive)
        dot2.setColorFilter(getColor(R.color.cloud_white))
        dot3.setImageResource(R.drawable.ic_dot_inactive)
        dot3.setColorFilter(getColor(R.color.cloud_white))

        when (currentStep) {
            0 -> {
                dot1.setImageResource(R.drawable.ic_dot_active)
                dot1.setColorFilter(getColor(R.color.accent_yellow))
            }
            1 -> {
                dot2.setImageResource(R.drawable.ic_dot_active)
                dot2.setColorFilter(getColor(R.color.accent_yellow))
            }
            2 -> {
                dot3.setImageResource(R.drawable.ic_dot_active)
                dot3.setColorFilter(getColor(R.color.accent_yellow))
                btnNext.text = "Get Started"
            }
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
