package to.bitkit.ui.screens.wallets.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DateRangePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Clock
import to.bitkit.R
import to.bitkit.ui.activityListViewModel
import to.bitkit.ui.appViewModel
import to.bitkit.ui.components.BottomSheetPreview
import to.bitkit.ui.components.PrimaryButton
import to.bitkit.ui.components.SecondaryButton
import to.bitkit.ui.components.SheetSize
import to.bitkit.ui.shared.modifiers.sheetHeight
import to.bitkit.ui.shared.util.gradientLinearBackground
import to.bitkit.ui.theme.AppThemeSurface
import to.bitkit.ui.theme.Colors
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeSelectorSheet() {
    val activity = activityListViewModel ?: return
    val app = appViewModel ?: return

    val startDate by activity.startDate.collectAsState()
    val endDate by activity.endDate.collectAsState()

    val dateRangeState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = startDate,
        initialSelectedEndDateMillis = endDate,
    )

    Content(
        dateRangeState = dateRangeState,
        onClearClick = {
            dateRangeState.setSelection(null, null)
            activity.clearDateRange()
            app.hideSheet()
        },
        onApplyClick = {
            activity.setDateRange(
                startDate = dateRangeState.selectedStartDateMillis,
                endDate = dateRangeState.selectedEndDateMillis,
            )
            app.hideSheet()
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Content(
    dateRangeState: DateRangePickerState = rememberDateRangePickerState(),
    onClearClick: () -> Unit = {},
    onApplyClick: () -> Unit = {},
) {
    val hasSelection = dateRangeState.selectedStartDateMillis != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .sheetHeight(SheetSize.CALENDAR)
            .gradientLinearBackground()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        DateRangePicker(
            state = dateRangeState,
            showModeToggle = false,
            colors = DatePickerDefaults.colors(
                containerColor = Color.Transparent,
                selectedDayContainerColor = Colors.Brand,
                dayInSelectionRangeContainerColor = Colors.Brand16,
            ),
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            SecondaryButton(
                onClick = onClearClick,
                text = stringResource(R.string.wallet__filter_clear),
                enabled = hasSelection,
                modifier = Modifier
                    .weight(1f)
                    .testTag("CalendarClearButton")
            )
            PrimaryButton(
                onClick = onApplyClick,
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

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showSystemUi = true)
@Composable
private fun Preview() {
    AppThemeSurface {
        BottomSheetPreview {
            Content(
                dateRangeState = rememberDateRangePickerState(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showSystemUi = true)
@Composable
private fun Preview2() {
    AppThemeSurface {
        BottomSheetPreview {
            Content(
                dateRangeState = rememberDateRangePickerState(
                    initialSelectedStartDateMillis = Clock.System.now().minus(2.days).toEpochMilliseconds(),
                    initialSelectedEndDateMillis = Clock.System.now().toEpochMilliseconds(),
                ),
            )
        }
    }
}
