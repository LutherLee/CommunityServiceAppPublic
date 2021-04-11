package com.example.communityserviceapp.ui.adapters

import android.view.View
import androidx.databinding.BindingAdapter
import androidx.paging.PagedList
import com.airbnb.lottie.LottieAnimationView
import com.example.communityserviceapp.data.model.Recipient

@BindingAdapter(
    value = ["showView", "viewPagedList", "viewCurrentUserID", "showOnlyIfLogin"],
    requireAll = false
)
fun View.bindShowView(
    showView: Boolean?,
    pagedList: PagedList<Recipient>?,
    currentUserID: String?,
    showOnlyIfLogin: Boolean
) {
    when {
        showView != null -> {
            this.visibility = if (showView) View.VISIBLE else View.GONE
        }
        currentUserID == null && pagedList == null -> {
            this.visibility = if (showOnlyIfLogin) View.GONE else View.VISIBLE
        }
        currentUserID != null && pagedList != null && pagedList.isNotEmpty() -> {
            this.visibility = View.GONE
        }
        else -> {
            this.visibility = if (showOnlyIfLogin) View.VISIBLE else View.GONE
        }
    }
}

@BindingAdapter(
    value = ["pauseAnim", "LottieViewPagedList", "LottieViewCurrentUserID", "showOnlyIfLogin"],
    requireAll = false
)
fun LottieAnimationView.bindPauseLottieAnim(
    pauseAnim: Boolean?,
    pagedList: PagedList<Recipient>?,
    currentUserID: String?,
    showOnlyIfLogin: Boolean
) {
    if (pauseAnim != null) {
        if (pauseAnim) this.pauseAnimation() else this.resumeAnimation()
    } else if (currentUserID == null && pagedList == null) {
        this.visibility = if (showOnlyIfLogin) {
            this.pauseAnimation()
            View.GONE
        } else {
            this.resumeAnimation()
            View.VISIBLE
        }
    } else if (currentUserID != null && pagedList != null && pagedList.isNotEmpty()) {
        this.pauseAnimation()
        this.visibility = View.GONE
    } else if (currentUserID != null && pagedList != null && pagedList.isEmpty()) {
        this.resumeAnimation()
        this.visibility = View.VISIBLE
    } else {
        this.visibility = if (showOnlyIfLogin) {
            this.pauseAnimation()
            View.VISIBLE
        } else {
            this.resumeAnimation()
            View.GONE
        }
    }
}