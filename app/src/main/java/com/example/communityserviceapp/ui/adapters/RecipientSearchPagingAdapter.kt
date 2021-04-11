package com.example.communityserviceapp.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.communityserviceapp.data.model.Recipient
import com.example.communityserviceapp.databinding.RecipientSearchItemBinding
import com.example.communityserviceapp.util.TextDrawable

class RecipientSearchPagingAdapter :
    PagingDataAdapter<Recipient, RecipientSearchPagingAdapter.SearchViewHolder>(
        RecipientItemDiffCallback()
    ) {

    var onRecipientItemClickListener: ((Recipient) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        return SearchViewHolder(
            RecipientSearchItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        val item = getItem(position)
        item?.let { holder.bind(it) }
    }

    inner class SearchViewHolder(
        private val binding: RecipientSearchItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(recipient: Recipient) {
            binding.apply {
                binding.recipientName.text = recipient.recipientName
                recipientState.setImageDrawable(TextDrawable(recipient.recipientState))
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
