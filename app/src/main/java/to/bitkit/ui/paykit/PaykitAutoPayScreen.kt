package to.bitkit.ui.paykit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import to.bitkit.paykit.models.AutoPayRule
import to.bitkit.paykit.models.PeerSpendingLimit
import to.bitkit.paykit.viewmodels.AutoPayViewModel
import to.bitkit.ui.components.Title
import to.bitkit.ui.scaffold.AppTopBar
import to.bitkit.ui.scaffold.ScreenColumn

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
            titleText = "Auto-Pay Settings",
            onBackClick = onNavigateBack
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
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
                        Title(text = "Global Settings")

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
                                        Text(
                                            text = "Enable Auto-Pay",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "Automatically approve payments based on rules",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = settings.isEnabled,
                                        onCheckedChange = {
                                            viewModel.updateSettings(settings.copy(isEnabled = it))
                                        }
                                    )
                                }

                                OutlinedTextField(
                                    value = settings.globalDailyLimitSats.toString(),
                                    onValueChange = {
                                        val limit = it.toLongOrNull() ?: 0L
                                        viewModel.updateSettings(settings.copy(globalDailyLimitSats = limit))
                                    },
                                    label = { Text("Daily Limit (sats)") },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text(
                                    text = "Used: ${settings.currentDailySpentSats} / ${settings.globalDailyLimitSats} sats",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Peer Limits
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Title(text = "Peer Limits")

                        if (peerLimits.isEmpty()) {
                            Text(
                                text = "No peer limits configured",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        Title(text = "Auto-Pay Rules")

                        if (rules.isEmpty()) {
                            Text(
                                text = "No rules configured",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
            Text(
                text = limit.peerName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Limit: ${limit.limitSats} sats / ${limit.period}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Used: ${limit.spentSats} / ${limit.limitSats} sats",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = onToggleEnabled
                )
            }

            rule.maxAmountSats?.let {
                Text(
                    text = "Max amount: $it sats",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (rule.allowedMethods.isNotEmpty()) {
                Text(
                    text = "Methods: ${rule.allowedMethods.joinToString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
