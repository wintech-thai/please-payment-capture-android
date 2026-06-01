package com.example.notification_agent.ui.status

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.notification_agent.R
import com.example.notification_agent.databinding.FragmentStatusBinding
import com.example.notification_agent.service.AgentForegroundService
import com.example.notification_agent.ui.PermissionHelper
import kotlinx.coroutines.launch

class StatusFragment : Fragment() {

    private var _binding: FragmentStatusBinding? = null
    private val binding get() = _binding!!
    private val viewModel: StatusViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatusBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.startService.setOnClickListener {
            AgentForegroundService.start(requireContext().applicationContext)
        }
        binding.stopService.setOnClickListener {
            AgentForegroundService.cancelProbe(requireContext().applicationContext)
            AgentForegroundService.stop(requireContext().applicationContext)
        }
        binding.grantListener.setOnClickListener {
            PermissionHelper.openNotificationListenerSettings(requireContext())
        }
        binding.grantSms.setOnClickListener {
            requireActivity().requestPermissions(
                arrayOf(android.Manifest.permission.RECEIVE_SMS, android.Manifest.permission.READ_SMS),
                100
            )
        }
        binding.grantBattery.setOnClickListener {
            PermissionHelper.requestIgnoreBatteryOptimizations(requireContext())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(s: StatusUiState) {
        // Service
        binding.serviceState.text = getString(
            if (s.agent.serviceRunning) R.string.status_service_running
            else R.string.status_service_stopped
        )
        binding.serviceUptime.text = if (s.agent.serviceRunning && s.agent.serviceStartedAt > 0) {
            getString(R.string.status_uptime, formatDuration(s.now - s.agent.serviceStartedAt))
        } else ""

        // Permissions
        binding.permListener.text = formatPerm(R.string.status_perm_listener, s.permissions.notificationListener)
        binding.permSms.text = formatPerm(R.string.status_perm_sms, s.permissions.sms)
        binding.permPost.text = formatPerm(R.string.status_perm_post, s.permissions.postNotifications)
        binding.permBattery.text = formatPerm(R.string.status_perm_battery, s.permissions.batteryUnrestricted)

        // Webhook
        binding.webhookEnabled.text = formatPerm(R.string.status_enabled, s.settings.webhookEnabled)
        binding.webhookCounters.text = getString(
            R.string.status_webhook_counters,
            s.agent.webhookSentCount, s.agent.webhookFailedCount, s.agent.webhookQueueDepth
        )
        binding.webhookLast.text = if (s.agent.lastWebhookAt == 0L) {
            getString(R.string.status_no_activity)
        } else {
            val ok = if (s.agent.lastWebhookOk == true) getString(R.string.test_ok) else getString(R.string.test_failed)
            getString(R.string.status_last_webhook, ok, formatRelative(s.agent.lastWebhookAt, s.now),
                s.agent.lastWebhookError ?: "")
        }

        // Probe
        binding.probeEnabled.text = formatPerm(R.string.status_enabled, s.settings.probeEnabled)
        binding.probeLast.text = if (s.agent.lastProbeAt == 0L) {
            getString(R.string.status_no_activity)
        } else {
            val ok = if (s.agent.lastProbeOk == true) getString(R.string.test_ok) else getString(R.string.test_failed)
            getString(R.string.status_last_probe, ok, formatRelative(s.agent.lastProbeAt, s.now),
                s.agent.lastProbeLatencyMs)
        }
        binding.probeNext.text = if (s.agent.nextProbeAt > s.now) {
            getString(R.string.status_next_probe, ((s.agent.nextProbeAt - s.now) / 1000L).coerceAtLeast(0L))
        } else if (s.settings.probeEnabled && s.settings.keepAliveEnabled) {
            getString(R.string.status_next_probe_pending)
        } else {
            ""
        }
    }

    private fun formatPerm(labelRes: Int, granted: Boolean): String {
        val mark = if (granted) "✓" else "✗"
        return "$mark  ${getString(labelRes)}"
    }

    private fun formatRelative(ts: Long, now: Long): CharSequence =
        DateUtils.getRelativeTimeSpanString(ts, now, DateUtils.SECOND_IN_MILLIS)

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%dh %02dm %02ds".format(h, m, s) else "%dm %02ds".format(m, s)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

