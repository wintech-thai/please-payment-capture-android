package com.example.notification_agent.ui.status

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.notification_agent.BuildConfig
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

        binding.webhookCard.setOnClickListener {
            showMonitoringPopup(
                title = getString(R.string.status_webhook),
                sent = viewModel.state.value.agent.webhookSentCount,
                failed = viewModel.state.value.agent.webhookFailedCount,
                lastAt = viewModel.state.value.agent.lastWebhookAt,
                lastOk = viewModel.state.value.agent.lastWebhookOk,
                lastError = viewModel.state.value.agent.lastWebhookError,
                now = viewModel.state.value.now
            )
        }
        binding.bankForwardCard.setOnClickListener {
            showMonitoringPopup(
                title = getString(R.string.status_bank_forward),
                sent = viewModel.state.value.agent.bankForwardSentCount,
                failed = viewModel.state.value.agent.bankForwardFailedCount,
                lastAt = viewModel.state.value.agent.lastBankForwardAt,
                lastOk = viewModel.state.value.agent.lastBankForwardOk,
                lastError = viewModel.state.value.agent.lastBankForwardError,
                bankName = viewModel.state.value.agent.lastBankForwardBankName,
                now = viewModel.state.value.now
            )
        }
        binding.probeCard.setOnClickListener {
            showMonitoringPopup(
                title = getString(R.string.status_probe),
                sent = -1, // Not tracked as a count in AgentStatus but we can show last result
                failed = -1,
                lastAt = viewModel.state.value.agent.lastProbeAt,
                lastOk = viewModel.state.value.agent.lastProbeOk,
                lastError = null,
                latency = viewModel.state.value.agent.lastProbeLatencyMs,
                now = viewModel.state.value.now
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(s: StatusUiState) {
        val versionName = BuildConfig.VERSION_NAME
        val commitSha = versionName.substringAfterLast('-', versionName)

        binding.appVersion.text = getString(R.string.status_app_version, versionName)
        binding.appCommit.text = getString(
            R.string.status_app_commit,
            BuildConfig.VERSION_CODE.toString(),
            commitSha
        )

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

        // Bank forwarding
        binding.bankForwardCard.isVisible = s.bankEndpoints.configuredCount > 0
        if (binding.bankForwardCard.isVisible) {
            binding.bankForwardEnabled.text = getString(
                R.string.status_bank_endpoint_counts,
                s.bankEndpoints.configuredCount,
                s.bankEndpoints.enabledCount
            )
            binding.bankForwardCounters.text = getString(
                R.string.status_bank_counters,
                s.agent.bankForwardSentCount,
                s.agent.bankForwardFailedCount
            )
            binding.bankForwardLast.text = if (s.agent.lastBankForwardAt == 0L) {
                getString(R.string.status_no_activity)
            } else {
                val ok = if (s.agent.lastBankForwardOk == true) {
                    getString(R.string.test_ok)
                } else {
                    getString(R.string.test_failed)
                }
                getString(
                    R.string.status_last_bank_forward,
                    s.agent.lastBankForwardBankName ?: "-",
                    ok,
                    formatRelative(s.agent.lastBankForwardAt, s.now),
                    s.agent.lastBankForwardError ?: ""
                )
            }
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

    private fun showMonitoringPopup(
        title: String,
        sent: Long,
        failed: Long,
        lastAt: Long,
        lastOk: Boolean?,
        lastError: String?,
        bankName: String? = null,
        latency: Long? = null,
        now: Long
    ) {
        val sb = StringBuilder()
        if (sent >= 0) sb.append("Total Sent: $sent\n")
        if (failed >= 0) sb.append("Total Failed: $failed\n")
        
        if (lastAt > 0) {
            sb.append("\nLast Activity: ${formatRelative(lastAt, now)}\n")
            val status = when (lastOk) {
                true -> "Success"
                false -> "Failed"
                else -> "Unknown"
            }
            sb.append("Last Status: $status\n")
            if (bankName != null) sb.append("Last Bank: $bankName\n")
            if (latency != null && latency > 0) sb.append("Latency: ${latency}ms\n")
            if (!lastError.isNullOrBlank()) {
                sb.append("\nError Detail:\n$lastError")
            }
        } else {
            sb.append("\nNo activity recorded yet.")
        }

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(sb.toString())
            .setPositiveButton(android.R.string.ok, null)
            .create()
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

