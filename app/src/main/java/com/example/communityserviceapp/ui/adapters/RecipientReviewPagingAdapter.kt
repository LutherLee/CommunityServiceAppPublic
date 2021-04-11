package com.example.communityserviceapp.ui.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.communityserviceapp.R
import com.example.communityserviceapp.data.model.Review
import com.example.communityserviceapp.databinding.RecipientReviewItemBinding
import com.example.communityserviceapp.util.Constants.CHIP_TYPE_ACTION
import com.example.communityserviceapp.util.getColor
import com.example.communityserviceapp.util.inflateChip
import javax.inject.Inject

class RecipientReviewAdapter @Inject constructor() :
    PagingDataAdapter<Review, RecipientReviewAdapter.ViewHolder>(RecipientReviewDiffCallback()) {

    var onReviewChipClickListener: ((String) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            RecipientReviewItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        item?.let { holder.bind(it) }
    }

    inner class ViewHolder(private val binding: RecipientReviewItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(review: Review) {
            loadUserProfilePicture(review.reviewerImageUrl)
            binding.apply {
                reviewBy.text = review.reviewerName
                userRating.rating = review.reviewRating
                reviewDate.text = review.reviewDate?.time?.getDurationBreakdown()
                reviewMessage.text = review.reviewMessage
                inflateReviewTags(review, root.context)
                executePendingBindings()
            }
        }

        private fun loadUserProfilePicture(imageUrl: String?) {
            Glide.with(binding.root.context)
                .load(imageUrl)
                .circleCrop()
                .placeholder(R.drawable.custom_account_circle)
                .thumbnail(0.25f)
                .encodeQuality(70)
                .into(binding.userProfilePicture)
        }

        private fun inflateReviewTags(review: Review, context: Context) {
            binding.recipientReviewTagChipGroup.removeAllViews()
            val reviewTags = review.reviewTags.split(", ").toTypedArray()
            for (tag in reviewTags) {
                if (tag.isEmpty()) {
                    binding.horizontalChipGroupScrollView.visibility = View.GONE
                } else {
                    binding.horizontalChipGroupScrollView.visibility = View.VISIBLE
                    val chip = inflateChip(CHIP_TYPE_ACTION, tag, context).apply {
                        setOnClickListener {
                            onReviewChipClickListener?.invoke(text.toString())
                        }
                        if (text == "Need Improvement") {
                            setTextColor(getColor(R.color.dark_red))
                        } else {
                            setTextColor(getColor(R.color.gnt_ad_green))
                        }
                    }
                    binding.recipientReviewTagChipGroup.addView(chip)
                }
            }
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        onReviewChipClickListener = null
        super.onDetachedFromRecyclerView(recyclerView)
    }
}

class RecipientReviewDiffCallback : DiffUtil.ItemCallback<Review>() {
    override fun areItemsTheSame(oldItem: Review, newItem: Review) =
        oldItem.reviewID === newItem.reviewID

    override fun areContentsTheSame(oldItem: Review, newItem: Review) = oldItem == newItem
}
