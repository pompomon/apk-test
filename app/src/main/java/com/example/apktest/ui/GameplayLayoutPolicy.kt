package com.example.apktest.ui

internal data class GameplayLayoutDimensions(
    val touchTarget: Int,
    val buttonWidth: Int,
    val buttonHeight: Int,
    val buttonGap: Int,
    val rowGap: Int,
    val controlsPadding: Int,
    val margin: Int,
    val shortBodyHeight: Int,
    val mazeMinWidth: Int,
    val mazeMinHeight: Int,
    val minimumButtonHeight: Int = touchTarget,
    val headerMinHeight: Int = touchTarget
)

internal data class GameplayLayoutPlan(
    val sideBySide: Boolean,
    val buttonWidth: Int,
    val buttonHeight: Int,
    val controlsWidth: Int,
    val controlsAlongsideHeader: Boolean,
    val headerViewportHeight: Int
)

internal data class GameplaySafeArea(val width: Int, val height: Int)

internal object GameplayLayoutPolicy {
    private const val COLUMNS = 3
    private const val ROWS = 2

    fun safeArea(
        width: Int,
        height: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) = GameplaySafeArea(
        (width - left.coerceAtLeast(0) - right.coerceAtLeast(0)).coerceAtLeast(0),
        (height - top.coerceAtLeast(0) - bottom.coerceAtLeast(0)).coerceAtLeast(0)
    )

    fun calculate(
        area: GameplaySafeArea,
        headerHeight: Int,
        hintHeight: Int,
        dimensions: GameplayLayoutDimensions
    ): GameplayLayoutPlan = with(dimensions) {
        val bodyHeight = (area.height - headerHeight).coerceAtLeast(0)
        val horizontalChrome = controlsPadding * 2 + buttonGap * (COLUMNS - 1)
        val minimumControlsWidth = touchTarget * COLUMNS + horizontalChrome
        // Large localized labels must wrap, not squeeze the HUD and maze into a thin column.
        val sideMazeWidth = maxOf(mazeMinWidth, (area.width + 1) / 2)
        val sideBySide = area.width > area.height &&
            bodyHeight <= shortBodyHeight &&
            area.width >= sideMazeWidth + minimumControlsWidth + margin * 3
        val controlsWidthBudget = if (sideBySide) {
            area.width - sideMazeWidth - margin * 3
        } else {
            area.width - margin * 2
        }
        val fittedWidth = ((controlsWidthBudget - horizontalChrome) / COLUMNS)
            .coerceAtLeast(touchTarget)
            .coerceAtMost(buttonWidth.coerceAtLeast(touchTarget))
        val verticalChrome = controlsPadding * 2 + rowGap + hintHeight.coerceAtLeast(0)
        val minimumHeight = maxOf(touchTarget, minimumButtonHeight)
        val controlsAlongsideHeader = sideBySide &&
            minimumHeight * ROWS + verticalChrome + margin * 2 > bodyHeight
        val controlsHeightBudget = if (controlsAlongsideHeader) {
            area.height - margin * 2
        } else if (sideBySide) {
            bodyHeight - margin * 2
        } else {
            bodyHeight - mazeMinHeight - margin * 3
        }
        val fittedHeight = ((controlsHeightBudget - verticalChrome) / ROWS)
            .coerceAtLeast(minimumHeight)
            .coerceAtMost(buttonHeight.coerceAtLeast(minimumHeight))
        val controlsHeight = fittedHeight * ROWS + verticalChrome
        val headerHeightBudget = when {
            controlsAlongsideHeader -> area.height - mazeMinHeight - margin * 2
            sideBySide -> area.height - maxOf(mazeMinHeight, controlsHeight) - margin * 2
            else -> area.height - controlsHeight - mazeMinHeight - margin * 3
        }
        val maximumHeaderHeight = (if (sideBySide) {
            area.height - touchTarget - margin * 2
        } else {
            area.height - controlsHeight - touchTarget - margin * 3
        }).coerceAtLeast(0)
        val minimumHeaderHeight = headerMinHeight.coerceAtMost(maximumHeaderHeight)
        GameplayLayoutPlan(
            sideBySide = sideBySide,
            buttonWidth = fittedWidth,
            buttonHeight = fittedHeight,
            controlsWidth = fittedWidth * COLUMNS + horizontalChrome,
            controlsAlongsideHeader = controlsAlongsideHeader,
            headerViewportHeight = headerHeight.coerceAtMost(
                headerHeightBudget.coerceAtLeast(minimumHeaderHeight)
            ).coerceAtMost(maximumHeaderHeight)
        )
    }
}
