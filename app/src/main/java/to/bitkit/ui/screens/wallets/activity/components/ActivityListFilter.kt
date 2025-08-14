package to.bitkit.ui.screens.wallets.activity.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import to.bitkit.R
import to.bitkit.ui.components.SearchInput
import to.bitkit.ui.components.SearchInputIconButton
import to.bitkit.ui.theme.AppThemeSurface
import to.bitkit.ui.theme.Colors

@Composable
fun ActivityListFilter(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    hasTagFilter: Boolean,
    hasDateRangeFilter: Boolean,
    onTagClick: () -> Unit,
    onDateRangeClick: () -> Unit,
    tabs: List<ActivityTab>,
    currentTabIndex: Int,
    onTabChange: (ActivityTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current

    Column(modifier = modifier) {
        SearchInput(
            value = searchText,
            onValueChange = onSearchTextChange,
            modifier = Modifier.fillMaxWidth(),
            trailingContent = {
                SearchInputIconButton(
                    iconRes = R.drawable.ic_tag,
                    isActive = hasTagFilter,
                    onClick = {
                        focusManager.clearFocus()
                        onTagClick()
                    },
                    modifier = Modifier.testTag("TagsPrompt")
                )
                Spacer(modifier = Modifier.width(12.dp))
                SearchInputIconButton(
                    iconRes = R.drawable.ic_calendar,
                    isActive = hasDateRangeFilter,
                    onClick = {
                        focusManager.clearFocus()
                        onDateRangeClick()
                    },
                    modifier = Modifier.testTag("DatePicker")
                )
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column {
            TabRow(
                selectedTabIndex = currentTabIndex,
                containerColor = Color.Transparent,
                indicator = { tabPositions ->
                    if (currentTabIndex < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            color = Colors.Brand,
                            modifier = Modifier.tabIndicatorOffset(tabPositions[currentTabIndex])
                        )
                    }
                },
                divider = {
                    HorizontalDivider(thickness = 3.0.dp)
                }
            ) {
                tabs.map { tab ->
                    Tab(
                        text = { Text(tab.uiText) },
                        selected = tabs[currentTabIndex] == tab,
                        onClick = { onTabChange(tab) },
                        unselectedContentColor = Colors.White64,
                        modifier = Modifier
                            .testTag("Tab-${tab.name.lowercase()}")
                    )
                }
            }
        }
    }
}

enum class ActivityTab {
    ALL, SENT, RECEIVED, OTHER;

    val uiText: String
        @Composable
        get() = when (this) {
            ALL -> stringResource(R.string.wallet__activity_tabs__all)
            SENT -> stringResource(R.string.wallet__activity_tabs__sent)
            RECEIVED -> stringResource(R.string.wallet__activity_tabs__received)
            OTHER -> stringResource(R.string.wallet__activity_tabs__other)
        }
}

@Preview
@Composable
private fun Preview() {
    AppThemeSurface {
        ActivityListFilter(
            searchText = "",
            onSearchTextChange = {},
            hasTagFilter = false,
            onTagClick = {},
            hasDateRangeFilter = false,
            onDateRangeClick = {},
            tabs = ActivityTab.entries,
            currentTabIndex = 0,
            onTabChange = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}
