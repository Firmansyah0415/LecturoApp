package com.lecturo.lecturo.ui.settings

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.lecturo.lecturo.databinding.FragmentSettingsBinding
import java.util.*

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var sharedPreferences: SharedPreferences

    private val languageOptions = arrayOf("Bahasa Indonesia", "English")
    private val languageValues = arrayOf("id", "en")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        setupNotificationSettings()
        setupLanguageSettings()
        setupThemeSettings()
        loadCurrentSettings()
    }

    private fun setupNotificationSettings() {
        binding.cardNotificationSettings.setOnClickListener {
            val intent = Intent(requireContext(), NotificationSettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupLanguageSettings() {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, languageOptions)
        binding.autoCompleteLanguage.setAdapter(adapter)

        binding.autoCompleteLanguage.setOnItemClickListener { _, _, position, _ ->
            val selectedLanguage = languageValues[position]
            saveLanguagePreference(selectedLanguage)
            changeLanguage(selectedLanguage)
        }
    }

    private fun setupThemeSettings() {
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            saveDarkModePreference(isChecked)
            applyTheme(isChecked)
        }
    }

    private fun loadCurrentSettings() {
        // Load dark mode setting
        val isDarkMode = sharedPreferences.getBoolean("dark_mode_enabled", false)
        binding.switchDarkMode.isChecked = isDarkMode

        // Load language setting
        val currentLanguage = sharedPreferences.getString("app_language", "id") ?: "id"
        val languageIndex = languageValues.indexOf(currentLanguage)
        if (languageIndex >= 0) {
            binding.autoCompleteLanguage.setText(languageOptions[languageIndex], false)
        }
    }

    private fun saveLanguagePreference(language: String) {
        sharedPreferences.edit()
            .putString("app_language", language)
            .apply()
    }

    private fun saveDarkModePreference(isDarkMode: Boolean) {
        sharedPreferences.edit()
            .putBoolean("dark_mode_enabled", isDarkMode)
            .apply()
    }

    private fun changeLanguage(languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration()
        config.setLocale(locale)

        requireContext().resources.updateConfiguration(config, requireContext().resources.displayMetrics)

        // Restart activity to apply language changes
        requireActivity().recreate()
    }

    private fun applyTheme(isDarkMode: Boolean) {
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
