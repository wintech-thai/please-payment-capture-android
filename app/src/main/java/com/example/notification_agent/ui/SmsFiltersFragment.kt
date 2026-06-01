package com.example.notification_agent.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.notification_agent.databinding.FragmentSmsFiltersBinding
import kotlinx.coroutines.launch

class SmsFiltersFragment : Fragment() {

    private var _binding: FragmentSmsFiltersBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by viewModels(ownerProducer = { requireActivity() })
    private val adapter = SmsFilterAdapter(
        onToggle = { rule, checked -> viewModel.toggleSmsRule(rule, checked) },
        onForwardToggle = { rule, checked -> viewModel.toggleSmsForward(rule, checked) },
        onDelete = { rule -> viewModel.deleteSmsRule(rule) }
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSmsFiltersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter

        binding.add.setOnClickListener {
            val text = binding.input.text?.toString().orEmpty()
            if (text.isNotBlank()) {
                viewModel.addSmsRule(text)
                binding.input.text?.clear()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.smsRules.collect { rules ->
                    adapter.submitList(rules)
                    binding.empty.visibility =
                        if (rules.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

