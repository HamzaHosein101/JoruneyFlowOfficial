package com.example.travelpractice.reviews

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class SpacesItemDecoration(private val spacePx: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val pos = parent.getChildAdapterPosition(view)
        outRect.left = 0; outRect.right = 0
        outRect.bottom = spacePx
        if (pos == 0) outRect.top = spacePx
    }
}

