package com.example.communityserviceapp.util

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import com.example.communityserviceapp.R

/**
 * A util class that helps to turn text as drawable resources using Canvas
 */
class TextDrawable(private val text: String) : Drawable() {

    private val paint = Paint()

    init {
        paint.apply {
            color = getColor(R.color.colorPrimary)
            textSize = 35f
            isAntiAlias = true
            isFakeBoldText = true
            style = Paint.Style.FILL
        }
    }

    override fun draw(canvas: Canvas) = canvas.drawText(text, 5f, 49f, paint)

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }
}
