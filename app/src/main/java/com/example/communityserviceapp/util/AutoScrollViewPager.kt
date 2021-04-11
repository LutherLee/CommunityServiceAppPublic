package com.example.communityserviceapp.util

import android.os.Handler
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import java.lang.ref.WeakReference
import java.util.*

/**
 * A Util class to auto scroll view pager with a delay and period of 12 sec.
 *
 * Note: This class is created with the intention to prevent memory leak by not
 * using anonymous inner class in the class that uses this function.
 */
class AutoScrollViewPager(
    private val viewPager: WeakReference<ViewPager2>,
    private val adapter: WeakReference<FragmentStateAdapter>,
    private val fragment: WeakReference<Fragment>
) : TimerTask() {

    override fun run() {
        val update = Runnable {
            val currentViewPager = viewPager.get()
            val currentAdapter = adapter.get()
            if (currentViewPager != null && currentAdapter != null) {
                var tab = currentViewPager.currentItem
                tab++
                if (tab == currentAdapter.itemCount) {
                    currentViewPager.currentItem = 0
                } else {
                    currentViewPager.currentItem = tab
                }
            }
        }
        Timer().schedule(
            object : TimerTask() {
                override fun run() {
                    val currentFragment = fragment.get()
                    if (currentFragment != null && currentFragment.isAdded) {
                        currentFragment.requireActivity().runOnUiThread { Handler().post(update) }
                    }
                }
            },
            12000, 12000
        )
    }
}
