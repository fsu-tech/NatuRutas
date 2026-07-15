package com.example.gpxeditor.view.fragments

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceFragmentCompat
import com.example.gpxeditor.R

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey) // Infla el archivo preferences.xml
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val horizontalPadding = (16 * resources.displayMetrics.density).toInt()
        val verticalPadding = (12 * resources.displayMetrics.density).toInt()
        listView.apply {
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.naturutas_background))
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            clipToPadding = false
        }
        setDivider(null)
        setDividerHeight(0)
    }
}
