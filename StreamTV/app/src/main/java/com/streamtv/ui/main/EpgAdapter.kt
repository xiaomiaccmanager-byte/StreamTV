package com.streamtv.ui.main

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.streamtv.data.model.EpgItem
import com.streamtv.data.scheduler.EpgScheduler
import com.streamtv.databinding.ItemEpgBinding

class EpgAdapter(
    private val onItemClick: (EpgItem) -> Unit
) : ListAdapter<EpgItem, EpgAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEpgBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position)) { onItemClick(it) }
    }

    class ViewHolder(private val b: ItemEpgBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: EpgItem, onClick: (EpgItem) -> Unit) {
            b.tvTime.text       = EpgScheduler.formatTime(item.startTime)
            b.tvEndTime.text    = EpgScheduler.formatTime(item.endTime)
            b.tvShowTitle.text  = item.show.title
            b.tvEpisode.text    = item.episode.title
            b.tvDuration.text   = EpgScheduler.formatDuration(item.episode.durationMin)

            when {
                item.isNow -> {
                    b.root.alpha = 1f
                    b.badgeNow.visibility   = View.VISIBLE
                    b.progressBar.visibility = View.VISIBLE
                    b.progressBar.progress  = item.progressPercent
                    b.tvTimeLeft.visibility = View.VISIBLE
                    b.tvTimeLeft.text       = "${item.minutesLeft}м осталось"
                    b.tvTime.setTypeface(null, Typeface.BOLD)
                }
                item.isPast -> {
                    b.root.alpha = 0.4f
                    b.badgeNow.visibility    = View.GONE
                    b.progressBar.visibility = View.GONE
                    b.tvTimeLeft.visibility  = View.GONE
                    b.tvTime.setTypeface(null, Typeface.NORMAL)
                }
                else -> {
                    b.root.alpha = 1f
                    b.badgeNow.visibility    = View.GONE
                    b.progressBar.visibility = View.GONE
                    b.tvTimeLeft.visibility  = View.GONE
                    b.tvTime.setTypeface(null, Typeface.NORMAL)
                }
            }

            b.root.setOnClickListener { onClick(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<EpgItem>() {
        override fun areItemsTheSame(a: EpgItem, b: EpgItem) =
            a.startTime == b.startTime && a.show.title == b.show.title
        override fun areContentsTheSame(a: EpgItem, b: EpgItem) = a == b
    }
}
