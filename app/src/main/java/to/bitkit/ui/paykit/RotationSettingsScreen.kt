package to.bitkit.ui.paykit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import to.bitkit.ui.theme.AppSwitchDefaults
import to.bitkit.ui.components.Caption
import to.bitkit.ui.components.BodyM
import to.bitkit.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import to.bitkit.paykit.viewmodels.RotationSettingsViewModel
import to.bitkit.ui.components.Title
import to.bitkit.ui.scaffold.AppTopBar
import to.bitkit.ui.scaffold.ScreenColumn
import to.bitkit.ui.theme.Colors

@Composable
fun RotationSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: RotationSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ScreenColumn {
        AppTopBar(
            titleText = stringResource(R.string.paykit__rotation_settings), // TODO: Localize via Transifex
            onBackClick = onNavigateBack
        )

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Colors.Brand)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Global Settings
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Title(text = stringResource(R.string.paykit__global_settings))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                BodyM(
                                    text = stringResource(R.string.paykit__auto_rotate_enabled),
                                )
                                Caption(
                                    text = stringResource(R.string.paykit__automatically_rotate_endpoints_after_use),
                                    color = Colors.White64,
                                )
                            }
                            Switch(
                                checked = uiState.settings?.autoRotateEnabled ?: false,
                                onCheckedChange = { viewModel.updateAutoRotateEnabled(it) },
                                colors = AppSwitchDefaults.colors,
                            )
                        }

                        OutlinedTextField(
                            value = uiState.settings?.defaultPolicy ?: "on-use",
                            onValueChange = { viewModel.updateDefaultPolicy(it) },
                            label = { Text(stringResource(R.string.paykit__default_policy)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Method Settings
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Title(text = stringResource(R.string.paykit__method_settings))

                        val methodSettings = uiState.settings?.methodSettings
                        if (methodSettings.isNullOrEmpty()) {
                            BodyM(
                                text = stringResource(R.string.paykit__no_method_specific_settings),
                                    color = Colors.White64,
                            )
                        } else {
                            methodSettings.forEach { (methodId, settings) ->
                                MethodRotationCard(
                                    methodId = methodId,
                                    methodSettings = settings
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MethodRotationCard(
    methodId: String,
    methodSettings: to.bitkit.paykit.storage.MethodRotationSettings
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BodyM(
                text = methodId,
            )
            Caption(
                text = "Policy: ${methodSettings.policy}",
                    color = Colors.White64,
            )
            Caption(
                text = "Use count: ${methodSettings.useCount}",
                    color = Colors.White64,
            )
            Caption(
                text = "Rotations: ${methodSettings.rotationCount}",
                    color = Colors.White64,
            )
        }
    }
}
