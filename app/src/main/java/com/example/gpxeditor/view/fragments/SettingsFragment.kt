package com.example.gpxeditor.view.fragments

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.example.gpxeditor.R

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey) // Infla el archivo preferences.xml
    }
}