package com.example.notification_agent.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.notification_agent.data.FilterRuleEntity
import com.example.notification_agent.databinding.ItemSmsFilterBinding

class SmsFilterAdapter(
    private val onToggle: (FilterRuleEntity, Boolean) -> Unit,
    private val onForwardToggle: (FilterRuleEntity, Boolean) -> Unit,
    private val onDelete: (FilterRuleEntity) -> Unit
) : ListAdapter<FilterRuleEntity, SmsFilterAdapter.VH>(DIFF) {

    object DIFF : DiffUtil.ItemCallback<FilterRuleEntity>() {
        override fun areItemsTheSame(a: FilterRuleEntity, b: FilterRuleEntity) =
            a.sourceKey == b.sourceKey
        override fun areContentsTheSame(a: FilterRuleEntity, b: FilterRuleEntity) = a == b
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemSmsFilterBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemSmsFilterBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FilterRuleEntity) {
            binding.key.text = item.sourceKey
            binding.toggle.setOnCheckedChangeListener(null)
            binding.toggle.isChecked = item.enabled
            binding.toggle.setOnCheckedChangeListener { _, checked -> onToggle(item, checked) }
            binding.delete.setOnClickListener { onDelete(item) }
            binding.forwardRow.visibility = if (item.enabled) View.VISIBLE else View.GONE
            binding.forwardToggle.setOnCheckedChangeListener(null)
            binding.forwardToggle.isChecked = item.forwardToWebhook
            binding.forwardToggle.setOnCheckedChangeListener { _, checked ->
                onForwardToggle(item, checked)
            }
        }
    }
}
