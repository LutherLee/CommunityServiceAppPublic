package com.example.communityserviceapp.ui.adapters

import android.text.Html
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.communityserviceapp.R
import com.example.communityserviceapp.data.model.Recipient
import com.example.communityserviceapp.databinding.RecommendationItemBinding
import com.example.communityserviceapp.util.getCircularProgressDrawable

class RecommendationAdapter : ListAdapter<Recipient, RecommendationAdapter.Holder>(RecipientItemDiffCallback()) {

    var onRecipientItemClickListener: ((Int, Recipient) -> Unit)? = null

    companion object {
        const val RECIPIENT_ROOT_VIEW = 1
        const val RECIPIENT_SHARE = 2
        const val RECIPIENT_DIRECTION = 3
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(
            RecommendationItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val recipient = getItem(position)
        recipient?.let {
            holder.from(recipient)
        }
    }

    inner class Holder(val binding: RecommendationItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun from(recipient: Recipient) {
            binding.apply {
                Glide.with(binding.root.context)
                    .load(recipient.recipientImageUrl)
                    .placeholder(getCircularProgressDrawable())
                    .error(R.drawable.ic_baseline_image_grey_24dp)
                    .into(recipientImage)

                recipientName.text = recipient.recipientName
                recipientState.text = recipient.recipientState
                recipientDetail.text =
                    Html.fromHtml(recipient.recipientDetail, HtmlCompat.FROM_HTML_MODE_LEGACY)

                recipientItem.setOnClickListener {
                    onRecipientItemClickListener?.invoke(RECIPIENT_ROOT_VIEW, recipient)
                }
                getDirectionButton.setOnClickListener {
                    onRecipientItemClickListener?.invoke(RECIPIENT_DIRECTION, recipient)
                }
                shareRecipientButton.setOnClickListener {
                    onRecipientItemClickListener?.invoke(RECIPIENT_SHARE, recipient)
                }
                executePendingBindings()
            }
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        onRecipientItemClickListener = null
        super.onDetachedFromRecyclerView(recyclerView)
    }
}
