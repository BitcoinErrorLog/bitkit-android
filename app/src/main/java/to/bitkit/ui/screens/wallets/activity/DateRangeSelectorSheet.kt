package to.bitkit.ui.screens.wallets.activity

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import to.bitkit.R
import to.bitkit.ui.activityListViewModel
import to.bitkit.ui.appViewModel
import to.bitkit.ui.components.BodyMSB
import to.bitkit.ui.components.BottomSheetPreview
import to.bitkit.ui.components.Caption13Up
import to.bitkit.ui.components.FillHeight
import to.bitkit.ui.components.PrimaryButton
import to.bitkit.ui.components.SecondaryButton
import to.bitkit.ui.components.SheetSize
import to.bitkit.ui.components.VerticalSpacer
import to.bitkit.ui.scaffold.SheetTopBar
import to.bitkit.ui.shared.modifiers.sheetHeight
import to.bitkit.ui.shared.util.gradientBackground
import to.bitkit.ui.theme.AppThemeSurface
import to.bitkit.ui.theme.Colors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.days

private object CalendarConstants {

    // Calendar grid
    const val DAYS_IN_WEEK = 7
    const val FIRST_DAY_OF_MONTH = 1

    // Date formatting
    const val MONTH_YEAR_FORMAT = "MMMM yyyy"
    const val DATE_FORMAT = "MMM d, yyyy"
    const val WEEKDAY_FORMAT = "EEE"
    const val WEEKDAY_ABBREVIATION_LENGTH = 3

    // Animation
    const val SWIPE_THRESHOLD_DP = 100
    const val FADE_DURATION_MILLIS = 200
    const val SLIDE_OFFSET_PX = 1000f
    const val INITIAL_ALPHA = 1f
    const val TRANSPARENT_ALPHA = 0f
    const val INITIAL_OFFSET = 0f

    // Month navigation
    const val NEXT_MONTH = 1
    const val PREVIOUS_MONTH = -1

    // Day view styling
    const val DAY_ASPECT_RATIO = 1f
    const val ROUNDED_CORNER_PERCENT = 50
    const val NO_CORNER_RADIUS = 0
    const val TODAY_INDICATOR_ALPHA = 0.1f

    // Calendar math
    const val DAYS_IN_WEEK_MOD = 7
    const val CALENDAR_WEEK_OFFSET = 1
    const val FIRST_MONTH_INDEX = 1
    const val MONTH_INDEX_OFFSET = 1

    // Leap year calculation
    const val LEAP_YEAR_DIVISOR_4 = 4
    const val LEAP_YEAR_DIVISOR_100 = 100
    const val LEAP_YEAR_DIVISOR_400 = 400

    // Preview
    const val PREVIEW_DAYS_AGO = 7
}

@Composable
fun DateRangeSelectorSheet() {
    val activity = activityListViewModel ?: return
    val app = appViewModel ?: return

    val startDate by activity.startDate.collectAsState()
    val endDate by activity.endDate.collectAsState()

    Content(
        initialStartDate = startDate,
        initialEndDate = endDate,
        onClearClick = {
            activity.clearDateRange()
            app.hideSheet()
        },
        onApplyClick = { start, end ->
            activity.setDateRange(
                startDate = start,
                endDate = end,
            )
            app.hideSheet()
        },
    )
}

@Composable
private fun Content(
    initialStartDate: Long? = null,
    initialEndDate: Long? = null,
    onClearClick: () -> Unit = {},
    onApplyClick: (Long?, Long?) -> Unit = { _, _ -> },
) {
    var displayedMonth by remember {
        mutableStateOf(
            initialStartDate?.let {
                Instant.fromEpochMilliseconds(it)
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .date
            } ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        )
    }

    var startDate by remember { mutableStateOf(initialStartDate) }
    var endDate by remember { mutableStateOf(initialEndDate) }

    val hasSelection = startDate != null

    var calendar = remember { Calendar.getInstance() }

    // Swipe animation state
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(CalendarConstants.INITIAL_OFFSET) }
    val density = LocalDensity.current
    var dragOffset by remember { mutableFloatStateOf(CalendarConstants.INITIAL_OFFSET) }
    val swipeThreshold = with(density) { CalendarConstants.SWIPE_THRESHOLD_DP.dp.toPx() }

    // Animation for month transition
    var isAnimating by remember { mutableStateOf(false) }
    val monthAlpha = remember { Animatable(CalendarConstants.INITIAL_ALPHA) }

    fun navigateMonth(direction: Int) {
        if (isAnimating) return

        scope.launch {
            isAnimating = true

            // Fade out and slide
            launch {
                monthAlpha.animateTo(
                    targetValue = CalendarConstants.TRANSPARENT_ALPHA,
                    animationSpec = tween(durationMillis = CalendarConstants.FADE_DURATION_MILLIS)
                )
            }

            launch {
                offsetX.animateTo(
                    targetValue = if (direction > 0) -CalendarConstants.SLIDE_OFFSET_PX else CalendarConstants.SLIDE_OFFSET_PX,
                    animationSpec = tween(durationMillis = CalendarConstants.FADE_DURATION_MILLIS)
                )
            }

            // Wait for animation to complete
            offsetX.snapTo(if (direction > 0) CalendarConstants.SLIDE_OFFSET_PX else -CalendarConstants.SLIDE_OFFSET_PX)

            // Update month
            displayedMonth = if (direction > 0) {
                displayedMonth.plusMonths(CalendarConstants.NEXT_MONTH)
            } else {
                displayedMonth.minusMonths(CalendarConstants.NEXT_MONTH)
            }

            // Fade in and slide back
            launch {
                monthAlpha.animateTo(
                    targetValue = CalendarConstants.INITIAL_ALPHA,
                    animationSpec = tween(durationMillis = CalendarConstants.FADE_DURATION_MILLIS)
                )
            }

            launch {
                offsetX.animateTo(
                    targetValue = CalendarConstants.INITIAL_OFFSET,
                    animationSpec = tween(durationMillis = CalendarConstants.FADE_DURATION_MILLIS)
                )
            }

            isAnimating = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .sheetHeight(SheetSize.CALENDAR)
            .gradientBackground()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        SheetTopBar(stringResource(R.string.wallet__filter_title))

        VerticalSpacer(10.dp)

        // Month navigation header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BodyMSB(
                text = displayedMonth.toMonthYearString(),
                color = Color.White
            )

            Row {
                IconButton(
                    onClick = {
                        navigateMonth(CalendarConstants.PREVIOUS_MONTH)
                    },
                    modifier = Modifier.testTag("PrevMonth")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous month",
                        tint = Colors.Brand
                    )
                }

                IconButton(
                    onClick = {
                        navigateMonth(CalendarConstants.NEXT_MONTH)
                    },
                    modifier = Modifier.testTag("NextMonth")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next month",
                        tint = Colors.Brand
                    )
                }
            }
        }

        // Weekday headers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            calendar.firstDayOfWeek = Calendar.SUNDAY
            val weekdaySymbols = SimpleDateFormat(CalendarConstants.WEEKDAY_FORMAT, Locale.getDefault()).apply {
                calendar = Calendar.getInstance()
            }

            for (i in 0 until CalendarConstants.DAYS_IN_WEEK) {
                val dayOfWeek =
                    (calendar.firstDayOfWeek + i - CalendarConstants.CALENDAR_WEEK_OFFSET) % CalendarConstants.DAYS_IN_WEEK_MOD + CalendarConstants.CALENDAR_WEEK_OFFSET
                calendar.set(Calendar.DAY_OF_WEEK, dayOfWeek)
                Caption13Up(
                    text = weekdaySymbols.format(calendar.time).take(CalendarConstants.WEEKDAY_ABBREVIATION_LENGTH)
                        .uppercase(),
                    color = Colors.White64,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        VerticalSpacer(8.dp)

        // Calendar grid with swipe gesture
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragOffset = CalendarConstants.INITIAL_OFFSET },
                        onDragEnd = {
                            scope.launch {
                                if (abs(dragOffset) > swipeThreshold) {
                                    // Trigger month change
                                    val direction =
                                        if (dragOffset < 0) CalendarConstants.NEXT_MONTH else CalendarConstants.PREVIOUS_MONTH
                                    navigateMonth(direction)
                                } else {
                                    // Spring back to original position
                                    offsetX.animateTo(
                                        targetValue = CalendarConstants.INITIAL_OFFSET,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                                }
                                dragOffset = CalendarConstants.INITIAL_OFFSET
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(
                                    targetValue = CalendarConstants.INITIAL_OFFSET,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                )
                                dragOffset = CalendarConstants.INITIAL_OFFSET
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (!isAnimating) {
                                dragOffset += dragAmount
                                scope.launch {
                                    offsetX.snapTo(offsetX.value + dragAmount)
                                }
                            }
                        }
                    )
                }
        ) {
            CalendarGrid(
                displayedMonth = displayedMonth,
                startDate = startDate,
                endDate = endDate,
                onDateSelected = { selectedDate ->
                    val selectedMillis = selectedDate
                        .atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

                    when (startDate) {
                        null -> {
                            // First selection
                            startDate = selectedMillis
                            endDate = selectedMillis
                        }

                        endDate -> {
                            // Second selection - create range
                            if (selectedMillis < startDate!!) {
                                endDate = startDate
                                startDate = selectedMillis
                            } else if (selectedMillis == startDate) {
                                // Same date clicked - do nothing
                                return@CalendarGrid
                            } else {
                                endDate = selectedMillis
                            }
                        }

                        else -> {
                            // Third selection - start new range
                            startDate = selectedMillis
                            endDate = selectedMillis
                        }
                    }
                },
                modifier = Modifier
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .graphicsLayer {
                        alpha = monthAlpha.value
                    }
            )
        }

        FillHeight()

        // Display selected range
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (startDate != null) {
                val startLocalDate = Instant.fromEpochMilliseconds(startDate!!)
                    .toLocalDateTime(TimeZone.currentSystemDefault()).date
                val endLocalDate = endDate?.let {
                    Instant.fromEpochMilliseconds(it)
                        .toLocalDateTime(TimeZone.currentSystemDefault()).date
                }

                BodyMSB(
                    text = if (endLocalDate != null && startLocalDate != endLocalDate) {
                        "${startLocalDate.toFormattedString()} - ${endLocalDate.toFormattedString()}"
                    } else {
                        startLocalDate.toFormattedString()
                    },
                    color = Color.White
                )
            }
        }

        VerticalSpacer(36.dp)

        // Action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            SecondaryButton(
                onClick = {
                    startDate = null
                    endDate = null
                    onClearClick()
                },
                text = stringResource(R.string.wallet__filter_clear),
                enabled = hasSelection,
                modifier = Modifier
                    .weight(1f)
                    .testTag("CalendarClearButton")
            )
            PrimaryButton(
                onClick = {
                    onApplyClick(startDate, endDate)
                },
                text = stringResource(R.string.wallet__filter_apply),
                enabled = hasSelection,
                modifier = Modifier
                    .weight(1f)
                    .testTag("CalendarApplyButton")
            )
        }
    }
}

@Composable
private fun CalendarGrid(
    displayedMonth: LocalDate,
    startDate: Long?,
    endDate: Long?,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val daysInMonth = remember(displayedMonth) {
        getDaysInMonth(displayedMonth)
    }

    val today = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        daysInMonth.chunked(CalendarConstants.DAYS_IN_WEEK).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                week.forEach { date ->
                    if (date != null) {
                        val dateMillis = date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
                        val isSelected = isDateInRange(dateMillis, startDate, endDate)
                        val isStartDate = dateMillis == startDate
                        val isEndDate = dateMillis == endDate
                        val isToday = date == today

                        CalendarDayView(
                            date = date,
                            isSelected = isSelected,
                            isStartDate = isStartDate,
                            isEndDate = isEndDate,
                            isToday = isToday,
                            onClick = { onDateSelected(date) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        // Empty space for days outside the month
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayView(
    date: LocalDate,
    isSelected: Boolean,
    isStartDate: Boolean,
    isEndDate: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(CalendarConstants.DAY_ASPECT_RATIO)
            .clickable(onClick = onClick)
            .testTag(if (isToday) "Today" else "Day-${date.dayOfMonth}"),
        contentAlignment = Alignment.Center
    ) {
        // Background for range selection
        if (isSelected) {
            when {
                isStartDate && isEndDate -> {
                    // Single day or same start/end
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(CircleShape)
                            .background(Colors.Brand16)
                    )
                }

                isStartDate -> {
                    // Start of range
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(
                                RoundedCornerShape(
                                    topStartPercent = CalendarConstants.ROUNDED_CORNER_PERCENT,
                                    bottomStartPercent = CalendarConstants.ROUNDED_CORNER_PERCENT,
                                    topEndPercent = CalendarConstants.NO_CORNER_RADIUS,
                                    bottomEndPercent = CalendarConstants.NO_CORNER_RADIUS,
                                )
                            )
                            .background(Colors.Brand16)
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(CircleShape)
                            .background(Colors.Brand16)
                    )
                }

                isEndDate -> {
                    // End of range
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(
                                RoundedCornerShape(
                                    topStartPercent = CalendarConstants.NO_CORNER_RADIUS,
                                    bottomStartPercent = CalendarConstants.NO_CORNER_RADIUS,
                                    topEndPercent = CalendarConstants.ROUNDED_CORNER_PERCENT,
                                    bottomEndPercent = CalendarConstants.ROUNDED_CORNER_PERCENT,
                                )
                            )
                            .background(Colors.Brand16)
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(CircleShape)
                            .background(Colors.Brand16)
                    )
                }

                else -> {
                    // Middle of range
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Colors.Brand16)
                    )
                }
            }
        }

        // Today indicator (if not selected)
        if (isToday && !isSelected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = CalendarConstants.TODAY_INDICATOR_ALPHA))
            )
        }

        // Day number
        BodyMSB(
            text = date.dayOfMonth.toString(),
            color = if (isStartDate || isEndDate) Colors.Brand else Color.White
        )
    }
}

// Helper functions
private fun getDaysInMonth(month: LocalDate): List<LocalDate?> {
    val firstDayOfMonth = LocalDate(month.year, month.month, CalendarConstants.FIRST_DAY_OF_MONTH)
    val daysInMonth = month.month.length(isLeapYear(month.year))

    // Get the day of week for the first day (1 = Monday, 7 = Sunday)
    val firstDayOfWeek = firstDayOfMonth.dayOfWeek.ordinal + CalendarConstants.CALENDAR_WEEK_OFFSET

    // Calculate offset (days before the first day)
    // We want Sunday to be 0, so adjust accordingly
    val offset = (firstDayOfWeek % CalendarConstants.DAYS_IN_WEEK_MOD)

    val days = mutableListOf<LocalDate?>()

    // Add empty spaces before the first day
    repeat(offset) {
        days.add(null)
    }

    // Add all days of the month
    for (day in CalendarConstants.FIRST_DAY_OF_MONTH..daysInMonth) {
        days.add(LocalDate(month.year, month.month, day))
    }

    // Add empty spaces to complete the last week (total should be multiple of 7)
    while (days.size % CalendarConstants.DAYS_IN_WEEK_MOD != 0) {
        days.add(null)
    }

    return days
}

private fun isLeapYear(year: Int): Boolean {
    return (year % CalendarConstants.LEAP_YEAR_DIVISOR_4 == 0 && year % CalendarConstants.LEAP_YEAR_DIVISOR_100 != 0) ||
        (year % CalendarConstants.LEAP_YEAR_DIVISOR_400 == 0)
}

private fun isDateInRange(dateMillis: Long, startMillis: Long?, endMillis: Long?): Boolean {
    if (startMillis == null) return false
    val end = endMillis ?: startMillis

    val normalizedDate = Instant.fromEpochMilliseconds(dateMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    val normalizedStart = Instant.fromEpochMilliseconds(startMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    val normalizedEnd = Instant.fromEpochMilliseconds(end)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date

    return normalizedDate >= normalizedStart && normalizedDate <= normalizedEnd
}

private fun LocalDate.toMonthYearString(): String {
    val formatter = SimpleDateFormat(CalendarConstants.MONTH_YEAR_FORMAT, Locale.getDefault())
    val calendar = Calendar.getInstance()
    calendar.set(year, monthNumber - CalendarConstants.MONTH_INDEX_OFFSET, CalendarConstants.FIRST_DAY_OF_MONTH)
    return formatter.format(calendar.time)
}

private fun LocalDate.toFormattedString(): String {
    val formatter = SimpleDateFormat(CalendarConstants.DATE_FORMAT, Locale.getDefault())
    val calendar = Calendar.getInstance()
    calendar.set(year, monthNumber - CalendarConstants.MONTH_INDEX_OFFSET, dayOfMonth)
    return formatter.format(calendar.time)
}

private fun LocalDate.minusMonths(months: Int): LocalDate {
    val calendar = Calendar.getInstance()
    calendar.set(year, monthNumber - CalendarConstants.MONTH_INDEX_OFFSET, dayOfMonth)
    calendar.add(Calendar.MONTH, -months)
    return LocalDate(
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + CalendarConstants.MONTH_INDEX_OFFSET,
        CalendarConstants.FIRST_DAY_OF_MONTH // Always use first day of month for display
    )
}

private fun LocalDate.plusMonths(months: Int): LocalDate {
    val calendar = Calendar.getInstance()
    calendar.set(year, monthNumber - CalendarConstants.MONTH_INDEX_OFFSET, dayOfMonth)
    calendar.add(Calendar.MONTH, months)
    return LocalDate(
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + CalendarConstants.MONTH_INDEX_OFFSET,
        CalendarConstants.FIRST_DAY_OF_MONTH // Always use first day of month for display
    )
}

@Preview(showSystemUi = true)
@Composable
private fun PreviewEmpty() {
    AppThemeSurface {
        BottomSheetPreview {
            Content()
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun PreviewWithSelection() {
    AppThemeSurface {
        BottomSheetPreview {
            Content(
                initialStartDate = Clock.System.now()
                    .minus(CalendarConstants.PREVIEW_DAYS_AGO.days)
                    .toEpochMilliseconds(),
                initialEndDate = Clock.System.now().toEpochMilliseconds(),
            )
        }
    }
}
