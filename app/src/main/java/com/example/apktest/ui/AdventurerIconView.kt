package com.example.apktest.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.SparseIntArray
import android.view.View
import com.example.apktest.game.render.AdventurerIcons

/** Square Android view matching the in-game Adventurer sprite. */
class AdventurerIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(255, 12, 12, 20)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val pattern = AdventurerIcons.pattern()
    private val rows = pattern.size
    private val cols = if (pattern.isNotEmpty()) pattern[0].length else 0
    private val colors = SparseIntArray(AdventurerIcons.colors().size).apply {
        AdventurerIcons.colors().forEach { (character, color) -> put(character.code, color) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val side = minOf(measuredWidth, measuredHeight)
        setMeasuredDimension(side, side)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (rows == 0 || cols == 0) return

        val size = minOf(width, height).toFloat()
        val padding = size * OUTLINE_PADDING_FRACTION
        val inner = size - 2f * padding
        val pixelWidth = inner / cols
        val pixelHeight = inner / rows
        val originX = (width - size) / 2f + padding
        val originY = (height - size) / 2f + padding
        canvas.drawRect(
            originX - padding,
            originY - padding,
            originX + inner + padding,
            originY + inner + padding,
            outlinePaint
        )

        for (row in 0 until rows) {
            val rowPattern = pattern[row]
            for (col in 0 until cols) {
                val character = rowPattern[col]
                if (character == ' ' || character == '0') continue
                val color = colors.get(character.code, TRANSPARENT)
                if (color == TRANSPARENT) continue
                fillPaint.color = color
                val left = originX + col * pixelWidth
                val top = originY + row * pixelHeight
                canvas.drawRect(
                    left,
                    top,
                    left + pixelWidth,
                    top + pixelHeight,
                    fillPaint
                )
            }
        }
    }

    companion object {
        private const val OUTLINE_PADDING_FRACTION = 0.08f
        private const val TRANSPARENT = 0
    }
}
