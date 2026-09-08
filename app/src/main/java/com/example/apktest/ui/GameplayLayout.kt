package com.example.apktest.ui

import android.app.Activity
import android.content.res.Configuration
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import android.widget.ToggleButton
import android.widget.ScrollView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.apktest.R
import kotlin.math.ceil

object GameplayLayout {
    fun bind(activity: Activity, root: View) {
        require(root is ConstraintLayout)
        if (root.getTag(R.id.gameplayLayoutBinding) != null) return
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val binding = Binding(root)
        root.setTag(R.id.gameplayLayoutBinding, binding)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            binding.reflow()
            insets
        }
        binding.attach()
        ViewCompat.requestApplyInsets(root)
    }

    private class Binding(private val root: ConstraintLayout) :
        ViewTreeObserver.OnGlobalLayoutListener, View.OnAttachStateChangeListener {
        private val header = root.findViewById<View>(R.id.gameHeader)
        private val headerViewport = root.findViewById<ScrollView>(R.id.gameHeaderViewport)
        private val controls = root.findViewById<View>(R.id.bottomControls)
        private val hint = root.findViewById<TextView>(R.id.controlHint)
        private val buttonIds = intArrayOf(
            R.id.buttonInertia, R.id.buttonUp, R.id.buttonAuto,
            R.id.buttonLeft, R.id.buttonDown, R.id.buttonRight
        )
        private val buttons = buttonIds.map { root.findViewById<View>(it) }
        private val toggles = buttons.filterIsInstance<ToggleButton>()
        private var configuration: Configuration? = null
        private var lastPlan: GameplayLayoutPlan? = null
        private var lastDimensions: GameplayLayoutDimensions? = null

        fun attach() {
            root.addOnAttachStateChangeListener(this)
            root.viewTreeObserver.addOnGlobalLayoutListener(this)
            reflow()
        }

        override fun onViewAttachedToWindow(view: View) {
            root.viewTreeObserver.removeOnGlobalLayoutListener(this)
            root.viewTreeObserver.addOnGlobalLayoutListener(this)
            ViewCompat.requestApplyInsets(root)
        }

        override fun onViewDetachedFromWindow(view: View) {
            root.viewTreeObserver.removeOnGlobalLayoutListener(this)
        }

        override fun onGlobalLayout() = reflow()

        fun reflow() {
            refreshConfiguration()
            if (root.width == 0 || root.height == 0) return
            val dimensions = readDimensions()
            val area = GameplayLayoutPolicy.safeArea(
                root.width, root.height,
                root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom
            )
            val hintHeight = if (hint.visibility == View.GONE) {
                0
            } else {
                hint.measuredHeight + dimensions.rowGap
            }
            val plan = GameplayLayoutPolicy.calculate(area, header.height, hintHeight, dimensions)
            if (plan == lastPlan && dimensions == lastDimensions) return
            lastPlan = plan
            lastDimensions = dimensions
            buttons.forEach { button ->
                val params = button.layoutParams as ViewGroup.MarginLayoutParams
                params.width = plan.buttonWidth
                params.height = plan.buttonHeight
                params.marginStart = if (button.id == R.id.buttonInertia ||
                    button.id == R.id.buttonLeft
                ) 0 else dimensions.buttonGap
                button.layoutParams = params
            }
            applyConstraints(plan, dimensions.margin)
        }

        private fun readDimensions(): GameplayLayoutDimensions {
            val textPadding = size(R.dimen.game_control_text_padding) * 2
            val labelWidth = toggles.maxOf { button ->
                ceil(maxOf(
                    button.paint.measureText(button.textOn.toString()),
                    button.paint.measureText(button.textOff.toString()),
                    button.paint.measureText(button.text.toString())
                )).toInt() + textPadding
            }
            val labelHeight = toggles.maxOf { button ->
                val metrics = button.paint.fontMetricsInt
                val lineHeight = maxOf(ceil(button.paint.fontSpacing).toInt(),
                    metrics.descent - metrics.ascent + metrics.leading)
                lineHeight * button.maxLines + textPadding
            }
            val primary = root.findViewById<TextView>(R.id.adventureStatusBar)
                ?: root.findViewById<TextView>(R.id.gameplayTitle)
            return GameplayLayoutDimensions(
                touchTarget = size(R.dimen.game_touch_target_min),
                buttonWidth = maxOf(size(R.dimen.maze_dpad_button_width), labelWidth),
                buttonHeight = maxOf(size(R.dimen.maze_dpad_button_height), labelHeight),
                buttonGap = size(R.dimen.maze_dpad_button_gap),
                rowGap = size(R.dimen.game_control_row_gap),
                controlsPadding = size(R.dimen.maze_dpad_container_padding),
                margin = size(R.dimen.game_content_margin),
                shortBodyHeight = size(R.dimen.game_short_body_height),
                mazeMinWidth = size(R.dimen.game_maze_min_width),
                mazeMinHeight = size(R.dimen.game_maze_min_height),
                minimumButtonHeight = labelHeight,
                headerMinHeight = maxOf(size(R.dimen.maze_menu_button_size), primary.height) +
                    size(R.dimen.game_header_padding) * 2
            )
        }

        private fun refreshConfiguration() {
            val current = root.resources.configuration
            if (configuration == current) return
            configuration = Configuration(current)
            lastPlan = null
            headerViewport.scrollTo(0, 0)
            header.minimumHeight = size(R.dimen.game_header_min_height)
            val headerPadding = size(R.dimen.game_header_padding)
            header.setPadding(headerPadding, headerPadding, headerPadding, headerPadding)
            val menu = root.findViewById<View>(R.id.buttonMenu)
            menu.layoutParams = menu.layoutParams.apply {
                width = size(R.dimen.maze_menu_button_size)
                height = width
            }
            val menuPadding = size(R.dimen.maze_menu_button_padding)
            menu.setPadding(menuPadding, menuPadding, menuPadding, menuPadding)
            val padding = size(R.dimen.maze_dpad_container_padding)
            controls.setPadding(padding, padding, padding, padding)
            buttons.forEach { button ->
                button.minimumWidth = size(R.dimen.game_touch_target_min)
                button.minimumHeight = size(R.dimen.game_touch_target_min)
                val buttonPadding = size(if (button is ToggleButton) {
                    R.dimen.game_control_text_padding
                } else {
                    R.dimen.game_control_icon_padding
                })
                button.setPadding(buttonPadding, buttonPadding, buttonPadding, buttonPadding)
                if (button is TextView) {
                    button.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        root.resources.getDimension(R.dimen.maze_dpad_button_text_size))
                }
            }
            setTextSize(R.id.gameplayTitle, R.dimen.game_header_title_text_size)
            setTextSize(R.id.adventureStatusBar, R.dimen.game_header_title_text_size)
            setTextSize(R.id.adventureStats, R.dimen.game_header_secondary_text_size)
            setTextSize(R.id.adventureStreak, R.dimen.game_header_secondary_text_size)
            setTextSize(R.id.adventurePerkStatusBar, R.dimen.adventure_perk_summary_text_size)
            setTextSize(R.id.controlHint, R.dimen.game_control_hint_text_size)
            setTopMargin(R.id.controlBottomRow, R.dimen.game_control_row_gap)
            setTopMargin(R.id.controlHint, R.dimen.game_control_row_gap)
            setTopMargin(R.id.adventurePerkStatusBar, R.dimen.adventure_perk_summary_padding)
        }

        private fun setTextSize(id: Int, dimension: Int) {
            root.findViewById<TextView>(id)?.setTextSize(
                TypedValue.COMPLEX_UNIT_PX, root.resources.getDimension(dimension)
            )
        }

        private fun setTopMargin(id: Int, dimension: Int) {
            val view = root.findViewById<View>(id) ?: return
            view.layoutParams = (view.layoutParams as ViewGroup.MarginLayoutParams).apply {
                topMargin = size(dimension)
            }
        }

        private fun applyConstraints(plan: GameplayLayoutPlan, margin: Int) {
            // Only reconnect the existing host; reinflating on rotation would replace its GL surface.
            val set = ConstraintSet()
            set.clone(root)
            val host = R.id.fragmentGameHost
            val panel = R.id.bottomControls
            val headerId = R.id.gameHeaderViewport
            val parent = ConstraintSet.PARENT_ID
            for (id in intArrayOf(host, panel)) {
                set.clear(id, ConstraintSet.TOP)
                set.clear(id, ConstraintSet.BOTTOM)
                set.clear(id, ConstraintSet.START)
                set.clear(id, ConstraintSet.END)
            }
            set.constrainWidth(panel, plan.controlsWidth)
            set.constrainHeight(panel, ConstraintSet.WRAP_CONTENT)
            set.constrainHeight(headerId, plan.headerViewportHeight)
            set.clear(headerId, ConstraintSet.END)
            if (plan.controlsAlongsideHeader) {
                set.connect(headerId, ConstraintSet.END, panel, ConstraintSet.START, margin)
            } else {
                set.connect(headerId, ConstraintSet.END, parent, ConstraintSet.END)
            }
            set.connect(host, ConstraintSet.TOP, headerId, ConstraintSet.BOTTOM, margin)
            set.connect(host, ConstraintSet.START, parent, ConstraintSet.START, margin)
            set.connect(panel, ConstraintSet.END, parent, ConstraintSet.END, margin)
            set.connect(panel, ConstraintSet.BOTTOM, parent, ConstraintSet.BOTTOM, margin)
            if (plan.sideBySide) {
                set.connect(host, ConstraintSet.END, panel, ConstraintSet.START, margin)
                set.connect(host, ConstraintSet.BOTTOM, parent, ConstraintSet.BOTTOM, margin)
                if (plan.controlsAlongsideHeader) {
                    set.connect(panel, ConstraintSet.TOP, parent, ConstraintSet.TOP, margin)
                } else {
                    set.connect(panel, ConstraintSet.TOP, headerId, ConstraintSet.BOTTOM, margin)
                }
            } else {
                set.connect(host, ConstraintSet.END, parent, ConstraintSet.END, margin)
                set.connect(host, ConstraintSet.BOTTOM, panel, ConstraintSet.TOP, margin)
                set.connect(panel, ConstraintSet.START, parent, ConstraintSet.START, margin)
            }
            set.applyTo(root)
        }

        private fun size(dimension: Int): Int = root.resources.getDimensionPixelSize(dimension)
    }
}
