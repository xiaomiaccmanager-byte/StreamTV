package com.streamtv.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.streamtv.data.model.Channel
import com.streamtv.data.scheduler.EpgScheduler
import com.streamtv.databinding.ItemChannelBinding

class ChannelAdapter(
    private val onChannelClick: (Int) -> Unit
) : ListAdapter<Channel, ChannelAdapter.ViewHolder>(DiffCallback()) {

    private var activeIndex = 0

    fun setActive(index: Int) {
        val old = activeIndex
        activeIndex = index
        notifyItemChanged(old)
        notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChannelBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position == activeIndex) {
            onChannelClick(position)
        }
    }

    class ViewHolder(private val b: ItemChannelBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(channel: Channel, isActive: Boolean, onClick: () -> Unit) {
            b.tvChannelNum.text  = (adapterPosition + 1).toString().padStart(2, '0')
            b.tvChannelName.text = channel.name
            b.tvChannelEmoji.text = channel.emoji

            val current = EpgScheduler.getCurrentItem(channel)
            b.tvNowPlaying.text = current?.show?.title ?: "Нет данных"

            b.root.isSelected = isActive
            b.root.setOnClickListener { onClick() }

            // Индикатор LIVE
            b.liveDot.alpha = if (isActive) 1f else 0.4f
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Channel>() {
        override fun areItemsTheSame(a: Channel, b: Channel) = a.id == b.id
        override fun areContentsTheSame(a: Channel, b: Channel) = a == b
    }
}
