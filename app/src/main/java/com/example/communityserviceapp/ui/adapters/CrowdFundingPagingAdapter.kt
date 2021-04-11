package com.example.communityserviceapp.ui.adapters

import android.content.Context
import android.net.Uri
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.communityserviceapp.R
import com.example.communityserviceapp.data.model.CrowdFunding
import com.example.communityserviceapp.databinding.CrowdFundingItemBinding
import com.example.communityserviceapp.util.Result
import com.example.communityserviceapp.util.getCircularProgressDrawable

class CrowdFundingPagingAdapter :
    PagingDataAdapter<CrowdFunding, CrowdFundingPagingAdapter.CrowdFundingViewHolder>(
        CrowdFundingDiffCallback()
    ) {

    var onCrowdFundingItemClickListener: ((Result<Uri>) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CrowdFundingViewHolder {
        return CrowdFundingViewHolder(
            CrowdFundingItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: CrowdFundingViewHolder, position: Int) {
        val item = getItem(position)
        item?.let { holder.bind(it) }
    }

    inner class CrowdFundingViewHolder(
        private val binding: CrowdFundingItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(crowdFunding: CrowdFunding) {
            binding.apply {
                val root = root
                val context = root.context

                checkPictureSourceAndLoadIt(context, crowdFunding.imageUrl)
                name.text = crowdFunding.name
                donationPolicy.text = Html.fromHtml(crowdFunding.donationPolicy, HtmlCompat.FROM_HTML_MODE_LEGACY)
                detail.text = crowdFunding.detail

                setupSocialMediaLinksIfNotEmpty(binding.recipientInstagram, crowdFunding.instagram)
                setupSocialMediaLinksIfNotEmpty(binding.recipientLinkedin, crowdFunding.linkedin)
                setupSocialMediaLinksIfNotEmpty(binding.recipientFacebook, crowdFunding.facebook)
                setupSocialMediaLinksIfNotEmpty(binding.recipientTwitter, crowdFunding.twitter)
                setupSocialMediaLinksIfNotEmpty(binding.recipientYoutube, crowdFunding.youtube)

                root.setOnClickListener {
                    val urlLink = Uri.parse(crowdFunding.websiteLink)
                    onCrowdFundingItemClickListener?.invoke(Result.Success(urlLink))
                }
                executePendingBindings()
            }
        }

        private fun setupSocialMediaLinksIfNotEmpty(view: View, link: String) {
            if (link.isNotEmpty()) {
                view.setOnClickListener {
                    try {
                        val urlLink = Uri.parse(link)
                        onCrowdFundingItemClickListener?.invoke(Result.Success(urlLink))
                    } catch (e: Exception) {
                        onCrowdFundingItemClickListener?.invoke(Result.Error(link))
                    }
                }
                view.visibility = View.VISIBLE
            } else {
                view.visibility = View.GONE
            }
        }

        private fun checkPictureSourceAndLoadIt(context: Context, imageUrl: String) {
            Glide.with(context)
                .load(imageUrl)
                .fitCenter()
                .placeholder(getCircularProgressDrawable())
                .error(R.drawable.ic_baseline_image_grey_24dp)
                .into(binding.image)
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        onCrowdFundingItemClickListener = null
        super.onDetachedFromRecyclerView(recyclerView)
    }
}

class CrowdFundingDiffCallback : DiffUtil.ItemCallback<CrowdFunding>() {
    override fun areItemsTheSame(oldItem: CrowdFunding, newItem: CrowdFunding): Boolean {
        return oldItem.crowdFundingID == newItem.crowdFundingID
    }

    override fun areContentsTheSame(oldItem: CrowdFunding, newItem: CrowdFunding): Boolean {
        return oldItem == newItem
    }
}
