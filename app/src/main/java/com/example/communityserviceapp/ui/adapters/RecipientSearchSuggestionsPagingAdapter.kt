package com.example.communityserviceapp.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.communityserviceapp.R
import com.example.communityserviceapp.data.model.Recipient
import com.example.communityserviceapp.databinding.RecipientSearchSuggestionItemBinding
import com.example.communityserviceapp.util.getCircularProgressDrawable
import javax.inject.Inject

class RecipientSearchSuggestionsPagingAdapter @Inject constructor() :
    PagingDataAdapter<Recipient, RecipientSearchSuggestionsPagingAdapter.SearchSuggestionViewHolder>(
        RecipientItemDiffCallback()
    ) {

    var onRecipientItemClickListener: ((Recipient) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchSuggestionViewHolder {
        return SearchSuggestionViewHolder(
            RecipientSearchSuggestionItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: SearchSuggestionViewHolder, position: Int) {
        val item = getItem(position) as Recipient
        holder.bind(item)
    }

    inner class SearchSuggestionViewHolder(
        private val binding: RecipientSearchSuggestionItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(recipient: Recipient) {
            binding.apply {
                Glide.with(binding.root.context)
                    .load(recipient.recipientImageUrl)
                    .placeholder(getCircularProgressDrawable())
                    .error(R.drawable.ic_baseline_image_grey_24dp)
                    .into(recipientImage)
                recipientName.text = recipient.recipientName
                recipientState.text = recipient.recipientState
                recipientItem.setOnClickListener { onRecipientItemClickListener?.invoke(recipient) }
                executePendingBindings()
            }
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        onRecipientItemClickListener = null
        super.onDetachedFromRecyclerView(recyclerView)
    }
}
