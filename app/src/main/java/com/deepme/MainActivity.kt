package com.deepme

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.deepme.ui.chat.ChatFragment
import com.deepme.ui.files.FilesFragment
import com.deepme.ui.github.GitHubFragment
import com.deepme.ui.settings.SettingsFragment
import com.deepme.ui.termux.TermuxFragment
import com.deepme.utils.Logger
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.init()
        Logger.log("MainActivity onCreate")
        setContentView(R.layout.activity_main)

        bottomNav = findViewById(R.id.bottom_navigation)
        if (savedInstanceState == null) {
            loadFragment(ChatFragment())
        }
        Logger.log("UI initialized")

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_chat -> { Logger.log("Nav: Chat"); loadFragment(ChatFragment()) }
                R.id.nav_files -> { Logger.log("Nav: Files"); loadFragment(FilesFragment()) }
                R.id.nav_termux -> { Logger.log("Nav: Termux"); loadFragment(TermuxFragment()) }
                R.id.nav_github -> { Logger.log("Nav: GitHub"); loadFragment(GitHubFragment()) }
                R.id.nav_settings -> { Logger.log("Nav: Settings"); loadFragment(SettingsFragment()) }
            }
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        Logger.log("Loading fragment: ${fragment.javaClass.simpleName}")
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}