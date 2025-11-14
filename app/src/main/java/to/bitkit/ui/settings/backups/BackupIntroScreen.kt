package to.bitkit.ui.settings.backups

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import to.bitkit.R
import to.bitkit.ui.components.BodyM
import to.bitkit.ui.components.BottomSheetPreview
import to.bitkit.ui.components.Display
import to.bitkit.ui.components.PrimaryButton
import to.bitkit.ui.components.SecondaryButton
import to.bitkit.ui.components.SheetSize
import to.bitkit.ui.scaffold.SheetTopBar
import to.bitkit.ui.shared.modifiers.sheetHeight
import to.bitkit.ui.shared.util.gradientBackground
import to.bitkit.ui.theme.AppThemeSurface
import to.bitkit.ui.theme.Colors
import to.bitkit.ui.utils.withAccent

@Composable
fun BackupIntroScreen(
    hasFunds: Boolean,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .gradientBackground()
            .navigationBarsPadding()
            .testTag("BackupIntroView")
    ) {
        SheetTopBar(stringResource(R.string.security__backup_wallet))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.safe),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("BackupIntroViewImage")
            )

            Display(
                text = stringResource(R.string.security__backup_title).withAccent(accentColor = Colors.Blue),
                color = Colors.White,
                modifier = Modifier
                    .testTag("BackupIntroViewTitle")
            )
            Spacer(Modifier.height(8.dp))
            BodyM(
                text = if (hasFunds) {
                    stringResource(R.string.security__backup_funds)
                } else {
                    stringResource(R.string.security__backup_funds_no)
                },
                color = Colors.White64,
                modifier = Modifier
                    .testTag("BackupIntroViewDescription")
            )
            Spacer(Modifier.height(32.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("BackupIntroViewButtons"),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SecondaryButton(
                    text = stringResource(R.string.common__later),
                    fullWidth = false,
                    onClick = onClose,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("BackupIntroViewCancel"),
                )

                PrimaryButton(
                    text = stringResource(R.string.security__backup_button),
                    fullWidth = false,
                    onClick = onConfirm,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("BackupIntroViewContinue"),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun Preview() {
    AppThemeSurface {
        BottomSheetPreview {
            BackupIntroScreen(
                onClose = {},
                onConfirm = {},
                hasFunds = true,
                modifier = Modifier.sheetHeight(SheetSize.MEDIUM),
            )
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun PreviewHasFunds() {
    AppThemeSurface {
        BottomSheetPreview {
            BackupIntroScreen(
                onClose = {},
                onConfirm = {},
                hasFunds = false,
                modifier = Modifier.sheetHeight(SheetSize.MEDIUM),
            )
        }
    }
}
