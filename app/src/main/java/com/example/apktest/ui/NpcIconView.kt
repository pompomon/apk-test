package com.example.apktest.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.SparseIntArray
import android.view.View
import com.example.apktest.game.core.NpcPolicyType
import com.example.apktest.game.render.NpcIcons

/**
 * Square Android view that renders the NPC pixel-art sprite tinted for a given
 * [NpcPolicyType]. Used by the legend dialog so the swatch always matches what
 * the in-game renderer ([com.example.apktest.game.render.MazeRenderer]) draws.
 *
 * Mirrors [PowerUpIconView]'s structure: cached pattern + per-pixel paint
 * lookup so [onDraw] never allocates.
 */
class NpcIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(255, 12, 12, 20)
    }
    // One reusable fill paint; color is reassigned per pixel from the cached
    // palette so we don't keep a Paint per character (only 4 distinct shades
    // anyway).
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var type: NpcPolicyType = NpcPolicyType.entries.first()
    private var cachedPattern: Array<String> = NpcIcons.pattern()
    private var cachedRows: Int = cachedPattern.size
    private var cachedCols: Int = if (cachedPattern.isNotEmpty()) cachedPattern[0].length else 0
    private var cachedColors: SparseIntArray = buildSparse(NpcIcons.androidColorsFor(type))

    fun setNpcPolicyType(type: NpcPolicyType) {
        if (this.type == type) return
        this.type = type
        cachedColors = buildSparse(NpcIcons.androidColorsFor(type))
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val side = minOf(measuredWidth, measuredHeight)
        setMeasuredDimension(side, side)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pattern = cachedPattern
        val rows = cachedRows
        if (rows == 0) return
        val cols = cachedCols
        if (cols == 0) return

        val size = minOf(width, height).toFloat()
        val padding = size * OUTLINE_PADDING_FRACTION
        val inner = size - 2f * padding
        val pixelW = inner / cols
        val pixelH = inner / rows
        val originX = (width - size) / 2f + padding
        val originY = (height - size) / 2f + padding

        canvas.drawRect(
            originX - padding,
            originY - padding,
            originX + inner + padding,
            originY + inner + padding,
            outlinePaint
        )

        val colors = cachedColors
        for (row in 0 until rows) {
            val rowStr = pattern[row]
            for (col in 0 until cols) {
                val ch = rowStr[col]
                if (ch == ' ' || ch == '0') continue
                val argb = colors.get(ch.code, TRANSPARENT)
                if (argb == TRANSPARENT) continue
                fillPaint.color = argb
                val left = originX + col * pixelW
                val top = originY + row * pixelH
                canvas.drawRect(left, top, left + pixelW, top + pixelH, fillPaint)
            }
        }
    }

    private fun buildSparse(map: Map<Char, Int>): SparseIntArray {
        val out = SparseIntArray(map.size)
        map.forEach { (ch, color) -> out.put(ch.code, color) }
        return out
    }

    companion object {
        private const val OUTLINE_PADDING_FRACTION = 0.08f
        private const val TRANSPARENT = 0
    }
}
