package to.bitkit.ui.screens.wallets.activity.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import to.bitkit.R
import to.bitkit.ui.components.CaptionB
import to.bitkit.ui.components.SearchInput
import to.bitkit.ui.components.SearchInputIconButton
import to.bitkit.ui.shared.util.clickableAlpha
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = tabs[currentTabIndex] == tab

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickableAlpha { onTabChange(tab) }
                                .padding(vertical = 8.dp)
                                .testTag("Tab-${tab.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            CaptionB(
                                tab.uiText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isSelected) Colors.White else Colors.White50
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp) // 8dp total spacing
                                .height(3.dp)
                                .background(
                                    if (isSelected) Colors.Brand else Colors.White.copy(alpha = 0.2f)
                                )
                        )
                    }

                    if (index < tabs.size - 1) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
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
