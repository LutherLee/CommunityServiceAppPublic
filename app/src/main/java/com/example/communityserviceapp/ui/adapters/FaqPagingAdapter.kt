package com.example.communityserviceapp.ui.adapters

import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.communityserviceapp.R
import com.example.communityserviceapp.data.model.Faq
import com.example.communityserviceapp.databinding.FaqItemBinding
import com.example.communityserviceapp.util.getColor
import javax.inject.Inject

class FaqAdapter @Inject constructor() :
    PagingDataAdapter<Faq, FaqAdapter.FaqViewHolder>(FaqDiffUtilCallback()) {

    private var mExpandedPosition = -1
    private var previousExpandedPosition = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FaqViewHolder {
        return FaqViewHolder(
            FaqItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: FaqViewHolder, position: Int) {
        // For animating dropdown effect
        val isExpanded = position == mExpandedPosition
        holder.binding.faqAnswer.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.itemView.isActivated = isExpanded
        if (isExpanded) {
            previousExpandedPosition = position
            holder.binding.faqQuestion.setTextColor(getColor(R.color.bright_background_blue))
            holder.binding.viewMoreBtn.animate().setDuration(200).rotation(180f)
        } else {
            holder.binding.faqQuestion.setTextColor(getColor(R.color.black))
            holder.binding.viewMoreBtn.animate().setDuration(200).rotation(0f)
        }
        // Bind item text and onClick
        val item = getItem(position)
        item?.let { holder.bind(it, isExpanded, position) }
    }

    inner class FaqViewHolder(val binding: FaqItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(faq: Faq, isExpanded: Boolean, position: Int) {
            binding.apply {
                faqQuestion.text = faq.faqQuestion
                faqAnswer.text = Html.fromHtml(faq.faqAnswer, HtmlCompat.FROM_HTML_MODE_LEGACY)
                faqItem.setOnClickListener {
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

class FaqDiffUtilCallback : DiffUtil.ItemCallback<Faq>() {
    override fun areItemsTheSame(oldItem: Faq, newItem: Faq): Boolean {
        return oldItem.faqID == newItem.faqID
    }

    override fun areContentsTheSame(oldItem: Faq, newItem: Faq): Boolean {
        return oldItem == newItem
    }
}
