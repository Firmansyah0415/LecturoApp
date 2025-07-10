package com.lecturo.lecturo.ui.main

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.WindowInsets
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.lecturo.lecturo.databinding.ActivityMainBinding
import com.lecturo.lecturo.ui.ViewModelFactory
import com.lecturo.lecturo.ui.tasks.TasksActivity
import com.lecturo.lecturo.ui.welcome.WelcomeActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.getValue
import kotlin.jvm.java
import android.view.MenuItem
import com.lecturo.lecturo.R

class MainActivity : AppCompatActivity() {

    private val viewModel by viewModels<MainViewModel> {
        ViewModelFactory.getInstance(this)
    }
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Mengatur judul di App Bar menjadi "Dashboard"
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.apply {
            title = "Dashboard"
        }

        viewModel.getSession().observe(this) { user ->
            if (!user.isLogin) {
                startActivity(Intent(this, WelcomeActivity::class.java))
                finish()
            }
        }

        setupView()
//        setupAction()
//        playAnimation()

        // Set current date
        val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID"))
        val currentDate = dateFormat.format(Date())
        binding.dateTextView.text = currentDate

        // Setup navigation to different sections
//        binding.scheduleCard.setOnClickListener {
//            startActivity(Intent(this, ScheduleActivity::class.java))
//        }

        binding.tasksCard.setOnClickListener {
            startActivity(Intent(this, TasksActivity::class.java))
        }

//        binding.coursesCard.setOnClickListener {
//            startActivity(Intent(this, CoursesActivity::class.java))
//        }
//
//        binding.appointmentsCard.setOnClickListener {
//            startActivity(Intent(this, AppointmentsActivity::class.java))
//        }

        // Setup upcoming events (demo data)
        setupUpcomingEvents()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                viewModel.logout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupView() {
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.statusBars())
        } else {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }
    }

    private fun setupUpcomingEvents() {
        // In a real app, this would come from a database
        val events = listOf(
            "08:00 - Kelas Pemrograman Mobile (Ruang 301)",
            "10:30 - Rapat Departemen",
            "13:00 - Jam Konsultasi Mahasiswa",
            "15:30 - Deadline Penilaian Tugas"
        )

        binding.event1TextView.text = events[0]
        binding.event2TextView.text = events[1]
        binding.event3TextView.text = events[2]
        binding.event4TextView.text = events[3]
    }
}