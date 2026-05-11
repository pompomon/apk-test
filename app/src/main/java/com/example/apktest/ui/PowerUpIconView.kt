package com.example.apktest.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.example.apktest.game.core.PowerUpType
import com.example.apktest.game.render.PowerUpIcons

/**
 * Square Android view that renders the pixel-art icon for a [PowerUpType] using
 * the patterns from [PowerUpIcons]. Used by the legend dialog so the legend
 * always matches what the player sees in-game.
 */
class PowerUpIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(255, 12, 12, 20)
    }

    private var type: PowerUpType = PowerUpType.entries.first()

    fun setPowerUpType(type: PowerUpType) {
        if (this.type == type) return
        this.type = type
        fillPaint.color = PowerUpIcons.androidColorFor(type)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        // Force square.
        val side = minOf(measuredWidth, measuredHeight)
        setMeasuredDimension(side, side)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pattern = PowerUpIcons.patternFor(type)
        val rows = pattern.size
        if (rows == 0) return
        val cols = pattern[0].length
        if (cols == 0) return

        val size = minOf(width, height).toFloat()
        val padding = size * OUTLINE_PADDING_FRACTION
        val inner = size - 2f * padding
        val pixelW = inner / cols
        val pixelH = inner / rows
        val originX = (width - size) / 2f + padding
        val originY = (height - size) / 2f + padding

        // Dark backdrop matches the in-game outline rectangle.
        canvas.drawRect(
            originX - padding,
            originY - padding,
            originX + inner + padding,
            originY + inner + padding,
            outlinePaint
        )

        fillPaint.color = PowerUpIcons.androidColorFor(type)
        for (row in 0 until rows) {
            val rowStr = pattern[row]
            for (col in 0 until cols) {
                if (rowStr[col] != '1') continue
                val left = originX + col * pixelW
                // Android Canvas has Y-down; pattern rows are listed top-to-bottom
                // so this maps directly without the libGDX flip.
                val top = originY + row * pixelH
                canvas.drawRect(left, top, left + pixelW, top + pixelH, fillPaint)
            }
        }
    }

    companion object {
        private const val OUTLINE_PADDING_FRACTION = 0.08f
    }
}
