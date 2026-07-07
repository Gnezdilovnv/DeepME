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

    private lateinit var chatFragment: ChatFragment
    private lateinit var filesFragment: FilesFragment
    private lateinit var termuxFragment: TermuxFragment
    private lateinit var gitHubFragment: GitHubFragment
    private lateinit var settingsFragment: SettingsFragment
    private var activeFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.init()
        Logger.log("Запуск приложения")
        setContentView(R.layout.activity_main)

        chatFragment = ChatFragment()
        filesFragment = FilesFragment()
        termuxFragment = TermuxFragment()
        gitHubFragment = GitHubFragment()
        settingsFragment = SettingsFragment()

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, settingsFragment, "settings").hide(settingsFragment)
                .add(R.id.fragment_container, filesFragment, "files").hide(filesFragment)
                .add(R.id.fragment_container, termuxFragment, "termux").hide(termuxFragment)
                .add(R.id.fragment_container, gitHubFragment, "github").hide(gitHubFragment)
                .add(R.id.fragment_container, chatFragment, "chat")
                .commit()
            activeFragment = chatFragment
        } else {
            chatFragment = supportFragmentManager.findFragmentByTag("chat") as ChatFragment
            filesFragment = supportFragmentManager.findFragmentByTag("files") as FilesFragment
            termuxFragment = supportFragmentManager.findFragmentByTag("termux") as TermuxFragment
            gitHubFragment = supportFragmentManager.findFragmentByTag("github") as GitHubFragment
            settingsFragment = supportFragmentManager.findFragmentByTag("settings") as SettingsFragment
        }

        bottomNav.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_chat -> chatFragment
                R.id.nav_files -> filesFragment
                R.id.nav_termux -> termuxFragment
                R.id.nav_github -> gitHubFragment
                R.id.nav_settings -> settingsFragment
                else -> chatFragment
            }
            if (fragment != activeFragment) {
                supportFragmentManager.beginTransaction()
                    .hide(activeFragment!!)
                    .show(fragment)
                    .commit()
                activeFragment = fragment
            }
            true
        }
    }

    override fun onBackPressed() {
        if (activeFragment != chatFragment) {
            supportFragmentManager.beginTransaction()
                .hide(activeFragment!!)
                .show(chatFragment)
                .commit()
            activeFragment = chatFragment
            findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId = R.id.nav_chat
        } else {
            super.onBackPressed()
        }
    }
}