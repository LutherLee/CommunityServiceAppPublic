package com.example.communityserviceapp.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import androidx.annotation.NonNull
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.communityserviceapp.R

/**
 * Helper class for recyclerview to implement swipe to delete and undo (using snackbar) feature
 *
 * Note: Class is abstract since onSwipe() is not implemented
 */
abstract class SwipeToDeleteCallback internal constructor(context: Context) :
    ItemTouchHelper.Callback() {

    private val mContext: Context = context
    private val mClearPaint: Paint = Paint()
    private val mBackground: ColorDrawable = ColorDrawable()
    private val backgroundColor: Int = getColor(R.color.colorSecondary)
    private val deleteDrawable: Drawable
    private val intrinsicWidth: Int
    private val intrinsicHeight: Int

    init {
        mClearPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        deleteDrawable = ContextCompat.getDrawable(
            mContext,
            R.drawable.ic_baseline_delete_white_24dp
        )!!
        intrinsicWidth = deleteDrawable.intrinsicWidth
        intrinsicHeight = deleteDrawable.intrinsicHeight
    }

    override fun getMovementFlags(
        @NonNull recyclerView: RecyclerView,
        @NonNull viewHolder: RecyclerView.ViewHolder
    ): Int {
        return makeMovementFlags(0, ItemTouchHelper.LEFT)
    }

    override fun onMove(
        @NonNull recyclerView: RecyclerView,
        @NonNull viewHolder: RecyclerView.ViewHolder,
        @NonNull viewHolder1: RecyclerView.ViewHolder
    ): Boolean {
        return false
    }

    override fun onChildDraw(
        canvas: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val itemView: View = viewHolder.itemView
        val itemHeight: Int = itemView.height
        val isCancelled = dX == 0f && !isCurrentlyActive
        if (isCancelled) {
            clearCanvas(
                canvas,
                itemView.right + dX,
                itemView.top.toFloat(),
                itemView.right.toFloat(),
                itemView.bottom.toFloat()
            )
            super.onChildDraw(
                canvas,
                recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive
            )
            return
        }

        mBackground.color = backgroundColor
        mBackground.setBounds(
            itemView.right + dX.toInt(),
            itemView.top,
            itemView.right,
            itemView.bottom
        )
        mBackground.draw(canvas)
        val deleteIconTop: Int = itemView.top + (itemHeight - intrinsicHeight) / 2
        val deleteIconMargin = (itemHeight - intrinsicHeight) / 2
        val deleteIconLeft: Int = itemView.right - deleteIconMargin - intrinsicWidth
        val deleteIconRight: Int = itemView.right - deleteIconMargin
        val deleteIconBottom = deleteIconTop + intrinsicHeight
        deleteDrawable.setBounds(
            deleteIconLeft,
            deleteIconTop,
            deleteIconRight,
            deleteIconBottom
        )
        deleteDrawable.draw(canvas)
        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    private fun clearCanvas(c: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        c.drawRect(left, top, right, bottom, mClearPaint)
    }

    override fun getSwipeThreshold(@NonNull viewHolder: RecyclerView.ViewHolder): Float {
        return 0.5f
    }
}
