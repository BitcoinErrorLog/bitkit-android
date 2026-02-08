package to.bitkit.ui.paykit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import to.bitkit.paykit.models.AutoPayRule
import to.bitkit.paykit.models.PeerSpendingLimit
import to.bitkit.paykit.viewmodels.AutoPayViewModel
import to.bitkit.ui.components.Title
import to.bitkit.ui.scaffold.AppTopBar
import to.bitkit.ui.scaffold.ScreenColumn
import to.bitkit.ui.theme.Colors

@Composable
fun PaykitAutoPayScreen(
    onNavigateBack: () -> Unit,
    viewModel: AutoPayViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val peerLimits by viewModel.peerLimits.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    ScreenColumn {
        AppTopBar(
            titleText = stringResource(R.string.paykit__auto_pay_settings), // TODO: Localize via Transifex
            onBackClick = onNavigateBack
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Colors.Brand)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Global Settings
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Title(text = stringResource(R.string.paykit__global_settings))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        BodyM(
                                            text = stringResource(R.string.paykit__enable_auto_pay),
                                        )
                                        Caption(
                                            text = stringResource(R.string.paykit__automatically_approve_payments_based_on_rules),
                                            color = Colors.White64,
                                        )
                                    }
                                    Switch(
                                        checked = settings.isEnabled,
                                        onCheckedChange = {
                                            viewModel.updateSettings(settings.copy(isEnabled = it))
                                        },
                                        colors = AppSwitchDefaults.colors,
                                    )
                                }

                                OutlinedTextField(
                                    value = settings.globalDailyLimitSats.toString(),
                                    onValueChange = {
                                        val limit = it.toLongOrNull() ?: 0L
                                        viewModel.updateSettings(settings.copy(globalDailyLimitSats = limit))
                                    },
                                    label = { Text(stringResource(R.string.paykit__daily_limit_sats)) },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Caption(
                                    text = "Used: ${settings.currentDailySpentSats} / ${settings.globalDailyLimitSats} sats",
                                        color = Colors.White64,
                                )
                            }
                        }
                    }
                }

                // Peer Limits
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Title(text = stringResource(R.string.paykit__peer_limits))

                        if (peerLimits.isEmpty()) {
                            BodyM(
                                text = stringResource(R.string.paykit__no_peer_limits_configured),
                                    color = Colors.White64,
                            )
                        } else {
                            peerLimits.forEach { limit ->
                                PeerLimitCard(limit = limit)
                            }
                        }
                    }
                }

                // Rules
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Title(text = stringResource(R.string.paykit__auto_pay_rules))

                        if (rules.isEmpty()) {
                            BodyM(
                                text = stringResource(R.string.paykit__no_rules_configured),
                                    color = Colors.White64,
                            )
                        } else {
                            rules.forEach { rule ->
                                RuleCard(
                                    rule = rule,
                                    onToggleEnabled = { enabled ->
                                        viewModel.updateRule(rule.copy(isEnabled = enabled))
                                    }
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
fun PeerLimitCard(limit: PeerSpendingLimit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BodyM(
                text = limit.peerName,
            )
            Caption(
                text = "Limit: ${limit.limitSats} sats / ${limit.period}",
                    color = Colors.White64,
            )
            Caption(
                text = "Used: ${limit.spentSats} / ${limit.limitSats} sats",
                    color = Colors.White64,
            )
        }
    }
}

@Composable
fun RuleCard(
    rule: AutoPayRule,
    onToggleEnabled: (Boolean) -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BodyM(
                    text = rule.name,
                )
                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = AppSwitchDefaults.colors,
                )
            }

            rule.maxAmountSats?.let {
                Caption(
                    text = "Max amount: $it sats",
                        color = Colors.White64,
                )
            }

            if (rule.allowedMethods.isNotEmpty()) {
                Caption(
                    text = "Methods: ${rule.allowedMethods.joinToString()}",
                        color = Colors.White64,
                )
            }
        }
    }
}
