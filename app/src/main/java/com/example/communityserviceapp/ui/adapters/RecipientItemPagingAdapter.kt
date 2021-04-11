package com.example.communityserviceapp.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.NavDirections
import androidx.navigation.Navigation
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.communityserviceapp.R
import com.example.communityserviceapp.data.model.Recipient
import com.example.communityserviceapp.databinding.RecipientItemBinding
import com.example.communityserviceapp.ui.home.HomeFragmentDirections
import com.example.communityserviceapp.ui.recipient.favorite.RecipientFavoriteFragmentDirections
import com.example.communityserviceapp.ui.recipient.filter.RecipientFilterFragmentDirections
import com.example.communityserviceapp.util.Constants
import com.example.communityserviceapp.util.inflateChip
import java.util.*
import javax.inject.Inject

class RecipientItemPagingAdapter @Inject constructor() :
    PagingDataAdapter<Recipient, RecipientItemPagingAdapter.RecipientItemViewHolder>(
        RecipientItemDiffCallback()
    ) {

    var onRecipientItemChipClickListener: ((String) -> Unit)? = null
    var onRecipientItemClickListener: ((Recipient) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipientItemViewHolder {
        return RecipientItemViewHolder(
            RecipientItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: RecipientItemViewHolder, position: Int) {
        val item = getItem(position) as Recipient
        holder.bind(item)
    }

    inner class RecipientItemViewHolder(
        private val binding: RecipientItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(recipient: Recipient) {
            checkPictureSourceAndLoadIt(recipient.recipientImageUrl)

            binding.apply {
                root.setOnClickListener {
                    val currentDestination =
                        Navigation.findNavController(root).currentDestination!!.id
                    when (currentDestination) {
                        R.id.recipientFavoriteFragment -> {
                            onRecipientItemClickListener?.invoke(recipient)
                        }
                        else -> {
                            var action: NavDirections? = null
                            if (currentDestination == R.id.homeFragment) {
                                action =
                                    HomeFragmentDirections.actionHomeFragmentToRecipientBottomSheet(
                                        recipient
                                    )
                            } else if (currentDestination == R.id.recipientFilterFragment) {
                                action =
                                    RecipientFilterFragmentDirections.actionRecipientFilterFragmentToRecipientBottomSheet(
                                        recipient
                                    )
                            }
                            performNavigation(it, action)
                        }
                    }
                }

                // To avoid chip bug with show tag with scroll up-down which
                // view reused old view to show tags item, use removeAllViews()
                jobNatureChipGroup.removeAllViews()
                val jobNature = recipient.recipientJobNature.split(", ").toTypedArray()
                for (tag in jobNature) {
                    val chip = inflateChip(
                        Constants.CHIP_TYPE_ACTION,
                        tag,
                        root.context,
                        minTouchTargetSize = true
                    ).apply {
                        setOnClickListener { view: View ->
                            val chipText = this.text as String
                            searchRecipientBasedOnSelectedActionChip(chipText, view)
                        }
                    }
                    jobNatureChipGroup.addView(chip)
                }

                val contact = recipient.recipientPhoneNum
                if (contact.isNotEmpty()) {
                    recipientContact.text = contact
                    contactText.visibility = View.VISIBLE
                    recipientContact.visibility = View.VISIBLE
                } else {
                    contactText.visibility = View.GONE
                    recipientContact.visibility = View.GONE
                }

                val email = recipient.recipientEmail
                if (email.isNotEmpty()) {
                    recipientEmail.text = email
                    emailText.visibility = View.VISIBLE
                    recipientEmail.visibility = View.VISIBLE
                } else {
                    emailText.visibility = View.GONE
                    recipientEmail.visibility = View.GONE
                }

                val website = recipient.recipientWebsite
                if (website.isNotEmpty()) {
                    recipientWebsite.text = website
                    websiteText.visibility = View.VISIBLE
                    recipientWebsite.visibility = View.VISIBLE
                } else {
                    websiteText.visibility = View.GONE
                    recipientWebsite.visibility = View.GONE
                }

                recipientAddress.text = recipient.recipientAddress
                // format recipient rating to 1 decimal place
                recipientRating.text =
                    String.format(Locale.ROOT, "%.1f", recipient.recipientRating)
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

        private fun searchRecipientBasedOnSelectedActionChip(chipText: String, view: View) {
            val currentDestination =
                Navigation.findNavController(binding.root).currentDestination!!.id
            if (currentDestination == R.id.recipientFilterFragment) {
                onRecipientItemChipClickListener?.invoke(chipText)
            } else {
                val searchQuery = (
                    "SELECT * FROM recipient WHERE name LIKE \"%" + chipText + "%\" " +
                        "OR address LIKE \"%" + chipText + "%\" " +
                        "OR jobNature LIKE \"%" + chipText + "%\" " +
                        "OR state LIKE \"%" + chipText + "%\""
                    )
                val action = setupNavDirections(currentDestination, searchQuery, chipText)
                performNavigation(view, action)
            }
        }

        private fun setupNavDirections(
            currentDestination: Int,
            searchQuery: String,
            chipText: String
        ): NavDirections? {
            return when (currentDestination) {
                R.id.homeFragment -> {
                    HomeFragmentDirections.actionHomeFragmentToRecipientFilterFragment(
                        searchQuery,
                        chipText,
                        null
                    )
                }
                R.id.recipientFavoriteFragment -> {
                    RecipientFavoriteFragmentDirections
                        .actionRecipientFavoriteFragmentToRecipientFilterFragment(
                            searchQuery,
                            chipText,
                            null
                        )
                }
                else -> null
            }
        }

        private fun performNavigation(view: View, action: NavDirections?) {
            action?.let { Navigation.findNavController(view).navigate(action) }
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        onRecipientItemChipClickListener = null
        onRecipientItemClickListener = null
        super.onDetachedFromRecyclerView(recyclerView)
    }
}

class RecipientItemDiffCallback : DiffUtil.ItemCallback<Recipient>() {
    override fun areItemsTheSame(oldItem: Recipient, newItem: Recipient): Boolean {
        return oldItem.recipientID == newItem.recipientID
    }

    override fun areContentsTheSame(oldItem: Recipient, newItem: Recipient): Boolean {
        return oldItem == newItem
    }
}