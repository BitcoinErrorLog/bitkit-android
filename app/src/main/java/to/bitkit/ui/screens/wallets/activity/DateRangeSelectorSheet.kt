package to.bitkit.ui.screens.wallets.activity

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Clock
import to.bitkit.R
import to.bitkit.ui.activityListViewModel
import to.bitkit.ui.appViewModel
import to.bitkit.ui.components.BottomSheetPreview
import to.bitkit.ui.components.PrimaryButton
import to.bitkit.ui.components.SecondaryButton
import to.bitkit.ui.components.SheetSize
import to.bitkit.ui.shared.modifiers.sheetHeight
import to.bitkit.ui.shared.util.gradientBackground
import to.bitkit.ui.theme.AppThemeSurface
import to.bitkit.ui.theme.Colors
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
    var currentMonthMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedStartDate by remember { mutableLongStateOf(initialStartDate ?: 0L) }
    var selectedEndDate by remember { mutableLongStateOf(initialEndDate ?: 0L) }

    val hasSelection = selectedStartDate != 0L

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .sheetHeight(SheetSize.CALENDAR)
            .gradientBackground()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        // Month header (swipeable)
        Text(
            text = getMonthYearLabel(currentMonthMillis),
            style = MaterialTheme.typography.titleLarge,
            color = Colors.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            textAlign = TextAlign.Center
        )

        // Calendar grid
        MonthCalendarGrid(
            currentMonthMillis = currentMonthMillis,
            selectedStartDate = selectedStartDate,
            selectedEndDate = selectedEndDate,
            onDateClick = { dateMillis ->
                when {
                    selectedStartDate == 0L -> {
                        // First selection
                        selectedStartDate = dateMillis
                        selectedEndDate = 0L
                    }
                    selectedEndDate == 0L -> {
                        // Second selection
                        if (dateMillis < selectedStartDate) {
                            selectedEndDate = selectedStartDate
                            selectedStartDate = dateMillis
                        } else {
                            selectedEndDate = dateMillis
                        }
                    }
                    else -> {
                        // Reset and start new selection
                        selectedStartDate = dateMillis
                        selectedEndDate = 0L
                    }
                }
            },
            onSwipeLeft = {
                currentMonthMillis = adjustMonth(currentMonthMillis, 1)
            },
            onSwipeRight = {
                currentMonthMillis = adjustMonth(currentMonthMillis, -1)
            },
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Selected date display
        if (hasSelection) {
            Text(
                text = formatSelectedDateRange(selectedStartDate, selectedEndDate),
                style = MaterialTheme.typography.bodyLarge,
                color = Colors.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(36.dp))
        }

        // Action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SecondaryButton(
                onClick = {
                    selectedStartDate = 0L
                    selectedEndDate = 0L
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
                    onApplyClick(
                        if (selectedStartDate != 0L) selectedStartDate else null,
                        if (selectedEndDate != 0L) selectedEndDate else null
                    )
                },
                text = stringResource(R.string.wallet__filter_apply),
                enabled = hasSelection,
                modifier = Modifier
                    .weight(1f)
                    .testTag("CalendarApplyButton")
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun MonthCalendarGrid(
    currentMonthMillis: Long,
    selectedStartDate: Long,
    selectedEndDate: Long,
    onDateClick: (Long) -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    modifier: Modifier = Modifier
) {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = currentMonthMillis
        set(Calendar.DAY_OF_MONTH, 1)
    }

    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1 // 0 = Sunday

    Column(
        modifier = modifier
            .pointerInput(Unit) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            totalDrag > 100 -> onSwipeRight() // Swipe right = previous month
                            totalDrag < -100 -> onSwipeLeft() // Swipe left = next month
                        }
                        totalDrag = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        totalDrag += dragAmount
                    }
                )
            }
    ) {
        // Day of week headers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = Colors.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Calendar days grid
        var dayCounter = 1
        val weeks = ((firstDayOfWeek + daysInMonth) / 7.0).toInt() + 1

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(weeks) { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    repeat(7) { dayOfWeek ->
                        val dayIndex = week * 7 + dayOfWeek

                        if (dayIndex < firstDayOfWeek || dayCounter > daysInMonth) {
                            // Empty cell
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                            )
                        } else {
                            val dayCalendar = Calendar.getInstance().apply {
                                timeInMillis = currentMonthMillis
                                set(Calendar.DAY_OF_MONTH, dayCounter)
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            val dateMillis = dayCalendar.timeInMillis

                            DayCell(
                                day = dayCounter,
                                dateMillis = dateMillis,
                                isSelected = dateMillis == selectedStartDate || dateMillis == selectedEndDate,
                                isInRange = selectedEndDate != 0L && dateMillis in selectedStartDate..selectedEndDate,
                                isRangeStart = dateMillis == selectedStartDate && selectedEndDate != 0L,
                                isRangeEnd = dateMillis == selectedEndDate && selectedStartDate != 0L,
                                onClick = { onDateClick(dateMillis) },
                                modifier = Modifier.weight(1f)
                            )
                            dayCounter++
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    dateMillis: Long,
    isSelected: Boolean,
    isInRange: Boolean,
    isRangeStart: Boolean,
    isRangeEnd: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .then(
                if (isInRange && !isSelected) {
                    Modifier.background(Colors.Brand16)
                } else {
                    Modifier
                }
            )
            .then(
                if (isSelected) {
                    Modifier
                        .clip(CircleShape)
                        .background(Colors.Brand)
                } else {
                    Modifier
                }
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = day.toString(),
            color = when {
                isSelected -> Colors.White
                isInRange -> Colors.White
                else -> Colors.White80
            },
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

private fun adjustMonth(currentMillis: Long, monthOffset: Int): Long {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = currentMillis
        add(Calendar.MONTH, monthOffset)
    }
    return calendar.timeInMillis
}

private fun getMonthYearLabel(millis: Long): String {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = millis
    }
    val formatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    return formatter.format(calendar.time)
}

private fun formatSelectedDateRange(startMillis: Long, endMillis: Long): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    return if (endMillis == 0L || startMillis == endMillis) {
        // Single day selection
        formatter.format(startMillis)
    } else {
        // Date range selection
        "${formatter.format(startMillis)} - ${formatter.format(endMillis)}"
    }
}

@Preview(showSystemUi = true)
@Composable
private fun Preview() {
    AppThemeSurface {
        BottomSheetPreview {
            Content()
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun Preview2() {
    AppThemeSurface {
        BottomSheetPreview {
            Content(
                initialStartDate = Clock.System.now().minus(2.days).toEpochMilliseconds(),
                initialEndDate = Clock.System.now().toEpochMilliseconds(),
            )
        }
    }
}
