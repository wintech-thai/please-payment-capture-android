package com.example.notification_agent.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.notification_agent.databinding.ItemAppFilterBinding

class AppFilterAdapter(
    private val onToggle: (AppFilterUiItem, Boolean) -> Unit,
    private val onForwardToggle: (AppFilterUiItem, Boolean) -> Unit
) : ListAdapter<AppFilterUiItem, AppFilterAdapter.VH>(DIFF) {

    object DIFF : DiffUtil.ItemCallback<AppFilterUiItem>() {
        override fun areItemsTheSame(a: AppFilterUiItem, b: AppFilterUiItem) =
            a.info.packageName == b.info.packageName
        override fun areContentsTheSame(a: AppFilterUiItem, b: AppFilterUiItem) = a == b
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAppFilterBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemAppFilterBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AppFilterUiItem) {
            binding.label.text = item.info.label
            binding.packageName.text = item.info.packageName
            binding.toggle.setOnCheckedChangeListener(null)
            binding.toggle.isChecked = item.enabled
            binding.toggle.setOnCheckedChangeListener { _, checked ->
                onToggle(item, checked)
            }
            binding.forwardRow.visibility = if (item.enabled) View.VISIBLE else View.GONE
            binding.forwardToggle.setOnCheckedChangeListener(null)
            binding.forwardToggle.isChecked = item.forwardToWebhook
            binding.forwardToggle.setOnCheckedChangeListener { _, checked ->
                onForwardToggle(item, checked)
            }
        }
    }
}
