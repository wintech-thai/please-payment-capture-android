package com.example.notification_agent.ui.settings

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.notification_agent.NotificationAgentApp
import com.example.notification_agent.R
import com.example.notification_agent.data.settings.AgentSettings
import com.example.notification_agent.databinding.FragmentSettingsBinding
import com.example.notification_agent.service.AgentForegroundService
import com.example.notification_agent.ui.PermissionHelper
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

    private var suppressBindingChanges = false

    private val requestPostNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result reflected on next status refresh */ }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        wireWebhook()
        wireProbe()
        wireKeepAlive()
        wireFilters()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.collect(::renderSettings)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.testEvents.collect { result ->
                    val (label, msg) = when (result) {
                        is TestResult.Success -> getString(R.string.test_ok) to result.message
                        is TestResult.Failure -> getString(R.string.test_failed) to result.message
                    }
                    Toast.makeText(requireContext(), "$label: $msg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun wireWebhook() {
        binding.webhookEnabled.setOnCheckedChangeListener { _, checked ->
            if (suppressBindingChanges) return@setOnCheckedChangeListener
            viewModel.update { it.copy(webhookEnabled = checked) }
        }
        binding.webhookUrl.addTextChangedListener(simpleWatcher { text ->
            viewModel.update { it.copy(webhookUrl = text) }
        })
        binding.testWebhook.setOnClickListener {
            val url = binding.webhookUrl.text?.toString().orEmpty()
            if (!URLUtil.isNetworkUrl(url)) {
                Toast.makeText(requireContext(), R.string.invalid_url, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.sendTestWebhook()
        }
    }

    private fun wireProbe() {
        binding.probeEnabled.setOnCheckedChangeListener { _, checked ->
            if (suppressBindingChanges) return@setOnCheckedChangeListener
            viewModel.update { it.copy(probeEnabled = checked) }
            applyKeepAliveServiceState()
        }
        binding.probeUrl.addTextChangedListener(simpleWatcher { text ->
            viewModel.update { it.copy(probeUrl = text) }
        })
        binding.probeInterval.addTextChangedListener(simpleWatcher { text ->
            val value = text.toIntOrNull() ?: return@simpleWatcher
            viewModel.update { it.copy(probeIntervalSec = value) }
        })
        binding.probeTimeout.addTextChangedListener(simpleWatcher { text ->
            val value = text.toIntOrNull() ?: return@simpleWatcher
            viewModel.update { it.copy(probeTimeoutMs = value) }
        })
        binding.testProbe.setOnClickListener {
            val url = binding.probeUrl.text?.toString().orEmpty()
            if (!URLUtil.isNetworkUrl(url)) {
                Toast.makeText(requireContext(), R.string.invalid_url, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.sendTestProbe()
        }
    }

    private fun wireKeepAlive() {
        binding.keepAlive.setOnCheckedChangeListener { _, checked ->
            if (suppressBindingChanges) return@setOnCheckedChangeListener
            viewModel.update { it.copy(keepAliveEnabled = checked) }
            if (checked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (!PermissionHelper.isIgnoringBatteryOptimizations(requireContext())) {
                    PermissionHelper.requestIgnoreBatteryOptimizations(requireContext())
                }
            }
            applyKeepAliveServiceState()
        }
        binding.openBatterySettings.setOnClickListener {
            PermissionHelper.requestIgnoreBatteryOptimizations(requireContext())
        }
        binding.openOemAutostart.setOnClickListener {
            val opened = PermissionHelper.openOemAutostart(requireContext())
            if (!opened) {
                Toast.makeText(requireContext(), R.string.no_oem_autostart, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun wireFilters() {
        binding.openNotificationFilters.setOnClickListener {
            findNavController().navigate(R.id.notificationFiltersFragment)
        }
        binding.openSmsFilters.setOnClickListener {
            findNavController().navigate(R.id.smsFiltersFragment)
        }
    }

    private fun renderSettings(s: AgentSettings) {
        suppressBindingChanges = true
        try {
            if (binding.webhookEnabled.isChecked != s.webhookEnabled)
                binding.webhookEnabled.isChecked = s.webhookEnabled
            setIfChanged(binding.webhookUrl, s.webhookUrl)

            if (binding.probeEnabled.isChecked != s.probeEnabled)
                binding.probeEnabled.isChecked = s.probeEnabled
            setIfChanged(binding.probeUrl, s.probeUrl)
            setIfChanged(binding.probeInterval, s.probeIntervalSec.toString())
            setIfChanged(binding.probeTimeout, s.probeTimeoutMs.toString())

            if (binding.keepAlive.isChecked != s.keepAliveEnabled)
                binding.keepAlive.isChecked = s.keepAliveEnabled
        } finally {
            suppressBindingChanges = false
        }
    }

    private fun setIfChanged(editText: com.google.android.material.textfield.TextInputEditText, value: String) {
        if (editText.text?.toString() != value) editText.setText(value)
    }

    private fun applyKeepAliveServiceState() {
        val s = viewModel.settings.value
        val ctx = requireContext().applicationContext
        if (s.keepAliveEnabled) {
            AgentForegroundService.start(ctx)
            if (s.probeEnabled) {
                AgentForegroundService.scheduleNextProbe(ctx, s.probeIntervalSec)
            } else {
                AgentForegroundService.cancelProbe(ctx)
            }
        } else {
            AgentForegroundService.cancelProbe(ctx)
            AgentForegroundService.stop(ctx)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun simpleWatcher(onChange: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
            if (suppressBindingChanges) return
            onChange(s?.toString().orEmpty())
        }
        override fun afterTextChanged(s: Editable?) {}
    }
}

