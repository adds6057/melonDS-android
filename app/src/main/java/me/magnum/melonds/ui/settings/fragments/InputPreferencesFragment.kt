package me.magnum.melonds.ui.settings.fragments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreference
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import kotlinx.coroutines.launch
import me.magnum.melonds.common.vibration.TouchVibrator
import me.magnum.melonds.domain.repositories.LayoutsRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.ui.inputsetup.InputSetupActivity
import me.magnum.melonds.ui.layouts.LayoutListActivity
import me.magnum.melonds.ui.layouts.LayoutSelectorActivity
import me.magnum.melonds.ui.settings.PreferenceFragmentTitleProvider
import me.magnum.melonds.ui.settings.preferences.SoftwareInputBehaviourPreference
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class InputPreferencesFragment : BasePreferenceFragment(), PreferenceFragmentTitleProvider {

    @Inject lateinit var vibrator: TouchVibrator
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var layoutsRepository: LayoutsRepository

    private lateinit var softInputBehaviourPreference: SoftwareInputBehaviourPreference
    private lateinit var layoutPresetPreferences: Map<Int, Preference>
    private var presetBeingSelected = 1

    private val layoutSelectorLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // A null ID means that the "global layout" placeholder was selected, which is treated as the preset not being set
            val layoutId = result.data?.getStringExtra(LayoutSelectorActivity.KEY_SELECTED_LAYOUT_ID)?.let { UUID.fromString(it) }
            settingsRepository.setLayoutPresetId(presetBeingSelected, layoutId)
            updateLayoutPresetSummaries()
        }
    }

    override fun getTitle() = getString(R.string.input)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_input, rootKey)
        softInputBehaviourPreference = findPreference("soft_input_behaviour")!!
        val touchVibratePreference = findPreference<SwitchPreference>("input_touch_haptic_feedback_enabled")!!
        val vibrationStrengthPreference = findPreference<SeekBarPreference>("input_touch_haptic_feedback_strength")!!
        val keyMappingPreference = findPreference<Preference>("input_key_mapping")!!
        val layoutsPreference = findPreference<Preference>("input_layouts")!!

        if (!vibrator.supportsVibration()) {
            touchVibratePreference.isVisible = false
        }
        vibrationStrengthPreference.isVisible = false

        vibrationStrengthPreference.setOnPreferenceChangeListener { _, newValue ->
            val strength = newValue as Int
            vibrator.performTouchHapticFeedback(strength)
            true
        }
        keyMappingPreference.setOnPreferenceClickListener {
            val intent = Intent(requireContext(), InputSetupActivity::class.java)
            startActivity(intent)
            true
        }
        layoutsPreference.setOnPreferenceClickListener {
            val intent = Intent(requireContext(), LayoutListActivity::class.java)
            startActivity(intent)
            true
        }

        layoutPresetPreferences = mapOf(
            1 to findPreference<Preference>("input_layout_preset_1")!!,
            2 to findPreference<Preference>("input_layout_preset_2")!!,
        )
        layoutPresetPreferences.forEach { (preset, preference) ->
            preference.setOnPreferenceClickListener {
                presetBeingSelected = preset
                val intent = Intent(requireContext(), LayoutSelectorActivity::class.java).apply {
                    putExtra(LayoutSelectorActivity.KEY_SELECTED_LAYOUT_ID, settingsRepository.getLayoutPresetId(preset)?.toString())
                }
                layoutSelectorLauncher.launch(intent)
                true
            }
        }
        updateLayoutPresetSummaries()
    }

    private fun updateLayoutPresetSummaries() {
        layoutPresetPreferences.forEach { (preset, preference) ->
            val layoutId = settingsRepository.getLayoutPresetId(preset)
            if (layoutId == null) {
                preference.summary = getString(R.string.layout_preset_not_set)
            } else {
                lifecycleScope.launch {
                    val layout = layoutsRepository.getLayout(layoutId)
                    preference.summary = if (layout == null) {
                        getString(R.string.layout_preset_not_set)
                    } else {
                        layout.name ?: getString(R.string.default_layout_name)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Set proper value for soft input behaviour preference since the value is not updated when returning from the fragment
        softInputBehaviourPreference.value = softInputBehaviourPreference.sharedPreferences?.getString(softInputBehaviourPreference.key, "hide_system_buttons_when_controller_connected")
    }
}