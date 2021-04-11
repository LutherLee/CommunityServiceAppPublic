package com.example.communityserviceapp.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.communityserviceapp.R
import com.example.communityserviceapp.data.model.Recipient
import com.example.communityserviceapp.databinding.RecipientFavoriteItemBinding
import javax.inject.Inject

class RecipientFavoritePagingAdapter @Inject constructor() :
    PagingDataAdapter<Recipient, RecipientFavoritePagingAdapter.RecipientFavoriteViewHolder>(
        RecipientItemDiffCallback()
    ) {

    var onRecipientItemClickListener: ((Recipient) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipientFavoriteViewHolder {
        return RecipientFavoriteViewHolder(
            RecipientFavoriteItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: RecipientFavoriteViewHolder, position: Int) {
        val item = getItem(position) as Recipient
        holder.bind(item)
    }

    inner class RecipientFavoriteViewHolder(
        private val binding: RecipientFavoriteItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(recipient: Recipient) {
            checkPictureSourceAndLoadIt(recipient.recipientImageUrl)

            binding.apply {
                recipientItem.setOnClickListener { onRecipientItemClickListener?.invoke(recipient) }
                recipientAddress.text = recipient.recipientAddress
                recipientName.text = recipient.recipientName
                executePendingBindings()
            }
        }

        private fun checkPictureSourceAndLoadIt(imageUrl: String) {
            Glide.with(binding.root.context)
                .load(imageUrl)
                .fitCenter()
                .placeholder(R.drawable.custom_default_image)
                .thumbnail(0.25f)
                .into(binding.recipientImage)
        }
    }

    fun getRecipientAtPosition(position: Int): Recipient {
        return getItem(position)!!
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        onRecipientItemClickListener = null
        super.onDetachedFromRecyclerView(recyclerView)
    }
}
