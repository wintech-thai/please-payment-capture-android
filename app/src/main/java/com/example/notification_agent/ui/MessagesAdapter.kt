package com.example.notification_agent.ui

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.notification_agent.data.MessageEntity
import com.example.notification_agent.data.SourceType
import com.example.notification_agent.databinding.ItemMessageBinding

class MessagesAdapter : ListAdapter<MessageEntity, MessagesAdapter.VH>(DIFF) {

    object DIFF : DiffUtil.ItemCallback<MessageEntity>() {
        override fun areItemsTheSame(a: MessageEntity, b: MessageEntity) = a.id == b.id
        override fun areContentsTheSame(a: MessageEntity, b: MessageEntity) = a == b
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(private val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MessageEntity) {
            val typeLabel = if (item.sourceType == SourceType.SMS) "SMS" else "NOTIF"
            val source = item.sourceLabel ?: item.sourceKey
            binding.source.text = "[$typeLabel] $source"
            binding.title.text = item.title.orEmpty()
            binding.body.text = item.text.orEmpty()
            binding.timestamp.text = DateUtils.getRelativeTimeSpanString(
                item.timestamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )
        }
    }
}

