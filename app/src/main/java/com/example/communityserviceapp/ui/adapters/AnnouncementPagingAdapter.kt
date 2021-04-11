package com.example.communityserviceapp.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.communityserviceapp.R
import com.example.communityserviceapp.data.model.Announcement
import com.example.communityserviceapp.databinding.AnnouncementItemBinding
import com.example.communityserviceapp.util.getColor
import javax.inject.Inject

class AnnouncementPagingAdapter @Inject constructor() :
    PagingDataAdapter<Announcement, AnnouncementPagingAdapter.ViewHolder>(AnnouncementDiffCallback()) {

    private var mExpandedPosition = -1
    private var previousExpandedPosition = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            AnnouncementItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // For animating dropdown effect
        val isExpanded = position == mExpandedPosition
        holder.binding.message.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.itemView.isActivated = isExpanded
        if (isExpanded) {
            previousExpandedPosition = position
            holder.binding.title.setTextColor(getColor(R.color.bright_background_blue))
        } else {
            holder.binding.title.setTextColor(getColor(R.color.black))
        }
        // Bind item text and onClick
        val item = getItem(position)
        item?.let { holder.bind(it, isExpanded, position) }
    }

    inner class ViewHolder(val binding: AnnouncementItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(announcement: Announcement, isExpanded: Boolean, position: Int) {
            binding.apply {
                title.text = announcement.announcementTitle
                message.text = announcement.announcementMessage
                createdDate.text =
                    announcement.announcementCreatedDate?.time?.getDurationBreakdown()
                announcementItem.setOnClickListener {
                    mExpandedPosition = when {
                        isExpanded -> { -1 }
                        else -> { position }
                    }
                    notifyItemChanged(previousExpandedPosition)
                    notifyItemChanged(position)
                }
                executePendingBindings()
            }
        }
    }
}

class AnnouncementDiffCallback : DiffUtil.ItemCallback<Announcement>() {
    override fun areItemsTheSame(oldItem: Announcement, newItem: Announcement) =
        oldItem.announcementID == newItem.announcementID

    override fun areContentsTheSame(oldItem: Announcement, newItem: Announcement) =
        oldItem == newItem
}
