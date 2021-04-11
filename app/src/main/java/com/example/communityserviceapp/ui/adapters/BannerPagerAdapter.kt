package com.example.communityserviceapp.ui.adapters

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.communityserviceapp.data.model.Banner
import com.example.communityserviceapp.ui.home.BannerItemFragment

class BannerPagerAdapter constructor(fragmentManager: FragmentManager, lifecycle: Lifecycle) :
    FragmentStateAdapter(fragmentManager, lifecycle) {

    private val bannerList: ArrayList<Banner> = arrayListOf()

    override fun createFragment(position: Int): Fragment {
        val bannerItemFragment = BannerItemFragment()
        val args = Bundle()
        args.apply {
            putString("imageUrl", bannerList[position].bannerImageUrl)
            putString("website", bannerList[position].bannerWebsiteLink)
            putString("detail", bannerList[position].bannerDetail)
        }
        bannerItemFragment.arguments = args
        return bannerItemFragment
    }

    override fun getItemCount() = bannerList.size

    fun setBannerList(banners: List<Banner>) {
        this.bannerList.clear()
        this.bannerList.addAll(banners)
        notifyDataSetChanged()
    }
}
