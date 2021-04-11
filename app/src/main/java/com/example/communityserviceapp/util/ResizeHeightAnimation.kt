package com.example.communityserviceapp.util

import android.view.View
import android.view.animation.Animation
import android.view.animation.Transformation
import java.lang.ref.WeakReference
import javax.inject.Inject
import kotlin.math.abs

/**
 * A generic class that helps to provide view resize animation. See usage in BaseBottomSheet class.
 */
class ResizeHeightAnimation @Inject constructor() : Animation() {
    private var startHeight = 0
    private var deltaHeight = 0
    private lateinit var view: WeakReference<View>

    fun setView(view: WeakReference<View>) {
        this.view = view
    }

    fun setHeights(start: Int, end: Int) {
        startHeight = start
        deltaHeight = end - startHeight
    }

    override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
        if (startHeight != 0) {
            if (deltaHeight > 0) {
                view.get()?.layoutParams?.height = (startHeight + deltaHeight * interpolatedTime).toInt()
            } else {
                view.get()?.layoutParams?.height = (startHeight - abs(deltaHeight) * interpolatedTime).toInt()
            }
        }
        view.get()?.requestLayout()
    }
}
