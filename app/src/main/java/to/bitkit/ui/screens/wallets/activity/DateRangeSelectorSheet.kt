package to.bitkit.ui.screens.wallets.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import to.bitkit.ui.theme.Typography
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.time.Duration.Companion.days

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
                        displayedMonth = displayedMonth.minusMonths(1)
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
                        displayedMonth = displayedMonth.plusMonths(1)
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
            val weekdaySymbols = SimpleDateFormat("EEE", Locale.getDefault()).apply {
                calendar = Calendar.getInstance()
            }

            for (i in 0 until 7) {
                val dayOfWeek = (calendar.firstDayOfWeek + i - 1) % 7 + 1
                calendar.set(Calendar.DAY_OF_WEEK, dayOfWeek)
                Caption13Up(
                    text = weekdaySymbols.format(calendar.time).take(3).uppercase(),
                    color = Colors.White64,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        VerticalSpacer(8.dp)

        // Calendar grid
        CalendarGrid(
            displayedMonth = displayedMonth,
            startDate = startDate,
            endDate = endDate,
            onDateSelected = { selectedDate ->
                val selectedMillis = selectedDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

                when {
                    startDate == null -> {
                        // First selection
                        startDate = selectedMillis
                        endDate = selectedMillis
                    }

                    startDate == endDate -> {
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
            }
        )

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
) {
    val daysInMonth = remember(displayedMonth) {
        getDaysInMonth(displayedMonth)
    }

    val today = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        daysInMonth.chunked(7).forEach { week ->
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
            .aspectRatio(1f)
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
                                    topStartPercent = 50,
                                    bottomStartPercent = 50,
                                    topEndPercent = 0,
                                    bottomEndPercent = 0,
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
                                    topStartPercent = 0,
                                    bottomStartPercent = 0,
                                    topEndPercent = 50,
                                    bottomEndPercent = 50,
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
                    .background(Color.White.copy(alpha = 0.1f))
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
    val firstDayOfMonth = LocalDate(month.year, month.month, 1)
    val daysInMonth = month.month.length(isLeapYear(month.year))

    // Get the day of week for the first day (1 = Monday, 7 = Sunday)
    val firstDayOfWeek = firstDayOfMonth.dayOfWeek.ordinal + 1 // Convert to 1-7

    // Calculate offset (days before the first day)
    // We want Sunday to be 0, so adjust accordingly
    val offset = (firstDayOfWeek % 7)

    val days = mutableListOf<LocalDate?>()

    // Add empty spaces before the first day
    repeat(offset) {
        days.add(null)
    }

    // Add all days of the month
    for (day in 1..daysInMonth) {
        days.add(LocalDate(month.year, month.month, day))
    }

    // Add empty spaces to complete the last week (total should be multiple of 7)
    while (days.size % 7 != 0) {
        days.add(null)
    }

    return days
}

private fun isLeapYear(year: Int): Boolean {
    return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
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
    val formatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    val calendar = Calendar.getInstance()
    calendar.set(year, monthNumber - 1, 1)
    return formatter.format(calendar.time)
}

private fun LocalDate.toFormattedString(): String {
    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    val calendar = Calendar.getInstance()
    calendar.set(year, monthNumber - 1, dayOfMonth)
    return formatter.format(calendar.time)
}

private fun LocalDate.minusMonths(months: Int): LocalDate {
    val calendar = Calendar.getInstance()
    calendar.set(year, monthNumber - 1, dayOfMonth)
    calendar.add(Calendar.MONTH, -months)
    return LocalDate(
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1,
        1 // Always use first day of month for display
    )
}

private fun LocalDate.plusMonths(months: Int): LocalDate {
    val calendar = Calendar.getInstance()
    calendar.set(year, monthNumber - 1, dayOfMonth)
    calendar.add(Calendar.MONTH, months)
    return LocalDate(
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1,
        1 // Always use first day of month for display
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
                initialStartDate = Clock.System.now().minus(7.days).toEpochMilliseconds(),
                initialEndDate = Clock.System.now().toEpochMilliseconds(),
            )
        }
    }
}
